package com.example.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SnippetRepository
import com.example.domain.CommandSnippet
import com.example.domain.HealthRefreshInterval
import com.example.domain.ManagedTmuxSession
import com.example.domain.RemoteTmuxSession
import com.example.domain.ServerMetrics
import com.example.domain.TmuxCheckResult
import com.example.settings.AppPreferencesRepository
import com.example.ssh.ManagedSessionUiState
import com.example.ssh.SshSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface MetricsLoadState {
    data object Idle : MetricsLoadState
    data object Loading : MetricsLoadState
    data class Loaded(
        val metrics: ServerMetrics,
        val isRefreshing: Boolean = false,
        val staleWarning: String? = null
    ) : MetricsLoadState
    data class Unsupported(val detectedOs: String, val reason: String) : MetricsLoadState
    data class Error(val message: String, val cachedMetrics: ServerMetrics? = null) : MetricsLoadState
}

data class TmuxSheetUiState(
    val isChecking: Boolean = false,
    val checkResult: TmuxCheckResult? = null,
    val remoteSessions: List<RemoteTmuxSession> = emptyList(),
    val savedProfileSessions: List<ManagedTmuxSession> = emptyList(),
    val isActionRunning: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class TerminalViewModel(
    private val sshSessionManager: SshSessionManager,
    private val snippetRepository: SnippetRepository,
    private val preferencesRepository: AppPreferencesRepository? = null
) : ViewModel() {

    val sessions: StateFlow<List<ManagedSessionUiState>> = sshSessionManager.sessions
    val activeSessionId: StateFlow<String?> = sshSessionManager.activeSessionId

    val snippets: StateFlow<List<CommandSnippet>> = snippetRepository.allSnippets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchVisible = MutableStateFlow(false)
    val searchVisible: StateFlow<Boolean> = _searchVisible.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchMatches = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    val searchMatches: StateFlow<List<Pair<Int, String>>> = _searchMatches.asStateFlow()

    private val _metricsBySession = MutableStateFlow<Map<String, MetricsLoadState>>(emptyMap())
    val metricsBySession: StateFlow<Map<String, MetricsLoadState>> = _metricsBySession.asStateFlow()

    // Preserved single-state view of currently selected/polled session for backwards compatibility
    private val _metricsState = MutableStateFlow<MetricsLoadState>(MetricsLoadState.Idle)
    val metricsState: StateFlow<MetricsLoadState> = _metricsState.asStateFlow()

    private val _dashboardInterval = MutableStateFlow(HealthRefreshInterval.FIVE_SECONDS)
    val dashboardInterval: StateFlow<HealthRefreshInterval> = _dashboardInterval.asStateFlow()

    private val _tmuxUiState = MutableStateFlow(TmuxSheetUiState())
    val tmuxUiState: StateFlow<TmuxSheetUiState> = _tmuxUiState.asStateFlow()

    private val _reconnectTmuxSuggestions = MutableStateFlow<Map<String, List<RemoteTmuxSession>>>(emptyMap())
    val reconnectTmuxSuggestions: StateFlow<Map<String, List<RemoteTmuxSession>>> = _reconnectTmuxSuggestions.asStateFlow()

    private var dashboardPollJob: Job? = null
    private var polledSessionId: String? = null

    init {
        preferencesRepository?.let { repo ->
            viewModelScope.launch {
                val initial = repo.settings.first()
                _dashboardInterval.value = initial.healthRefreshInterval
            }
        }
        viewModelScope.launch {
            sessions.collect { currentSessions ->
                val activeIds = currentSessions.map { it.sessionId }.toSet()
                // Clean up dashboard & tmux caches for closed tabs
                _metricsBySession.update { map -> map.filterKeys { it in activeIds } }
                _reconnectTmuxSuggestions.update { map -> map.filterKeys { it in activeIds } }
                val polled = polledSessionId
                if (polled != null) {
                    val session = currentSessions.firstOrNull { it.sessionId == polled }
                    if (session == null || !session.status.isLiveConnected) {
                        stopDashboardPolling()
                    }
                }
            }
        }
    }

    fun selectTab(sessionId: String) {
        if (polledSessionId != null && polledSessionId != sessionId) {
            stopDashboardPolling()
        }
        sshSessionManager.selectSession(sessionId)
        _metricsState.value = _metricsBySession.value[sessionId] ?: MetricsLoadState.Idle
    }

    fun getHandle(sessionId: String): TerminalSessionHandle? {
        return sshSessionManager.getRuntime(sessionId)?.terminalHandle
    }

    fun reconnect(sessionId: String) {
        stopDashboardPolling()
        sshSessionManager.reconnectSession(sessionId)
    }

    fun disconnect(sessionId: String) {
        stopDashboardPolling()
        _reconnectTmuxSuggestions.update { it - sessionId }
        sshSessionManager.disconnectSession(sessionId)
    }

    fun closeTab(sessionId: String) {
        if (polledSessionId == sessionId) {
            stopDashboardPolling()
        }
        _metricsBySession.update { it - sessionId }
        _reconnectTmuxSuggestions.update { it - sessionId }
        sshSessionManager.closeSessionTab(sessionId)
    }

    fun openTailscaleApp(): Boolean = sshSessionManager.openTailscaleApp()

    fun openTailscalePlayStore(): Boolean = sshSessionManager.openTailscalePlayStore()

    fun toggleSearch() {
        val next = !_searchVisible.value
        _searchVisible.value = next
        if (!next) {
            _searchQuery.value = ""
            _searchMatches.value = emptyList()
        }
    }

    fun updateSearchQuery(query: String, sessionId: String?) {
        _searchQuery.value = query
        val handle = sessionId?.let { getHandle(it) }
        _searchMatches.value = handle?.scrollbackBuffer?.search(query).orEmpty()
    }

    fun insertSnippetWithoutAutoExecute(sessionId: String?, command: String) {
        val handle = sessionId?.let { getHandle(it) } ?: return
        handle.insertTextWithoutExecuting(command.trimEnd('\r', '\n'))
    }

    fun saveSnippet(snippet: CommandSnippet) {
        viewModelScope.launch {
            snippetRepository.saveSnippet(snippet)
        }
    }

    fun deleteSnippet(id: String) {
        viewModelScope.launch {
            snippetRepository.deleteSnippet(id)
        }
    }

    fun setDashboardInterval(sessionId: String, interval: HealthRefreshInterval) {
        _dashboardInterval.value = interval
        startDashboardPolling(sessionId, interval)
    }

    fun startDashboardPolling(
        sessionId: String,
        interval: HealthRefreshInterval = _dashboardInterval.value
    ) {
        dashboardPollJob?.cancel()
        polledSessionId = sessionId
        dashboardPollJob = viewModelScope.launch {
            fetchAndUpdateMetrics(sessionId, forceSlow = true)
            if (interval == HealthRefreshInterval.MANUAL) return@launch

            val intervalMs = interval.seconds.coerceAtLeast(3) * 1000L
            while (isActive) {
                delay(intervalMs)
                val session = sessions.value.firstOrNull { it.sessionId == sessionId }
                if (session == null || !session.status.isLiveConnected) {
                    break
                }
                val current = _metricsBySession.value[sessionId]
                if (current is MetricsLoadState.Unsupported) {
                    break
                }
                fetchAndUpdateMetrics(sessionId, forceSlow = false)
            }
        }
    }

    fun stopDashboardPolling() {
        dashboardPollJob?.cancel()
        dashboardPollJob = null
        polledSessionId = null
    }

    fun loadServerMetrics(sessionId: String, forceSlow: Boolean = true) {
        viewModelScope.launch {
            fetchAndUpdateMetrics(sessionId, forceSlow = forceSlow)
        }
    }

    private suspend fun fetchAndUpdateMetrics(sessionId: String, forceSlow: Boolean) {
        val existing = _metricsBySession.value[sessionId]
        val previousMetrics = when (existing) {
            is MetricsLoadState.Loaded -> existing.metrics
            is MetricsLoadState.Error -> existing.cachedMetrics
            else -> null
        }

        val initialState = if (previousMetrics != null) {
            MetricsLoadState.Loaded(metrics = previousMetrics, isRefreshing = true)
        } else {
            MetricsLoadState.Loading
        }
        updateMetricsState(sessionId, initialState)

        val result = sshSessionManager.fetchServerMetrics(sessionId, forceSlowRefresh = forceSlow)
        val nextState = result.fold(
            onSuccess = { metrics ->
                if (!metrics.isLinux) {
                    MetricsLoadState.Unsupported(
                        detectedOs = metrics.detectedOsKernelName ?: "Unknown",
                        reason = metrics.availabilityNote ?: "รองรับเฉพาะเซิร์ฟเวอร์ Linux"
                    )
                } else {
                    MetricsLoadState.Loaded(metrics = metrics, isRefreshing = false, staleWarning = null)
                }
            },
            onFailure = { err ->
                val msg = err.message ?: "ไม่สามารถอ่านข้อมูลเซิร์ฟเวอร์ได้"
                if (previousMetrics != null) {
                    MetricsLoadState.Loaded(
                        metrics = previousMetrics,
                        isRefreshing = false,
                        staleWarning = "อัปเดตล่าสุดไม่สำเร็จ: $msg"
                    )
                } else {
                    MetricsLoadState.Error(msg, cachedMetrics = null)
                }
            }
        )
        updateMetricsState(sessionId, nextState)
    }

    private fun updateMetricsState(sessionId: String, state: MetricsLoadState) {
        _metricsBySession.update { it + (sessionId to state) }
        if (activeSessionId.value == sessionId || polledSessionId == sessionId) {
            _metricsState.value = state
        }
    }

    fun checkReconnectTmuxSuggestions(sessionId: String, profileId: String) {
        viewModelScope.launch {
            val saved = sshSessionManager.getSavedTmuxSessionsForProfile(profileId)
            if (saved.isEmpty()) return@launch
            val remote = sshSessionManager.listRemoteTmuxSessions(sessionId).getOrNull() ?: return@launch
            val managedAlive = remote.filter { it.isManagedByApp && saved.any { s -> s.sessionName == it.name } }
            if (managedAlive.isNotEmpty()) {
                _reconnectTmuxSuggestions.update { it + (sessionId to managedAlive) }
            }
        }
    }

    fun dismissReconnectTmuxSuggestion(sessionId: String) {
        _reconnectTmuxSuggestions.update { it - sessionId }
    }

    fun openTmuxSheet(sessionId: String, profileId: String) {
        _tmuxUiState.value = TmuxSheetUiState(isChecking = true)
        viewModelScope.launch {
            val saved = sshSessionManager.getSavedTmuxSessionsForProfile(profileId)
            val check = sshSessionManager.checkTmuxAvailability(sessionId)
            val remoteList = if (check is TmuxCheckResult.Available) {
                sshSessionManager.listRemoteTmuxSessions(sessionId).getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _tmuxUiState.value = TmuxSheetUiState(
                isChecking = false,
                checkResult = check,
                remoteSessions = remoteList,
                savedProfileSessions = saved
            )
        }
    }

    fun refreshTmuxSessions(sessionId: String, profileId: String) {
        viewModelScope.launch {
            _tmuxUiState.update { it.copy(isChecking = true, errorMessage = null, feedbackMessage = null) }
            val saved = sshSessionManager.getSavedTmuxSessionsForProfile(profileId)
            val listRes = sshSessionManager.listRemoteTmuxSessions(sessionId)
            _tmuxUiState.update { cur ->
                listRes.fold(
                    onSuccess = { remote ->
                        cur.copy(
                            isChecking = false,
                            remoteSessions = remote,
                            savedProfileSessions = saved
                        )
                    },
                    onFailure = { err ->
                        cur.copy(
                            isChecking = false,
                            errorMessage = err.message ?: "อ่านรายการ tmux sessions ไม่สำเร็จ"
                        )
                    }
                )
            }
        }
    }

    fun attachOrCreateTmux(sessionId: String, customName: String? = null, onAttached: () -> Unit = {}) {
        viewModelScope.launch {
            _tmuxUiState.update { it.copy(isActionRunning = true, errorMessage = null, feedbackMessage = null) }
            val res = sshSessionManager.attachOrCreateTmuxSession(sessionId, customName)
            res.fold(
                onSuccess = { name ->
                    _reconnectTmuxSuggestions.update { it - sessionId }
                    _tmuxUiState.update {
                        it.copy(
                            isActionRunning = false,
                            feedbackMessage = "เชื่อมต่อ tmux session '$name' แล้ว"
                        )
                    }
                    onAttached()
                },
                onFailure = { err ->
                    _tmuxUiState.update {
                        it.copy(
                            isActionRunning = false,
                            errorMessage = err.message ?: "เปิด tmux session ไม่สำเร็จ"
                        )
                    }
                }
            )
        }
    }

    fun detachTmux(sessionId: String) {
        viewModelScope.launch {
            sshSessionManager.detachCurrentTmuxSession(sessionId).fold(
                onSuccess = {
                    _tmuxUiState.update { it.copy(feedbackMessage = "ส่งคำสั่ง Detach (Ctrl+B d) แล้ว") }
                },
                onFailure = { err ->
                    _tmuxUiState.update { it.copy(errorMessage = err.message ?: "Detach ไม่สำเร็จ") }
                }
            )
        }
    }

    fun killTmuxSession(sessionId: String, profileId: String, sessionName: String) {
        viewModelScope.launch {
            _tmuxUiState.update { it.copy(isActionRunning = true, errorMessage = null) }
            sshSessionManager.killTmuxSession(sessionId, sessionName).fold(
                onSuccess = {
                    _tmuxUiState.update {
                        it.copy(
                            isActionRunning = false,
                            feedbackMessage = "ปิด tmux session '$sessionName' แล้ว"
                        )
                    }
                    refreshTmuxSessions(sessionId, profileId)
                },
                onFailure = { err ->
                    _tmuxUiState.update {
                        it.copy(
                            isActionRunning = false,
                            errorMessage = err.message ?: "ปิด tmux session ไม่สำเร็จ"
                        )
                    }
                }
            )
        }
    }

    override fun onCleared() {
        stopDashboardPolling()
        super.onCleared()
    }

    class Factory(
        private val sshSessionManager: SshSessionManager,
        private val snippetRepository: SnippetRepository,
        private val preferencesRepository: AppPreferencesRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TerminalViewModel(sshSessionManager, snippetRepository, preferencesRepository) as T
        }
    }
}
