package com.example.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkTransportType(val label: String) {
    WIFI("Wi-Fi / LAN"),
    VPN_OR_TAILSCALE("VPN / Tailscale"),
    CELLULAR("Cellular"),
    ETHERNET("Ethernet"),
    OTHER("Network"),
    OFFLINE("ออฟไลน์")
}

data class NetworkStatus(
    val isConnected: Boolean = false,
    val transportType: NetworkTransportType = NetworkTransportType.OFFLINE,
    val hasVpn: Boolean = false
)

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(readCurrentStatus())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val _networkTransitions = MutableSharedFlow<NetworkStatus>(extraBufferCapacity = 8)
    val networkTransitions: SharedFlow<NetworkStatus> = _networkTransitions.asSharedFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateStatus()
        }

        override fun onLost(network: Network) {
            updateStatus()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateStatus()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Throwable) {
            // Safe fallback on restricted test environments
        }
    }

    private fun updateStatus() {
        val next = readCurrentStatus()
        val prev = _status.value
        _status.value = next
        if (next != prev) {
            _networkTransitions.tryEmit(next)
        }
    }

    fun readCurrentStatus(): NetworkStatus {
        return try {
            val active = connectivityManager.activeNetwork ?: return NetworkStatus(false, NetworkTransportType.OFFLINE, false)
            val caps = connectivityManager.getNetworkCapabilities(active)
                ?: return NetworkStatus(false, NetworkTransportType.OFFLINE, false)
            val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val transport = when {
                !isConnected -> NetworkTransportType.OFFLINE
                hasVpn -> NetworkTransportType.VPN_OR_TAILSCALE
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransportType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransportType.ETHERNET
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransportType.CELLULAR
                else -> NetworkTransportType.OTHER
            }
            NetworkStatus(isConnected = isConnected, transportType = transport, hasVpn = hasVpn)
        } catch (_: Throwable) {
            NetworkStatus(isConnected = true, transportType = NetworkTransportType.OTHER, hasVpn = false)
        }
    }
}
