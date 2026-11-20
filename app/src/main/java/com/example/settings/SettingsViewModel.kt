package com.example.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.KnownHostRepository
import com.example.data.ProfileRepository
import com.example.domain.AppAccentPreset
import com.example.domain.AppThemeMode
import com.example.domain.KnownHost
import com.example.domain.TerminalCursorStyle
import com.example.domain.TerminalFontFamily
import com.example.domain.TerminalThemePreset
import com.example.network.TailscaleConnectionCoordinator
import com.example.network.TailscaleDiagnosticSnapshot
import com.example.security.CredentialVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: AppPreferencesRepository,
    private val knownHostRepository: KnownHostRepository,
    private val profileRepository: ProfileRepository,
    private val credentialVault: CredentialVault,
    private val tailscaleCoordinator: TailscaleConnectionCoordinator? = null
) : ViewModel() {

    val settings: StateFlow<AppSettings> = preferencesRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val tailscaleDiagnostics: StateFlow<TailscaleDiagnosticSnapshot> =
        tailscaleCoordinator?.diagnosticSnapshot ?: MutableStateFlow(TailscaleDiagnosticSnapshot()).asStateFlow()

    val knownHosts: StateFlow<List<KnownHost>> = knownHostRepository.allKnownHosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _vaultCount = MutableStateFlow(credentialVault.listAllSummaries().size)
    val vaultCount: StateFlow<Int> = _vaultCount.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    fun refreshVaultCount() {
        _vaultCount.value = credentialVault.listAllSummaries().size
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAppLockEnabled(enabled)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricEnabled(enabled)
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            preferencesRepository.setPin(pin.toCharArray())
            _feedbackMessage.value = "ตั้งค่า PIN และเปิดใช้งาน App Lock แล้ว"
        }
    }

    fun disablePinAndAppLock() {
        viewModelScope.launch {
            preferencesRepository.clearPin()
            _feedbackMessage.value = "ปิดการล็อกแอปและลบ PIN แล้ว"
        }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        viewModelScope.launch {
            preferencesRepository.setAutoLockTimeout(timeout)
        }
    }

    fun setProtectScreenshots(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setProtectScreenshots(enabled)
        }
    }

    fun setDefaultFontSize(sizeSp: Float) {
        viewModelScope.launch {
            preferencesRepository.setDefaultTerminalFontSize(sizeSp)
        }
    }

    fun setDefaultTheme(theme: TerminalThemePreset) {
        viewModelScope.launch {
            preferencesRepository.setDefaultTerminalTheme(theme)
        }
    }

    fun setDefaultTermType(termType: String) {
        viewModelScope.launch {
            preferencesRepository.setDefaultTermType(termType)
        }
    }

    fun setDefaultKeepAlive(sec: Int) {
        viewModelScope.launch {
            preferencesRepository.setDefaultKeepAliveSec(sec)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setAccentPreset(preset: AppAccentPreset) {
        viewModelScope.launch {
            preferencesRepository.setAccentPreset(preset)
        }
    }

    fun setTerminalFontFamily(family: TerminalFontFamily) {
        viewModelScope.launch {
            preferencesRepository.setTerminalFontFamily(family)
        }
    }

    fun setScrollbackLines(lines: Int) {
        viewModelScope.launch {
            preferencesRepository.setScrollbackLines(lines)
        }
    }

    fun setShowExtraKeyboardToolbar(show: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowExtraKeyboardToolbar(show)
        }
    }

    fun setCursorStyle(style: TerminalCursorStyle) {
        viewModelScope.launch {
            preferencesRepository.setCursorStyle(style)
        }
    }

    fun setHapticKeyFeedback(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHapticKeyFeedback(enabled)
        }
    }

    fun setDefaultConnectTimeoutSec(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setDefaultConnectTimeoutSec(seconds)
        }
    }

    fun setAutoReconnectPrompt(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoReconnectPrompt(enabled)
        }
    }

    fun setTailscaleAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTailscaleAutomationEnabled(enabled)
        }
    }

    fun setTailscaleAutoDisconnect(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTailscaleAutoDisconnect(enabled)
        }
    }

    fun setTailscaleConnectTimeoutSec(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setTailscaleConnectTimeoutSec(seconds)
        }
    }

    fun refreshTailscaleDiagnostics() {
        tailscaleCoordinator?.refreshDiagnostics()
    }

    fun openTailscaleApp() {
        val opened = tailscaleCoordinator?.openTailscaleApp() ?: false
        if (!opened) {
            _feedbackMessage.value = "ไม่สามารถเปิดแอป Tailscale ได้ กรุณาตรวจสอบว่าติดตั้งแอป Tailscale แล้ว"
        }
    }

    fun openTailscalePlayStore() {
        tailscaleCoordinator?.openTailscalePlayStore()
    }

    fun disconnectOwnedTailscaleVpn() {
        viewModelScope.launch {
            val result = tailscaleCoordinator?.disconnectOwnedVpnNow()
            if (result != null && !result.launched && !result.errorMessage.isNullOrBlank()) {
                _feedbackMessage.value = result.errorMessage
            } else if (result?.launched == true) {
                _feedbackMessage.value = "ส่งคำสั่งตัดการเชื่อมต่อ Tailscale VPN แล้ว"
            }
        }
    }

    fun removeKnownHost(id: String) {
        viewModelScope.launch {
            knownHostRepository.removeTrustedHost(id)
            _feedbackMessage.value = "ยกเลิกความเชื่อถือ Host Key แล้ว"
        }
    }

    fun clearAllKnownHosts() {
        viewModelScope.launch {
            knownHostRepository.clearAllTrustedHosts()
            _feedbackMessage.value = "ล้างรายการ Trusted Hosts ทั้งหมดแล้ว"
        }
    }

    fun cleanOrphanedCredentials() {
        viewModelScope.launch {
            val refs = profileRepository.getAllReferencedCredentialIds()
            val removed = credentialVault.deleteOrphanedCredentials(refs)
            refreshVaultCount()
            _feedbackMessage.value = "ล้าง Credential ที่ไม่ถูกใช้งานออก $removed รายการ"
        }
    }

    class Factory(
        private val preferencesRepository: AppPreferencesRepository,
        private val knownHostRepository: KnownHostRepository,
        private val profileRepository: ProfileRepository,
        private val credentialVault: CredentialVault,
        private val tailscaleCoordinator: TailscaleConnectionCoordinator? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferencesRepository,
                knownHostRepository,
                profileRepository,
                credentialVault,
                tailscaleCoordinator
            ) as T
        }
    }
}
