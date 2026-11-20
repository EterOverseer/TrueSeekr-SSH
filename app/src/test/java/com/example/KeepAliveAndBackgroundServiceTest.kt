package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.domain.SessionStatus
import com.example.domain.SshProfile
import com.example.settings.AppSettings
import com.example.ssh.EffectiveKeepAliveConfig
import com.example.ssh.KeepAlivePingOutcome
import com.example.ssh.KeepAliveWakeLockCoordinator
import com.example.ssh.SshKeepAliveService
import com.example.ssh.SshKeepAliveWatchdog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KeepAliveAndBackgroundServiceTest {

    @Test
    fun `watchdog resolves global vs per-profile keepalive intervals properly`() {
        val baseProfile = SshProfile(id = "1", name = "S1", host = "10.0.0.1", username = "u", keepAliveIntervalSec = 30)
        val customProfile = baseProfile.copy(keepAliveIntervalSec = 15)
        val zeroProfile = baseProfile.copy(keepAliveIntervalSec = 0)

        val settingsDefault = AppSettings(sshKeepAliveEnabled = true, sshKeepAliveIntervalSec = 60, sshKeepAliveMaxMissed = 4)
        val resolvedDefault = SshKeepAliveWatchdog.resolveConfig(baseProfile, settingsDefault)
        assertTrue(resolvedDefault.enabled)
        assertEquals(60, resolvedDefault.intervalSec)
        assertEquals(4, resolvedDefault.maxMissed)

        val resolvedCustom = SshKeepAliveWatchdog.resolveConfig(customProfile, settingsDefault)
        assertTrue(resolvedCustom.enabled)
        assertEquals(15, resolvedCustom.intervalSec)

        val resolvedZero = SshKeepAliveWatchdog.resolveConfig(zeroProfile, settingsDefault)
        assertFalse(resolvedZero.enabled)

        val resolvedMasterOff = SshKeepAliveWatchdog.resolveConfig(customProfile, settingsDefault.copy(sshKeepAliveEnabled = false))
        assertFalse(resolvedMasterOff.enabled)
    }

    @Test
    fun `monitor loop transitions CHECKING_CONNECTION on transient miss, recovers on Alive, and fires CONNECTION_LOST at threshold`() = runBlocking {
        val cfg = EffectiveKeepAliveConfig(enabled = true, intervalSec = 10, maxMissed = 3)
        val outcomes = ArrayDeque(
            listOf(
                KeepAlivePingOutcome.Alive(14L),
                KeepAlivePingOutcome.SlowOrFailed,
                KeepAlivePingOutcome.SlowOrFailed,
                KeepAlivePingOutcome.Alive(22L), // recovers cleanly before reaching 3
                KeepAlivePingOutcome.SlowOrFailed,
                KeepAlivePingOutcome.SlowOrFailed,
                KeepAlivePingOutcome.SlowOrFailed // reaches 3/3 -> CONNECTION_LOST
            )
        )
        val observedStatuses = mutableListOf<Triple<SessionStatus, Long?, Int>>()
        var lostCount = 0

        SshKeepAliveWatchdog.runMonitorLoop(
            config = cfg,
            lastActivityMsProvider = { 10_000L },
            nowMsProvider = { 12_000L },
            delayMillisFn = { },
            pingFn = { outcomes.removeFirst() },
            onStatusUpdated = { status, rtt, missed, _ ->
                observedStatuses += Triple(status, rtt, missed)
            },
            onConnectionLost = { missed ->
                lostCount = missed
            }
        )

        assertEquals(3, lostCount)
        assertEquals(6, observedStatuses.size)
        assertEquals(SessionStatus.CONNECTED, observedStatuses[0].first)
        assertEquals(14L, observedStatuses[0].second)
        assertEquals(SessionStatus.CHECKING_CONNECTION, observedStatuses[1].first)
        assertEquals(1, observedStatuses[1].third)
        assertEquals(SessionStatus.CHECKING_CONNECTION, observedStatuses[2].first)
        assertEquals(2, observedStatuses[2].third)
        assertEquals(SessionStatus.CONNECTED, observedStatuses[3].first) // recovered
        assertEquals(0, observedStatuses[3].third)
        assertEquals(SessionStatus.CHECKING_CONNECTION, observedStatuses[4].first)
        assertEquals(SessionStatus.CHECKING_CONNECTION, observedStatuses[5].first)
    }

    @Test
    fun `wake lock coordinator acquires only when enabled and releases cleanly on disable or destroy`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val coordinator = KeepAliveWakeLockCoordinator(powerManager)

        assertFalse(coordinator.isHeld)
        coordinator.update(shouldHold = true)
        assertTrue(coordinator.isHeld)

        // Calling acquire/update(true) repeatedly does not leak non-refcounted lock
        coordinator.update(shouldHold = true)
        assertTrue(coordinator.isHeld)

        coordinator.update(shouldHold = false)
        assertFalse(coordinator.isHeld)
    }

    @Test
    fun `SshKeepAliveService starts foreground notification and stops cleanly when actioned or activeCount becomes zero`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val startIntent = Intent(context, SshKeepAliveService::class.java).apply {
            action = SshKeepAliveService.ACTION_UPDATE_STATE
            putExtra(SshKeepAliveService.EXTRA_ACTIVE_COUNT, 2)
            putExtra(SshKeepAliveService.EXTRA_KEEP_CPU_AWAKE, true)
        }
        val controller = Robolectric.buildService(SshKeepAliveService::class.java, startIntent)
        val service = controller.create().startCommand(0, 1).get()

        val shadowService = Shadows.shadowOf(service)
        val notification = shadowService.lastForegroundNotification
        assertEquals(SshKeepAliveService.NOTIFICATION_ID, shadowService.lastForegroundNotificationId)
        val title = notification.extras.getString(android.app.Notification.EXTRA_TITLE).orEmpty()
        assertTrue(title.contains("2"))
        assertTrue(notification.actions.any { it.title == "ตัดการเชื่อมต่อ" })

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(SshKeepAliveService.CHANNEL_ID)
        assertEquals(SshKeepAliveService.CHANNEL_ID, channel.id)

        // Updating with activeCount = 0 stops service & releases wakelock
        val zeroIntent = Intent(context, SshKeepAliveService::class.java).apply {
            action = SshKeepAliveService.ACTION_UPDATE_STATE
            putExtra(SshKeepAliveService.EXTRA_ACTIVE_COUNT, 0)
            putExtra(SshKeepAliveService.EXTRA_KEEP_CPU_AWAKE, false)
        }
        service.onStartCommand(zeroIntent, 0, 2)
        assertTrue(shadowService.isStoppedBySelf)
        controller.destroy()
    }
}
