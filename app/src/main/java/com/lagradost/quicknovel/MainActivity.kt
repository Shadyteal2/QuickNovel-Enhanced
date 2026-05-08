package com.lagradost.quicknovel

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.doOnPreDraw
import android.view.ViewGroup
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import android.view.ViewAnimationUtils
import kotlin.math.hypot
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.lagradost.quicknovel.APIRepository.Companion.providersActive
import com.lagradost.quicknovel.BookDownloader2.openQuickStream
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.BookDownloader2Helper.checkWrite
import com.lagradost.quicknovel.BookDownloader2Helper.createQuickStream
import com.lagradost.quicknovel.BookDownloader2Helper.requestRW
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.CommonActivity.updateLocale
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.getKeys
import com.lagradost.quicknovel.NotificationHelper.requestNotifications
import com.lagradost.quicknovel.databinding.ActivityMainBinding
import com.lagradost.quicknovel.databinding.BottomPreviewBinding
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.mvvm.observeNullable
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.ui.result.ResultFragment
import com.lagradost.quicknovel.ui.result.ResultViewModel
import com.lagradost.quicknovel.ui.search.SearchFragment
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.Apis.Companion.getApiSettings
import com.lagradost.quicknovel.util.Apis.Companion.printProviders
import com.lagradost.quicknovel.util.BackupUtils.setUpBackup
import com.lagradost.quicknovel.util.Coroutines
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.SettingsHelper.getLibraryNavStyle
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import com.lagradost.quicknovel.util.bindBackgroundEffects
import com.lagradost.quicknovel.util.getBackgroundEffectState
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.dismissSafe
import com.lagradost.quicknovel.util.UIHelper.getResourceColor
import com.lagradost.quicknovel.util.UIHelper.html
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.util.DrawerHelper
import com.lagradost.safefile.SafeFile
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.lagradost.quicknovel.ui.history.HistoryFragment
import com.lagradost.quicknovel.ui.settings.SettingsFragment
import com.lagradost.quicknovel.util.getSafeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.SharedPreferences
import okhttp3.Protocol
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit
import java.lang.ref.WeakReference
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.reflect.KClass
import android.net.Uri
import coil3.asDrawable
import com.lagradost.quicknovel.ui.TabNavigator
import com.lagradost.quicknovel.util.PluginManager
import com.lagradost.quicknovel.util.PluginItem
import com.lagradost.quicknovel.API_VERSION
import com.lagradost.quicknovel.MainAPI
import dalvik.system.DexClassLoader
import dalvik.system.DexFile
import java.io.File

import android.view.HapticFeedbackConstants
import android.view.animation.OvershootInterpolator
import android.animation.ValueAnimator
import android.widget.LinearLayout

class MainActivity : AppCompatActivity(), TabNavigator {
    override fun switchToMainTab(index: Int) {
        binding?.mainViewpager?.setCurrentItem(index, true)
    }

    override fun moveToTab(index: Int) {
        switchToMainTab(index)
    }

    private var lastPillX = 0f
    private var lastPillWidth = 0
    private var hapticTickDone = false

    private val tabIds = listOf(
        R.id.navigation_download,
        R.id.navigation_search,
        R.id.navigation_foryou,
        R.id.navigation_history,
    )
    
    private var lastActiveTab: Int = 0

