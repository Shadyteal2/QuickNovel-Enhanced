package com.lagradost.quicknovel

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import com.lagradost.quicknovel.providers.RedditProvider
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
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.dismissSafe
import com.lagradost.quicknovel.util.UIHelper.getResourceColor
import com.lagradost.quicknovel.util.UIHelper.html
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.safefile.SafeFile
import android.view.ViewGroup
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.lagradost.quicknovel.ui.history.HistoryFragment
import com.lagradost.quicknovel.ui.settings.SettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.reflect.KClass
import android.net.Uri
import coil3.load
import coil3.request.crossfade
import coil3.asDrawable

class MainActivity : AppCompatActivity() {
    companion object {
        private var _mainActivity: WeakReference<MainActivity>? = null
        private var mainActivity
            get() = _mainActivity?.get()
            private set(value) {
                _mainActivity = WeakReference(value)
            }

        fun loadPreviewPage(searchResponse: SearchResponse) {
            mainActivity?.loadPopup(searchResponse.url, searchResponse.apiName)
        }

        fun loadPreviewPage(card: DownloadFragment.DownloadDataLoaded) {
            mainActivity?.loadPopup(card)
        }

        fun loadPreviewPage(cached: ResultCached) {
            mainActivity?.loadPopup(cached)
        }

        fun importEpub() {
            mainActivity?.openEpubPicker()
        }

        var app = Requests(
            OkHttpClient()
                .newBuilder()
                .ignoreAllSSLErrors()
                .readTimeout(50L, TimeUnit.SECONDS)//to online translations
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
        lateinit var navOptions: NavOptions

        fun loadResult(url: String, apiName: String, startAction: Int = 0) {
            (activity as? AppCompatActivity)?.loadResult(url, apiName, startAction)
        }

        fun Activity?.navigate(@IdRes navigation: Int, arguments: Bundle? = null, options: NavOptions? = null) {
            try {
                if (this is FragmentActivity) {
                    val navHostFragment =
                        supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment?

                    if (navigation == R.id.global_to_navigation_mainpage) {
                        // Clear outer SupportFragmentManager
                        supportFragmentManager.fragments.firstOrNull { it is MainPageFragment }?.let {
                            supportFragmentManager.beginTransaction()
                                .setReorderingAllowed(true)
                                .remove(it)
                                .commitNowAllowingStateLoss()
                        }

                        // Clear child FragmentManager
                        val childFragments = navHostFragment?.childFragmentManager?.fragments
                        val oldFragment = childFragments?.firstOrNull { it is MainPageFragment }
                        if (oldFragment != null) {
                            navHostFragment.childFragmentManager.beginTransaction()
                                .setReorderingAllowed(true)
                                .remove(oldFragment)
                                .commitNowAllowingStateLoss()
                        }
                    }

                    navHostFragment?.navController?.navigate(navigation, arguments, options)
                }
            } catch (t: Throwable) {
                logError(t)
            }
        }

        fun FragmentActivity.loadResult(url: String, apiName: String, startAction: Int = 0) {
            SearchFragment.currentDialog?.dismiss()
            runOnUiThread {
                this.navigate(
                    R.id.global_to_navigation_results,
                    ResultFragment.newInstance(url, apiName, startAction)
                )
                /*supportFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.enter_anim,
                            R.anim.exit_anim,
                            R.anim.pop_enter,
                            R.anim.pop_exit
                        )
                        .add(R.id.homeRoot, ResultFragment().newInstance(url, apiName, startAction))
                        .commit()*/
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

            // kinda dirty ik
            val reddit = RedditProvider()
            RedditProvider.getName(url)?.let { name ->
                try {
                    Coroutines.main {
                        val uri = withContext(Dispatchers.IO) {
                            createQuickStream(
                                QuickStreamData(
                                    QuickStreamMetaData(
                                        "Not found",
                                        name,
                                        reddit.name,
                                    ),
                                    null,
                                    mutableListOf(ChapterData("Single Post", url, null, null))
                                )
                            )
                        }
                        openQuickStream(uri)
                    }
                } catch (e: Exception) {
                    logError(e)
                }
                return true
            }
            return false
        }

        /*fun AppCompatActivity.backPressed(): Boolean {
            this.window?.navigationBarColor =
                this.colorFromAttribute(R.attr.primaryGrayBackground)

            val currentFragment = supportFragmentManager.fragments.last {
                it.isVisible
            }

            if (currentFragment is NavHostFragment) {
                val child = currentFragment.childFragmentManager.fragments.last {
                    it.isVisible
                }
                if (child is MainPageFragment) {
                    val navController = findNavController(R.id.nav_host_fragment)
                    navController.navigate(R.id.navigation_homepage, Bundle(), navOptions)
                    return true
                }
            }

            if (currentFragment != null && supportFragmentManager.fragments.size > 2) {
                if (supportFragmentManager.fragments.size == 3) {
                    //window?.navigationBarColor =
                    //    colorFromAttribute(R.attr.primaryBlackBackground)
                }
                //MainActivity.showNavbar()
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.enter_anim,
                        R.anim.exit_anim,
                        R.anim.pop_enter,
                        R.anim.pop_exit
                    )
                    .remove(currentFragment)
                    .commitAllowingStateLoss()
                supportFragmentManager
                return true
            }
            return false
        }*/

        /* fun semihideNavbar() {
             activity
             val w: Window? = activity?.window // in Activity's onCreate() for instance
             if (w != null) {
                 val uiVisibility =
                     View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                 w.decorView.systemUiVisibility = uiVisibility
                 w.navigationBarColor =
                     activity?.getResourceColor(android.R.attr.navigationBarColor, 0.7F)
             }
         }

         fun showNavbar() {
             val w: Window? = activity.window // in Activity's onCreate() for instance
             if (w != null) {
                 w.decorView.systemUiVisibility = 0
                 w.navigationBarColor = android.R.attr.navigationBarColor
             }
         }

         fun transNavbar(trans: Boolean) {
             val w: Window? = activity.window // in Activity's onCreate() for instance
             if (w != null) {
                 if (trans) {
                     w.setFlags(
                         WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                         WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                     )
                 } else {
                     w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                 }
             }
         }*/
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
            key == getString(R.string.background_blur_key) ||
            key == getString(R.string.background_dim_key)
        ) {
            updateGlobalBackground()
        }
    }

    private fun hidePreviewPopupDialog() {
        viewModel.clear()
        bottomPreviewPopup.dismissSafe(this)
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
                println("Loaded epub file. Selected URI path: $uri - Name: $fileName")

                ioSafe {
                    try {
                        if (mimeType == "application/pdf" || fileName?.endsWith(".pdf") == true) {
                            BookDownloader2.downloadPDFWorkThread(uri, ctx)
                        }
                        else{
                            BookDownloader2.downloadWorkThread(uri, ctx)
                        }
                    } catch (t : Throwable) {
                        logError(t)
                        showToast(t.message)
                    }
                }
            }
        }

