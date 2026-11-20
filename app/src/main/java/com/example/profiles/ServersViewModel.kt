package com.example.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.NetworkMonitor
import com.example.core.NetworkStatus
import com.example.data.KnownHostRepository
import com.example.data.ProfileRepository
import com.example.domain.AuthMethodType
import com.example.domain.KnownHost
import com.example.domain.ProfileGroup
import com.example.domain.ProfileSortOrder
import com.example.domain.RecentConnection
import com.example.domain.SshProfile
import com.example.domain.TerminalThemePreset
import com.example.security.BackupCryptoManager
import com.example.security.BackupImportResult
import com.example.security.CredentialVault
import com.example.security.ExportBackupPreview
import com.example.security.ImportDuplicateStrategy
import com.example.security.ImportPreviewSummary
import com.example.security.ProfileBackupBundle
import com.example.settings.AppPreferencesRepository
import com.example.settings.AppSettings
import com.example.ssh.ManagedSessionUiState
import com.example.ssh.SshSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.util.UUID

data class ProfileEditorDraft(
    val existingId: String? = null,
    val name: String = "",
    val host: String = "",
    val portText: String = "22",
    val username: String = "",
    val authMethod: AuthMethodType = AuthMethodType.PASSWORD,
    val description: String = "",
    val groupName: String = "Home",
    val tagsText: String = "",
    val isFavorite: Boolean = false,
    val connectTimeoutText: String = "15",
    val keepAliveText: String = "30",
    val terminalType: String = "xterm-256color",
    val terminalFontSizeSp: Float = 13f,
    val terminalTheme: TerminalThemePreset = TerminalThemePreset.CHARCOAL_LIME,
    val initialWorkingDir: String = "",
    val autoTailscaleConnect: Boolean = false,
    val passwordInput: String = "",
    val privateKeyInput: String = "",
    val passphraseInput: String = "",
    val existingCredentialRefId: String? = null,
    val hasStoredCredential: Boolean = false,
    val clearExistingCredential: Boolean = false,
    val isSaving: Boolean = false,
    val isPersistedReady: Boolean = false,
    val duplicateWarningProfiles: List<SshProfile> = emptyList(),
    val validationError: String? = null
)

data class PendingDeleteConfirmation(
    val profiles: List<SshProfile>,
    val activeSessionsForTargets: List<ManagedSessionUiState>,
    val fromEditor: Boolean = false
) {
    val isBulk: Boolean get() = profiles.size > 1
    val hasActiveSessions: Boolean get() = activeSessionsForTargets.isNotEmpty()
    val hasAnyStoredCredential: Boolean get() = profiles.any { !it.credentialRefId.isNullOrBlank() }
}

data class DeletedProfilesUndoState(
    val nonSecretProfiles: List<SshProfile>,
    val message: String,
    val removedCredentialsCount: Int = 0
)

data class ServersUiState(
    val allProfiles: List<SshProfile> = emptyList(),
    val filteredProfiles: List<SshProfile> = emptyList(),
    val favoriteProfiles: List<SshProfile> = emptyList(),
    val recentEntries: List<Pair<SshProfile, RecentConnection>> = emptyList(),
    val groups: List<ProfileGroup> = emptyList(),
    val activeSessions: List<ManagedSessionUiState> = emptyList(),
    val trustedHostKeys: Map<String, String> = emptyMap(),
    val searchQuery: String = "",
    val selectedGroup: String? = null,
    val sortOrder: ProfileSortOrder = ProfileSortOrder.NAME_ASC,
    val networkStatus: NetworkStatus = NetworkStatus(),
    val messageBanner: String? = null,
    val errorBanner: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedProfileIds: Set<String> = emptySet(),
    val pendingDelete: PendingDeleteConfirmation? = null,
    val undoDeleteState: DeletedProfilesUndoState? = null
)

data class PendingImportReview(
    val rawBundle: ProfileBackupBundle,
    val preview: ImportPreviewSummary
)

