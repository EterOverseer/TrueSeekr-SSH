package com.example.ssh

import com.example.domain.SessionStatus
import com.example.domain.SshProfile
import com.example.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.sshlib.PingResult
import org.connectbot.sshlib.SshClient

sealed interface KeepAlivePingOutcome {
    data class Alive(val rttMs: Long) : KeepAlivePingOutcome
    data object SlowOrFailed : KeepAlivePingOutcome
}

data class EffectiveKeepAliveConfig(
    val enabled: Boolean,
    val intervalSec: Int,
    val maxMissed: Int
)

/**
 * Pure watchdog helper managing SSH-level keepalive probing via ConnectBot cbssh `ping()`,
 * distinguishing slow/checking responses from genuine dead peers (`missedCount >= maxMissed`).
 * Never uses shell-echo heartbeats or second SSH connections.
 */
object SshKeepAliveWatchdog {

    fun resolveConfig(profile: SshProfile, settings: AppSettings): EffectiveKeepAliveConfig {
        if (!settings.sshKeepAliveEnabled || profile.keepAliveIntervalSec == 0) {
            return EffectiveKeepAliveConfig(
                enabled = false,
                intervalSec = 0,
                maxMissed = settings.sshKeepAliveMaxMissed.coerceIn(1, 10)
            )
        }
        val effectiveInterval = if (profile.keepAliveIntervalSec != 30) {
            profile.keepAliveIntervalSec.coerceIn(5, 300)
        } else {
            settings.sshKeepAliveIntervalSec.coerceIn(5, 300)
        }
        return EffectiveKeepAliveConfig(
            enabled = true,
            intervalSec = effectiveInterval,
            maxMissed = settings.sshKeepAliveMaxMissed.coerceIn(1, 10)
        )
    }

    suspend fun probeSshClient(client: SshClient, timeoutMs: Long): KeepAlivePingOutcome {
        if (!client.isAuthenticated) {
            return KeepAlivePingOutcome.SlowOrFailed
        }
        val startNs = System.nanoTime()
        return try {
            val result = withTimeoutOrNull(timeoutMs) {
                client.ping()
            } ?: return KeepAlivePingOutcome.SlowOrFailed

            val measuredRtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            when (result) {
                is PingResult.Success -> {
                    val rtt = (result.elapsedNs / 1_000_000L).coerceAtLeast(1L)
                    KeepAlivePingOutcome.Alive(rtt)
                }
                // Remote SSH server replied SSH_MSG_UNIMPLEMENTED -> peer & TCP socket are alive!
                is PingResult.NotSupported -> KeepAlivePingOutcome.Alive(measuredRtt)
                is PingResult.Failure, is PingResult.NotAuthenticated -> KeepAlivePingOutcome.SlowOrFailed
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            KeepAlivePingOutcome.SlowOrFailed
        }
    }

    suspend fun runMonitorLoop(
        config: EffectiveKeepAliveConfig,
        lastActivityMsProvider: () -> Long,
        nowMsProvider: () -> Long = { System.currentTimeMillis() },
        delayMillisFn: suspend (Long) -> Unit = { delay(it) },
        pingFn: suspend (timeoutMs: Long) -> KeepAlivePingOutcome,
        onStatusUpdated: (SessionStatus, Long?, Int, String?) -> Unit,
        onConnectionLost: suspend (Int) -> Unit
    ) {
        if (!config.enabled || config.intervalSec <= 0) return

        val intervalMs = config.intervalSec * 1000L
        val pingTimeoutMs = intervalMs.coerceIn(5_000L, 12_000L)
        val idleThresholdMs = intervalMs * 2L
        var missedCount = 0

        while (true) {
            delayMillisFn(intervalMs)
            when (val outcome = pingFn(pingTimeoutMs)) {
                is KeepAlivePingOutcome.Alive -> {
                    missedCount = 0
                    val idleDurationMs = nowMsProvider() - lastActivityMsProvider()
                    val status = if (idleDurationMs >= idleThresholdMs) {
                        SessionStatus.CONNECTION_IDLE
                    } else {
                        SessionStatus.CONNECTED
                    }
                    onStatusUpdated(status, outcome.rttMs, 0, null)
                }
                is KeepAlivePingOutcome.SlowOrFailed -> {
                    missedCount++
                    if (missedCount < config.maxMissed) {
                        onStatusUpdated(
                            SessionStatus.CHECKING_CONNECTION,
                            null,
                            missedCount,
                            "กำลังตรวจสอบการเชื่อมต่อ ($missedCount/${config.maxMissed})..."
                        )
                    } else {
                        onConnectionLost(missedCount)
                        return
                    }
                }
            }
        }
    }
}
