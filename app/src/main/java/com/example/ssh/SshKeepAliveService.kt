package com.example.ssh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.PocketSshApplication
import com.example.R

interface KeepAliveServiceBridge {
    fun syncState(enabled: Boolean, keepCpuAwake: Boolean, activeCount: Int)
}

/**
 * Pure controller wrapping PowerManager.WakeLock so CPU wake-lock acquire/release
 * invariants are enforced consistently and unit-testable without leaking locks.
 */
class KeepAliveWakeLockCoordinator(
    private val powerManager: PowerManager?
) {
    private var wakeLock: PowerManager.WakeLock? = null

    val isHeld: Boolean
        get() = wakeLock?.isHeld == true

    @Synchronized
    fun update(shouldHold: Boolean) {
        if (shouldHold) {
            acquire()
        } else {
            release()
        }
    }

    @Synchronized
    fun acquire() {
        val pm = powerManager ?: return
        try {
            val lock = wakeLock ?: pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TrueSeekrSSH:KeepAliveWakeLock"
            ).apply {
                setReferenceCounted(false)
            }.also { wakeLock = it }

            if (!lock.isHeld) {
                lock.acquire()
            }
        } catch (_: Throwable) {
            release()
        }
    }

    @Synchronized
    fun release() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                }
            }
        } catch (_: Throwable) {
            // Best-effort release
        } finally {
            wakeLock = null
        }
    }
}

class AndroidKeepAliveServiceBridge(
    private val context: Context
) : KeepAliveServiceBridge {
    private var running = false

    @Synchronized
    override fun syncState(enabled: Boolean, keepCpuAwake: Boolean, activeCount: Int) {
        if (!enabled || activeCount <= 0) {
            if (running) {
                running = false
                runCatching {
                    context.stopService(Intent(context, SshKeepAliveService::class.java))
                }
            }
            return
        }

        val intent = Intent(context, SshKeepAliveService::class.java).apply {
            action = SshKeepAliveService.ACTION_UPDATE_STATE
            putExtra(SshKeepAliveService.EXTRA_ACTIVE_COUNT, activeCount)
            putExtra(SshKeepAliveService.EXTRA_KEEP_CPU_AWAKE, keepCpuAwake)
        }
        runCatching {
            ContextCompat.startForegroundService(context, intent)
            running = true
        }
    }
}

/**
 * Opt-in foreground service (`connectedDevice`) that keeps active SSH connections alive
 * across background transitions when explicitly enabled by the user, displays an ongoing
 * status notification with Return + Safe Disconnect actions, and manages the optional partial WakeLock.
 */
class SshKeepAliveService : Service() {

    private var wakeLockCoordinator: KeepAliveWakeLockCoordinator? = null

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLockCoordinator = KeepAliveWakeLockCoordinator(pm)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopCleanly()
                return START_NOT_STICKY
            }
            ACTION_DISCONNECT_ALL -> {
                wakeLockCoordinator?.release()
                val app = application as? PocketSshApplication
                app?.container?.sshSessionManager?.disconnectAllSessions()
                stopCleanly()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATE, null -> {
                val activeCount = intent?.getIntExtra(EXTRA_ACTIVE_COUNT, 0) ?: 0
                val keepCpuAwake = intent?.getBooleanExtra(EXTRA_KEEP_CPU_AWAKE, false) ?: false

                if (activeCount <= 0) {
                    stopCleanly()
                    return START_NOT_STICKY
                }

                val notification = buildNotification(activeCount, keepCpuAwake)
                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                }
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
                wakeLockCoordinator?.update(shouldHold = keepCpuAwake && activeCount > 0)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeLockCoordinator?.release()
        super.onDestroy()
    }

    private fun stopCleanly() {
        wakeLockCoordinator?.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "SSH Background Connection",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "แสดงสถานะการเชื่อมต่อ SSH ขณะแอปทำงานอยู่เบื้องหลัง"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(activeCount: Int, keepCpuAwake: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, SshKeepAliveService::class.java).apply {
            action = ACTION_DISCONNECT_ALL
        }
        val disconnectPending = PendingIntent.getService(
            this,
            101,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusLine = if (keepCpuAwake) {
            "กำลังรักษาการเชื่อมต่อ $activeCount เซสชัน (CPU WakeLock เปิดอยู่)"
        } else {
            "กำลังรักษาการเชื่อมต่อ $activeCount เซสชันในเบื้องหลัง"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("TrueSeekrSSH เชื่อมต่ออยู่ ($activeCount)")
            .setContentText(statusLine)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPending)
            .addAction(0, "เปิดแอป", openPending)
            .addAction(0, "ตัดการเชื่อมต่อ", disconnectPending)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "trueseekr_ssh_keepalive"
        const val NOTIFICATION_ID = 4401

        const val ACTION_UPDATE_STATE = "com.example.ssh.action.UPDATE_KEEPALIVE_STATE"
        const val ACTION_STOP_SERVICE = "com.example.ssh.action.STOP_KEEPALIVE_SERVICE"
        const val ACTION_DISCONNECT_ALL = "com.example.ssh.action.DISCONNECT_ALL_SSH"

        const val EXTRA_ACTIVE_COUNT = "extra_active_count"
        const val EXTRA_KEEP_CPU_AWAKE = "extra_keep_cpu_awake"
    }
}