class ServersViewModel(
    private val profileRepository: ProfileRepository,
    private val credentialVault: CredentialVault,
    private val sshSessionManager: SshSessionManager,
    private val preferencesRepository: AppPreferencesRepository,
    networkMonitor: NetworkMonitor,
    knownHostRepository: KnownHostRepository? = null
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedGroup = MutableStateFlow<String?>(null)
    private val sortOrder = MutableStateFlow(ProfileSortOrder.NAME_ASC)
    private val messageBanner = MutableStateFlow<String?>(null)
    private val errorBanner = MutableStateFlow<String?>(null)
    private val isSelectionMode = MutableStateFlow(false)
    private val selectedProfileIds = MutableStateFlow<Set<String>>(emptySet())
    private val pendingDelete = MutableStateFlow<PendingDeleteConfirmation?>(null)
    private val undoDeleteState = MutableStateFlow<DeletedProfilesUndoState?>(null)

    private val _editorDraft = MutableStateFlow<ProfileEditorDraft?>(null)
    val editorDraft: StateFlow<ProfileEditorDraft?> = _editorDraft.asStateFlow()

    private val _pendingImportReview = MutableStateFlow<PendingImportReview?>(null)
    val pendingImportReview: StateFlow<PendingImportReview?> = _pendingImportReview.asStateFlow()

    val tailscaleAutomationEnabled: StateFlow<Boolean> = preferencesRepository.settings
        .combine(MutableStateFlow(Unit)) { settings, _ -> settings.tailscaleAutomationEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val knownHostsFlow = knownHostRepository?.allKnownHosts ?: flowOf(emptyList())

    private val filterParamsFlow = combine(
        searchQuery,
        selectedGroup,
        sortOrder,
        networkMonitor.status,
        messageBanner,
        knownHostsFlow
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        FilterParams(
            query = arr[0] as String,
            selectedGroup = arr[1] as String?,
            sortOrder = arr[2] as ProfileSortOrder,
            networkStatus = arr[3] as NetworkStatus,
            messageBanner = arr[4] as String?,
            knownHosts = arr[5] as List<KnownHost>
        )
    }

    private val managementParamsFlow = combine(
        errorBanner,
        isSelectionMode,
        selectedProfileIds,
        pendingDelete,
        undoDeleteState
    ) { err, selectionMode, selectedIds, pending, undo ->
        ManagementParams(
            errorBanner = err,
            isSelectionMode = selectionMode,
            selectedProfileIds = selectedIds,
            pendingDelete = pending,
            undoDeleteState = undo
        )
    }

    private val combinedUiMetaFlow = combine(filterParamsFlow, managementParamsFlow) { f, m -> f to m }

    val uiState: StateFlow<ServersUiState> = combine(
        profileRepository.allProfiles,
        profileRepository.allGroups,
        profileRepository.recentConnections,
        sshSessionManager.sessions,
        combinedUiMetaFlow
    ) { profiles, groups, recent, sessions, (params, mgmt) ->
        val query = params.query.trim()
        val filtered = profiles
            .filter { profile ->
                val matchesGroup = params.selectedGroup == null || profile.groupName == params.selectedGroup
                val matchesQuery = query.isEmpty() ||
                    profile.name.contains(query, ignoreCase = true) ||
                    profile.host.contains(query, ignoreCase = true) ||
                    profile.username.contains(query, ignoreCase = true) ||
                    profile.port.toString().contains(query) ||
                    profile.groupName.contains(query, ignoreCase = true) ||
                    profile.tags.any { it.contains(query, ignoreCase = true) }
                matchesGroup && matchesQuery
            }
            .let { list ->
                when (params.sortOrder) {
                    ProfileSortOrder.NAME_ASC -> list.sortedWith(
                        compareByDescending<SshProfile> { it.isFavorite }.thenBy { it.name.lowercase() }
                    )
                    ProfileSortOrder.LAST_CONNECTED_DESC -> list.sortedWith(
                        compareByDescending<SshProfile> { it.lastConnectedAt ?: 0L }
                            .thenByDescending { it.updatedAt }
                    )
                    ProfileSortOrder.CREATED_DESC -> list.sortedWith(
                        compareByDescending<SshProfile> { it.createdAt }.thenBy { it.name.lowercase() }
                    )
                    ProfileSortOrder.HOST_ASC -> list.sortedWith(
                        compareBy<SshProfile> { it.host.lowercase() }.thenBy { it.port }
                    )
                    ProfileSortOrder.MOST_USED_DESC -> list.sortedWith(
                        compareByDescending<SshProfile> { it.connectionCount }
                            .thenByDescending { it.lastConnectedAt ?: 0L }
                    )
                }
            }

        val profileById = profiles.associateBy { it.id }
        val recentPairs = recent.mapNotNull { rec ->
            profileById[rec.profileId]?.let { prof -> prof to rec }
        }

        val trustedMap = params.knownHosts.associate { kh ->
            "${kh.host.trim().lowercase()}:${kh.port}" to kh.sha256Fingerprint
        }

        val validSelectedIds = mgmt.selectedProfileIds.intersect(profileById.keys)

        ServersUiState(
            allProfiles = profiles,
            filteredProfiles = filtered,
            favoriteProfiles = profiles.filter { it.isFavorite },
            recentEntries = recentPairs,
            groups = groups,
            activeSessions = sessions,
            trustedHostKeys = trustedMap,
            searchQuery = params.query,
            selectedGroup = params.selectedGroup,
            sortOrder = params.sortOrder,
            networkStatus = params.networkStatus,
            messageBanner = params.messageBanner,
            errorBanner = mgmt.errorBanner,
            isSelectionMode = mgmt.isSelectionMode,
            selectedProfileIds = validSelectedIds,
            pendingDelete = mgmt.pendingDelete,
            undoDeleteState = mgmt.undoDeleteState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ServersUiState()
    )

    private data class FilterParams(
        val query: String,
        val selectedGroup: String?,
        val sortOrder: ProfileSortOrder,
        val networkStatus: NetworkStatus,
        val messageBanner: String?,
        val knownHosts: List<KnownHost>
    )

    private data class ManagementParams(
        val errorBanner: String?,
        val isSelectionMode: Boolean,
        val selectedProfileIds: Set<String>,
        val pendingDelete: PendingDeleteConfirmation?,
        val undoDeleteState: DeletedProfilesUndoState?
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectGroupFilter(groupName: String?) {
        selectedGroup.value = groupName
    }

    fun setSortOrder(order: ProfileSortOrder) {
        sortOrder.value = order
    }

    fun clearBanner() {
        messageBanner.value = null
    }

    fun clearErrorBanner() {
        errorBanner.value = null
    }

    fun openCreateEditor() {
        viewModelScope.launch {
            val defaults: AppSettings = preferencesRepository.settings.first()
            _editorDraft.value = ProfileEditorDraft(
                groupName = selectedGroup.value ?: "Home",
                connectTimeoutText = defaults.defaultConnectTimeoutSec.toString(),
                keepAliveText = defaults.defaultKeepAliveSec.toString(),
                terminalType = defaults.defaultTermType,
                terminalFontSizeSp = defaults.defaultTerminalFontSizeSp,
                terminalTheme = defaults.defaultTerminalTheme,
                autoTailscaleConnect = false,
                isPersistedReady = false
            )
        }
    }

    fun openEditEditor(profile: SshProfile) {
        val hasCred = credentialVault.hasCredential(profile.credentialRefId)
        _editorDraft.value = ProfileEditorDraft(
            existingId = profile.id,
            name = profile.name,
            host = profile.host,
            portText = profile.port.toString(),
            username = profile.username,
            authMethod = profile.authMethod,
            description = profile.description,
            groupName = profile.groupName,
            tagsText = profile.tags.joinToString(", "),
            isFavorite = profile.isFavorite,
            connectTimeoutText = profile.connectTimeoutSec.toString(),
            keepAliveText = profile.keepAliveIntervalSec.toString(),
            terminalType = profile.terminalType,
            terminalFontSizeSp = profile.terminalFontSizeSp,
            terminalTheme = profile.terminalTheme,
            initialWorkingDir = profile.initialWorkingDir,
            autoTailscaleConnect = profile.autoTailscaleConnect,
            existingCredentialRefId = profile.credentialRefId,
            hasStoredCredential = hasCred,
            isPersistedReady = true
        )
    }

    fun closeEditor() {
        _editorDraft.value = null
    }

    fun updateEditorDraft(transform: (ProfileEditorDraft) -> ProfileEditorDraft) {
        val updated = _editorDraft.updateAndGet { current ->
            current?.let(transform)?.copy(validationError = null)
        }
        if (updated != null && updated.host.isNotBlank() && updated.username.isNotBlank()) {
            val port = updated.portText.toIntOrNull() ?: 22
            viewModelScope.launch {
                val dupes = profileRepository.findPotentialDuplicates(
                    host = updated.host,
                    port = port,
                    username = updated.username,
                    excludeId = updated.existingId
                )
                _editorDraft.update { it?.copy(duplicateWarningProfiles = dupes) }
            }
        }
    }

    fun saveEditorProfile(
        connectAfterSave: Boolean,
        onConnectedNavigateToTerminal: () -> Unit
    ) {
        val draft = _editorDraft.value ?: return
        val name = draft.name.trim()
        val host = draft.host.trim()
        val username = draft.username.trim()
        val port = draft.portText.toIntOrNull()
        val timeout = draft.connectTimeoutText.toIntOrNull() ?: 15
        val keepAlive = draft.keepAliveText.toIntOrNull() ?: 30

        val error = when {
            name.isBlank() -> "กรุณาระบุชื่อโปรไฟล์ (Profile Name)"
            host.isBlank() -> "กรุณาระบุ Hostname หรือ IP Address"
            port == null || port !in 1..65535 -> "Port ต้องอยู่ระหว่าง 1 - 65535"
            username.isBlank() -> "กรุณาระบุ Username"
            else -> null
        }

        if (error != null) {
            _editorDraft.update { it?.copy(validationError = error) }
            return
        }

        _editorDraft.update { it?.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            try {
                val existing = draft.existingId?.let { profileRepository.getProfileById(it) }
                val tags = draft.tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val targetId = draft.existingId ?: UUID.randomUUID().toString()
                val profile = SshProfile(
                    id = targetId,
                    name = name,
                    host = host,
                    port = port!!,
                    username = username,
                    authMethod = draft.authMethod,
                    description = draft.description.trim(),
                    groupName = draft.groupName.ifBlank { "Home" },
                    tags = tags,
                    isFavorite = draft.isFavorite,
                    connectTimeoutSec = timeout.coerceIn(5, 120),
                    keepAliveIntervalSec = keepAlive.coerceIn(0, 300),
                    terminalType = draft.terminalType.ifBlank { "xterm-256color" },
                    terminalFontSizeSp = draft.terminalFontSizeSp.coerceIn(9f, 24f),
                    terminalTheme = draft.terminalTheme,
                    initialWorkingDir = draft.initialWorkingDir.trim(),
                    autoTailscaleConnect = draft.autoTailscaleConnect,
                    credentialRefId = draft.existingCredentialRefId,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    lastConnectedAt = existing?.lastConnectedAt,
                    connectionCount = existing?.connectionCount ?: 0
                )

                val saved = profileRepository.saveProfile(
                    profile = profile,
                    newPasswordChars = draft.passwordInput.takeIf { it.isNotEmpty() }?.toCharArray(),
                    newPrivateKeyPem = draft.privateKeyInput.takeIf { it.isNotBlank() },
                    clearExistingCredential = draft.clearExistingCredential
                )

                val passphraseChars = draft.passphraseInput.takeIf { it.isNotEmpty() }?.toCharArray()
                _editorDraft.value = null
                messageBanner.value = "บันทึกโปรไฟล์ \"${saved.name}\" เรียบร้อยแล้ว"

                if (connectAfterSave) {
                    sshSessionManager.connectProfile(
                        profile = saved,
                        oneTimePassphrase = passphraseChars,
                        onConnectedNavigateToTerminal = onConnectedNavigateToTerminal
                    )
                }
            } catch (t: Throwable) {
                _editorDraft.update {
                    it?.copy(
                        isSaving = false,
                        validationError = "บันทึกโปรไฟล์ไม่สำเร็จ: ${t.message}"
                    )
                }
            }
        }
    }

    fun connectSavedProfile(profile: SshProfile, onNavigateToTerminal: () -> Unit) {
        viewModelScope.launch {
            val fresh = profileRepository.getProfileById(profile.id) ?: profile
            sshSessionManager.connectProfile(
                profile = fresh,
                onConnectedNavigateToTerminal = onNavigateToTerminal
            )
        }
    }

    fun selectActiveSession(sessionId: String, onNavigateToTerminal: () -> Unit) {
        sshSessionManager.selectSession(sessionId)
        onNavigateToTerminal()
    }

    fun reconnectActiveSession(sessionId: String, onNavigateToTerminal: () -> Unit) {
        sshSessionManager.selectSession(sessionId)
        sshSessionManager.reconnectSession(sessionId)
        onNavigateToTerminal()
    }

    fun toggleFavorite(profile: SshProfile) {
        viewModelScope.launch {
            profileRepository.toggleFavorite(profile.id, !profile.isFavorite)
        }
    }

    fun enterSelectionMode(initialProfileId: String? = null) {
        isSelectionMode.value = true
        if (!initialProfileId.isNullOrBlank()) {
            selectedProfileIds.update { it + initialProfileId }
        }
    }

    fun exitSelectionMode() {
        isSelectionMode.value = false
        selectedProfileIds.value = emptySet()
    }

    fun toggleProfileSelection(profileId: String) {
        if (!isSelectionMode.value) {
            isSelectionMode.value = true
        }
        selectedProfileIds.update { current ->
            if (profileId in current) current - profileId else current + profileId
        }
    }

    fun selectAllVisible() {
        isSelectionMode.value = true
        val visibleIds = uiState.value.filteredProfiles.map { it.id }.toSet()
        selectedProfileIds.update { it + visibleIds }
    }

    fun deselectAll() {
        selectedProfileIds.value = emptySet()
    }

    fun duplicateProfile(profile: SshProfile, includeCredential: Boolean = false) {
        viewModelScope.launch {
            runCatching {
                profileRepository.duplicateProfile(profile.id, includeCredential = includeCredential)
            }.onSuccess { copy ->
                if (copy != null) {
                    errorBanner.value = null
                    messageBanner.value = if (includeCredential && copy.credentialRefId != null) {
                        "คัดลอกโปรไฟล์ \"${copy.name}\" พร้อม Credential ที่เข้ารหัสแยกชุดใหม่แล้ว"
                    } else {
                        "คัดลอกโปรไฟล์ \"${copy.name}\" แล้ว (ไม่คัดลอก Credential เพื่อความปลอดภัย)"
                    }
                }
            }.onFailure { t ->
                errorBanner.value = "คัดลอกโปรไฟล์ไม่สำเร็จ: ${t.message ?: "Unknown error"}"
            }
        }
    }

    fun renameProfile(profile: SshProfile, newName: String) {
        viewModelScope.launch {
            profileRepository.renameProfile(profile.id, newName)
        }
    }

    fun moveToGroup(profile: SshProfile, groupName: String) {
        viewModelScope.launch {
            profileRepository.moveToGroup(profile.id, groupName)
        }
    }

    fun requestDeleteProfile(profile: SshProfile, fromEditor: Boolean = false) {
        val activeForTarget = sshSessionManager.getActiveSessionsForProfiles(setOf(profile.id))
        pendingDelete.value = PendingDeleteConfirmation(
            profiles = listOf(profile),
            activeSessionsForTargets = activeForTarget,
            fromEditor = fromEditor
        )
    }

    fun requestDeleteEditingProfile() {
        val editingId = _editorDraft.value?.existingId ?: return
        viewModelScope.launch {
            val target = uiState.value.allProfiles.firstOrNull { it.id == editingId }
                ?: profileRepository.getProfileById(editingId)
                ?: return@launch
            requestDeleteProfile(target, fromEditor = true)
        }
    }

    fun requestBulkDelete() {
        val selectedIds = uiState.value.selectedProfileIds
        if (selectedIds.isEmpty()) return
        val targets = uiState.value.allProfiles.filter { it.id in selectedIds }
        if (targets.isEmpty()) return
        val activeForTargets = sshSessionManager.getActiveSessionsForProfiles(targets.map { it.id }.toSet())
        pendingDelete.value = PendingDeleteConfirmation(
            profiles = targets,
            activeSessionsForTargets = activeForTargets,
            fromEditor = false
        )
    }

    fun cancelPendingDelete() {
        pendingDelete.value = null
    }

    fun confirmPendingDelete(
        removeAssociatedCredentials: Boolean,
        disconnectActiveFirst: Boolean
    ) {
        val pending = pendingDelete.value ?: return
        val liveSessions = sshSessionManager.getActiveSessionsForProfiles(pending.profiles.map { it.id }.toSet())
        if (liveSessions.isNotEmpty() && !disconnectActiveFirst) {
            pendingDelete.value = pending.copy(activeSessionsForTargets = liveSessions)
            errorBanner.value = "พบเซสชัน SSH ที่กำลังเชื่อมต่ออยู่ กรุณาเลือก 'ตัดการเชื่อมต่อและลบ' หรือ 'ยกเลิก'"
            return
        }

        viewModelScope.launch {
            val targetIds = pending.profiles.map { it.id }
            val targetIdSet = targetIds.toSet()
            try {
                if (liveSessions.isNotEmpty() && disconnectActiveFirst) {
                    sshSessionManager.disconnectAndCloseTabsForProfiles(targetIdSet)
                }

                val deleteResult = profileRepository.deleteProfiles(
                    ids = targetIds,
                    removeAssociatedCredentials = removeAssociatedCredentials
                )
                sshSessionManager.closeNonLiveTabsForProfiles(targetIdSet)

                pendingDelete.value = null
                if (pending.fromEditor) {
                    _editorDraft.value = null
                }
                if (isSelectionMode.value) {
                    selectedProfileIds.update { it - targetIdSet }
                    if (selectedProfileIds.value.isEmpty()) {
                        isSelectionMode.value = false
                    }
                }
                errorBanner.value = null
                messageBanner.value = null

                val deletedCount = deleteResult.deletedProfilesSanitized.size
                val undoMsg = if (deletedCount == 1) {
                    val singleName = deleteResult.deletedProfilesSanitized.first().name
                    if (removeAssociatedCredentials && deleteResult.deletedCredentialCount > 0) {
                        "ลบโปรไฟล์ \"$singleName\" และ Credential ใน Vault แล้ว (กู้คืนได้เฉพาะค่าโปรไฟล์)"
                    } else {
                        "ลบโปรไฟล์ \"$singleName\" แล้ว (กู้คืนเฉพาะค่าโปรไฟล์ได้)"
                    }
                } else {
                    "ลบโปรไฟล์ $deletedCount รายการแล้ว (กู้คืนเฉพาะค่าโปรไฟล์ได้ ไม่รวมรหัสผ่าน/Key)"
                }

                undoDeleteState.value = DeletedProfilesUndoState(
                    nonSecretProfiles = deleteResult.deletedProfilesSanitized.map { it.copy(credentialRefId = null) },
                    message = undoMsg,
                    removedCredentialsCount = deleteResult.deletedCredentialCount
                )
            } catch (t: Throwable) {
                pendingDelete.value = null
                errorBanner.value = "ลบโปรไฟล์ไม่สำเร็จ (ฐานข้อมูลยังคงสมบูรณ์): ${t.message ?: "Unknown error"}"
            }
        }
    }

    fun undoLastDelete() {
        val state = undoDeleteState.value ?: return
        viewModelScope.launch {
            try {
                val restoredCount = profileRepository.restoreNonSecretProfilesFromUndo(state.nonSecretProfiles)
                undoDeleteState.value = null
                errorBanner.value = null
                messageBanner.value = "กู้คืนการตั้งค่าโปรไฟล์ $restoredCount รายการแล้ว (ต้องกรอกรหัสผ่าน/Key ใหม่เพื่อความปลอดภัย)"
            } catch (t: Throwable) {
                errorBanner.value = "กู้คืนโปรไฟล์ไม่สำเร็จ: ${t.message ?: "Unknown error"}"
            }
        }
    }

    fun dismissUndo() {
        undoDeleteState.value = null
    }

    fun deleteProfile(profile: SshProfile, removeAssociatedCredential: Boolean) {
        val activeSessions = sshSessionManager.getActiveSessionsForProfiles(setOf(profile.id))
        if (activeSessions.isNotEmpty()) {
            requestDeleteProfile(profile)
            return
        }
        viewModelScope.launch {
            try {
                val result = profileRepository.deleteProfiles(listOf(profile.id), removeAssociatedCredential)
                sshSessionManager.closeNonLiveTabsForProfiles(setOf(profile.id))
                errorBanner.value = null
                val msg = if (removeAssociatedCredential) {
                    "ลบโปรไฟล์ \"${profile.name}\" และ Credential ที่ผูกไว้แล้ว (Host Trust ยังคงอยู่)"
                } else {
                    "ลบโปรไฟล์ \"${profile.name}\" แล้ว (เก็บ Credential และ Host Trust ไว้)"
                }
                undoDeleteState.value = DeletedProfilesUndoState(
                    nonSecretProfiles = result.deletedProfilesSanitized.map { it.copy(credentialRefId = null) },
                    message = msg,
                    removedCredentialsCount = result.deletedCredentialCount
                )
            } catch (t: Throwable) {
                errorBanner.value = "ลบโปรไฟล์ไม่สำเร็จ: ${t.message ?: "Unknown error"}"
            }
        }
    }

    fun removeStoredCredentialOnly(profile: SshProfile) {
        viewModelScope.launch {
            val refId = profile.credentialRefId
            if (refId != null) {
                val otherUses = uiState.value.allProfiles.any { it.id != profile.id && it.credentialRefId == refId }
                if (!otherUses) {
                    credentialVault.deleteCredential(refId)
                }
            }
            profileRepository.saveProfile(profile.copy(credentialRefId = null))
            messageBanner.value = "ลบ Credential ของ \"${profile.name}\" ออกจาก Vault แล้ว"
        }
    }

    fun clearRecentHistory() {
        viewModelScope.launch {
            profileRepository.clearRecentHistory()
            messageBanner.value = "ล้างประวัติการเชื่อมต่อล่าสุดแล้ว (โปรไฟล์ที่บันทึกไว้ยังคงอยู่)"
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            profileRepository.createCustomGroup(name)
        }
    }

    fun renameGroup(oldName: String, newName: String) {
        viewModelScope.launch {
            profileRepository.renameCustomGroup(oldName, newName)
            if (selectedGroup.value == oldName) {
                selectedGroup.value = newName.trim()
            }
        }
    }

    fun deleteGroup(name: String) {
        viewModelScope.launch {
            profileRepository.deleteCustomGroup(name)
            if (selectedGroup.value == name) {
                selectedGroup.value = null
            }
        }
    }

    fun getExportPreview(profileIds: Set<String>? = null): ExportBackupPreview {
        val targetProfiles = resolveExportProfiles(profileIds)
        return BackupCryptoManager.buildExportPreview(targetProfiles, credentialVault)
    }

    fun buildExportContent(encryptWithPassphrase: String?, profileIds: Set<String>? = null): Result<String> {
        val profiles = resolveExportProfiles(profileIds)
        val usedGroupNames = profiles.map { it.groupName }.toSet()
        val groups = uiState.value.groups
            .filter { !it.isBuiltIn && (profileIds == null || it.name in usedGroupNames) }
            .map { it.name }
        return runCatching {
            if (encryptWithPassphrase.isNullOrBlank()) {
                BackupCryptoManager.exportMetadataJson(profiles, groups)
            } else {
                val pass = encryptWithPassphrase.toCharArray()
                try {
                    BackupCryptoManager.exportEncryptedBackup(profiles, groups, pass)
                } finally {
                    pass.fill('\u0000')
                }
            }
        }
    }

    fun buildEncryptedFullBackup(
        passphrase: String,
        includeCredentials: Boolean,
        profileIds: Set<String>? = null
    ): Result<String> {
        val profiles = resolveExportProfiles(profileIds)
        val usedGroupNames = profiles.map { it.groupName }.toSet()
        val groups = uiState.value.groups
            .filter { !it.isBuiltIn && (profileIds == null || it.name in usedGroupNames) }
            .map { it.name }
        val passChars = passphrase.toCharArray()
        return try {
            runCatching {
                BackupCryptoManager.exportEncryptedFullBackup(
                    profiles = profiles,
                    groups = groups,
                    passphrase = passChars,
                    credentialVault = credentialVault,
                    includeCredentials = includeCredentials
                )
            }
        } finally {
            passChars.fill('\u0000')
        }
    }

    private fun resolveExportProfiles(profileIds: Set<String>?): List<SshProfile> {
        val all = uiState.value.allProfiles
        return if (profileIds == null) all else all.filter { it.id in profileIds }
    }

    fun prepareImportPreview(rawText: String, passphrase: String?): BackupImportResult {
        val passChars = passphrase?.toCharArray()
        return try {
            val result = BackupCryptoManager.importBackup(rawText, passChars)
            if (result is BackupImportResult.Success) {
                val preview = BackupCryptoManager.analyzeImportPreview(
                    bundle = result.bundle,
                    existingProfiles = uiState.value.allProfiles,
                    isEncrypted = BackupCryptoManager.isEncryptedBackup(rawText)
                )
                _pendingImportReview.value = PendingImportReview(
                    rawBundle = result.bundle,
                    preview = preview
                )
            }
            result
        } finally {
            passChars?.fill('\u0000')
        }
    }

    fun dismissImportPreview() {
        _pendingImportReview.value = null
    }

    fun confirmImportWithStrategy(strategy: ImportDuplicateStrategy, onDone: (String) -> Unit) {
        val review = _pendingImportReview.value ?: return
        viewModelScope.launch {
            try {
                val summary = profileRepository.importWithStrategy(review.rawBundle, strategy)
                _pendingImportReview.value = null
                val parts = buildList {
                    if (summary.importedNewCount > 0) add("ใหม่ ${summary.importedNewCount}")
                    if (summary.replacedCount > 0) add("เขียนทับ ${summary.replacedCount}")
                    if (summary.keptBothCount > 0) add("เก็บคู่ ${summary.keptBothCount}")
                    if (summary.skippedDuplicateCount > 0) add("ข้ามซ้ำ ${summary.skippedDuplicateCount}")
                    if (summary.reEncryptedCredentialsCount > 0) add("กู้คืน Credential ${summary.reEncryptedCredentialsCount}")
                }
                val detail = if (parts.isEmpty()) "ไม่มีรายการเปลี่ยนแปลง" else parts.joinToString(" • ")
                val msg = "นำเข้าสำเร็จ: $detail (Host Key ยังต้องยืนยัน TOFU ด้วยตนเอง)"
                messageBanner.value = msg
                onDone(msg)
            } catch (t: Throwable) {
                onDone("นำเข้าล้มเหลวและยกเลิกการเปลี่ยนแปลงทั้งหมด: ${t.message ?: "Unknown error"}")
            }
        }
    }

    fun importBackupContent(rawText: String, passphrase: String?, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val passChars = passphrase?.toCharArray()
            try {
                when (val result = BackupCryptoManager.importBackup(rawText, passChars)) {
                    is BackupImportResult.Success -> {
                        val count = profileRepository.importProfilesSafely(
                            importedProfiles = result.bundle.profiles,
                            importedGroups = result.bundle.groups
                        )
                        val msg = "นำเข้า $count โปรไฟล์สำเร็จ (ไม่เขียนทับของเดิม และไม่เชื่อถือ Host Key อัตโนมัติ)"
                        messageBanner.value = msg
                        onDone(msg)
                    }
                    is BackupImportResult.Error -> {
                        onDone(result.message)
                    }
                }
            } finally {
                passChars?.fill('\u0000')
            }
        }
    }

    class Factory(
        private val profileRepository: ProfileRepository,
        private val credentialVault: CredentialVault,
        private val sshSessionManager: SshSessionManager,
        private val preferencesRepository: AppPreferencesRepository,
        private val networkMonitor: NetworkMonitor,
        private val knownHostRepository: KnownHostRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ServersViewModel(
                profileRepository,
                credentialVault,
                sshSessionManager,
                preferencesRepository,
                networkMonitor,
                knownHostRepository
            ) as T
        }
    }
}
