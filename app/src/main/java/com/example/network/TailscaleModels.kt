package com.example.network

enum class VpnOwnership(val label: String) {
    NONE("ไม่มี VPN ที่ถือสิทธิ์"),
    EXTERNAL("VPN ภายนอก (External)"),
    APP_REQUESTED("เริ่มโดย TrueSeekrSSH"),
    UNKNOWN("ไม่ทราบเจ้าของ (หลังรีสตาร์ท)")
}

enum class TailscaleInstallState(val label: String) {
    INSTALLED("ติดตั้ง Tailscale แล้ว"),
    NOT_INSTALLED("ยังไม่ได้ติดตั้ง Tailscale"),
    AUTOMATION_UNAVAILABLE("AutomationActivity ไม่พร้อมใช้งาน"),
    UNSUPPORTED_ANDROID_VERSION("ต้องใช้ Android 8.0 ขึ้นไป")
}

enum class TailscaleProgressState(
    val label: String,
    val isInProgress: Boolean = false,
    val isError: Boolean = false
) {
    IDLE("พร้อมใช้งาน"),
    CHECKING_TAILSCALE("กำลังตรวจสอบ Tailscale...", isInProgress = true),
    STARTING_TAILSCALE("กำลังสั่งเปิด Tailscale VPN...", isInProgress = true),
    WAITING_FOR_VPN("กำลังรอเชื่อมต่อ Tailscale VPN...", isInProgress = true),
    VERIFYING_SERVER("กำลังตรวจสอบการเข้าถึงเซิร์ฟเวอร์...", isInProgress = true),
    CONNECTING_SSH("กำลังเชื่อมต่อ SSH...", isInProgress = true),
    CONNECTED("เชื่อมต่อ Tailscale VPN แล้ว"),
    REUSING_EXTERNAL_VPN("ใช้ VPN ภายนอกที่เชื่อมต่ออยู่แล้ว"),
    NOT_INSTALLED("ยังไม่ได้ติดตั้งแอป Tailscale", isError = true),
    LOGIN_OR_PERMISSION_REQUIRED("ต้องเข้าสู่ระบบหรืออนุญาตสิทธิ์ VPN ใน Tailscale", isError = true),
    TIMEOUT("หมดเวลารอ Tailscale VPN", isError = true),
    FAILED("สั่งงาน Tailscale VPN ไม่สำเร็จ", isError = true)
}

data class TailscaleLaunchResult(
    val launched: Boolean,
    val installState: TailscaleInstallState,
    val errorMessage: String? = null
)

data class TailscaleVpnStatus(
    val hasTransportVpn: Boolean = false,
    val hasInternet: Boolean = false,
    val interfaceNames: List<String> = emptyList(),
    val hasTailscaleHint: Boolean = false
)

sealed interface TailscalePreparationResult {
    data class Proceed(
        val progressState: TailscaleProgressState,
        val ownership: VpnOwnership,
        val noticeMessage: String? = null,
        val leaseGranted: Boolean = false
    ) : TailscalePreparationResult

    data class Failed(
        val progressState: TailscaleProgressState,
        val errorMessage: String,
        val canOpenTailscale: Boolean = false,
        val canInstallTailscale: Boolean = false
    ) : TailscalePreparationResult
}

data class TailscaleDiagnosticSnapshot(
    val installState: TailscaleInstallState = TailscaleInstallState.NOT_INSTALLED,
    val vpnStatus: TailscaleVpnStatus = TailscaleVpnStatus(),
    val ownership: VpnOwnership = VpnOwnership.NONE,
    val activeLeaseCount: Int = 0,
    val progressState: TailscaleProgressState = TailscaleProgressState.IDLE,
    val lastError: String? = null
)
