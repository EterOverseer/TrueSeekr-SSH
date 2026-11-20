package com.example.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks whether the currently active VPN is owned by this TrueSeekrSSH process.
 *
 * SAFETY RULE:
 * Ownership across app process restarts is NEVER promoted back to APP_REQUESTED.
 * If Android restarted the app while a VPN remained active, ownership downgrades
 * to UNKNOWN so TrueSeekrSSH never disconnects an uncertain VPN.
 */
class TailscaleOwnershipStore(
    private val prefs: SharedPreferences? = null,
    private val currentPidProvider: () -> Int = { Process.myPid() },
    initialVpnActive: Boolean = false
) {
    constructor(context: Context, initialVpnActive: Boolean = false) : this(
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        initialVpnActive = initialVpnActive
    )

    companion object {
        private const val PREFS_NAME = "trueseeekr_tailscale_ownership"
        private const val KEY_WAS_APP_REQUESTED = "was_app_requested"
        private const val KEY_OWNER_PID = "owner_pid"
        private const val KEY_UPDATED_AT_MS = "updated_at_ms"
    }

    private val mutex = Mutex()
    private val _ownership = MutableStateFlow(computeInitialState(initialVpnActive))
    val ownership: StateFlow<VpnOwnership> = _ownership.asStateFlow()

    private fun computeInitialState(initialVpnActive: Boolean): VpnOwnership {
        val currentPid = currentPidProvider()
        val wasAppRequested = prefs?.getBoolean(KEY_WAS_APP_REQUESTED, false) ?: false
        val savedPid = prefs?.getInt(KEY_OWNER_PID, -1) ?: -1

        if (!initialVpnActive) {
            clearPersisted()
            return VpnOwnership.NONE
        }

        // VPN is active when store initializes:
        // Never restore APP_REQUESTED across a different process lifetime.
        return if (wasAppRequested && savedPid != currentPid) {
            clearPersisted()
            VpnOwnership.UNKNOWN
        } else if (wasAppRequested && savedPid == currentPid) {
            VpnOwnership.APP_REQUESTED
        } else {
            VpnOwnership.EXTERNAL
        }
    }

    fun current(): VpnOwnership = _ownership.value

    suspend fun markAppRequested(nowMs: Long = System.currentTimeMillis()) = mutex.withLock {
        _ownership.value = VpnOwnership.APP_REQUESTED
        prefs?.edit()
            ?.putBoolean(KEY_WAS_APP_REQUESTED, true)
            ?.putInt(KEY_OWNER_PID, currentPidProvider())
            ?.putLong(KEY_UPDATED_AT_MS, nowMs)
            ?.apply()
    }

    suspend fun markExternal() = mutex.withLock {
        _ownership.value = VpnOwnership.EXTERNAL
        clearPersisted()
    }

    suspend fun markUnknown() = mutex.withLock {
        _ownership.value = VpnOwnership.UNKNOWN
        clearPersisted()
    }

    suspend fun markNone() = mutex.withLock {
        _ownership.value = VpnOwnership.NONE
        clearPersisted()
    }

    suspend fun onVpnObserved(vpnActive: Boolean, isStartingInThisApp: Boolean) = mutex.withLock {
        if (!vpnActive) {
            if (_ownership.value != VpnOwnership.NONE) {
                _ownership.value = VpnOwnership.NONE
                clearPersisted()
            }
            return@withLock
        }

        // VPN is active externally when app didn't initiate it
        if (!isStartingInThisApp && _ownership.value == VpnOwnership.NONE) {
            _ownership.value = VpnOwnership.EXTERNAL
            clearPersisted()
        }
    }

    private fun clearPersisted() {
        prefs?.edit()
            ?.putBoolean(KEY_WAS_APP_REQUESTED, false)
            ?.remove(KEY_OWNER_PID)
            ?.remove(KEY_UPDATED_AT_MS)
            ?.apply()
    }
}