    private fun openEpubPicker() {
        try {
            epubPathPicker.launch(
                arrayOf(
                    //"text/plain",
                    //"text/str",
                    //"application/octet-stream",
                    "application/pdf",
                    "application/epub+zip",
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
            val builder =
                BottomSheetDialog(this)

            val bottom = BottomPreviewBinding.inflate(layoutInflater, null, false)
            builder.setContentView(bottom.root)
            builder.setOnDismissListener {
                bottomPreviewBinding = null
                bottomPreviewPopup = null
                viewModel.clear()
            }
            builder.setCanceledOnTouchOutside(true)
            builder.show()
            bottomPreviewPopup = builder
            bottom
        })
        bottomPreviewBinding = ret
        return ret
    }

    /* // MOON READER WONT RETURN THE DURATION, BUT THIS CAN BE USED FOR SOME USER FEEDBACK IN THE FUTURE??? SEE @moonreader
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }*/

    override fun onResume() {
        super.onResume()
        activity = this
        mainActivity = this
    }

    /*override fun onBackPressed() {
        if (backPressed()) return
        super.onBackPressed()
    }*/

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val extraText = try { // I don't trust android
                intent.getStringExtra(Intent.EXTRA_TEXT)
            } catch (e: Exception) {
                null
            }
            val cd = intent.clipData
            val item = if (cd != null && cd.itemCount > 0) cd.getItemAt(0) else null
            val url = item?.text?.toString()

            // idk what I am doing, just hope any of these work
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


    private fun updateNavBar(destination: NavDestination) {
        val tabIds = listOf(
            R.id.navigation_download,
            R.id.navigation_search,
            R.id.navigation_history,
            R.id.navigation_settings,
        )
        val isTab = tabIds.contains(destination.id)

        binding?.apply {
            navBarContainer.isVisible = isTab || destination.id == R.id.navigation_homepage || destination.id == R.id.navigation_mainpage
            mainViewpager.isVisible = isTab
            // Since navHostFragment is a <fragment> tag, it might not be directly in the binding
            // Depending on the version of view binding, it might be there or we might need to find it
            findViewById<android.view.View>(R.id.nav_host_fragment)?.isVisible = !isTab
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLocale() // android fucks me by chaining lang when rotating the phone
    }

    var binding: ActivityMainBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        mainActivity = this

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        CommonActivity.loadThemes(this)
        CommonActivity.init(this)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        
        updateGlobalBackground()
        settingsManager.registerOnSharedPreferenceChangeListener(backgroundListener)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding?.navBarBlurView?.setRenderEffect(
                RenderEffect.createBlurEffect(
                    30f, 30f, Shader.TileMode.CLAMP
                )
            )
        }

        setUpBackup()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, bundle: Bundle? ->
            // Intercept search and add a query
            updateNavBar(navDestination)

            // Background visibility scope
            val hideBackgroundOn = listOf<Int>(
                // R.id.global_to_navigation_results,
                // R.id.navigation_results,
            )
            val shouldHide = hideBackgroundOn.contains(navDestination.id)
            
            binding?.apply {
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                val imageUri = settingsManager.getString(getString(R.string.background_image_key), null)
                val hasBackground = !imageUri.isNullOrBlank()

                appBackgroundImage.isVisible = hasBackground && !shouldHide
                appBackgroundDim.isVisible = hasBackground && !shouldHide
                appBackgroundLightScrim.isVisible = hasBackground && !shouldHide
            }
        }

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        
        // Programmatically disable clipping on BottomNavigationItemViews
        navView.apply {
            val menuView = getChildAt(0) as? BottomNavigationMenuView
            menuView?.apply {
                clipChildren = false
                clipToPadding = false
                for (i in 0 until childCount) {
                    val itemView = getChildAt(i) as? BottomNavigationItemView
                    itemView?.apply {
                        clipChildren = false
                        clipToPadding = false
                    }
                }
            }
        }

