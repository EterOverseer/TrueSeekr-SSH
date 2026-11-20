package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.core.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

interface VpnStateProvider {
    val vpnStatus: StateFlow<TailscaleVpnStatus>
    fun readCurrentVpnStatus(): TailscaleVpnStatus
}

interface ServerReachabilityChecker {
    suspend fun isServerReachable(host: String, port: Int, timeoutMs: Int): Boolean
}

/**
 * Monitors Android active VPN transport state via ConnectivityManager.
 *
 * IMPORTANT ANDROID LIMITATION:
 * ConnectivityManager reports `TRANSPORT_VPN` for ANY active VPN service on the device
 * (Tailscale, WireGuard, OpenVPN, work VPN, ad blockers). It does not expose cryptographic
 * proof that Tailscale owns the tunnel or which tailnet is active. Therefore TrueSeekrSSH
 * never treats `TRANSPORT_VPN` alone as proof of app ownership.
 */
class AndroidTailscaleVpnMonitor(
    context: Context,
    private val networkMonitor: NetworkMonitor
) : VpnStateProvider {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _vpnStatus = MutableStateFlow(readCurrentVpnStatus())
    override val vpnStatus: StateFlow<TailscaleVpnStatus> = _vpnStatus.asStateFlow()

    private val vpnCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refresh()
        }

        override fun onLost(network: Network) {
            refresh()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refresh()
        }
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivityManager.registerNetworkCallback(request, vpnCallback)
        }
    }

    fun refresh() {
        _vpnStatus.value = readCurrentVpnStatus()
    }

    @Suppress("DEPRECATION")
    override fun readCurrentVpnStatus(): TailscaleVpnStatus {
        return try {
            val netMonitorSnapshot = networkMonitor.readCurrentStatus()
            val interfaceNames = mutableListOf<String>()
            var anyVpnTransport = netMonitorSnapshot.hasVpn
            var hasInternet = netMonitorSnapshot.isConnected

            val networks = connectivityManager.allNetworks
            for (network in networks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                if (isVpn) {
                    anyVpnTransport = true
                    val linkProps = connectivityManager.getLinkProperties(network)
                    linkProps?.interfaceName?.let { name ->
                        if (name.isNotBlank() && name !in interfaceNames) {
                            interfaceNames.add(name)
                        }
                    }
                }
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    hasInternet = true
                }
            }

            val hasTailscaleHint = interfaceNames.any {
                it.contains("tailscale", ignoreCase = true) || it.startsWith("tun", ignoreCase = true)
            }

            val result = TailscaleVpnStatus(
                hasTransportVpn = anyVpnTransport,
                hasInternet = hasInternet,
                interfaceNames = interfaceNames,
                hasTailscaleHint = hasTailscaleHint
            )
            _vpnStatus.value = result
            result
        } catch (_: Throwable) {
            val fallback = networkMonitor.readCurrentStatus()
            TailscaleVpnStatus(
                hasTransportVpn = fallback.hasVpn,
                hasInternet = fallback.isConnected
            )
        }
    }
}

class SocketServerReachabilityChecker : ServerReachabilityChecker {
    override suspend fun isServerReachable(host: String, port: Int, timeoutMs: Int): Boolean =
        withContext(Dispatchers.IO) {
            val cleanHost = host.trim()
            if (cleanHost.isEmpty() || port !in 1..65535) return@withContext false
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(cleanHost, port), timeoutMs.coerceIn(400, 5000))
                }
                true
            }.getOrDefault(false)
        }
}
