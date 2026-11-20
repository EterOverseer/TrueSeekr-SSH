package com.example.network

import com.example.domain.SshProfile
import com.example.settings.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TailscaleConnectionCoordinator(
    private val controller: TailscaleController,
    private val vpnStateProvider: VpnStateProvider,
    private val ownershipStore: TailscaleOwnershipStore,
    private val leaseManager: TailscaleSessionLeaseManager,
    private val reachabilityChecker: ServerReachabilityChecker,
    private val preferencesRepository: AppPreferencesRepository,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val pollIntervalMs: Long = 500L
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val orchestrationMutex = Mutex()

    @Volatile
    private var isAppStartingVpn = false

    private val _diagnosticSnapshot = MutableStateFlow(buildSnapshot(TailscaleProgressState.IDLE, null))
    val diagnosticSnapshot: StateFlow<TailscaleDiagnosticSnapshot> = _diagnosticSnapshot.asStateFlow()

    init {
        scope.launch {
            vpnStateProvider.vpnStatus.collect { status ->
                ownershipStore.onVpnObserved(status.hasTransportVpn, isAppStartingVpn)
                publishSnapshot(_diagnosticSnapshot.value.progressState, _diagnosticSnapshot.value.lastError)
            }
        }
        scope.launch {
            ownershipStore.ownership.collect {
                publishSnapshot(_diagnosticSnapshot.value.progressState, _diagnosticSnapshot.value.lastError)
            }
        }
        scope.launch {
            leaseManager.leaseCount.collect {
                publishSnapshot(_diagnosticSnapshot.value.progressState, _diagnosticSnapshot.value.lastError)
            }
        }
    }

    fun refreshDiagnostics() {
        vpnStateProvider.readCurrentVpnStatus()
        publishSnapshot(_diagnosticSnapshot.value.progressState, _diagnosticSnapshot.value.lastError)
    }

    fun openTailscaleApp(): Boolean = controller.openTailscaleApp()

    fun openTailscalePlayStore(): Boolean = controller.openTailscalePlayStore()

    suspend fun prepareVpnForSession(
        sessionId: String,
        profile: SshProfile,
        onProgress: (TailscaleProgressState) -> Unit = {}
    ): TailscalePreparationResult {
        val settings = preferencesRepository.settings.first()
        if (!settings.tailscaleAutomationEnabled || !profile.autoTailscaleConnect) {
            return TailscalePreparationResult.Proceed(
                progressState = TailscaleProgressState.IDLE,
                ownership = ownershipStore.current(),
                leaseGranted = false
            )
        }

        leaseManager.beginAcquire()
        return try {
            orchestrationMutex.withLock {
                prepareLocked(
                    sessionId = sessionId,
                    profile = profile,
                    timeoutSec = settings.tailscaleConnectTimeoutSec.coerceIn(10, 120),
                    onProgress = onProgress
                )
            }
        } finally {
            leaseManager.endAcquire()
        }
    }

    private suspend fun prepareLocked(
        sessionId: String,
        profile: SshProfile,
        timeoutSec: Int,
        onProgress: (TailscaleProgressState) -> Unit
    ): TailscalePreparationResult {
        fun report(state: TailscaleProgressState, error: String? = null) {
            publishSnapshot(state, error)
            onProgress(state)
        }

        val currentVpn = vpnStateProvider.readCurrentVpnStatus()
        var currentOwnership = ownershipStore.current()

        // Case A: A VPN is ALREADY active before this connect request
        if (currentVpn.hasTransportVpn) {
            if (currentOwnership == VpnOwnership.APP_REQUESTED) {
                report(TailscaleProgressState.VERIFYING_SERVER)
                val reachable = waitForServerReachability(
                    host = profile.host,
                    port = profile.port,
                    deadlineMs = timeProvider() + (timeoutSec * 1000L).coerceAtMost(8_000L)
                )
                if (!reachable) {
                    val msg = "Tailscale VPN เปิดอยู่แล้ว แต่ไม่สามารถเข้าถึง ${profile.host}:${profile.port} ได้ (ตรวจสอบ Tailnet ACL / MagicDNS / สถานะเซิร์ฟเวอร์)"
                    report(TailscaleProgressState.FAILED, msg)
                    return TailscalePreparationResult.Failed(
                        progressState = TailscaleProgressState.FAILED,
                        errorMessage = msg,
                        canOpenTailscale = true
                    )
                }
                leaseManager.grantLease(sessionId)
                report(TailscaleProgressState.CONNECTED)
                return TailscalePreparationResult.Proceed(
                    progressState = TailscaleProgressState.CONNECTED,
                    ownership = VpnOwnership.APP_REQUESTED,
                    noticeMessage = "ใช้ Tailscale VPN ที่เปิดอยู่ร่วมกันสำหรับเซสชันนี้",
                    leaseGranted = true
                )
            } else {
                // External or unknown VPN: never hijack ownership, never auto-disconnect
                if (currentOwnership == VpnOwnership.NONE) {
                    ownershipStore.markExternal()
                    currentOwnership = VpnOwnership.EXTERNAL
                }
                val note = if (currentOwnership == VpnOwnership.UNKNOWN) {
                    "ตรวจพบ VPN เดิมทำงานอยู่ (ไม่ทราบเจ้าของหลังรีสตาร์ท) — เชื่อมต่อผ่านเส้นทางเดิมโดยไม่ปิด VPN อัตโนมัติ"
                } else {
                    "ตรวจพบ VPN ที่เปิดใช้งานอยู่ภายนอก — เชื่อมต่อโดยไม่เข้าควบคุมหรือปิด VPN อัตโนมัติ"
                }
                report(TailscaleProgressState.VERIFYING_SERVER)
                val reachable = waitForServerReachability(
                    host = profile.host,
                    port = profile.port,
                    deadlineMs = timeProvider() + (timeoutSec * 1000L).coerceAtMost(6_000L)
                )
                if (!reachable) {
                    val msg = "VPN ภายนอกเชื่อมต่ออยู่ แต่ไม่สามารถเข้าถึง ${profile.host}:${profile.port} ได้ — อาจกำลังเชื่อมต่อ VPN อื่นที่ไม่ใช่ Tailnet นี้"
                    report(TailscaleProgressState.FAILED, msg)
                    return TailscalePreparationResult.Failed(
                        progressState = TailscaleProgressState.FAILED,
                        errorMessage = msg,
                        canOpenTailscale = controller.checkInstallState() == TailscaleInstallState.INSTALLED
                    )
                }
                report(TailscaleProgressState.REUSING_EXTERNAL_VPN)
                return TailscalePreparationResult.Proceed(
                    progressState = TailscaleProgressState.REUSING_EXTERNAL_VPN,
                    ownership = currentOwnership,
                    noticeMessage = note,
                    leaseGranted = false
                )
            }
        }

        // No VPN active -> ensure stale ownership from earlier network state is cleared
        if (currentOwnership != VpnOwnership.NONE) {
            ownershipStore.markNone()
        }

        // Step 1: Verify Tailscale installation and AutomationActivity availability
        report(TailscaleProgressState.CHECKING_TAILSCALE)
        when (val installState = controller.checkInstallState()) {
            TailscaleInstallState.NOT_INSTALLED -> {
                val msg = "ไม่พบแอป Tailscale อย่างเป็นทางการบนเครื่อง กรุณาติดตั้ง Tailscale ก่อนใช้การเชื่อมต่ออัตโนมัติ"
                report(TailscaleProgressState.NOT_INSTALLED, msg)
                return TailscalePreparationResult.Failed(
                    progressState = TailscaleProgressState.NOT_INSTALLED,
                    errorMessage = msg,
                    canInstallTailscale = true
                )
            }
            TailscaleInstallState.UNSUPPORTED_ANDROID_VERSION -> {
                val msg = "การสั่งเปิด/ปิด Tailscale อัตโนมัติรองรับ Android 8.0 ขึ้นไป"
                report(TailscaleProgressState.FAILED, msg)
                return TailscalePreparationResult.Failed(
                    progressState = TailscaleProgressState.FAILED,
                    errorMessage = msg
                )
            }
            TailscaleInstallState.AUTOMATION_UNAVAILABLE -> {
                val msg = "แอป Tailscale เวอร์ชันที่ติดตั้งไม่รองรับ AutomationActivity — กรุณาอัปเดต Tailscale หรือเปิด VPN ด้วยตนเอง"
                report(TailscaleProgressState.LOGIN_OR_PERMISSION_REQUIRED, msg)
                return TailscalePreparationResult.Failed(
                    progressState = TailscaleProgressState.LOGIN_OR_PERMISSION_REQUIRED,
                    errorMessage = msg,
                    canOpenTailscale = true
                )
            }
            TailscaleInstallState.INSTALLED -> Unit
        }

        // Step 2: Launch Tailscale CONNECT_VPN Intent
        report(TailscaleProgressState.STARTING_TAILSCALE)
        isAppStartingVpn = true
        try {
            val launchResult = controller.launchConnectVpn()
            if (!launchResult.launched) {
                val msg = launchResult.errorMessage ?: "ไม่สามารถส่งคำสั่งเปิด Tailscale VPN ได้"
                report(TailscaleProgressState.FAILED, msg)
                return TailscalePreparationResult.Failed(
                    progressState = TailscaleProgressState.FAILED,
                    errorMessage = msg,
                    canOpenTailscale = launchResult.installState == TailscaleInstallState.INSTALLED,
                    canInstallTailscale = launchResult.installState == TailscaleInstallState.NOT_INSTALLED
                )
            }

            // Step 3: Wait for VPN transport readiness
            report(TailscaleProgressState.WAITING_FOR_VPN)
            val deadlineMs = timeProvider() + (timeoutSec * 1000L)
            var vpnReady = vpnStateProvider.readCurrentVpnStatus().hasTransportVpn
            while (!vpnReady && timeProvider() < deadlineMs) {
                delay(pollIntervalMs)
                vpnReady = vpnStateProvider.readCurrentVpnStatus().hasTransportVpn
            }

            if (!vpnReady) {
                ownershipStore.markNone()
                val msg = "รอ Tailscale VPN ไม่สำเร็จภายใน ${timeoutSec} วินาที — อาจยังไม่ได้เข้าสู่ระบบ Tailscale หรือยังไม่อนุมัติสิทธิ์ Android VPN"
                report(TailscaleProgressState.TIMEOUT, msg)
                return TailscalePreparationResult.Failed(
                    progressState = TailscaleProgressState.TIMEOUT,
                    errorMessage = msg,
                    canOpenTailscale = true
                )
            }

            // VPN came up directly due to our app's CONNECT_VPN request
            ownershipStore.markAppRequested(timeProvider())

            // Step 4: Verify server reachability over Tailscale before handing to SSH
            report(TailscaleProgressState.VERIFYING_SERVER)
            val remainingMs = (deadlineMs - timeProvider()).coerceIn(4_000L, 8_000L)
            val verifyDeadline = timeProvider() + remainingMs
            val reachable = waitForServerReachability(
                host = profile.host,
                port = profile.port,
                deadlineMs = verifyDeadline
            )
            if (!reachable) {
                val msg = "Tailscale VPN เชื่อมต่อแล้ว แต่ไม่สามารถเข้าถึง ${profile.host}:${profile.port} ได้ (ตรวจสอบ Tailnet / MagicDNS / Firewall)"
                report(TailscaleProgressState.FAILED, msg)
                // Clean up VPN if we were the sole requester and auto-disconnect is enabled
                disconnectIfSafeToDoSoLocked(forceZeroLeasesCheck = true)
                return TailscalePreparationResult.Failed(
                    progressState = TailscaleProgressState.FAILED,
                    errorMessage = msg,
                    canOpenTailscale = true
                )
            }

            leaseManager.grantLease(sessionId)
            report(TailscaleProgressState.CONNECTING_SSH)
            return TailscalePreparationResult.Proceed(
                progressState = TailscaleProgressState.CONNECTING_SSH,
                ownership = VpnOwnership.APP_REQUESTED,
                noticeMessage = "เปิด Tailscale VPN อัตโนมัติแล้ว",
                leaseGranted = true
            )
        } finally {
            isAppStartingVpn = false
        }
    }

    private suspend fun waitForServerReachability(
        host: String,
        port: Int,
        deadlineMs: Long
    ): Boolean {
        while (true) {
            if (!vpnStateProvider.readCurrentVpnStatus().hasTransportVpn) {
                return false
            }
            if (reachabilityChecker.isServerReachable(host, port, timeoutMs = 2000)) {
                return true
            }
            val now = timeProvider()
            if (now >= deadlineMs) return false
            delay(pollIntervalMs.coerceAtMost((deadlineMs - now).coerceAtLeast(1L)))
        }
    }

    suspend fun onSessionEnded(sessionId: String) {
        val noLeasesRemaining = leaseManager.releaseLease(sessionId)
        if (!noLeasesRemaining) {
            publishSnapshot(_diagnosticSnapshot.value.progressState, _diagnosticSnapshot.value.lastError)
            return
        }
        orchestrationMutex.withLock {
            disconnectIfSafeToDoSoLocked(forceZeroLeasesCheck = false)
        }
    }

    suspend fun onAllSessionsEnded() {
        val noLeasesRemaining = leaseManager.releaseAllLeases()
        if (!noLeasesRemaining) {
            publishSnapshot(_diagnosticSnapshot.value.progressState, _diagnosticSnapshot.value.lastError)
            return
        }
        orchestrationMutex.withLock {
            disconnectIfSafeToDoSoLocked(forceZeroLeasesCheck = false)
        }
    }

    suspend fun disconnectOwnedVpnNow(): TailscaleLaunchResult = orchestrationMutex.withLock {
        if (ownershipStore.current() != VpnOwnership.APP_REQUESTED) {
            return@withLock TailscaleLaunchResult(
                launched = false,
                installState = controller.checkInstallState(),
                errorMessage = "ไม่สามารถปิด VPN ที่ไม่ได้เริ่มจาก TrueSeekrSSH ได้"
            )
        }
        val result = controller.launchDisconnectVpn()
        if (result.launched) {
            leaseManager.releaseAllLeases()
            ownershipStore.markNone()
            publishSnapshot(TailscaleProgressState.IDLE, null)
        }
        result
    }

    private suspend fun disconnectIfSafeToDoSoLocked(forceZeroLeasesCheck: Boolean) {
        if (!forceZeroLeasesCheck && leaseManager.hasAnyActiveOrAcquiring()) {
            return
        }
        val settings = preferencesRepository.settings.first()
        if (!settings.tailscaleAutoDisconnect) {
            return
        }
        if (ownershipStore.current() != VpnOwnership.APP_REQUESTED) {
            // Core safety guarantee: never disconnect EXTERNAL, UNKNOWN, or NONE
            return
        }
        if (!vpnStateProvider.readCurrentVpnStatus().hasTransportVpn) {
            ownershipStore.markNone()
            publishSnapshot(TailscaleProgressState.IDLE, null)
            return
        }
        val res = controller.launchDisconnectVpn()
        if (res.launched) {
            ownershipStore.markNone()
            publishSnapshot(TailscaleProgressState.IDLE, null)
        }
    }

    private fun buildSnapshot(progress: TailscaleProgressState, lastError: String?): TailscaleDiagnosticSnapshot {
        val vpn = vpnStateProvider.readCurrentVpnStatus()
        val install = controller.checkInstallState()
        val own = ownershipStore.current()
        return TailscaleDiagnosticSnapshot(
            installState = install,
            vpnStatus = vpn,
            ownership = own,
            activeLeaseCount = leaseManager.leaseCount.value,
            progressState = progress,
            lastError = lastError
        )
    }

    private fun publishSnapshot(progress: TailscaleProgressState, lastError: String?) {
        _diagnosticSnapshot.value = buildSnapshot(progress, lastError)
    }
}
