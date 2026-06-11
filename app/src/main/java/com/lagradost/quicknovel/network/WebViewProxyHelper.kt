package com.lagradost.quicknovel.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor

object WebViewProxyHelper {
    private const val TAG = "WebViewProxyHelper"

    fun syncProxy(context: Context) {
        // Run on the main thread since WebView interactions require the UI thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                syncProxy(context)
            }
            return
        }

        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                Log.w(TAG, "syncProxy: PROXY_OVERRIDE is not supported on this device.")
                return
            }

            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val enabled = sharedPrefs.getBoolean("custom_proxy_enabled", false)
            val host = sharedPrefs.getString("custom_proxy_host", "")?.trim() ?: ""
            val portString = sharedPrefs.getString("custom_proxy_port", "")?.trim() ?: ""
            val port = portString.toIntOrNull() ?: 0
            val typeStr = sharedPrefs.getString("custom_proxy_type", "HTTP") ?: "HTTP"

            val controller = ProxyController.getInstance()

            if (enabled && host.isNotEmpty() && port in 1..65535) {
                val scheme = if (typeStr.uppercase() == "SOCKS") "socks5" else "http"
                val proxyUrl = "$scheme://$host:$port"
                
                val proxyConfig = ProxyConfig.Builder()
                    .addProxyRule(proxyUrl)
                    .build()

                val executor = Executor { command -> command.run() }
                controller.setProxyOverride(proxyConfig, executor, Runnable {
                    Log.d(TAG, "setProxyOverride: WebView proxy set successfully to $proxyUrl")
                })
            } else {
                val executor = Executor { command -> command.run() }
                controller.clearProxyOverride(executor, Runnable {
                    Log.d(TAG, "clearProxyOverride: WebView proxy cleared successfully")
                })
            }
        } catch (t: Throwable) {
            Log.e(TAG, "syncProxy: WebView proxy override failed safely", t)
        }
    }
}
