package com.example.domain

import kotlinx.serialization.Serializable

@Serializable
enum class AuthMethodType(val label: String) {
    PASSWORD("รหัสผ่าน (Password)"),
    PRIVATE_KEY("SSH Private Key"),
    PRIVATE_KEY_PASSPHRASE("SSH Private Key + Passphrase")
}

@Serializable
enum class TerminalThemePreset(
    val displayName: String,
    val backgroundArgb: Long,
    val foregroundArgb: Long,
    val selectionBgArgb: Long,
    val selectionFgArgb: Long
) {
    OBSIDIAN_AURORA(
        displayName = "Obsidian Aurora",
        backgroundArgb = 0xFF111820,
        foregroundArgb = 0xFFF2F6FA,
        selectionBgArgb = 0xFF174A55,
        selectionFgArgb = 0xFFFFFFFF
    ),
    CHARCOAL_LIME(
        displayName = "Charcoal Lime",
        backgroundArgb = 0xFF0D1117,
        foregroundArgb = 0xFFE6EDF3,
        selectionBgArgb = 0xFF1F4E4A,
        selectionFgArgb = 0xFFFFFFFF
    ),
    TEAL_MATRIX(
        displayName = "Teal Matrix",
        backgroundArgb = 0xFF081418,
        foregroundArgb = 0xFF99F6E4,
        selectionBgArgb = 0xFF115E59,
        selectionFgArgb = 0xFFFFFFFF
    ),
    SOLARIZED_DARK(
        displayName = "Solarized Dark",
        backgroundArgb = 0xFF002B36,
        foregroundArgb = 0xFF93A1A1,
        selectionBgArgb = 0xFF073642,
        selectionFgArgb = 0xFFFDF6E3
    ),
    HIGH_CONTRAST(
        displayName = "High Contrast",
        backgroundArgb = 0xFF000000,
        foregroundArgb = 0xFFFFFFFF,
        selectionBgArgb = 0xFF24D8E7,
        selectionFgArgb = 0xFF000000
    )
}

enum class AppThemeMode(val label: String) {
    DARK("Dark (Obsidian)"),
    LIGHT("Light"),
    SYSTEM("ตามระบบ")
}

enum class AppAccentPreset(val label: String, val primaryArgb: Long, val secondaryArgb: Long) {
    CYAN("Cyan Aurora", 0xFF24D8E7, 0xFFB6F36A),
    LIME("Electric Lime", 0xFFB6F36A, 0xFF24D8E7),
    ICE_BLUE("Ice Signal", 0xFF4BA3FF, 0xFF24D8E7),
    VIOLET("Cyber Violet", 0xFFA78BFA, 0xFF24D8E7)
}

enum class TerminalFontFamily(val label: String) {
    JETBRAINS_MONO("JetBrains Mono"),
    SYSTEM_MONO("System Monospace")
}

enum class TerminalCursorStyle(val label: String) {
    BLOCK("Block"),
    UNDERLINE("Underline"),
    BAR("Bar")
}

enum class ProfileSortOrder(val label: String) {
    NAME_ASC("ชื่อ (A-Z)"),
    LAST_CONNECTED_DESC("เชื่อมต่อล่าสุด"),
    CREATED_DESC("วันที่สร้างล่าสุด"),
    HOST_ASC("Host / IP"),
    MOST_USED_DESC("ใช้งานบ่อย")
}

@Serializable
data class SshProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authMethod: AuthMethodType = AuthMethodType.PASSWORD,
    val description: String = "",
    val groupName: String = "Home",
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val connectTimeoutSec: Int = 15,
    val keepAliveIntervalSec: Int = 30,
    val terminalType: String = "xterm-256color",
    val terminalFontSizeSp: Float = 13f,
    val terminalTheme: TerminalThemePreset = TerminalThemePreset.CHARCOAL_LIME,
    val initialWorkingDir: String = "",
    val autoTailscaleConnect: Boolean = false,
    val credentialRefId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null,
    val connectionCount: Int = 0
)

data class ProfileGroup(
    val name: String,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0
)

data class RecentConnection(
    val profileId: String,
    val lastConnectedAt: Long,
    val successCount: Int
)

data class KnownHost(
    val id: String,
    val host: String,
    val port: Int,
    val keyType: String,
    val publicKeyBase64: String,
    val sha256Fingerprint: String,
    val trustedAt: Long,
    val lastVerifiedAt: Long
)

