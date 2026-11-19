package com.example.sftp

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.SftpFileItem
import com.example.domain.SftpTransferState
import com.example.ssh.ManagedSessionUiState
import com.example.ssh.SshSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.sshlib.SftpClient
import java.util.UUID

data class SftpUiState(
    val selectedSessionId: String? = null,
    val currentPath: String = "",
    val entries: List<SftpFileItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusBanner: String? = null,
    val activeTransfer: SftpTransferState? = null
)

class SftpViewModel(
    private val sshSessionManager: SshSessionManager
) : ViewModel() {

    val sessions: StateFlow<List<ManagedSessionUiState>> = sshSessionManager.sessions

    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    private var activeTransferJob: Job? = null

    fun attachOrOpenSession(sessionId: String) {
        _uiState.update { it.copy(selectedSessionId = sessionId, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val clientResult = sshSessionManager.getOrOpenSftp(sessionId)
            val client = clientResult.getOrElse { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "ไม่สามารถเปิด SFTP ได้")
                }
                return@launch
            }
            try {
                val initialPath = _uiState.value.currentPath.ifBlank {
                    SftpManager.resolvePath(client, ".")
                }
                val list = SftpManager.listDirectory(client, initialPath)
                _uiState.update {
                    it.copy(currentPath = initialPath, entries = list, isLoading = false, errorMessage = null)
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.message ?: "โหลดรายการไฟล์ไม่สำเร็จ")
                }
            }
        }
    }

    fun navigateTo(path: String) {
        val sid = _uiState.value.selectedSessionId ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            withSftpClient(sid) { client ->
                val resolved = SftpManager.resolvePath(client, path)
                val items = SftpManager.listDirectory(client, resolved)
                _uiState.update {
                    it.copy(currentPath = resolved, entries = items, isLoading = false, errorMessage = null)
                }
            }
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current.isBlank() || current == "/") return
        val parent = current.substringBeforeLast('/', "").ifBlank { "/" }
        navigateTo(parent)
    }

    fun refresh() {
        val current = _uiState.value.currentPath.ifBlank { "." }
        navigateTo(current)
    }

    fun createFolder(folderName: String) {
        val trimmed = folderName.trim()
        if (trimmed.isBlank()) return
        val sid = _uiState.value.selectedSessionId ?: return
        val base = _uiState.value.currentPath.trimEnd('/')
        val fullPath = if (base.isEmpty()) "/$trimmed" else "$base/$trimmed"
        viewModelScope.launch {
            withSftpClient(sid) { client ->
                SftpManager.createDirectory(client, fullPath)
                val items = SftpManager.listDirectory(client, _uiState.value.currentPath)
                _uiState.update {
                    it.copy(entries = items, statusBanner = "สร้างโฟลเดอร์ \"$trimmed\" แล้ว")
                }
            }
        }
    }

    fun renameItem(item: SftpFileItem, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == item.name) return
        val sid = _uiState.value.selectedSessionId ?: return
        val parent = item.fullPath.substringBeforeLast('/', "")
        val newPath = if (parent.isEmpty()) "/$trimmed" else "$parent/$trimmed"
        viewModelScope.launch {
            withSftpClient(sid) { client ->
                SftpManager.rename(client, item.fullPath, newPath)
                val items = SftpManager.listDirectory(client, _uiState.value.currentPath)
                _uiState.update {
                    it.copy(entries = items, statusBanner = "เปลี่ยนชื่อเป็น \"$trimmed\" แล้ว")
                }
            }
        }
    }

    fun deleteItem(item: SftpFileItem) {
        val sid = _uiState.value.selectedSessionId ?: return
        viewModelScope.launch {
            withSftpClient(sid) { client ->
                SftpManager.deleteItem(client, item)
                val items = SftpManager.listDirectory(client, _uiState.value.currentPath)
                _uiState.update {
                    it.copy(entries = items, statusBanner = "ลบ \"${item.name}\" แล้ว")
                }
            }
        }
    }

    fun uploadLocalFile(
        contentResolver: ContentResolver,
        localUri: Uri,
        fileName: String,
        totalBytes: Long?
    ) {
        val sid = _uiState.value.selectedSessionId ?: return
        val base = _uiState.value.currentPath.trimEnd('/')
        val remotePath = if (base.isEmpty()) "/$fileName" else "$base/$fileName"
        val transferId = UUID.randomUUID().toString()

        activeTransferJob?.cancel()
        activeTransferJob = viewModelScope.launch {
            val client = sshSessionManager.getOrOpenSftp(sid).getOrElse { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    activeTransfer = SftpTransferState(
                        transferId = transferId,
                        fileName = fileName,
                        isUpload = true,
                        bytesTransferred = 0L,
                        totalBytes = totalBytes,
                        isActive = true
                    )
                )
            }
            try {
                SftpManager.uploadFromUri(client, contentResolver, localUri, remotePath) { bytes ->
                    _uiState.update { state ->
                        state.copy(
                            activeTransfer = state.activeTransfer?.copy(bytesTransferred = bytes)
                        )
                    }
                }
                val items = SftpManager.listDirectory(client, _uiState.value.currentPath)
                _uiState.update {
                    it.copy(
                        entries = items,
                        activeTransfer = null,
                        statusBanner = "อัปโหลด \"$fileName\" สำเร็จ"
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(activeTransfer = null, statusBanner = "ยกเลิกการอัปโหลด \"$fileName\" แล้ว")
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        activeTransfer = null,
                        errorMessage = t.message ?: "อัปโหลดถูกขัดจังหวะและล้มเหลว"
                    )
                }
            }
        }
    }

    fun downloadRemoteFile(
        contentResolver: ContentResolver,
        item: SftpFileItem,
        destinationUri: Uri
    ) {
        val sid = _uiState.value.selectedSessionId ?: return
        val transferId = UUID.randomUUID().toString()

        activeTransferJob?.cancel()
        activeTransferJob = viewModelScope.launch {
            val client = sshSessionManager.getOrOpenSftp(sid).getOrElse { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    activeTransfer = SftpTransferState(
                        transferId = transferId,
                        fileName = item.name,
                        isUpload = false,
                        bytesTransferred = 0L,
                        totalBytes = item.sizeBytes,
                        isActive = true
                    )
                )
            }
            try {
                SftpManager.downloadToUri(client, contentResolver, item.fullPath, destinationUri) { bytes ->
                    _uiState.update { state ->
                        state.copy(
                            activeTransfer = state.activeTransfer?.copy(bytesTransferred = bytes)
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        activeTransfer = null,
                        statusBanner = "ดาวน์โหลด \"${item.name}\" สำเร็จ"
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(activeTransfer = null, statusBanner = "ยกเลิกการดาวน์โหลด \"${item.name}\" แล้ว")
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        activeTransfer = null,
                        errorMessage = t.message ?: "ดาวน์โหลดถูกขัดจังหวะและล้มเหลว"
                    )
                }
            }
        }
    }

    fun cancelActiveTransfer() {
        activeTransferJob?.cancel()
        activeTransferJob = null
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null, errorMessage = null) }
    }

    private suspend fun withSftpClient(sessionId: String, block: suspend (SftpClient) -> Unit) {
        val client = sshSessionManager.getOrOpenSftp(sessionId).getOrElse { err ->
            _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            return
        }
        try {
            block(client)
        } catch (t: Throwable) {
            _uiState.update { it.copy(isLoading = false, errorMessage = t.message ?: "คำสั่ง SFTP ล้มเหลว") }
        }
    }

    class Factory(
        private val sshSessionManager: SshSessionManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SftpViewModel(sshSessionManager) as T
        }
    }
}
