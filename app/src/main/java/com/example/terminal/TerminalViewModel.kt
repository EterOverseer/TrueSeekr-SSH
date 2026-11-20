package com.example.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SnippetRepository
import com.example.domain.CommandSnippet
import com.example.domain.ServerMetrics
import com.example.ssh.ManagedSessionUiState
import com.example.ssh.SshSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MetricsLoadState {
    data object Idle : MetricsLoadState
    data object Loading : MetricsLoadState
    data class Loaded(val metrics: ServerMetrics) : MetricsLoadState
    data class Error(val message: String) : MetricsLoadState
}

class TerminalViewModel(
    private val sshSessionManager: SshSessionManager,
    private val snippetRepository: SnippetRepository
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

    private val _metricsState = MutableStateFlow<MetricsLoadState>(MetricsLoadState.Idle)
    val metricsState: StateFlow<MetricsLoadState> = _metricsState.asStateFlow()

    fun selectTab(sessionId: String) {
        sshSessionManager.selectSession(sessionId)
    }

    fun getHandle(sessionId: String): TerminalSessionHandle? {
        return sshSessionManager.getRuntime(sessionId)?.terminalHandle
    }

    fun reconnect(sessionId: String) {
        sshSessionManager.reconnectSession(sessionId)
    }

    fun disconnect(sessionId: String) {
        sshSessionManager.disconnectSession(sessionId)
    }

    fun closeTab(sessionId: String) {
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
        // Never append newline automatically; insert command text only
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

    fun loadServerMetrics(sessionId: String) {
        _metricsState.value = MetricsLoadState.Loading
        viewModelScope.launch {
            val result = sshSessionManager.fetchServerMetrics(sessionId)
            _metricsState.value = result.fold(
                onSuccess = { MetricsLoadState.Loaded(it) },
                onFailure = { MetricsLoadState.Error(it.message ?: "ไม่สามารถอ่านข้อมูลเซิร์ฟเวอร์ได้") }
            )
        }
    }

    class Factory(
        private val sshSessionManager: SshSessionManager,
        private val snippetRepository: SnippetRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TerminalViewModel(sshSessionManager, snippetRepository) as T
        }
    }
}
