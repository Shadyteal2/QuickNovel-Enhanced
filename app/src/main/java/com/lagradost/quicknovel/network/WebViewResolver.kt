package com.lagradost.quicknovel.network

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.*
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.Coroutines.mainWork
import com.lagradost.nicehttp.requestCreator
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.USER_AGENT
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URI

/**
 * When used as Interceptor additionalUrls cannot be returned, use WebViewResolver(...).resolveUsingWebView(...)
 * @param interceptUrl will stop the WebView when reaching this url.
 * @param additionalUrls this will make resolveUsingWebView also return all other requests matching the list of Regex.
 * @param userAgent if null then will use the default user agent
 * @param useOkhttp will try to use the okhttp client as much as possible, but this might cause some requests to fail. Disable for cloudflare.
 * */
class WebViewResolver(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex> = emptyList(),
    val userAgent: String? = USER_AGENT,
    val useOkhttp: Boolean = true
) :
    Interceptor {

    companion object {
        var webViewUserAgent: String? = null

        @JvmName("getWebViewUserAgent1")
        fun getWebViewUserAgent(): String? {
            return webViewUserAgent ?: context?.let { ctx ->
                runBlocking {
                    mainWork {
                        WebView(ctx).settings.userAgentString.also { userAgent ->
                            webViewUserAgent = userAgent
                        }
                    }
                }
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return runBlocking {
            val fixedRequest = resolveUsingWebView(request).first
            return@runBlocking chain.proceed(fixedRequest ?: request)
        }
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        method: String = "GET",
        showDialog: Boolean = false,
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> {
        return resolveUsingWebView(
            requestCreator(method, url, referer = referer), showDialog, requestCallBack
        )
    }

    /**
     * @param requestCallBack asynchronously return matched requests by either interceptUrl or additionalUrls. If true, destroy WebView.
     * @return the final request (by interceptUrl) and all the collected urls (by additionalUrls).
     * */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveUsingWebView(
        request: Request,
        showDialog: Boolean = false,
        requestCallBack: (Request) -> Boolean = { false }
    ): Pair<Request?, List<Request>> {
        val url = request.url.toString()
        val headers = request.headers
        println("Initial web-view request: $url (Dialog: $showDialog)")
        var webView: WebView? = null
        var dialog: androidx.appcompat.app.AlertDialog? = null
        // Extra assurance it exits as it should.
        var shouldExit = false

        fun destroyWebView() {
            main {
                dialog?.dismiss()
                webView?.stopLoading()
                webView?.destroy()
                webView = null
                shouldExit = true
                println("Destroyed webview")
            }
        }

        var fixedRequest: Request? = null
        val extraRequestList = mutableListOf<Request>()

        main {
            // Useful for debugging
            WebView.setWebContentsDebuggingEnabled(true)
            try {
                // IMPORTANT: For AlertDialog we MUST use an Activity context.
                // We try to get the current activity from CommonActivity.
                val activity = com.lagradost.quicknovel.CommonActivity.activity
                val ctx = activity ?: context ?: return@main
                
                println("Creating WebView with context: $ctx (isActivity: ${ctx is android.app.Activity})")
                
                webView = WebView(ctx).apply {
                    // Bare minimum to bypass captcha
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    
                    // Force matching User-Agent
                    if (userAgent != null) {
                        settings.userAgentString = userAgent
                    }
                    webViewUserAgent = settings.userAgentString
                }

                if (showDialog) {
                    if (activity == null) {
                        println("Cannot show dialog: No Activity context available!")
                        return@main
                    }
                    
                    val builder = androidx.appcompat.app.AlertDialog.Builder(activity)
                        .setView(webView)
                        .setTitle("Cloudflare Verification")
                        .setNegativeButton("Cancel") { _, _ -> destroyWebView() }
                        .setOnCancelListener { destroyWebView() }
                    
                    dialog = builder.create()
                    dialog?.show()
                    
                    // Resize to be useful but not full screen
                    dialog?.window?.setLayout(
                        (activity.resources.displayMetrics.widthPixels * 0.9).toInt(),
                        (activity.resources.displayMetrics.heightPixels * 0.8).toInt()
                    )
                }

                webView?.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = runBlocking {
                        val webViewUrl = request.url.toString()
                        println("Loading WebView URL: $webViewUrl")

                        if (interceptUrl.containsMatchIn(webViewUrl)) {
                            fixedRequest = request.toRequest().also {
                                requestCallBack(it)
                            }
                            println("Web-view request finished: $webViewUrl")
                            destroyWebView()
                            return@runBlocking null
                        }

                        if (additionalUrls.any { it.containsMatchIn(webViewUrl) }) {
                            extraRequestList.add(request.toRequest().also {
                                if (requestCallBack(it)) destroyWebView()
                            })
                        }

                        // Suppress image requests as we don't display them anywhere
                        // Less data, low chance of causing issues.
                        // blockNetworkImage also does this job but i will keep it for the future.
                        val blacklistedFiles = listOf(
                            ".jpg",
                            ".png",
                            ".webp",
                            ".mpg",
                            ".mpeg",
                            ".jpeg",
                            ".webm",
                            ".mp4",
                            ".mp3",
                            ".gifv",
                            ".flv",
                            ".asf",
                            ".mov",
                            ".mng",
                            ".mkv",
                            ".ogg",
                            ".avi",
                            ".wav",
                            ".woff2",
                            ".woff",
                            ".ttf",
                            ".css",
                            ".vtt",
                            ".srt",
                            ".ts",
                            ".gif",
                            // Warning, this might fuck some future sites, but it's used to make Sflix work.
                            "wss://"
                        )

                        /** NOTE!  request.requestHeaders is not perfect!
                         *  They don't contain all the headers the browser actually gives.
                         *  Overriding with okhttp might fuck up otherwise working requests,
                         *  e.g the recaptcha request.
                         * **/

                        return@runBlocking try {
                            when {
                                blacklistedFiles.any { URI(webViewUrl).path.contains(it) } || webViewUrl.endsWith(
                                    "/favicon.ico"
                                ) -> WebResourceResponse(
                                    "image/png",
                                    null,
                                    null
                                )
                                webViewUrl.contains("recaptcha") || webViewUrl.contains("/cdn-cgi/") -> super.shouldInterceptRequest(
                                    view,
                                    request
                                )

                                useOkhttp && request.method == "GET" -> app.get(
                                    webViewUrl,
                                    headers = request.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()

                                useOkhttp && request.method == "POST" -> app.post(
                                    webViewUrl,
                                    headers = request.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()

                                else -> super.shouldInterceptRequest(
                                    view,
                                    request
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed() // Ignore ssl issues
                    }
                }
                webView?.loadUrl(url, headers.toMap())
            } catch (e: Exception) {
                logError(e)
            }
        }

        var loop = 0
        // Timeouts after this amount, 60s
        val totalTime = 60000L

        val delayTime = 500L

        // A bit sloppy, but couldn't find a better way
        while (loop < totalTime / delayTime && !shouldExit) {
            if (fixedRequest != null) return fixedRequest to extraRequestList
            
            // Periodically check if solved via the callback (e.g. cookie check)
            if (requestCallBack(request)) {
                println("Web-view solved via polling check!")
                destroyWebView()
                break
            }
            
            delay(delayTime)
            loop += 1
        }

        println("Web-view timeout after ${totalTime / 1000}s")
        destroyWebView()
        return fixedRequest to extraRequestList
    }

}

fun WebResourceRequest.toRequest(): Request {
    val webViewUrl = this.url.toString()

    return requestCreator(
        this.method,
        webViewUrl,
        this.requestHeaders,
    )
}

fun Response.toWebResourceResponse(): WebResourceResponse {
    val contentTypeValue = this.header("Content-Type")
    // 1. contentType. 2. charset
    val typeRegex = Regex("""(.*);(?:.*charset=(.*)(?:|;)|)""")
    return if (contentTypeValue != null) {
        val found = typeRegex.find(contentTypeValue)
        val contentType = found?.groupValues?.getOrNull(1)?.ifBlank { null } ?: contentTypeValue
        val charset = found?.groupValues?.getOrNull(2)?.ifBlank { null }
        WebResourceResponse(contentType, charset, this.body.byteStream())
    } else {
        WebResourceResponse("application/octet-stream", null, this.body.byteStream())
    }
}