    private val providerApkPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            ioSafe {
                val pluginsDir = PluginManager.getPluginsDir(this@MainActivity)
                val tempApk = File(pluginsDir, "_import_staging.apk")
                try {
                    // ── 1. Verify signature ─────────────────────────────────────────
                    if (!PluginManager.verifyApkSignature(this@MainActivity, uri)) {
                        runOnUiThread { showToast(getString(R.string.import_provider_apk_bad_sig)) }
                        return@ioSafe
                    }

                    // ── 2. Stage APK to a temp file ──────────────────────────────────
                    // We inspect the APK before committing it to its final location so
                    // we can derive the correct on-disk name from the provider's own identity.
                    tempApk.delete() // clean up any leftover from a previous crashed import
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempApk.outputStream().use { out -> input.copyTo(out) }
                    }
                    if (tempApk.canWrite()) tempApk.setReadOnly()

                    // ── 3. Scan DEX and instantiate each MainAPI class ────────────
                    // Instantiating lets us read `instance.name` — the real provider name.
                    // This is the source-of-truth we use to build a STABLE bundleId
                    // (e.g. "Ranobes") that never changes across APK renames.
                    data class FoundProvider(val className: String, val providerName: String)
                    val foundProviders = mutableListOf<FoundProvider>()
                    try {
                        @Suppress("DEPRECATION")
                        val dex = DexFile(tempApk.absolutePath)
                        val loader = DexClassLoader(
                            tempApk.absolutePath,
                            codeCacheDir.absolutePath,
                            null,
                            classLoader
                        )
                        val entries = dex.entries()
                        while (entries.hasMoreElements()) {
                            val className = entries.nextElement()
                            if (className.startsWith("android.") ||
                                className.startsWith("kotlin.") ||
                                className.startsWith("kotlinx.") ||
                                className.startsWith("java.")) continue
                            try {
                                val clazz = loader.loadClass(className)
                                if (MainAPI::class.java.isAssignableFrom(clazz) &&
                                    !java.lang.reflect.Modifier.isAbstract(clazz.modifiers) &&
                                    !clazz.isInterface) {
                                    val instance = clazz.getDeclaredConstructor().newInstance() as MainAPI
                                    foundProviders.add(FoundProvider(className, instance.name))
                                }
                            } catch (_: Throwable) {}
                        }
                        dex.close()
                    } catch (e: Exception) {
                        logError(e)
                    }

                    // ── 4. Abort if no providers were found ───────────────────────
                    if (foundProviders.isEmpty()) {
                        runOnUiThread { showToast(getString(R.string.import_provider_apk_none_found)) }
                        return@ioSafe
                    }

                    // ── 5. Derive a stable bundleId from the actual provider name(s) ──
                    // "Ranobes_Fix_v2.apk" and "Ranobes.apk" will both produce bundleId="Ranobes"
                    // if both contain a class whose name property returns "Ranobes".
                    val providerNames = foundProviders.map { it.providerName }
                    val bundleId = providerNames
                        .joinToString("_")
                        .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                        .take(128)

                    val destApk  = File(pluginsDir, "$bundleId.apk")
                    val destJson = File(pluginsDir, "$bundleId.json")
                    val mapper   = jacksonObjectMapper()

                    // ── 6. Legacy cleanup — remove stale bundles for these providers ──
                    // On existing installs, providers may be stored under an old filename-based
                    // id (e.g. "Ranobes_v1"). We scan every JSON file and remove any that
                    // cover the same provider names as the APK we are importing.
                    // This is the silent fix that saves users from having to clear app data.
                    pluginsDir.listFiles { _, n -> n.endsWith(".json") }?.forEach { jsonFile ->
                        // Never delete the destination file we're about to write
                        if (jsonFile.absolutePath == destJson.absolutePath) return@forEach
                        try {
                            val existingMeta = mapper.readValue(jsonFile.readText(), PluginItem::class.java)
                            // Match: if the stored pluginId or name equals any new provider name
                            // (accounting for underscores used in old filename-based IDs)
                            val isStale = providerNames.any { newName ->
                                existingMeta.name.equals(newName, ignoreCase = true) ||
                                existingMeta.pluginId.equals(newName, ignoreCase = true) ||
                                existingMeta.pluginId.replace("_", " ").equals(newName, ignoreCase = true)
                            }
                            if (isStale) {
                                val staleApk = File(pluginsDir, "${existingMeta.pluginId}.apk")
                                val staleDex = File(pluginsDir, "${existingMeta.pluginId}.dex")
                                PluginManager.removeCachesForPath(staleApk.absolutePath)
                                staleApk.delete()
                                staleDex.delete()
                                jsonFile.delete()
                                android.util.Log.i("PluginImport",
                                    "Removed stale bundle: ${existingMeta.pluginId} → replaced by $bundleId")
                            }
                        } catch (_: Exception) { /* corrupt json — leave it alone */ }
                    }

                    // ── 7. Notify if updating an already-installed same-id bundle ──
                    if (destJson.exists()) {
                        try {
                            val old = mapper.readValue(destJson.readText(), PluginItem::class.java)
                            runOnUiThread {
                                showToast(getString(R.string.import_provider_apk_duplicate_format, old.version))
                            }
                        } catch (_: Exception) {}
                    }

                    // ── 8. Move staged APK to its final name-stable destination ───
                    PluginManager.removeCachesForPath(destApk.absolutePath)
                    if (destApk.exists()) destApk.delete()
                    tempApk.renameTo(destApk)
                    if (destApk.canWrite()) destApk.setReadOnly()

                    // ── 9. Write companion JSON ───────────────────────────────────
                    val meta = PluginItem(
                        pluginId      = bundleId,
                        name          = bundleId,
                        version       = 1,
                        minApiVersion = API_VERSION,
                        mainClasses   = foundProviders.map { it.className },
                        url           = "local://$bundleId",
                        isManualImport = true
                    )
                    destJson.writeText(mapper.writeValueAsString(meta))

                    // ── 10. Hot-reload so providers appear immediately ─────────────
                    PluginManager.loadAllPlugins(this@MainActivity)
                    runOnUiThread {
                        showToast(getString(R.string.import_provider_apk_success_format, foundProviders.size))
                    }
                } catch (e: Exception) {
                    logError(e)
                    runOnUiThread { showToast("Import failed: ${e.message}") }
                } finally {
                    // Always clean up the staging file, even if we crashed mid-import
                    if (tempApk.exists()) tempApk.delete()
                }
            }
        }

    private fun showProviderImportWarning() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val skipWarning = settingsManager.getBoolean("SKIP_PROVIDER_IMPORT_WARNING", false)
        
        if (skipWarning) {
            providerApkPicker.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
            return
        }

        val builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder.setTitle("Import Provider")
        builder.setMessage("Join the NeoQN telegram/discord to get the latest providers apk, you can find the social links in settings and import it")
        
        val checkBoxView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_checkbox, null)
        val checkBox = checkBoxView.findViewById<android.widget.CheckBox>(R.id.dialog_checkbox)
        checkBox.text = "Do not show again"
        builder.setView(checkBoxView)

        builder.setPositiveButton("OK") { _, _ ->
            if (checkBox.isChecked) {
                settingsManager.edit().putBoolean("SKIP_PROVIDER_IMPORT_WARNING", true).apply()
            }
            providerApkPicker.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    private fun android.view.View.applySpringTouch() {
        val springInterpolator = android.view.animation.OvershootInterpolator(1.5f)
        this.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(150).setInterpolator(springInterpolator).start()
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(250).setInterpolator(springInterpolator).start()
                }
            }
            false
        }
    }

    companion object {
        @JvmStatic
        var navOptions: androidx.navigation.NavOptions = androidx.navigation.navOptions {
            launchSingleTop = true
            restoreState = true
        }

        private var _mainActivity: WeakReference<MainActivity>? = null
        private var mainActivity
            get() = _mainActivity?.get()
            private set(value) {
                _mainActivity = WeakReference(value)
            }

        @JvmStatic
        fun loadPreviewPage(searchResponse: SearchResponse) {
            mainActivity?.loadPopup(searchResponse.url, searchResponse.apiName)
        }

        @JvmStatic
        fun loadPreviewPage(card: DownloadFragment.DownloadDataLoaded) {
            mainActivity?.loadPopup(card)
        }

        @JvmStatic
        fun loadPreviewPage(cached: ResultCached) {
            mainActivity?.loadPopup(cached)
        }

        @JvmStatic
        fun importEpub() {
            mainActivity?.openEpubPicker()
        }

        @JvmStatic
        var app = Requests(
            OkHttpClient()
                .newBuilder()
                .ignoreAllSSLErrors()
                .addInterceptor(com.lagradost.quicknovel.network.CloudflareKiller())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(ConnectionPool(64, 5, TimeUnit.MINUTES))
                .build(),
            responseParser = object : ResponseParser {
                val mapper: ObjectMapper = jacksonObjectMapper().configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false
                )

                override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
                    return mapper.readValue(text, kClass.java)
                }

                override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
                    return try {
                        mapper.readValue(text, kClass.java)
                    } catch (e: Exception) {
                        null
                    }
                }

                override fun writeValueAsString(obj: Any): String {
                    return mapper.writeValueAsString(obj)
                }
            }
        ).apply {
            defaultHeaders = mapOf("user-agent" to USER_AGENT)
        }


        // === API ===

        @JvmStatic
        fun loadResult(url: String, apiName: String, startAction: Int = 0, startChapterUrl: String? = null) {
            (activity as? AppCompatActivity)?.loadResult(url, apiName, startAction, startChapterUrl)
        }

        fun Activity?.navigate(@IdRes navigation: Int, arguments: Bundle? = null, options: NavOptions? = null, extras: androidx.navigation.Navigator.Extras? = null) {
            try {
                if (this is FragmentActivity) {
                    val navHostFragment =
                        supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment?


                    navHostFragment?.navController?.navigate(navigation, arguments, options, extras)
                }
            } catch (t: Throwable) {
                logError(t)
            }
        }

        fun FragmentActivity.loadResult(url: String, apiName: String, startAction: Int = 0, startChapterUrl: String? = null, extras: androidx.navigation.Navigator.Extras? = null) {
            SearchFragment.currentDialog?.dismiss()
            runOnUiThread {
                this.navigate(
                    R.id.global_to_navigation_results,
                    ResultFragment.newInstance(url, apiName, startAction, startChapterUrl),
                    null,
                    extras
                )
            }
        }

        fun Activity?.loadSearchResult(card: SearchResponse, startAction: Int = 0) {
            (this as AppCompatActivity?)?.loadResult(card.url, card.apiName, startAction)
        }

        fun AppCompatActivity.loadResultFromUrl(url: String?): Boolean {
            if (url == null) return false
            for (api in apis) {
                if (url.contains(api.mainUrl)) {
                    loadResult(url, api.name)
                    return false
                }
            }

            return false
        }
    }

    private fun NavDestination.matchDestination(@IdRes destId: Int): Boolean =
        hierarchy.any { it.id == destId }

    private fun onNavDestinationSelected(item: MenuItem, navController: NavController): Boolean {
        val builder = NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true)
            .setEnterAnim(R.anim.enter_anim)
            .setExitAnim(R.anim.exit_anim)
            .setPopEnterAnim(R.anim.pop_enter)
            .setPopExitAnim(R.anim.pop_exit)
        if (item.order and Menu.CATEGORY_SECONDARY == 0) {
            builder.setPopUpTo(
                navController.graph.findStartDestination().id,
                inclusive = false,
                saveState = true
            )
        }
        val options = builder.build()
        return try {
            navController.navigate(item.itemId, null, options)
            navController.currentDestination?.matchDestination(item.itemId) == true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private val viewModel: ResultViewModel by viewModels()

    private val backgroundListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String? ->
        if (key == getString(R.string.background_image_key) ||
            key == getString(R.string.background_effect_mode_key) ||
            key == getString(R.string.background_blur_key) ||
            key == getString(R.string.background_dim_key) ||
            key == getString(R.string.background_grain_key) ||
            key == getString(R.string.background_vignette_key)
        ) {
            updateGlobalBackground()
        }
        if (key == getString(R.string.living_glass_key) ||
            key == getString(R.string.aura_intensity_key) ||
            key == getString(R.string.aura_palette_key) ||
            key == getString(R.string.aura_speed_key)
        ) {
            updateGlobalAura()
        }
        if (key == "NEW_UPDATES_COUNT") {
            updateUpdatesBadge()
        }
        if (key == getString(R.string.navbar_width_key)) {
            updateNavBarWidth()
        }
        if (key == getString(R.string.library_nav_style_key)) {
            val currentPos = binding?.mainViewpager?.currentItem ?: 0
            updateSwipeLock(currentPos)
        }
    }

    private fun hidePreviewPopupDialog() {
        viewModel.clear()
        bottomPreviewPopup?.dismiss()
    }

    fun loadPopup(
        resultCached: ResultCached,
    ) {
        viewModel.initState(resultCached)
    }

    fun loadPopup(card: DownloadFragment.DownloadDataLoaded) {
        viewModel.initState(card)
    }

    fun loadPopup(
        url: String,
        apiName: String,
    ) {
        viewModel.initState(apiName, url)
    }


    //imports area -------------------------------
    private val epubPathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            safe {
                // It lies, it can be null if file manager quits.
                if (uri == null) return@safe
                val ctx = this

                // RW perms for the path
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                val file = SafeFile.fromUri(ctx, uri)
                val fileName = file?.name()

                val mimeType = ctx.contentResolver.getType(uri)
                println("Loaded ebook file. Selected URI path: $uri - Name: $fileName")

                ioSafe {
                    try {
                        val inputData = androidx.work.Data.Builder()
                            .putString("uri", uri.toString())
                            .putString("fileName", fileName ?: "Unknown")
                            .putString("mimeType", mimeType ?: "")
                            .build()
                            
                        val request = androidx.work.OneTimeWorkRequestBuilder<com.lagradost.quicknovel.sync.MetadataExtractionWorker>()
                            .setInputData(inputData)
                            .build()
                            
                        androidx.work.WorkManager.getInstance(ctx).enqueue(request)
                            
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            showToast(getString(R.string.download_started))
                        }
                    } catch (t : Throwable) {
                        logError(t)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            showToast(t.message)
                        }
                    }
                }
            }
        }

    private fun openEpubPicker() {
        try {
            epubPathPicker.launch(
                arrayOf(
                    "application/pdf",
                    "application/epub+zip",
                    "application/x-mobipocket-ebook",
                    "application/vnd.amazon.mobi8-ebook"
                )
            )
        } catch (e: Exception) {
            logError(e)
        }
    }

    var bottomPreviewBinding: BottomPreviewBinding? = null
    var bottomPreviewPopup: BottomSheetDialog? = null
    private fun showPreviewPopupDialog(): BottomPreviewBinding {
        val ret = (bottomPreviewBinding ?: run {
            val diag = BottomSheetDialog(this, R.style.BottomSheetDrawerTheme)
            val bottom = BottomPreviewBinding.inflate(layoutInflater, null, false)
            diag.setContentView(bottom.root)
            
            // Vaul-style scaling animation using DrawerHelper
            val backgroundView = binding?.mainContentWrapper
            val behavior = diag.behavior
            behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN || 
                        newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        backgroundView?.let { DrawerHelper.resetScaling(it) }
                        binding?.navBarContainer?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(250)?.start()
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) diag.dismiss()
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    backgroundView?.let { DrawerHelper.applyScalingAnimation(it, slideOffset) }
                    // Also scale nav bar
                    val scale = 1f - (slideOffset * 0.05f)
                    binding?.navBarContainer?.apply {
                        scaleX = scale
                        scaleY = scale
                    }
                }
            })

            diag.setOnDismissListener {
                backgroundView?.let { DrawerHelper.resetScaling(it) }
                binding?.navBarContainer?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(200)?.start()
                
                bottomPreviewBinding = null
                bottomPreviewPopup = null
                viewModel.clear()
            }
            diag.setCanceledOnTouchOutside(true)
            diag.show()
            bottomPreviewPopup = diag
            bottom
        })
        bottomPreviewBinding = ret
        return ret
    }

    override fun onResume() {
        super.onResume()
        activity = this
        mainActivity = this
        updateGlobalBackground()
        updateGlobalAura()
        val currentPos = binding?.mainViewpager?.currentItem ?: 0
        updateSwipeLock(currentPos)
    }

    private fun updateUpdatesBadge() {
        // updates badge logic disabled as tab is removed
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val extraText = try {
                intent.getStringExtra(Intent.EXTRA_TEXT)
            } catch (e: Exception) {
                null
            }
            val cd = intent.clipData
            val item = if (cd != null && cd.itemCount > 0) cd.getItemAt(0) else null
            val url = item?.text?.toString()

            if (item?.uri != null && loadResultFromUrl(item.uri?.toString())) {
                return
            }
            if (url != null && loadResultFromUrl(url)) {
                return
            }
            if (extraText != null && loadResultFromUrl(extraText)) {
                return
            }
        }
        val data: String? = intent.data?.toString()
        loadResultFromUrl(data)
    }

    override fun onNewIntent(intent: Intent?) {
        handleIntent(intent)
        super.onNewIntent(intent)
    }


    private fun updateNavBar(destinationId: Int) {
        val isTab = tabIds.contains(destinationId)
        val selectedIndex = tabIds.indexOf(destinationId)

        binding?.apply {
            val navHost = findViewById<android.view.View>(R.id.nav_host_fragment)
            val isMainBar = isTab || destinationId == R.id.navigation_homepage || destinationId == R.id.navigation_mainpage || destinationId == R.id.navigation_settings
            
            navBarContainer.visibility = if (isMainBar) android.view.View.VISIBLE else android.view.View.GONE

            val slideDistance = 400f 
            if (isTab) {
                // If mainViewpager is already visible and at full alpha, just ensure it's not stuck shrunken
                if (mainViewpager.visibility == android.view.View.VISIBLE && mainViewpager.alpha >= 1f) {
                    mainViewpager.scaleX = 1f
                    mainViewpager.scaleY = 1f
                    mainViewpager.translationX = 0f
                } else {
                    mainViewpager.animate().cancel()
                    mainViewpager.alpha = 0f
                    mainViewpager.scaleX = 0.95f
                    mainViewpager.scaleY = 0.95f
                    mainViewpager.translationX = -slideDistance 
                    mainViewpager.visibility = android.view.View.VISIBLE
                    mainViewpager.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationX(0f)
                        .setDuration(400)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .withEndAction {
                            // Secondary safety lock
                            mainViewpager.scaleX = 1f
                            mainViewpager.scaleY = 1f
                            mainViewpager.translationX = 0f
                        }
                        .start()
                }

                if (selectedIndex != -1 && mainViewpager.currentItem != selectedIndex) {
                    mainViewpager.setCurrentItem(selectedIndex, false)
                }

                if (navHost?.visibility == android.view.View.VISIBLE) {
                    navHost.animate().setListener(null)
                    navHost.animate().cancel()
                    navHost.animate()
                        .alpha(0f)
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .translationX(slideDistance) 
                        .setDuration(300)
                        .setInterpolator(android.view.animation.AccelerateInterpolator())
                        .withEndAction { 
                            navHost.visibility = android.view.View.INVISIBLE 
                            navHost.translationX = 0f 
                            navHost.scaleX = 1f
                            navHost.scaleY = 1f
                        }.start()
                }
            } else {
                if (navHost?.visibility != android.view.View.VISIBLE || navHost?.alpha ?: 0f < 1f) {
                    navHost?.animate()?.setListener(null)
                    navHost?.animate()?.cancel()
                    navHost?.alpha = 0f
                    navHost?.scaleX = 0.95f
                    navHost?.scaleY = 0.95f
                    navHost?.translationX = slideDistance 
                    navHost?.visibility = android.view.View.VISIBLE
                    navHost?.animate()
                        ?.alpha(1f)
                        ?.scaleX(1f)
                        ?.scaleY(1f)
                        ?.translationX(0f)
                        ?.setDuration(400)
                        ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                        ?.withEndAction {
                             navHost?.scaleX = 1f
                             navHost?.scaleY = 1f
                             navHost?.translationX = 0f
                        }
                        ?.start()
                }
                if (mainViewpager.visibility == android.view.View.VISIBLE) {
                    mainViewpager.animate().setListener(null)
                    mainViewpager.animate().cancel()
                    mainViewpager.animate()
                        .alpha(0f)
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .translationX(-slideDistance) 
                        .setDuration(300)
                        .setInterpolator(android.view.animation.AccelerateInterpolator())
                        .withEndAction { 
                            mainViewpager.visibility = android.view.View.INVISIBLE 
                            mainViewpager.translationX = 0f 
                            mainViewpager.scaleX = 1f
                            mainViewpager.scaleY = 1f
                        }.start()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLocale()
        
        val tabs = listOf(
            R.id.navigation_download,
            R.id.navigation_search,
            R.id.navigation_foryou,
            R.id.navigation_history,
        )
        val currentItem = tabs.getOrNull(binding?.mainViewpager?.currentItem ?: 0) ?: return
        syncIndicator(currentItem)
    }

    var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        mainActivity = this

        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        CommonActivity.loadThemes(this)
        CommonActivity.init(this)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ioSafe {
            com.lagradost.quicknovel.util.BookmarkMigrationManager.migrateIfNeeded(this@MainActivity)
        }

        if (CommonActivity.pendingThemeChangeScreenshot != null) {
            val screenshot = CommonActivity.pendingThemeChangeScreenshot
            val decorView = window.decorView as android.view.ViewGroup
            val overlay = android.widget.ImageView(this)
            overlay.setImageBitmap(screenshot)
            overlay.scaleType = android.widget.ImageView.ScaleType.FIT_XY
            val params = android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            decorView.addView(overlay, params)

            overlay.doOnPreDraw {
                it.postDelayed({
                    if (isFinishing || isDestroyed) return@postDelayed
                    
                    val width = it.width.toFloat()
                    val height = it.height.toFloat()
                    val cx = CommonActivity.themeCenterX ?: width
                    val cy = CommonActivity.themeCenterY ?: 0f
                    val finalRadius = hypot(width.toDouble(), height.toDouble()).toFloat()

                    try {
                        overlay.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        val anim = ViewAnimationUtils.createCircularReveal(overlay, cx.toInt(), cy.toInt(), finalRadius, 0f)
                        anim.duration = 800 
                        anim.interpolator = FastOutSlowInInterpolator()
                        anim.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                overlay.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                                decorView.removeView(overlay)
                                overlay.setImageDrawable(null)
                                screenshot?.recycle()
                                CommonActivity.pendingThemeChangeScreenshot = null
                                CommonActivity.themeCenterX = null
                                CommonActivity.themeCenterY = null
                            }
                        })
                        anim.start()
                    } catch (e: Exception) {
                        overlay.animate().alpha(0f).setDuration(250).withEndAction {
                            decorView.removeView(overlay)
                            CommonActivity.pendingThemeChangeScreenshot = null
                        }.start()
                    }
                }, 10)
            }
        }


        binding?.let { b ->
            // Target navRootLayout directly — it is the bottom-constrained parent.
            // The old approach updated navBarContainer (a child inside navRootLayout)
            // which had no effect on screen position.
            // Setting on b.root (CoordinatorLayout) and updating a deep child also fails
            // because intermediate FrameLayouts swallow insets without relaying them.
            ViewCompat.setOnApplyWindowInsetsListener(b.navRootLayout) { view, windowInsets ->
                val navBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val bottomInset = navBarInsets.bottom
                val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null) {
                    lp.bottomMargin = if (bottomInset > 0) bottomInset + 16.toPx else 24.toPx
                    view.layoutParams = lp
                    view.requestLayout()
                }
                windowInsets
            }
            // Force a re-dispatch: insets may have already fired before this listener was set
            ViewCompat.requestApplyInsets(b.root)
        }
        
        updateGlobalBackground()
        updateGlobalAura()
        updateNavBarWidth()
        settingsManager.registerOnSharedPreferenceChangeListener(backgroundListener)
        updateUpdatesBadge()

        setUpBackup()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, _: Bundle? ->
            // If return navigation lands on Download (startDestination) while viewpager is hidden, 
            // it means we are popping back from Results/Settings to the main UI.
            // We force it back to the lastActiveTab instead of defaulting to Tab 0.
            val targetNavId = if (navDestination.id == R.id.navigation_download && binding?.mainViewpager?.isVisible == false) {
                tabIds.getOrNull(lastActiveTab) ?: navDestination.id
            } else {
                navDestination.id
            }
            
            updateNavBar(targetNavId)

            val hideBackgroundOn = listOf<Int>()
            val shouldHide = hideBackgroundOn.contains(navDestination.id)
            
            binding?.apply {
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                val imageUri = settingsManager.getString(getString(R.string.background_image_key), null)
                val hasBackground = !imageUri.isNullOrBlank()

                appBackgroundImage.isVisible = hasBackground && !shouldHide
                appBackgroundDim.isVisible = hasBackground && !shouldHide
                appBackgroundLightScrim.isVisible = hasBackground && !shouldHide
                appBackgroundGrain.isVisible = hasBackground && !shouldHide
                appBackgroundVignette.isVisible = hasBackground && !shouldHide
            }
        }

        handleIntent(intent)

        if (!checkWrite()) {
            requestRW()
        }
        requestNotifications()

        printProviders()

        setupCustomNav()

        binding?.mainViewpager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var lastSelectedPos = -1
            
            override fun onPageSelected(position: Int) {
                if (lastSelectedPos == position) return
                lastSelectedPos = position
                
                updateSwipeLock(position)
                updateNavSelection(position)
                
                // Track last active tab only if ViewPager is visible
                if (binding?.mainViewpager?.isVisible == true) {
                    lastActiveTab = position
                }
                
                val isDashboardVisible = binding?.mainViewpager?.isVisible == true
                binding?.homeSyncFab?.visibility = if (position == 1 && isDashboardVisible) android.view.View.VISIBLE else android.view.View.GONE
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
        })

        val tabs = listOf(
            R.id.navigation_download,
            R.id.navigation_search,
            R.id.navigation_foryou,
            R.id.navigation_history,
        )

        class ZoomOutPageTransformer : androidx.viewpager2.widget.ViewPager2.PageTransformer {
            override fun transformPage(page: android.view.View, position: Float) {
                val absPos = kotlin.math.abs(position)
                val pageWidth = page.width
                val pageHeight = page.height

                page.apply {
                    when {
                        position < -1 -> { alpha = 0f }
                        position <= 1 -> {
                            val scaleFactor = Math.max(0.85f, 1 - absPos)
                            val vertMargin = pageHeight * (1 - scaleFactor) / 2
                            val horzMargin = pageWidth * (1 - scaleFactor) / 2
                            
                            translationX = if (position < 0) {
                                horzMargin - vertMargin / 2
                            } else {
                                -horzMargin + vertMargin / 2
                            }

                            scaleX = scaleFactor
                            scaleY = scaleFactor

                            alpha = 0.5f + (((scaleFactor - 0.85f) / (1 - 0.85f)) * (1 - 0.5f))
                        }
                        else -> { alpha = 0f }
                    }
                }
            }
        }

        binding?.homeSyncFab?.apply {
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showProviderImportWarning()
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isMainTabs = tabs.contains(destination.id)
            val currentPos = binding?.mainViewpager?.currentItem ?: 0
            
            // Centralized visibility check
            updateNavSelection(currentPos)

            val isProvidersTab = currentPos == 1
            binding?.homeSyncFab?.visibility = if (isMainTabs && isProvidersTab && binding?.mainViewpager?.isVisible == true) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        var syncSnackbar: com.google.android.material.snackbar.Snackbar? = null
        com.lagradost.quicknovel.util.Apis.isSyncing.observe(this) { syncing ->
            if (syncing) {
                syncSnackbar = com.google.android.material.snackbar.Snackbar.make(binding!!.container, "Checking for plugin updates...", com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                syncSnackbar?.setAnchorView(R.id.nav_bar_container)
                syncSnackbar?.show()
            } else {
                syncSnackbar?.dismiss()
                syncSnackbar = null
            }
        }

        binding?.mainViewpager?.apply {
            offscreenPageLimit = 4
            setPageTransformer(ZoomOutPageTransformer())
            adapter = object : FragmentStateAdapter(this@MainActivity) {
                override fun getItemCount(): Int = tabs.size
                override fun createFragment(position: Int) = when (tabs[position]) {
                    R.id.navigation_download -> DownloadFragment()
                    R.id.navigation_search -> SearchFragment()
                    R.id.navigation_foryou -> com.lagradost.quicknovel.ui.foryou.ForYouFragment()
                    R.id.navigation_history -> HistoryFragment()
                    else -> DownloadFragment()
                }

                override fun getItemId(position: Int): Long = tabs[position].toLong()
                override fun containsItem(itemId: Long): Boolean = tabs.contains(itemId.toInt())
            }
            // Swipe lock handled by updateSwipeLock called via initial updateNavSelection
            updateSwipeLock(currentItem)
        }

        binding?.navItemDownload?.root?.post {
            if (isFinishing || isDestroyed) return@post
            updateNavSelection(binding?.mainViewpager?.currentItem ?: 0, animate = false)
        }


        observe(viewModel.readState) {
            bottomPreviewBinding?.apply {
                bookmark.setIconResource(if (it == ReadType.NONE) R.drawable.ic_baseline_bookmark_border_24 else R.drawable.ic_baseline_bookmark_24)
                bookmark.setText(it.stringRes)
            }
        }

        observe(viewModel.downloadState) { progressState ->
            val hasDownload = progressState != null && progressState.progress > 0
            bottomPreviewBinding?.downloadDeleteTrashFromResult?.apply {
                isVisible = hasDownload
                isClickable = hasDownload
            }
        }

        observeNullable(viewModel.loadResponse) { resource ->
            if (resource == null) {
                bottomPreviewPopup?.dismiss()
                return@observeNullable
            }
            when (resource) {
                is Resource.Failure -> {
                    showToast(this, R.string.error_loading_novel)
                    hidePreviewPopupDialog()
                }

                is Resource.Loading -> {
                    showPreviewPopupDialog().apply {
                        resultviewPreviewLoading.isVisible = true
                        resultviewPreviewResult.isVisible = false
                    }
                }

                is Resource.Success -> {
                    val d = resource.value
                    showPreviewPopupDialog().apply {
                        downloadDeleteTrashFromResult.setOnClickListener {
                            viewModel.deleteAlert()
                        }

                        bookmark.setOnClickListener { view ->
                            val popup = android.widget.ListPopupWindow(view.context)
                            popup.anchorView = view
                            popup.isModal = true
                            
                            val currentReadState = viewModel.readState.value
                            val items = ReadType.entries.map { it.prefValue to it.stringRes }
                            
                            val adapter = object : android.widget.ArrayAdapter<Pair<Int, Int>>(
                                view.context, 
                                android.R.layout.simple_list_item_1, 
                                items
                            ) {
                                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                                    val v = super.getView(position, convertView, parent) as android.widget.TextView
                                    val pair = items[position]
                                    v.setText(pair.second)
                                    
                                    if (pair.first == currentReadState?.prefValue) {
                                        val checkIcon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_check_24)?.mutate()?.apply {
                                            setTint(context.getResourceColor(android.R.attr.textColorPrimary))
                                        }
                                        v.setCompoundDrawablesWithIntrinsicBounds(checkIcon, null, null, null)
                                    } else {
                                        v.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                                    }
                                    return v
                                }
                            }
                            popup.setAdapter(adapter)
                            popup.setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(view.context, R.drawable.spatial_glass_card))
                            popup.width = 180.toPx
                            
                            popup.setOnItemClickListener { _, _, position, _ ->
                                viewModel.bookmark(items[position].first)
                                popup.dismiss()
                            }
                            popup.show()
                        }

                        readMore.setOnClickListener {
                            loadResult(d.url, viewModel.apiName)
                            hidePreviewPopupDialog()
                        }

                        readMore.isVisible = viewModel.apiName != IMPORT_SOURCE && viewModel.apiName != IMPORT_SOURCE_PDF
                        bookmark.isVisible = viewModel.apiName != IMPORT_SOURCE && viewModel.apiName != IMPORT_SOURCE_PDF

                        resultviewPreviewLoading.isVisible = false
                        resultviewPreviewResult.isVisible = true

                        resultviewPreviewPoster.apply {
                            setImage(d.downloadImage())
                            setOnClickListener {
                                loadResult(d.url, viewModel.apiName)
                                hidePreviewPopupDialog()
                            }
                        }

                        resultviewPreviewTitle.text = d.name

                        resultviewPreviewMoreInfo.setOnClickListener {
                            loadResult(d.url, viewModel.apiName)
                            hidePreviewPopupDialog()
                        }

                        resultviewPreviewDescription.text = d.synopsis ?: getString(R.string.no_data)

                        resultviewPreviewDescription.setOnClickListener { view ->
                            view.context?.let { ctx ->
                                val builder: AlertDialog.Builder =
                                    AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                                builder.setMessage(d.synopsis.html())
                                    .setTitle(d.name)
                                    .show()
                            }
                        }

                        d.rating?.let { rating ->
                            resultviewPreviewMetaRating.text = getRating(rating)
                            resultviewPreviewMetaRating.isVisible = true
                        } ?: run {
                            resultviewPreviewMetaRating.isVisible = false
                        }

                        resultviewPreviewMetaStatus.apply {
                            val statusTxt = d.status?.resource?.let { getString(it) } ?: ""
                            resultviewPreviewMetaStatus.text = statusTxt
                            resultviewPreviewMetaStatus.isVisible = statusTxt.isNotBlank()
                        }

                        if (d is StreamResponse) {
                            resultviewPreviewMetaChapters.text = "${d.data.size} ${getString(R.string.chapter_sort)}"
                            resultviewPreviewMetaChapters.isVisible = d.data.isNotEmpty()
                        } else {
                            resultviewPreviewMetaChapters.isVisible = false
                        }
                    }
                }
            }
        }

        val apiNames = getApiSettings()
        providersActive.clear()
        providersActive.addAll(apiNames)
        val edit = settingsManager.edit()
        edit.putStringSet(getString(R.string.search_providers_list_key), providersActive)
        edit.apply()

        thread {
            val keys = getKeys(DOWNLOAD_FOLDER)
            for (k in keys) {
                getKey<DownloadFragment.DownloadData>(k)
            }
        }

        ioSafe {
            runAutoUpdate()
        }

        handleIntent(intent)

        if (!checkWrite()) {
            requestRW()
        }
        requestNotifications()

        printProviders()

        thread {
            test()
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    window?.navigationBarColor = colorFromAttribute(R.attr.primaryGrayBackground)
                    updateLocale()
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        )
    }

    fun test() {
    }

    fun updateGlobalBackground() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val imageUri = settingsManager.getString(getString(R.string.background_image_key), null)

        binding?.apply {
            if (imageUri.isNullOrBlank()) {
                appBackgroundImage.isVisible = false
                appBackgroundDim.isVisible = false
                appBackgroundLightScrim.isVisible = false
                appBackgroundGrain.isVisible = false
                appBackgroundVignette.isVisible = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    appBackgroundImage.setRenderEffect(null)
                }
                appBackgroundImage.colorFilter = null
                return@apply
            }

            bindBackgroundEffects(
                context = this@MainActivity,
                imageView = appBackgroundImage,
                dimView = appBackgroundDim,
                lightScrimView = appBackgroundLightScrim,
                grainView = appBackgroundGrain,
                vignetteView = appBackgroundVignette,
                imageUri = imageUri,
                enabled = true,
                state = settingsManager.getBackgroundEffectState(this@MainActivity),
            )
        }
    }
    fun updateGlobalAura() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val enabled = settingsManager.getBoolean(getString(R.string.living_glass_key), false)
        val intensity = settingsManager.getSafeInt(getString(R.string.aura_intensity_key), 70)
        val speed = settingsManager.getSafeInt(getString(R.string.aura_speed_key), 100)
        val palette = settingsManager.getString(getString(R.string.aura_palette_key), "nebula") ?: "nebula"

        binding?.appLivingGlass?.apply {
            if (enabled) {
                visibility = android.view.View.VISIBLE
                setAuraIntensity(intensity)
                setAuraSpeed(speed)
                setAuraPalette(palette)

                // One-time shallow transparency on top-level shell containers only.
                // Do NOT use a persistent listener — that causes cards/bars in fragments
                // to lose their backgrounds on every layout pass.
                val helper = com.lagradost.quicknovel.util.AuraTransparencyHelper
                helper.forceTransparent(binding?.mainContentWrapper)
                helper.forceTransparent(binding?.homeRoot)
            } else {
                visibility = android.view.View.GONE
            }
        }
    }

    private fun setupCustomNav() {
        val b = binding ?: return

        // 4-item pill navigation
        val navItems = listOf(
            b.navItemDownload to (R.drawable.ic_baseline_collections_bookmark_24 to R.string.title_download),
            b.navItemSearch to (R.drawable.ic_baseline_search_24 to R.string.title_search),
            b.navItemForyou to (R.drawable.ic_stars_special to R.string.title_foryou_short),
            b.navItemHistory to (R.drawable.ic_baseline_history_24 to R.string.title_history)
        )

        navItems.forEachIndexed { index, (itemBinding, data) ->
            itemBinding.navItemIcon.setImageResource(data.first)
            itemBinding.navItemLabel.setText(data.second)
            
            // Premium Spring Touch Feedback
            itemBinding.navItemRoot.applySpringTouch()
            
            itemBinding.navItemRoot.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val navController = navHostFragment?.navController
                
                if (b.mainViewpager.isVisible == false && navController != null) {
                    // Unified Sync: Let updateNavBar handle the cross-fade/slide
                    // Just pop the stack, the listener will trigger the luxury transition
                    navController.popBackStack(navController.graph.startDestinationId, false)
                    
                    if (b.mainViewpager.currentItem != index) {
                        b.mainViewpager.setCurrentItem(index, true)
                    }
                } else if (b.mainViewpager.currentItem != index) {
                    b.mainViewpager.setCurrentItem(index, true)
                }
            }
        }

        // Dedicated Settings button navigates manually (NOT via ViewPager2 swiping)
        b.navExtraButton.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            val navController = navHostFragment?.navController
            
            // Navigate to settings destination explicitly with luxury slide animations
            navController?.navigate(R.id.navigation_settings, null, navOptions {
                anim {
                    enter = R.anim.slide_in_right
                    exit = R.anim.slide_out_left
                    popEnter = R.anim.slide_in_left
                    popExit = R.anim.slide_out_right
                }
            })
        }
    }

    private fun updateNavAndSettings(selectedIndex: Int, animate: Boolean = true) {
        val b = binding ?: return
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val navController = navHostFragment?.navController
        
        // 1. Determine Settings Button Visibility Logic
        val currentDest = navController?.currentDestination?.id
        val isAtMainTabs = currentDest == null || tabIds.contains(currentDest)
        val isLibraryTabActive = selectedIndex == 0 && b.mainViewpager.isVisible == true
        val shouldShowSettings = isLibraryTabActive && isAtMainTabs

        // 2. Fetch Theme Colors for dynamic indicator
        val primaryColor = getResourceColor(R.attr.colorPrimary)
        val onPrimaryColor = getResourceColor(R.attr.white)
        val iconColor = getResourceColor(R.attr.iconColor)
        val textColor = getResourceColor(R.attr.grayTextColor)
        
        val navItems = listOf(b.navItemDownload, b.navItemSearch, b.navItemForyou, b.navItemHistory)
        var targetId: Int? = null

        // 3. Update Global Nav Visibility with a single unified transition pass
        if (animate) {
            val transition = android.transition.TransitionSet()
                .addTransition(android.transition.ChangeBounds().setInterpolator(android.view.animation.DecelerateInterpolator()))
                .addTransition(android.transition.Fade())
                .setDuration(300)
            android.transition.TransitionManager.beginDelayedTransition(b.navRootLayout, transition)
        }

        if (isAtMainTabs) {
            if (b.navRootLayout.visibility != android.view.View.VISIBLE) {
                b.navRootLayout.visibility = android.view.View.VISIBLE
                b.navRootLayout.alpha = 1f
            }
        } else {
            if (b.navRootLayout.visibility == android.view.View.VISIBLE) {
                b.navRootLayout.visibility = android.view.View.GONE
            }
        }

        // 4. Update Settings Button Visibility
        if (shouldShowSettings) {
            if (b.navExtraButton.visibility != android.view.View.VISIBLE) {
                b.navExtraButton.visibility = android.view.View.VISIBLE
                b.navExtraButton.alpha = 1f
                b.navExtraButton.scaleX = 1f
                b.navExtraButton.scaleY = 1f
            }
        } else {
            if (b.navExtraButton.visibility == android.view.View.VISIBLE) {
                b.navExtraButton.visibility = android.view.View.GONE
            }
        }

        // 5. Update Nav Selection (Pill & Weights)

        // Apply weights and visibility updates
        navItems.forEachIndexed { index, itemBinding ->
            val isSelected = index == selectedIndex
            itemBinding.navItemLabel.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            
            val tint = if (isSelected) onPrimaryColor else iconColor
            val labelColor = if (isSelected) onPrimaryColor else textColor
            
            itemBinding.navItemIcon.imageTintList = android.content.res.ColorStateList.valueOf(tint)
            itemBinding.navItemLabel.setTextColor(labelColor)
            
            val root = itemBinding.root
            root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                this.horizontalWeight = if (isSelected) 3.2f else 0.8f
            }
            
            if (isSelected) targetId = itemBinding.root.id
        }

        b.navIndicatorPill.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor).withAlpha(225)

        if (targetId != null) {
            val constraintSet = ConstraintSet()
            constraintSet.clone(b.navBarContainer)
            constraintSet.connect(R.id.nav_indicator_pill, ConstraintSet.START, targetId!!, ConstraintSet.START)
            constraintSet.connect(R.id.nav_indicator_pill, ConstraintSet.END, targetId!!, ConstraintSet.END)
            constraintSet.applyTo(b.navBarContainer)
            b.navIndicatorPill.alpha = 1f
        } else {
            b.navIndicatorPill.alpha = 0f
        }
    }

    private fun updateNavSelection(selectedIndex: Int, animate: Boolean = true) {
        updateNavAndSettings(selectedIndex, animate)
        updateSwipeLock(selectedIndex)
    }

    private fun updateSwipeLock(position: Int) {
        val navStyle = getLibraryNavStyle()
        binding?.mainViewpager?.post {
            binding?.mainViewpager?.isUserInputEnabled = (navStyle == "0") || (position != 0)
        }
    }

    /**
     * Compatibility Bridge: Resolves "Unresolved reference" for legacy calls 
     * while redirecting visibility logic to the unified updateNavAndSettings method.
     */
    private fun updateSettingsButtonVisibility() {
        val currentPos = binding?.mainViewpager?.currentItem ?: 0
        updateNavAndSettings(currentPos, animate = true)
    }

    private fun syncIndicator(itemId: Int) {
        updateNavSelection(itemId)
    }

    private fun updateNavBarWidth() {
        val b = binding ?: return
        val settingsManager = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        
        // Handle potential type mismatch gracefully (Float is expected but handle Int just in case)
        val widthDp = try {
            settingsManager.getFloat(getString(R.string.navbar_width_key), 330f)
        } catch (e: Exception) {
            // Backup for manual values or legacy preference formats
            settingsManager.getInt(getString(R.string.navbar_width_key), 330).toFloat()
        }
        
        val widthPx = widthDp.toInt().toPx
        
        b.navBarContainer.apply {
            minimumWidth = widthPx
            updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                this.matchConstraintMinWidth = widthPx
                this.matchConstraintDefaultWidth = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_WRAP
            }
            // Force a re-layout of the container and its parent chain
            requestLayout()
        }
        b.navRootLayout.requestLayout()
    }

}