data class CommandSnippet(
    val id: String,
    val title: String,
    val command: String,
    val groupName: String = "Diagnostics",
    val description: String = "",
    val requiresConfirmation: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class SessionStatus(val label: String) {
    IDLE("ยังไม่เชื่อมต่อ"),
    CONNECTING("กำลังเชื่อมต่อ"),
    VERIFYING_HOST("ตรวจสอบ Host Key"),
    AUTHENTICATING("กำลังยืนยันตัวตน"),
    CONNECTED("เชื่อมต่อแล้ว"),
    CONNECTION_IDLE("เชื่อมต่อแล้ว (Idle)"),
    CHECKING_CONNECTION("กำลังตรวจสอบการเชื่อมต่อ"),
    RECONNECTING("กำลังเชื่อมต่อใหม่"),
    DISCONNECTED("ตัดการเชื่อมต่อแล้ว"),
    CONNECTION_LOST("การเชื่อมต่อขาดหาย"),
    ERROR("เชื่อมต่อล้มเหลว");

    val isLiveConnected: Boolean
        get() = this == CONNECTED || this == CONNECTION_IDLE || this == CHECKING_CONNECTION
}

data class SessionConnectionMeta(
    val kexAlgorithm: String = "",
    val hostKeyAlgorithm: String = "",
    val cipherC2S: String = "",
    val macC2S: String = "",
    val isPostQuantum: Boolean = false,
    val hostFingerprint: String = ""
)

sealed interface HostKeyVerificationRequest {
    val requestId: String
    val host: String
    val port: Int
    val keyType: String
    val actualSha256: String
    val actualColonHex: String

    data class UnknownHost(
        override val requestId: String,
        override val host: String,
        override val port: Int,
        override val keyType: String,
        override val actualSha256: String,
        override val actualColonHex: String
    ) : HostKeyVerificationRequest

    data class KeyMismatch(
        override val requestId: String,
        override val host: String,
        override val port: Int,
        override val keyType: String,
        override val actualSha256: String,
        override val actualColonHex: String,
        val expectedSha256: String
    ) : HostKeyVerificationRequest
}

data class CredentialPromptRequest(
    val profile: SshProfile,
    val reason: String,
    val needsPassphraseOnly: Boolean = false
)

data class SftpFileItem(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val sizeBytes: Long?,
    val permissionsOctal: Int?,
    val permissionsDisplay: String,
    val modifiedEpochSec: Int?,
    val longName: String
)

data class SftpTransferState(
    val transferId: String,
    val fileName: String,
    val isUpload: Boolean,
    val bytesTransferred: Long,
    val totalBytes: Long?,
    val isActive: Boolean,
    val errorMessage: String? = null
)

enum class HealthRefreshInterval(val seconds: Int, val label: String) {
    FIVE_SECONDS(5, "5s"),
    TEN_SECONDS(10, "10s"),
    THIRTY_SECONDS(30, "30s"),
    SIXTY_SECONDS(60, "60s"),
    MANUAL(0, "Manual")
}

enum class MetricAvailability {
    AVAILABLE,
    UNSUPPORTED,
    UNAVAILABLE,
    PERMISSION_DENIED,
    CONNECTION_ERROR
}

enum class DashboardDataSource(val label: String) {
    FASTFETCH("Fastfetch JSON"),
    LINUX_PROC("Linux /proc"),
    MIXED("Fastfetch + /proc"),
    UNSUPPORTED("Unsupported OS")
}

data class ServerMetrics(
    val retrievedAt: Long = System.currentTimeMillis(),
    val dataSource: DashboardDataSource = DashboardDataSource.LINUX_PROC,
    val isLinux: Boolean = true,
    val detectedOsKernelName: String? = null,
    val fastfetchAvailable: Boolean = false,
    val osInfo: String? = null,
    val kernelInfo: String? = null,
    val hostname: String? = null,
    val uptimeText: String? = null,
    val loadAverage: String? = null,
    val cpuModel: String? = null,
    val cpuUsagePercent: Float? = null,
    val cpuAvailability: MetricAvailability = MetricAvailability.UNAVAILABLE,
    val memoryUsedMb: Long? = null,
    val memoryTotalMb: Long? = null,
    val memoryAvailability: MetricAvailability = MetricAvailability.UNAVAILABLE,
    val temperatureCelsius: Float? = null,
    val temperatureSource: String? = null,
    val temperatureAvailability: MetricAvailability = MetricAvailability.UNAVAILABLE,
    val networkRxBytesPerSec: Long? = null,
    val networkTxBytesPerSec: Long? = null,
    val networkThroughputAvailability: MetricAvailability = MetricAvailability.UNAVAILABLE,
    val diskSummary: List<DiskMountInfo> = emptyList(),
    val diskAvailability: MetricAvailability = MetricAvailability.UNAVAILABLE,
    val networkInterfaces: List<NetworkInterfaceInfo> = emptyList(),
    val availabilityNote: String? = null,
    val cpuHistory: List<Float> = emptyList(),
    val memoryHistory: List<Float> = emptyList()
)

data class DiskMountInfo(
    val filesystem: String,
    val mountPoint: String,
    val usedText: String,
    val totalText: String,
    val usePercent: Int
)

data class NetworkInterfaceInfo(
    val name: String,
    val addresses: String
)

data class ManagedTmuxSession(
    val sessionName: String,
    val profileId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttachedAt: Long = System.currentTimeMillis()
)

data class RemoteTmuxSession(
    val name: String,
    val windowsCount: Int = 1,
    val isAttached: Boolean = false,
    val createdEpochSec: Long? = null,
    val activityEpochSec: Long? = null,
    val isManagedByApp: Boolean = false,
    val associatedProfileId: String? = null
)

sealed interface TmuxCheckResult {
    data class Available(val versionText: String) : TmuxCheckResult
    data class NotInstalled(val detectedOs: String, val installInstructions: List<String>) : TmuxCheckResult
    data class Error(val message: String) : TmuxCheckResult
}
