package com.lagradost.quicknovel.ui.download

import android.R.attr.fragment
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.launch
import androidx.appcompat.widget.SearchView
import androidx.core.view.doOnAttach
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.CURRENT_TAB
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.DOWNLOAD_NORMAL_SORTING_METHOD
import com.lagradost.quicknovel.DOWNLOAD_SETTINGS
import com.lagradost.quicknovel.DOWNLOAD_SORTING_METHOD
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.FragmentDownloadsBinding
import com.lagradost.quicknovel.databinding.SortBottomSheetBinding
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.SortingMethodAdapter
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.ui.img
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadFragment : Fragment() {
    private lateinit var viewModel: DownloadViewModel
    lateinit var binding: FragmentDownloadsBinding



    data class DownloadData(
        @JsonProperty("source")
        val source: String,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("author")
        val author: String?,
        @JsonProperty("posterUrl")
        val posterUrl: String?,
        //RATING IS FROM 0-100
        @JsonProperty("rating")
        val rating: Int?,
        @JsonProperty("peopleVoted")
        val peopleVoted: Int?,
        @JsonProperty("views")
        val views: Int?,
        @JsonProperty("synopsis")
        val synopsis: String?,
        @JsonProperty("tags")
        val tags: List<String>?,
        @JsonProperty("apiName")
        val apiName: String,
        /** Unix time ms */
        @JsonProperty("lastUpdated")
        val lastUpdated: Long?,
        /** Unix time ms */
        @JsonProperty("lastDownloaded")
        val lastDownloaded: Long?,
    )

    data class DownloadDataLoaded(
        val source: String,
        val name: String,
        val author: String?,
        val posterUrl: String?,
        //RATING IS FROM 0-100
        val rating: Int?,
        val peopleVoted: Int?,
        val views: Int?,
        val synopsis: String?,
        val tags: List<String>?,
        val apiName: String,
        val downloadedCount: Long,
        val downloadedTotal: Long,
        val ETA: String,
        val state: DownloadState,
        val id: Int,
        val generating: Boolean,
        val lastUpdated: Long?,
        val lastDownloaded: Long?,
    ) {
        val image by lazy {
            if(isImported) {
                val bitmap = BookDownloader2Helper.getCachedBitmap(activity, apiName, author, name)
                if(bitmap != null) {
                    return@lazy UiImage.Bitmap(bitmap)
                }
            }
            img(posterUrl)
        }

        override fun hashCode(): Int {
            return id
        }

        val isImported: Boolean get() = (apiName == IMPORT_SOURCE || apiName ==IMPORT_SOURCE_PDF)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        viewModel = ViewModelProvider(activity ?: this)[DownloadViewModel::class.java]
        binding = FragmentDownloadsBinding.inflate(inflater)
        return binding.root
        //return inflater.inflate(R.layout.fragment_downloads, container, false)
    }

    override fun onResume() {
        super.onResume()
        setupGridView()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupGridView() {
        val adapter = (binding.viewpager.adapter as? ViewpagerAdapter) ?: return
        for ((_, ref) in adapter.collectionsOfRecyclerView) {
            val rv = ref.get() ?: continue
            val compactView = rv.context.getDownloadIsCompact()

            val spanCountLandscape = if (compactView) 2 else 6
            val spanCountPortrait = if (compactView) 1 else 3
            val orientation = rv.resources.configuration.orientation
            rv.spanCount = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                spanCountLandscape
            } else {
                spanCountPortrait
            }

            (rv.adapter as? AnyAdapter)?.notifyDataSetChanged()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGridView()
        binding.downloadFabContainer.visibility = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    // https://stackoverflow.com/a/67441735/13746422
    fun ViewPager2.reduceDragSensitivity(f: Int = 4) {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView

        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * f)       // "8" was obtained experimentally
    }

    val isOnDownloads get() = viewModel.currentTab.value == 0

    lateinit var searchExitIcon: ImageView
    lateinit var searchMagIcon: ImageView

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadAllData(true)
        // activity?.fixPaddingStatusbar(binding.downloadToolbar)
        activity?.fixPaddingStatusbar(binding.downloadRoot)
        //viewModel = ViewModelProviders.of(activity!!).get(DownloadViewModel::class.java)


        searchExitIcon =
            binding.downloadSearch.findViewById(androidx.appcompat.R.id.search_close_btn)
        searchMagIcon = binding.downloadSearch.findViewById(androidx.appcompat.R.id.search_mag_icon)
        searchMagIcon.scaleX = 0.65f
        searchMagIcon.scaleY = 0.65f

        binding.downloadSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                viewModel.search(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.search(newText)
                return true
            }
        })


        val adapter = ViewpagerAdapter(viewModel, this) { isScrollingDown ->
            binding.downloadFabText.isVisible = !isScrollingDown
        }

        observe(viewModel.pages) { pages ->
            adapter.submitList(pages)
            viewModel.currentTab.value?.let {
                if (it != binding.viewpager.currentItem) {
                    binding.viewpager.setCurrentItem(it, false)
                }
            }
        }

        binding.viewpager.adapter = adapter
        binding.viewpager.isUserInputEnabled = false // Disable smooth swiping
        
        val dotsHolder = binding.pillDotsHolder
        var initialTouchX = 0f
        var initialPillX = 0f
        var currentSelectedIndex = 0
        var isDragging = false

        for (i in 0 until dotsHolder.childCount) {
            dotsHolder.getChildAt(i).setOnClickListener { view ->
                binding.viewpager.setCurrentItem(i, false) // Fast switch
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }

        binding.travelerIcon.setOnTouchListener { view, event ->
            val maxOffset = binding.libraryPillMenu.width - view.width
            val stepSize = if (maxOffset > 0) maxOffset.toFloat() / 5f else 0f

            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    initialTouchX = event.rawX
                    initialPillX = view.translationX
                    isDragging = true
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    var newX = initialPillX + dx
                    newX = newX.coerceIn(0f, maxOffset.toFloat())
                    view.translationX = newX

                    if (stepSize > 0) {
                        val index = (newX / stepSize).coerceIn(0f, 5f).toInt()
                        if (index != currentSelectedIndex) {
                            currentSelectedIndex = index
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            
                            // Highlight dot
                            for (i in 0 until dotsHolder.childCount) {
                                val dot = (dotsHolder.getChildAt(i) as? android.view.ViewGroup)?.getChildAt(0)
                                if (dot != null) {
                                    if (i == index) {
                                        dot.alpha = 1.0f
                                        dot.scaleX = 1.25f
                                        dot.scaleY = 1.25f
                                    } else {
                                        dot.alpha = 0.4f
                                        dot.scaleX = 1.0f
                                        dot.scaleY = 1.0f
                                    }
                                }
                            }
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    isDragging = false
                    if (stepSize > 0) {
                        val targetX = currentSelectedIndex * stepSize
                        android.animation.ObjectAnimator.ofFloat(view, "translationX", targetX).apply {
                            duration = 200
                            interpolator = android.view.animation.OvershootInterpolator(1.2f)
                            start()
                        }
                        binding.viewpager.setCurrentItem(currentSelectedIndex, false) // Fast switch
                    }
                    true
                }
                else -> false
            }
        }

        binding.viewpager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (isDragging) return // Don't override user drag offset
                
                currentSelectedIndex = position
                binding.travelerIcon.post {
                    val maxOffset = binding.libraryPillMenu.width - binding.travelerIcon.width
                    if (maxOffset > 0) {
                        val stepSize = maxOffset.toFloat() / 5f
                        val targetX = position * stepSize
                        android.animation.ObjectAnimator.ofFloat(binding.travelerIcon, "translationX", targetX).apply {
                            duration = 250
                            interpolator = android.view.animation.OvershootInterpolator(1.1f)
                            start()
                        }
                    }
                }

                for (i in 0 until dotsHolder.childCount) {
                    val dot = (dotsHolder.getChildAt(i) as? android.view.ViewGroup)?.getChildAt(0)
                    if (dot != null) {
                        if (i == position) {
                            dot.alpha = 1.0f
                            dot.scaleX = 1.25f
                            dot.scaleY = 1.25f
                        } else {
                            dot.alpha = 0.4f
                            dot.scaleX = 1.0f
                            dot.scaleY = 1.0f
                        }
                    }
                }
            }
        })

        binding.bookmarkTabs.apply {
            val tabs = mutableListOf(R.string.tab_downloads)
            for (read in viewModel.readList) {
                tabs.add(read.stringRes)
            }
            TabLayoutMediator(this, binding.viewpager) { tab, position ->
                tab.setId(tabs[position]).setText(tabs[position])
            }.attach()

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    //binding.swipeContainer.isEnabled = binding.bookmarkTabs.selectedTabPosition == 0
                    viewModel.switchPage(binding.bookmarkTabs.selectedTabPosition)
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {
                }

                override fun onTabReselected(tab: TabLayout.Tab?) {
                }

            })
        }

        binding.downloadFab.setOnClickListener { view ->
            val binding = SortBottomSheetBinding.inflate(layoutInflater, null, false)
            val bottomSheetDialog = BottomSheetDialog(view.context)
            bottomSheetDialog.setContentView(binding.root)

            val (sorting, key) = if (isOnDownloads) {
                DownloadViewModel.sortingMethods to DOWNLOAD_SORTING_METHOD
            } else {
                DownloadViewModel.normalSortingMethods to DOWNLOAD_NORMAL_SORTING_METHOD
            }
            val current = (getKey<Int>(DOWNLOAD_SETTINGS, key) ?: DEFAULT_SORT)

            val adapter = SortingMethodAdapter(current) { item, position, newId ->
                setKey(DOWNLOAD_SETTINGS, key, newId)
                viewModel.resortAllData()
                bottomSheetDialog.dismiss()
            }.apply {
                submitList(sorting.toList())
            }
            binding.sortClick.adapter = adapter
            bottomSheetDialog.show()
        }
        /*
        download_filter.setOnClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this.context!!)
            lateinit var dialog: AlertDialog
            builder.setSingleChoiceItems(sotringMethods.map { t -> t.name }.toTypedArray(),
                sotringMethods.indexOfFirst { t -> t.id ==  viewModel.currentSortingMethod.value }
            ) { _, which ->
                val id = sotringMethods[which].id
                viewModel.currentSortingMethod.postValue(id)
                DataStore.setKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, id)

                dialog.dismiss()
            }
            builder.setTitle("Sorting order")
            builder.setNegativeButton("Cancel") { _, _ -> }

            dialog = builder.create()
            dialog.show()
        }*/

        //swipe_container.setProgressBackgroundColorSchemeColor(requireContext().colorFromAttribute(R.attr.darkBackground))


        binding.swipeContainer.apply {
            setColorSchemeColors(context.colorFromAttribute(R.attr.colorPrimary))
            setProgressBackgroundColorSchemeColor(context.colorFromAttribute(R.attr.primaryGrayBackground))
            setOnRefreshListener {
                if(isOnDownloads){
                    viewModel.refresh()
                    isRefreshing = false

                }
                else{
                    viewModel.refreshReadingProgress()
                }
            }
        }

        observe(viewModel.isRefreshing) { refreshing ->
            if(refreshing != binding.swipeContainer.isRefreshing){
                binding.swipeContainer.isRefreshing = refreshing
            }
        }


        lifecycleScope.launch{
            viewModel.refresh.collect { tab ->
                (binding.viewpager.adapter as? ViewpagerAdapter)?.updateProgressOfPage(tab)
            }
        }

        var canSwip = true
        binding.viewpager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val currentTab = getKey(DOWNLOAD_SETTINGS, CURRENT_TAB, null)?:1
                binding.swipeContainer.isRefreshing =  viewModel.activeRefreshTabs.contains(currentTab)
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                canSwip = state == ViewPager2.SCROLL_STATE_IDLE
            }
        })
        binding.swipeContainer.setOnChildScrollUpCallback { parent, child ->
            return@setOnChildScrollUpCallback  !canSwip// true = can't Swip, false = can swip
        }

        setupGridView()

        /*binding.downloadCardSpace.apply {
            itemAnimator?.changeDuration = 0
            val downloadAdapter = DownloadAdapter2(viewModel, this)
            downloadAdapter.setHasStableIds(true)
            adapter = downloadAdapter
            observe(viewModel.downloadCards) { cards ->
                // we need to copy here because otherwise diff wont work
                downloadAdapter.submitList(cards.map { it.copy() })
            }
        }

        binding.bookmarkCardSpace.apply {
            val bookmarkAdapter = CachedAdapter2(viewModel, this)
            adapter = bookmarkAdapter
            observe(viewModel.normalCards) { cards ->
                bookmarkAdapter.submitList(cards.map { it.copy() })
            }
        }*/

    }
}