        val tabs = listOf(
            R.id.navigation_download,
            R.id.navigation_search,
            R.id.navigation_history,
            R.id.navigation_settings,
        )

        binding?.mainViewpager?.apply {
            adapter = object : FragmentStateAdapter(this@MainActivity) {
                override fun getItemCount(): Int = tabs.size
                override fun createFragment(position: Int) = when (tabs[position]) {
                    R.id.navigation_download -> DownloadFragment()
                    R.id.navigation_search -> SearchFragment()
                    R.id.navigation_history -> HistoryFragment()
                    R.id.navigation_settings -> SettingsFragment()
                    else -> DownloadFragment()
                }
            }
            isUserInputEnabled = true // Enable swiping
            
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    navView.selectedItemId = tabs[position]
                    // Fade in indicator if it was hidden
                    binding?.navIndicator?.animate()?.alpha(1f)?.setDuration(200)?.start()
                }

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    binding?.apply {
                        val width = navView.width
                        if (width > 0) {
                            val tabEntries = tabs.size
                            val tabWidth = width / tabEntries
                            val indicatorWidth = navIndicator.width
                            val centerX = tabWidth / 2f
                            val startX = position * tabWidth + centerX - indicatorWidth / 2f
                            
                            // 1:1 tracking with interpolation
                            val currentX = startX + tabWidth * FastOutSlowInInterpolator().getInterpolation(positionOffset)
                            navIndicator.translationX = currentX
                        }
                    }
                }

                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        // Subtly fade alpha when idle
                        binding?.navIndicator?.animate()?.alpha(0.6f)?.setDuration(1000)?.start()
                    } else {
                        binding?.navIndicator?.animate()?.alpha(1f)?.setDuration(200)?.start()
                    }
                }
            })
        }

        navView.setOnItemSelectedListener { item ->
            navView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val index = tabs.indexOf(item.itemId)
            if (index != -1) {
                // If we are currently showing a non-tab screen (like results), navigate back to a tab
                if (binding?.mainViewpager?.isVisible == false) {
                    onNavDestinationSelected(item, navController)
                }
                binding?.mainViewpager?.currentItem = index
                true
            } else {
                onNavDestinationSelected(
                    item,
                    navController = navController
                )
            }
        }

        //val navController = findNavController(R.id.nav_host_fragment)

        //window.navigationBarColor = colorFromAttribute(R.attr.darkBackground)
        navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setEnterAnim(R.anim.nav_enter_anim)
            .setExitAnim(R.anim.nav_exit_anim)
            .setPopEnterAnim(R.anim.nav_pop_enter)
            .setPopExitAnim(R.anim.nav_pop_exit)
            .setPopUpTo(navController.graph.startDestinationId, false)
            .build()
        navView.setOnItemReselectedListener { item ->
            val index = tabs.indexOf(item.itemId)
            if (index != -1) {
                if (binding?.mainViewpager?.isVisible == false) {
                    // Explicit inclusive pop using integer literal (1) for POP_BACKSTACK_INCLUSIVE
                    supportFragmentManager.popBackStack(null, 1)
                    navController.popBackStack(navController.graph.startDestinationId, false)
                }
                binding?.mainViewpager?.currentItem = index
            }
        }
        /*navView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_homepage -> {
                    navController.navigate(R.id.navigation_homepage, null, navOptions)
                }

                R.id.navigation_search -> {
                    navController.navigate(R.id.navigation_search, null, navOptions)
                }

                R.id.navigation_download -> {
                    navController.navigate(R.id.navigation_download, null, navOptions)
                }

                R.id.navigation_history -> {
                    navController.navigate(R.id.navigation_history, null, navOptions)
                }

                R.id.navigation_settings -> {
                    navController.navigate(R.id.navigation_settings, null, navOptions)
                }
            }
            true
        }*/

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
                bottomPreviewPopup.dismissSafe(this)
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
                            view.popupMenu(
                                ReadType.entries.map { it.prefValue to it.stringRes },
                                selectedItemId = viewModel.readState.value?.prefValue
                            ) {
                                viewModel.bookmark(itemId)
                            }
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

        //navView.itemRippleColor =
        //    ColorStateList.valueOf(getResourceColor(R.attr.colorPrimary, 0.1f))

        val apiNames = getApiSettings()
        providersActive = apiNames
        val edit = settingsManager.edit()
        edit.putStringSet(getString(R.string.search_providers_list_key), providersActive)
        edit.apply()
        /*
        val apiName = settingsManager.getString(getString(R.string.provider_list_key), apis[0].name)
        activeAPI = getApiFromName(apiName ?: apis[0].name)
        val edit = settingsManager.edit()
        edit.putString(getString(R.string.provider_list_key, activeAPI.name), activeAPI.name)
        edit.apply()*/

        thread { // IDK, WARMUP OR SMTH, THIS WILL JUST REDUCE THE INITIAL LOADING TIME FOR DOWNLOADS, NO REAL USAGE, SEE @WARMUP
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
        // Note that android can normally not request 2 permissions at once
        // But storage permissions are not required for android 13, but notifications are
        requestNotifications()

        printProviders()

        //loadResult("https://www.novelpassion.com/novel/battle-frenzy")
        //loadResult("https://www.royalroad.com/fiction/40182/only-villains-do-that", MainActivity.activeAPI.name)
        thread {
            test()
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    window?.navigationBarColor = colorFromAttribute(R.attr.primaryGrayBackground)
                    updateLocale()

                    // If we don't disable we end up in a loop with default behavior calling
                    // this callback as well, so we disable it, run default behavior,
                    // then re-enable this callback so it can be used for next back press.
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        )
    }

    fun test() {
        // val response = app.get("https://ranobes.net/up/a-bored-lich/936969-1.html")
        // println(response.text)
    }

    fun updateGlobalBackground() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val imageUri = settingsManager.getString(getString(R.string.background_image_key), null)
        val blur = settingsManager.getInt(getString(R.string.background_blur_key), 0)
        val dim = settingsManager.getInt(getString(R.string.background_dim_key), 0)
        val isLightTheme = settingsManager.getString(getString(R.string.theme_key), "AmoledLight") == "Light"

        binding?.apply {
            if (imageUri.isNullOrBlank()) {
                appBackgroundImage.isVisible = false
                appBackgroundDim.isVisible = false
                appBackgroundLightScrim.isVisible = false
                return@apply
            }

            appBackgroundImage.isVisible = true
            appBackgroundDim.isVisible = true
            appBackgroundLightScrim.isVisible = true

            appBackgroundImage.load(Uri.parse(imageUri)) {
                crossfade(true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (blur > 0) {
                    appBackgroundImage.setRenderEffect(
                        RenderEffect.createBlurEffect(
                            blur.toFloat(),
                            blur.toFloat(),
                            Shader.TileMode.CLAMP
                        )
                    )
                } else {
                    appBackgroundImage.setRenderEffect(null)
                }
            }

            appBackgroundDim.alpha = dim / 100f
            appBackgroundLightScrim.alpha = if (isLightTheme) 0.25f else 0f
        }
    }
}