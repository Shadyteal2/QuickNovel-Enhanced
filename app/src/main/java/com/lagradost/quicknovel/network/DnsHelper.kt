package com.lagradost.quicknovel.network

import android.content.Context
import androidx.preference.PreferenceManager
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.lagradost.quicknovel.NetworkPrefs
import com.lagradost.quicknovel.mvvm.logError
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

object DnsHelper {
    private var dynamicDns: DynamicDns? = null
    private val connectionPools = mutableListOf<ConnectionPool>()

    fun init(context: Context) {
        if (dynamicDns == null) {
            dynamicDns = DynamicDns(context.applicationContext)
        }
    }

    fun getDns(): Dns {
        return dynamicDns ?: Dns.SYSTEM
    }

    fun registerConnectionPool(pool: ConnectionPool) {
        synchronized(connectionPools) {
            if (!connectionPools.contains(pool)) {
                connectionPools.add(pool)
            }
        }
    }

    fun updateDns(context: Context) {
        init(context)
        dynamicDns?.updateDns()
        // Dynamically evict all connections to sever existing standard DNS connections immediately
        synchronized(connectionPools) {
            connectionPools.forEach { pool ->
                try {
                    pool.evictAll()
                } catch (e: Exception) {
                    logError(e)
                }
            }
        }
    }

    fun buildDnsOverHttps(context: Context, provider: String): Dns {
        val bootstrapClient = OkHttpClient.Builder()
            .ignoreAllSSLErrors()
            .build()

        return when (provider) {
            "cloudflare" -> DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    listOf(
                        InetAddress.getByName("1.1.1.1"),
                        InetAddress.getByName("1.0.0.1"),
                        InetAddress.getByName("2606:4700:4700::1111"),
                        InetAddress.getByName("2606:4700:4700::1001")
                    )
                )
                .build()
            "google" -> DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://dns.google/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    listOf(
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("8.8.4.4"),
                        InetAddress.getByName("2001:4860:4860::8888"),
                        InetAddress.getByName("2001:4860:4860::8844")
                    )
                )
                .build()
            "adguard" -> DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://dns.adguard-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    listOf(
                        InetAddress.getByName("94.140.14.14"),
                        InetAddress.getByName("94.140.15.15")
                    )
                )
                .build()
            "quad9" -> DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://dns.quad9.net/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    listOf(
                        InetAddress.getByName("9.9.9.9"),
                        InetAddress.getByName("149.112.112.112")
                    )
                )
                .build()
            "dnspod" -> DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://doh.pub/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    listOf(
                        InetAddress.getByName("119.29.29.29"),
                        InetAddress.getByName("1.12.34.5"),
                        InetAddress.getByName("120.53.53.53")
                    )
                )
                .build()
            "mullvad" -> DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://doh.mullvad.net/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    listOf(
                        InetAddress.getByName("194.242.2.2"),
                        InetAddress.getByName("194.242.2.3")
                    )
                )
                .build()
            else -> Dns.SYSTEM
        }
    }

    private class DynamicDns(private val context: Context) : Dns {
        private var currentProvider: String? = null
        private var currentDns: Dns = Dns.SYSTEM

        init {
            updateDns()
        }

        fun updateDns() {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val dohProvider = sharedPrefs.getString(NetworkPrefs.DOH_PROVIDER, "none") ?: "none"
            if (dohProvider == currentProvider) return

            currentProvider = dohProvider
            currentDns = if (dohProvider == "none") {
                Dns.SYSTEM
            } else {
                try {
                    buildDnsOverHttps(context, dohProvider)
                } catch (e: Exception) {
                    logError(e)
                    Dns.SYSTEM
                }
            }
        }

        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                currentDns.lookup(hostname)
            } catch (e: Exception) {
                logError(e)
                if (currentDns != Dns.SYSTEM) {
                    // Fall back to system default DNS if DoH provider is unreachable/blocked
                    try {
                        Dns.SYSTEM.lookup(hostname)
                    } catch (systemEx: Exception) {
                        throw systemEx
                    }
                } else {
                    throw e
                }
            }
        }
    }
}
