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
import com.lagradost.quicknovel.BuildConfig
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.R
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
                try {
                    android.webkit.CookieManager.getInstance().flush()
                } catch (t: Throwable) {
                    logError(t)
                }
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
        var btnBack: android.widget.Button? = null
        var btnForward: android.widget.Button? = null
        var txtUrl: android.widget.TextView? = null

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            // Useful for debugging
            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
            try {
                // IMPORTANT: For AlertDialog we MUST use an Activity context.
                // We try to get the current activity from CommonActivity.
                val activity = com.lagradost.quicknovel.CommonActivity.activity
                val ctx = activity ?: context ?: return@withContext
                
                println("Creating WebView with context: $ctx (isActivity: ${ctx is android.app.Activity})")
                
                webView = WebView(ctx).apply {
                    // Bare minimum to bypass captcha
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    
                    // Enable third-party cookies for OAuth/social redirects
                    try {
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    } catch (t: Throwable) {
                        logError(t)
                    }
                    
                    // Enable keyboard input and focusability
                    isFocusable = true
                    isFocusableInTouchMode = true
                    
                    // Force matching User-Agent (ensure we don't pass null to settings.userAgentString)
                    val ua = userAgent ?: USER_AGENT
                    settings.userAgentString = ua
                    webViewUserAgent = ua
                    
                    // Ensure touch focus behaves correctly
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN,
                            android.view.MotionEvent.ACTION_UP -> {
                                if (!v.hasFocus()) {
                                    v.requestFocus()
                                }
                            }
                        }
                        false
                    }
                }

                val stealthScript = """
                    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                    Object.defineProperty(navigator, 'userAgentData', { get: () => undefined });
                    Object.defineProperty(navigator, 'plugins', {
                        get: () => {
                            const arr = [
                                { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
                                { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: '' },
                                { name: 'Native Client', filename: 'internal-nacl-plugin', description: '' }
                            ];
                            arr.__proto__ = PluginArray.prototype;
                            return arr;
                        }
                    });
                    window.chrome = {
                        runtime: {
                            connect: () => {},
                            sendMessage: () => {},
                            id: undefined
                        },
                        loadTimes: () => ({
                            requestTime: Date.now() / 1000,
                            startLoadTime: Date.now() / 1000,
                            commitLoadTime: Date.now() / 1000,
                            finishDocumentLoadTime: 0,
                            finishLoadTime: 0,
                            firstPaintTime: 0,
                            firstPaintAfterLoadTime: 0,
                            navigationType: 'Other',
                            wasFetchedViaSpdy: false,
                            wasNpnNegotiated: false,
                            npnNegotiatedProtocol: 'http/1.1',
                            wasAlternateProtocolAvailable: false,
                            connectionInfo: 'http/1.1'
                        }),
                        csi: () => ({
                            startE: Date.now(),
                            onloadT: Date.now(),
                            pageT: 0,
                            tran: 15
                        })
                    };
                    if (window.navigator.permissions && window.navigator.permissions.query) {
                        const originalQuery = window.navigator.permissions.query;
                        window.navigator.permissions.query = (parameters) =>
                            parameters.name === 'notifications'
                                ? Promise.resolve({ state: typeof Notification !== 'undefined' ? Notification.permission : 'default' })
                                : originalQuery(parameters);
                    }
                """.trimIndent()

                val hasDocStartScriptSupport = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                if (hasDocStartScriptSupport) {
                    WebViewCompat.addDocumentStartJavaScript(webView!!, stealthScript, setOf("*"))
                }

                if (showDialog) {
                    if (activity == null) {
                        println("Cannot show dialog: No Activity context available!")
                        return@withContext
                    }
                    
                    val rootLayout = android.widget.LinearLayout(activity).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // Top Bar for navigation and address
                    val topBar = android.widget.LinearLayout(activity).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(8, 4, 8, 4)
                        }
                    }

                    btnBack = android.widget.Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
                        text = "◀"
                        textSize = 14f
                        minimumWidth = 0
                        minWidth = 0
                        setPadding(16, 0, 16, 0)
                        isEnabled = false
                        setOnClickListener {
                            webView?.goBack()
                        }
                    }

                    btnForward = android.widget.Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
                        text = "▶"
                        textSize = 14f
                        minimumWidth = 0
                        minWidth = 0
                        setPadding(16, 0, 16, 0)
                        isEnabled = false
                        setOnClickListener {
                            webView?.goForward()
                        }
                    }

                    val btnRefresh = android.widget.Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
                        text = "⟳"
                        textSize = 18f
                        minimumWidth = 0
                        minWidth = 0
                        setPadding(16, 0, 16, 0)
                        setOnClickListener {
                            webView?.reload()
                        }
                    }

                    txtUrl = android.widget.TextView(activity).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            0,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            setMargins(8, 0, 8, 0)
                        }
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = android.view.Gravity.CENTER
                        textSize = 12f
                        text = url
                        setTextColor(activity.colorFromAttribute(R.attr.grayTextColor))
                    }

                    topBar.addView(btnBack)
                    topBar.addView(btnForward)
                    topBar.addView(txtUrl)
                    topBar.addView(btnRefresh)
                    rootLayout.addView(topBar)

                    // Add WebView with a fixed height to prevent collapsing inside wrap_content dialog parent
                    val webViewHeight = (activity.resources.displayMetrics.heightPixels * 0.75).toInt()
                    webView?.layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        webViewHeight
                    )
                    (webView?.parent as? android.view.ViewGroup)?.removeView(webView)
                    rootLayout.addView(webView)

                    // Bottom Bar for cancel / done
                    val buttonBar = android.widget.LinearLayout(activity).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.END
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(16, 8, 16, 8)
                        }
                    }

                    val btnCancel = android.widget.Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
                        text = "Cancel"
                        setOnClickListener {
                            destroyWebView()
                        }
                    }

                    val btnDone = android.widget.Button(activity).apply {
                        text = "I'm Done"
                        setOnClickListener {
                            shouldExit = true
                            destroyWebView()
                        }
                    }

                    buttonBar.addView(btnCancel)
                    val spacer = android.view.View(activity).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(16, 1)
                    }
                    buttonBar.addView(spacer)
                    buttonBar.addView(btnDone)
                    rootLayout.addView(buttonBar)

                    val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity, com.lagradost.quicknovel.R.style.AlertDialogCustom)
                        .setView(rootLayout)
                        .setOnCancelListener { destroyWebView() }
                    
                    dialog = builder.create()
                    dialog?.show()
                    
                    // Clear NOT_FOCUSABLE to allow keyboard/input focus on the WebView input elements AFTER showing dialog
                    dialog?.window?.clearFlags(
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    )
                    dialog?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                    
                    // Force input focus on the WebView on the UI thread
                    webView?.requestFocus()
                    
                    // Set bigger layout size: 95% width, 92% height
                    dialog?.window?.setLayout(
                        (activity.resources.displayMetrics.widthPixels * 0.95).toInt(),
                        (activity.resources.displayMetrics.heightPixels * 0.92).toInt()
                    )
                }

                webView?.webViewClient = object : WebViewClient() {
                    private fun updateNavigationState(view: WebView?, currentUrl: String?) {
                        activity?.runOnUiThread {
                            currentUrl?.let { txtUrl?.text = it }
                            btnBack?.isEnabled = view?.canGoBack() == true
                            btnForward?.isEnabled = view?.canGoForward() == true
                        }
                    }

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        super.onPageStarted(view, url, favicon)
                        if (!hasDocStartScriptSupport) {
                            view?.evaluateJavascript(stealthScript, null)
                        }
                        updateNavigationState(view, url)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        updateNavigationState(view, url)
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        updateNavigationState(view, url)
                    }

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
                            // ".css", removed to allow Cloudflare's own layout/dark mode to render
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

                                (webViewUrl.contains("accounts.google.com") || webViewUrl.contains("github.com")) && request.method == "GET" -> {
                                    val reqHeaders = request.requestHeaders.toMutableMap()
                                    reqHeaders.remove("X-Requested-With")
                                    reqHeaders["User-Agent"] = userAgent ?: USER_AGENT
                                    app.get(
                                        webViewUrl,
                                        headers = reqHeaders
                                    ).okhttpResponse.toWebResourceResponse()
                                }

                                (webViewUrl.contains("accounts.google.com") || webViewUrl.contains("github.com")) && request.method == "POST" -> {
                                    val reqHeaders = request.requestHeaders.toMutableMap()
                                    reqHeaders.remove("X-Requested-With")
                                    reqHeaders["User-Agent"] = userAgent ?: USER_AGENT
                                    app.post(
                                        webViewUrl,
                                        headers = reqHeaders
                                    ).okhttpResponse.toWebResourceResponse()
                                }

                                useOkhttp && request.method == "GET" -> {
                                    val reqHeaders = request.requestHeaders.toMutableMap()
                                    reqHeaders.remove("X-Requested-With")
                                    app.get(
                                        webViewUrl,
                                        headers = reqHeaders
                                    ).okhttpResponse.toWebResourceResponse()
                                }

                                useOkhttp && request.method == "POST" -> {
                                    val reqHeaders = request.requestHeaders.toMutableMap()
                                    reqHeaders.remove("X-Requested-With")
                                    app.post(
                                        webViewUrl,
                                        headers = reqHeaders
                                    ).okhttpResponse.toWebResourceResponse()
                                }

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
                val loadHeaders = headers.toMap().toMutableMap()
                // Override/remove X-Requested-With header to bypass "unsafe browser" blocks on OAuth screens
                loadHeaders["X-Requested-With"] = ""
                webView?.loadUrl(url, loadHeaders)
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
