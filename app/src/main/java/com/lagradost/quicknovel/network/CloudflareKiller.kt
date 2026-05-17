package com.lagradost.quicknovel.network

import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.AnyThread
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.nicehttp.cookies
import com.lagradost.nicehttp.getHeaders
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.mvvm.debugWarning
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.net.URI

@AnyThread
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val mutex = Mutex()
        
        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    private val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val response = chain.proceed(request)
        
        Log.d(TAG, "Intercepted ${request.url} - Code: ${response.code}")

        // Check if we are being blocked by Cloudflare (403/503) or generic challenge (200)
        val isChallengeCode = response.code == 403 || response.code == 503 || response.code == 429
        val bodySnippet = try {
            response.peekBody(1024 * 10).string()
        } catch (e: Exception) {
            ""
        }
        
        // Refined markers: for 200 OK, we want to be more certain it's a challenge, not just a site mention
        val hasChallengeMarkers = response.header("cf-mitigated") == "challenge" ||
                                 bodySnippet.contains("cf-challenge", ignoreCase = true) ||
                                 bodySnippet.contains("Turnstile", ignoreCase = true) ||
                                 bodySnippet.contains("ctp-button", ignoreCase = true) ||
                                 (bodySnippet.contains("cloudflare", ignoreCase = true) && 
                                  bodySnippet.contains("challenges.cloudflare.com", ignoreCase = true))

        if (isChallengeCode || (response.code == 200 && hasChallengeMarkers && bodySnippet.contains("javascript", ignoreCase = true))) {
            if (hasChallengeMarkers || isChallengeCode) {
                val ctx = com.lagradost.quicknovel.BaseApplication.context
                val settingsManager = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx ?: return@runBlocking response)
                val autoSolveKey = ctx.getString(com.lagradost.quicknovel.R.string.cloudflare_auto_solve_key)
                val autoSolve = settingsManager.getBoolean(autoSolveKey, false)

                Log.d(TAG, "Cloudflare challenge detected at ${request.url} (Code: ${response.code}, AutoSolve: $autoSolve)")
                
                return@runBlocking mutex.withLock {
                    // Try to solve or use fresh cookies from CookieManager (this is optimization/fast part)
                    if (trySolveWithSavedCookies(request)) {
                        response.close()
                        Log.d(TAG, "Successfully solved using existing cookies for ${request.url.host}")
                        proceed(request, savedCookies[request.url.host]!!)
                    } else if (autoSolve) {
                        response.close()
                        Log.d(TAG, "Triggering WebViewResolver flow for ${request.url}")
                        bypassCloudflare(request) ?: chain.proceed(request)
                    } else {
                        Log.d(TAG, "AutoSolve disabled, proceeding with original response")
                        response
                    }
                }
            }
        }

        return@runBlocking response
    }

    private fun getWebViewCookie(url: String): String? {
        return CookieManager.getInstance()?.getCookie(url)
    }

    private fun trySolveWithSavedCookies(request: Request): Boolean {
        return getWebViewCookie(request.url.toString())?.let { cookie ->
            if (cookie.contains("cf_clearance")) {
                savedCookies[request.url.host] = parseCookieMap(cookie)
                true
            } else false
        } ?: false
    }

    private suspend fun proceed(request: Request, cookies: Map<String, String>): Response {
        val userAgent = WebViewResolver.getWebViewUserAgent()
        val headers = getHeaders(
            request.headers.toMap() + if (userAgent != null) mapOf("user-agent" to userAgent) else emptyMap(),
            null, 
            cookies + request.cookies
        )
        
        return app.baseClient.newCall(
            request.newBuilder()
                .headers(headers)
                .build()
        ).await()
    }

    private suspend fun bypassCloudflare(request: Request): Response? {
        val url = request.url.toString()

        // 1. Attempt background resolution (hidden webview)
        WebViewResolver(
            Regex(".^"),
            userAgent = null,
            useOkhttp = false,
            additionalUrls = listOf(Regex("."))
        ).resolveUsingWebView(url) {
            trySolveWithSavedCookies(request)
        }

        // 2. Check if background solve worked
        if (trySolveWithSavedCookies(request)) {
            val cookies = savedCookies[request.url.host] ?: return null
            return proceed(request, cookies)
        }

        // 3. Fallback: Manual solve (Visible WebView Dialog)
        Log.d(TAG, "Background solve failed, showing manual dialog for $url")
        WebViewResolver(
            Regex(".^"),
            userAgent = null,
            useOkhttp = false,
            additionalUrls = listOf(Regex("."))
        ).resolveUsingWebView(url, showDialog = true) {
            trySolveWithSavedCookies(request)
        }

        // 4. Check if manual solve worked
        if (trySolveWithSavedCookies(request)) {
            val cookies = savedCookies[request.url.host] ?: return null
            return proceed(request, cookies)
        }

        return null
    }
}