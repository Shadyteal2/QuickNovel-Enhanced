package com.lagradost.quicknovel.providers

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.USER_AGENT
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.AppUtils.parseJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object WebViewParserEngine {
    private val mutex = Mutex()
    private var webView: WebView? = null
    private var activeDialog: androidx.appcompat.app.AlertDialog? = null

    private val STEALTH_SCRIPT = """
        Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
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

    
    // In-memory script cache to avoid repeated disk reads
    private var cachedCoreScripts: String? = null
    
    private suspend fun getCoreScripts(context: Context): String {
        cachedCoreScripts?.let { return it }
        return withContext(Dispatchers.IO) {
            val bridge = context.assets.open("webtoepub_bridge.js").bufferedReader().use { it.readText() }
            val util = context.assets.open("Util.js").bufferedReader().use { it.readText() }
            val imgur = context.assets.open("Imgur.js").bufferedReader().use { it.readText() }
            val imageCollector = context.assets.open("ImageCollector.js").bufferedReader().use { it.readText() }
            val epubMetaInfo = context.assets.open("EpubMetaInfo.js").bufferedReader().use { it.readText() }
            val parser = context.assets.open("Parser.js").bufferedReader().use { it.readText() }
            val factory = context.assets.open("ParserFactory.js").bufferedReader().use { it.readText() }
            val uiText = context.assets.open("UIText.js").bufferedReader().use { it.readText() }
            
            val scripts = "$bridge\n$util\n$imgur\n$imageCollector\n$epubMetaInfo\n$parser\n$factory\n$uiText"
            cachedCoreScripts = scripts
            scripts
        }
    }
    
    private suspend fun getParserScript(context: Context, parserFile: String): String {
        return withContext(Dispatchers.IO) {
            context.assets.open("parsers/$parserFile").bufferedReader().use { it.readText() }
        }
    }

    private fun assetExists(context: Context, path: String): Boolean {
        return try {
            context.assets.open(path).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getParserScriptWithDependencies(context: Context, parserFile: String): String {
        val script = getParserScript(context, parserFile)
        
        // Match: class ClassName extends ParentName
        val match = Regex("""class\s+\w+\s+extends\s+(\w+)""").find(script)
        if (match != null) {
            val parentClass = match.groupValues[1]
            if (parentClass != "Parser" && parentClass != "Object") {
                val candidate1 = "$parentClass.js"
                val candidate2 = "${parentClass}Parser.js"
                
                val parentFile = when {
                    assetExists(context, "parsers/$candidate1") -> candidate1
                    assetExists(context, "parsers/$candidate2") -> candidate2
                    else -> null
                }
                
                if (parentFile != null && parentFile != parserFile) {
                    println("WebViewParserEngine: Found parent dependency $parentFile for $parserFile. Prepending.")
                    val parentScript = getParserScriptWithDependencies(context, parentFile)
                    return "$parentScript\n$script"
                }
            }
        }
        return script
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getOrCreateWebView(context: Context): WebView {
        webView?.let { return it }
        val new = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = USER_AGENT
            // Do NOT block images as it triggers headless/bot detection on Cloudflare Turnstile
            settings.blockNetworkImage = false 
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(new, STEALTH_SCRIPT, setOf("*"))
        }
        webView = new
        return new
    }

    private fun showSolverDialog(view: WebView, context: Context) {
        main {
            if (activeDialog != null) return@main
            val activity = com.lagradost.quicknovel.CommonActivity.activity
            if (activity == null) {
                println("WebViewParserEngine: No Activity context to display verification challenge!")
                return@main
            }
            
            // Remove view from prior parent if attached
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            
            val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity, com.lagradost.quicknovel.R.style.AlertDialogCustom)
                .setView(view)
                .setTitle("Verification Required")
                .setMessage("Please complete the Cloudflare challenge below to load the novel.")
                .setNegativeButton("Cancel") { _, _ -> dismissDialog() }
                .setOnCancelListener { dismissDialog() }
                
            activeDialog = builder.create()
            activeDialog?.show()
            
            // Resize dialog window
            activeDialog?.window?.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.9).toInt(),
                (activity.resources.displayMetrics.heightPixels * 0.85).toInt()
            )
        }
    }

    private fun dismissDialog() {
        main {
            activeDialog?.let { dialog ->
                webView?.let { view ->
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                }
                dialog.dismiss()
            }
            activeDialog = null
        }
    }

    private suspend fun runEngine(
        context: Context,
        url: String,
        action: (WebView, onResult: (String) -> Unit, onError: (String) -> Unit) -> Unit
    ): String = suspendCancellableCoroutine { cont ->
        main {
            try {
                val view = getOrCreateWebView(context)
                
                // Expose Javascript Interface
                val bridge = object {
                    @JavascriptInterface
                    fun onMetadataParsed(json: String) {
                        dismissDialog()
                        if (cont.isActive) cont.resume(json)
                    }
                    
                    @JavascriptInterface
                    fun onHtmlParsed(html: String) {
                        dismissDialog()
                        if (cont.isActive) cont.resume(html)
                    }
                    
                    @JavascriptInterface
                    fun onError(err: String) {
                        dismissDialog()
                        if (cont.isActive) cont.resumeWithException(Exception(err))
                    }
                }
                
                view.addJavascriptInterface(bridge, "AndroidBridge")
                
                view.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        v: WebView?,
                        pageUrl: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        super.onPageStarted(v, pageUrl, favicon)
                        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                            v?.evaluateJavascript(STEALTH_SCRIPT, null)
                        }
                    }

                    override fun onPageFinished(v: WebView?, pageUrl: String?) {
                        super.onPageFinished(v, pageUrl)
                        
                        val isSolved = pageUrl?.let { u ->
                            val cookies = android.webkit.CookieManager.getInstance().getCookie(u) ?: ""
                            cookies.contains("cf_clearance")
                        } ?: false
                        
                        // Check if loaded page is a Cloudflare Turnstile or similar challenge
                        v?.evaluateJavascript(
                            "(document.getElementById('challenge-form') != null || document.querySelector('.cf-turnstile') != null || document.title.includes('Cloudflare') || document.title.includes('Just a moment'))"
                        ) { cfResult ->
                            if (cfResult == "true") {
                                if (isSolved) {
                                    // Solved but transitioning. Dismiss dialog, wait for next page.
                                    dismissDialog()
                                } else {
                                    // Not solved yet, show challenge dialog.
                                    showSolverDialog(v, context)
                                }
                            } else {
                                // Challenge solved / not present. Run injection.
                                dismissDialog()
                                
                                action(v, { json ->
                                    v.removeJavascriptInterface("AndroidBridge")
                                    if (cont.isActive) cont.resume(json)
                                }, { err ->
                                    v.removeJavascriptInterface("AndroidBridge")
                                    if (cont.isActive) cont.resumeWithException(Exception(err))
                                })
                            }
                        }
                    }
                }
                
                // Load URL
                view.loadUrl(url)
                
                cont.invokeOnCancellation {
                    main {
                        dismissDialog()
                        view.stopLoading()
                        view.removeJavascriptInterface("AndroidBridge")
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }

    data class ParsedMetadata(
        val title: String,
        val author: String?,
        val cover: String?,
        val chapters: List<ChapterData>
    )

    suspend fun parseMetadata(context: Context, url: String, parserFile: String): ParsedMetadata = mutex.withLock {
        withTimeout(60000L) {
            val coreScripts = getCoreScripts(context)
            val parserScript = getParserScriptWithDependencies(context, parserFile)
            
            val resultJson = runEngine(context, url) { view, onResult, onError ->
                val orchestration = """
                    (async () => {
                        try {
                            let parser = parserFactory.fetchByUrl(window.location.href);
                            if (!parser) {
                                AndroidBridge.onError("No parser found for URL: " + window.location.href);
                                return;
                            }
                            let dom = document;
                            await parser.loadEpubMetaInfo(dom);
                            let title = parser.extractTitle(dom);
                            let author = parser.extractAuthor(dom);
                            let cover = parser.findCoverImageUrl(dom);
                            let chapters = await parser.getChapterUrls(dom);
                            
                            // Map WebToEpub chapter structure to flat name & url map
                            let chapterList = chapters.map(c => ({
                                name: c.title || c.name || "[No Title]",
                                url: c.sourceUrl || c.url
                            }));
                            
                            AndroidBridge.onMetadataParsed(JSON.stringify({
                                title: title,
                                author: author,
                                cover: cover,
                                chapters: chapterList
                            }));
                        } catch (err) {
                            AndroidBridge.onError(err.toString());
                        }
                    })();
                """.trimIndent()
                
                val fullScript = "{\n$coreScripts\n$parserScript\n$orchestration\n}"
                view.evaluateJavascript(fullScript, null)
            }
            
            // Deserialize JSON on default Dispatchers
            withContext(Dispatchers.Default) {
                val map = parseJson<Map<String, Any>>(resultJson)
                val title = map["title"] as? String ?: "Unknown Title"
                val author = map["author"] as? String ?: "Unknown Author"
                val cover = (map["cover"] as? String)?.takeIf { it.isNotBlank() }
                @Suppress("UNCHECKED_CAST")
                val chaptersRaw = map["chapters"] as? List<Map<String, String>> ?: emptyList()
                
                val chapters = chaptersRaw.map { c ->
                    ChapterData(
                        name = c["name"] ?: "[No Title]",
                        url = c["url"] ?: ""
                    )
                }
                ParsedMetadata(title, author, cover, chapters)
            }
        }
    }

    suspend fun parseHtml(context: Context, url: String, parserFile: String): String = mutex.withLock {
        withTimeout(60000L) {
            val coreScripts = getCoreScripts(context)
            val parserScript = getParserScriptWithDependencies(context, parserFile)
            
            runEngine(context, url) { view, onResult, onError ->
                val orchestration = """
                    (async () => {
                        try {
                            let parser = parserFactory.fetchByUrl(window.location.href);
                            if (!parser) {
                                AndroidBridge.onHtmlParsed(document.body.innerHTML);
                                return;
                            }
                            
                            // Initialize translation glossaries/terms to avoid undefined crashes in parsers like Wtrlab
                            parser.termsstory = parser.termsstory || [];
                            parser.termsuser = parser.termsuser || [];
                            
                            let webPageDom = await parser.fetchChapter(window.location.href);
                            
                            let webPage = {
                                sourceUrl: window.location.href,
                                rawDom: webPageDom,
                                isIncludeable: true,
                                title: "[placeholder]",
                                nextPrevChapters: new Set()
                            };
                            parser.state.chapterListUrl = window.location.href;
                            parser.onUserPreferencesUpdate({
                                removeNextAndPreviousChapterHyperlinks: { value: true },
                                removeAuthorNotes: { value: false },
                                addInformationPage: { value: false }
                            });
                            
                            let content = parser.findContent(webPageDom);
                            if (!content) {
                                AndroidBridge.onError("Content element not found by parser");
                                return;
                            }
                            let cleanContent = parser.convertRawDomToContent(webPage);
                            AndroidBridge.onHtmlParsed(cleanContent.innerHTML);
                        } catch (err) {
                            AndroidBridge.onError(err.toString());
                        }
                    })();
                """.trimIndent()
                
                val fullScript = "{\n$coreScripts\n$parserScript\n$orchestration\n}"
                view.evaluateJavascript(fullScript, null)
            }
        }
    }
}
