package com.example.network

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

interface TailscaleController {
    fun checkInstallState(): TailscaleInstallState
    fun launchConnectVpn(): TailscaleLaunchResult
    fun launchDisconnectVpn(): TailscaleLaunchResult
    fun openTailscaleApp(): Boolean
    fun openTailscalePlayStore(): Boolean
}

class AndroidTailscaleIntentController(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT
) : TailscaleController {

    companion object {
        const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
        const val AUTOMATION_ACTIVITY = "com.tailscale.ipn.AutomationActivity"
        const val ACTION_CONNECT_VPN = "com.tailscale.ipn.CONNECT_VPN"
        const val ACTION_DISCONNECT_VPN = "com.tailscale.ipn.DISCONNECT_VPN"
        const val MIN_AUTOMATION_SDK = Build.VERSION_CODES.O
    }

    override fun checkInstallState(): TailscaleInstallState {
        if (sdkInt < MIN_AUTOMATION_SDK) {
            return TailscaleInstallState.UNSUPPORTED_ANDROID_VERSION
        }
        val pm = context.packageManager
        val isPackagePresent = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(TAILSCALE_PACKAGE, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(TAILSCALE_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }

        if (!isPackagePresent) {
            return TailscaleInstallState.NOT_INSTALLED
        }

        val probeIntent = createAutomationIntent(ACTION_CONNECT_VPN)
        val resolveInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(probeIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(probeIntent, 0)
            }
        } catch (_: Throwable) {
            null
        }

        return if (resolveInfo?.activityInfo?.exported == true) {
            TailscaleInstallState.INSTALLED
        } else {
            TailscaleInstallState.AUTOMATION_UNAVAILABLE
        }
    }

    override fun launchConnectVpn(): TailscaleLaunchResult {
        return launchAutomationAction(ACTION_CONNECT_VPN)
    }

    override fun launchDisconnectVpn(): TailscaleLaunchResult {
        return launchAutomationAction(ACTION_DISCONNECT_VPN)
    }

    override fun openTailscaleApp(): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    override fun openTailscalePlayStore(): Boolean {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$TAILSCALE_PACKAGE")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(marketIntent); true }.getOrDefault(false)) {
            return true
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$TAILSCALE_PACKAGE")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(webIntent)
            true
        }.getOrDefault(false)
    }

    private fun launchAutomationAction(action: String): TailscaleLaunchResult {
        val installState = checkInstallState()
        if (installState != TailscaleInstallState.INSTALLED) {
            val reason = when (installState) {
                TailscaleInstallState.NOT_INSTALLED -> "ไม่พบแอป Tailscale บนอุปกรณ์"
                TailscaleInstallState.AUTOMATION_UNAVAILABLE -> "แอป Tailscale เวอร์ชันนี้ไม่เปิด AutomationActivity"
                TailscaleInstallState.UNSUPPORTED_ANDROID_VERSION -> "AutomationActivity รองรับ Android 8.0 ขึ้นไป"
                TailscaleInstallState.INSTALLED -> ""
            }
            return TailscaleLaunchResult(
                launched = false,
                installState = installState,
                errorMessage = reason
            )
        }

        val intent = createAutomationIntent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            TailscaleLaunchResult(
                launched = true,
                installState = TailscaleInstallState.INSTALLED
            )
        } catch (e: ActivityNotFoundException) {
            TailscaleLaunchResult(
                launched = false,
                installState = TailscaleInstallState.AUTOMATION_UNAVAILABLE,
                errorMessage = "ไม่พบ Tailscale AutomationActivity (${e.message ?: "ActivityNotFound"})"
            )
        } catch (e: SecurityException) {
            TailscaleLaunchResult(
                launched = false,
                installState = TailscaleInstallState.AUTOMATION_UNAVAILABLE,
                errorMessage = "ไม่ได้รับสิทธิ์เรียกใช้งาน Tailscale AutomationActivity (${e.message ?: "SecurityException"})"
            )
        } catch (t: Throwable) {
            TailscaleLaunchResult(
                launched = false,
                installState = TailscaleInstallState.INSTALLED,
                errorMessage = "เปิด Tailscale ไม่สำเร็จ: ${t.message ?: t::class.simpleName}"
            )
        }
    }

    private fun createAutomationIntent(action: String): Intent {
        return Intent(action).apply {
            setClassName(TAILSCALE_PACKAGE, AUTOMATION_ACTIVITY)
        }
    }
}
