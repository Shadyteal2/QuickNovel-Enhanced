package com.lagradost.quicknovel.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale
import javax.net.SocketFactory

object NetworkUtils {

    /**
     * Resolves the local IPv4 address of the device on the Wi-Fi network interface.
     */
    fun getLocalIpAddress(context: Context): String? {
        try {
            // Standard approach using WifiManager for IP resolution
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val connectionInfo = wifiManager?.connectionInfo
            val ipAddress = connectionInfo?.ipAddress ?: 0
            if (ipAddress != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            com.lagradost.quicknovel.mvvm.logError(e)
        }

        // Fallback: iterate over all network interfaces
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in Collections.list(interfaces)) {
                if (intf.name.contains("wlan") || intf.name.contains("p2p")) {
                    val addrs = intf.inetAddresses
                    for (addr in Collections.list(addrs)) {
                        if (!addr.isLoopbackAddress) {
                            val sAddr = addr.hostAddress ?: continue
                            val isIPv4 = sAddr.indexOf(':') < 0
                            if (isIPv4) return sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            com.lagradost.quicknovel.mvvm.logError(e)
        }
        return null
    }

    /**
     * Returns the active Wi-Fi network interface to bypass VPN profiles.
     */
    fun getWifiNetwork(context: Context): Network? {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val networks = connectivityManager?.allNetworks ?: emptyArray()
            for (network in networks) {
                val caps = connectivityManager?.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return network
                }
            }
            null
        } catch (e: Exception) {
            com.lagradost.quicknovel.mvvm.logError(e)
            null
        }
    }

    /**
     * Returns a SocketFactory bound directly to the Wi-Fi interface if available,
     * which forces OkHttp to bypass local VPN routing.
     */
    fun getWifiSocketFactory(context: Context): SocketFactory? {
        val wifiNetwork = getWifiNetwork(context) ?: return null
        return wifiNetwork.socketFactory
    }
}
