package com.lagradost.quicknovel.network

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class ProxyAuthenticator(private val proxySelector: DynamicProxySelector) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val username = proxySelector.usernameVal
        val password = proxySelector.passwordVal

        if (username.isNotEmpty() && password.isNotEmpty()) {
            // Prevent infinite authentication loop if credentials are invalid
            if (response.request.header("Proxy-Authorization") != null) {
                return null
            }

            val credential = Credentials.basic(username, password)
            return response.request.newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }
        return null
    }
}
