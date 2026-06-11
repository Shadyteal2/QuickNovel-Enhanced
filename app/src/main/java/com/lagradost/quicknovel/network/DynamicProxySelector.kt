package com.lagradost.quicknovel.network

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

class DynamicProxySelector(context: Context) : ProxySelector() {
    private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    
    // In-memory cached fields
    @Volatile var isEnabled: Boolean = false
        private set
    @Volatile var host: String = ""
        private set
    @Volatile var port: Int = 0
        private set
    @Volatile var proxyTypeStr: String = "HTTP"
        private set
    @Volatile var usernameVal: String = ""
        private set
    @Volatile var passwordVal: String = ""
        private set

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "custom_proxy_enabled",
            "custom_proxy_host",
            "custom_proxy_port",
            "custom_proxy_type",
            "custom_proxy_username",
            "custom_proxy_password" -> updateCache()
        }
    }

    init {
        updateCache()
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun updateCache() {
        isEnabled = sharedPrefs.getBoolean("custom_proxy_enabled", false)
        host = sharedPrefs.getString("custom_proxy_host", "")?.trim() ?: ""
        val portString = sharedPrefs.getString("custom_proxy_port", "")?.trim() ?: ""
        port = portString.toIntOrNull() ?: 0
        proxyTypeStr = sharedPrefs.getString("custom_proxy_type", "HTTP") ?: "HTTP"
        usernameVal = sharedPrefs.getString("custom_proxy_username", "") ?: ""
        passwordVal = sharedPrefs.getString("custom_proxy_password", "") ?: ""
    }

    override fun select(uri: URI?): List<Proxy> {
        if (isEnabled && host.isNotEmpty() && port in 1..65535) {
            val proxyType = if (proxyTypeStr.uppercase() == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP
            try {
                val address = InetSocketAddress.createUnresolved(host, port)
                return listOf(Proxy(proxyType, address))
            } catch (e: Exception) {
                // Fall back
            }
        }
        return ProxySelector.getDefault()?.select(uri) ?: listOf(Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        // No-op or log
    }
}

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
data class IpifyResponse(val ip: String)
