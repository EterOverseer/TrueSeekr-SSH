package com.example.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Concurrency-safe reference counter tracking SSH sessions that rely on the app-managed Tailscale VPN.
 * Also counts in-flight connection attempts (`acquiringCount`) so a disconnecting tab cannot
 * tear down Tailscale while a concurrent tab is waiting to establish its SSH session.
 */
class TailscaleSessionLeaseManager {
    private val mutex = Mutex()
    private val activeLeases = linkedSetOf<String>()
    private var acquiringCount = 0

    private val _leaseCount = MutableStateFlow(0)
    val leaseCount: StateFlow<Int> = _leaseCount.asStateFlow()

    suspend fun beginAcquire() = mutex.withLock {
        acquiringCount++
    }

    suspend fun endAcquire() = mutex.withLock {
        if (acquiringCount > 0) acquiringCount--
    }

    suspend fun grantLease(sessionId: String) = mutex.withLock {
        activeLeases.add(sessionId)
        _leaseCount.value = activeLeases.size
    }

    suspend fun hasLease(sessionId: String): Boolean = mutex.withLock {
        sessionId in activeLeases
    }

    suspend fun activeLeaseCount(): Int = mutex.withLock {
        activeLeases.size
    }

    suspend fun hasAnyActiveOrAcquiring(): Boolean = mutex.withLock {
        activeLeases.isNotEmpty() || acquiringCount > 0
    }

    /**
     * Releases a single session lease.
     * @return true ONLY when zero active leases remain AND zero concurrent acquisitions are in flight.
     */
    suspend fun releaseLease(sessionId: String): Boolean = mutex.withLock {
        activeLeases.remove(sessionId)
        _leaseCount.value = activeLeases.size
        activeLeases.isEmpty() && acquiringCount == 0
    }

    suspend fun releaseAllLeases(): Boolean = mutex.withLock {
        activeLeases.clear()
        _leaseCount.value = 0
        acquiringCount == 0
    }
}
