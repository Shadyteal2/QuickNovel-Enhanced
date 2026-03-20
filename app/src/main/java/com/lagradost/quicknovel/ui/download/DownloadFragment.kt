package com.lagradost.quicknovel.ui.download

import android.R.attr.fragment
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.launch
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
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
import com.lagradost.quicknovel.util.toPx
import android.graphics.Rect
import android.widget.FrameLayout

class DownloadFragment : Fragment() {
    private lateinit var viewModel: DownloadViewModel
    lateinit var binding: FragmentDownloadsBinding
    private var tabsMediator: TabLayoutMediator? = null



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

        var initialPillMargin = -1
        var initialFabMargin = -1

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomInset = insets.bottom

            if (initialPillMargin == -1) {
                initialPillMargin = (binding.libraryPillMenu.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 110.toPx
            }
            if (initialFabMargin == -1) {
                initialFabMargin = (binding.downloadFabContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 180.toPx
            }

            val threshold = 24.toPx
            val extraMargin = if (bottomInset > threshold) bottomInset else 0

            binding.libraryPillMenu.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = initialPillMargin + extraMargin
            }
            binding.downloadFabContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = initialFabMargin + extraMargin
            }

            windowInsets
        }


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

        binding.updatesButton.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            val navHostFragment = activity?.supportFragmentManager?.findFragmentById(R.id.nav_host_fragment) as? androidx.navigation.fragment.NavHostFragment
            navHostFragment?.navController?.navigate(R.id.navigation_updates)
        }

        binding.editCategories.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            showCategoriesManager()
        }


        val adapter = ViewpagerAdapter(viewModel, this) { isScrollingDown ->
            binding.downloadFabText.isVisible = !isScrollingDown
        }

        observe(viewModel.pages) { pages ->
            if (pages == null) return@observe
            adapter.submitList(pages)

            val tabsList = mutableListOf(context?.getString(R.string.tab_downloads) ?: "Downloads")
            for (read in viewModel.readList) {
                tabsList.add(if (read.isSystem && read.stringRes != null) context?.getString(read.stringRes) ?: read.name else read.name)
            }

            tabsMediator?.detach()
            tabsMediator = TabLayoutMediator(binding.bookmarkTabs, binding.viewpager) { tab, position ->
                if (position < tabsList.size) {
                    tab.text = tabsList[position]
                }
            }
            tabsMediator?.attach()

            // Dynamic Dots Generation
            val dotsHolder = binding.pillDotsHolder
            dotsHolder.removeAllViews()
            val density = context?.resources?.displayMetrics?.density ?: 1f
            val dotSize = (4 * density).toInt()
            
            for (i in 0 until tabsList.size) {
                 val ctx = context ?: return@observe
                 val frame = android.widget.FrameLayout(ctx).apply {
                     layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
                 }
                 val dot = android.view.View(ctx).apply {
                     layoutParams = android.widget.FrameLayout.LayoutParams(dotSize, dotSize, android.view.Gravity.CENTER)
                     background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.dot_bg_grey)
                     alpha = if (i == binding.viewpager.currentItem) 1.0f else 0.4f
                     scaleX = if (i == binding.viewpager.currentItem) 1.25f else 1.0f
                     scaleY = if (i == binding.viewpager.currentItem) 1.25f else 1.0f
                 }
                 frame.addView(dot)
                 frame.setOnClickListener {
                     binding.viewpager.currentItem = i
                     it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                 }
                 dotsHolder.addView(frame)
            }

            viewModel.currentTab.value?.let {
                if (it != binding.viewpager.currentItem) {
                    binding.viewpager.setCurrentItem(it, false)
                }
            }
        }

        binding.viewpager.adapter = adapter
        binding.viewpager.isUserInputEnabled = true // Enable smooth swiping
        binding.viewpager.offscreenPageLimit = 1 // Smooth load adjacent sections
        
        val dotsHolder = binding.pillDotsHolder
        var initialTouchX = 0f
        var initialPillX = 0f
        var currentSelectedIndex = 0
        var isDragging = false

        binding.travelerIcon.setOnTouchListener { view, event ->
            val maxOffset = binding.libraryPillMenu.width - view.width
            val totalCats = viewModel.readList.size
            val stepSize = if (maxOffset > 0 && totalCats > 0) maxOffset.toFloat() / totalCats.toFloat() else 0f

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
                        val index = (newX / stepSize).coerceIn(0f, totalCats.toFloat()).toInt()
                        if (index != currentSelectedIndex) {
                            currentSelectedIndex = index
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            binding.viewpager.setCurrentItem(index, true) 
                            
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
                            interpolator = android.view.animation.DecelerateInterpolator()
                            start()
                        }
                        binding.viewpager.setCurrentItem(currentSelectedIndex, true) // Smooth switch
                    }
                    true
                }
                else -> false
            }
        }

        binding.viewpager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                if (isDragging) return // Don't override user drag offset
                val maxOffset = binding.libraryPillMenu.width - binding.travelerIcon.width
                val totalCats = viewModel.readList.size
                if (maxOffset > 0 && totalCats > 0) {
                    val stepSize = maxOffset.toFloat() / totalCats.toFloat()
                    val targetX = (position + positionOffset) * stepSize
                    binding.travelerIcon.translationX = targetX
                }
            }

            override fun onPageSelected(position: Int) {
                if (isDragging) return // Don't override user drag offset
                
                currentSelectedIndex = position
                // Highlight dots updated continuously in continuous callback if desired, or here!
                // But dot updates are fast and simple!

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

    private fun showCategoriesManager() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_categories_manager, null)
        dialog.setContentView(view)

        val list = view.findViewById<RecyclerView>(R.id.categories_list)
        val addInput = view.findViewById<EditText>(R.id.add_category_input)
        val addBtn = view.findViewById<ImageView>(R.id.add_category_btn)

        var items = viewModel.readList.toMutableList()

        class CategoryAdapter(
            val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
            val onDelete: (CategoryItem) -> Unit,
            val onRename: (CategoryItem, String) -> Unit
        ) : androidx.recyclerview.widget.ListAdapter<CategoryItem, CategoryAdapter.VH>(
            object : androidx.recyclerview.widget.DiffUtil.ItemCallback<CategoryItem>() {
                override fun areItemsTheSame(a: CategoryItem, b: CategoryItem) = a.id == b.id
                override fun areContentsTheSame(a: CategoryItem, b: CategoryItem) = a == b
            }
        ) {
            inner class VH(val view: View) : RecyclerView.ViewHolder(view)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category_manager, parent, false)
                return VH(v)
            }

            override fun onBindViewHolder(holder: VH, @SuppressLint("RecyclerView") position: Int) {
                val item = getItem(position)
                val name = holder.view.findViewById<TextView>(R.id.category_name)
                val drag = holder.view.findViewById<ImageView>(R.id.category_drag_handle)
                val rename = holder.view.findViewById<ImageView>(R.id.category_rename)
                val delete = holder.view.findViewById<ImageView>(R.id.category_delete)

                name.text = if (item.isSystem && item.stringRes != null) holder.view.context.getString(item.stringRes) else item.name
                delete.isVisible = !item.isSystem
                rename.isVisible = !item.isSystem

                drag.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                        onStartDrag(holder)
                    }
                    false
                }

                rename.setOnClickListener {
                    val builder = android.app.AlertDialog.Builder(holder.view.context)
                    val input = EditText(holder.view.context)
                    input.setText(item.name)
                    builder.setTitle("Rename Category")
                        .setView(input)
                        .setPositiveButton("OK") { _: android.content.DialogInterface, _: Int ->
                            val n = input.text.toString().trim()
                            if (n.isNotEmpty()) {
                                onRename(item, n)
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }

                delete.setOnClickListener {
                    onDelete(item)
                }
            }
        }

        val adapter = CategoryAdapter(
            onStartDrag = { holder ->
                // Handled implicitly by ItemTouchHelper simple drag setup below!
            },
            onDelete = { item ->
                viewModel.deleteCategory(item.id)
                items = viewModel.readList.toMutableList()
                (list.adapter as? CategoryAdapter)?.submitList(items)
                
                // Refresh tabs titles manually
                val adapterVp = binding.viewpager.adapter as? ViewpagerAdapter
                viewModel.loadAllData(false)
            },
            onRename = { item, newName ->
                viewModel.renameCategory(item.id, newName)
                items = viewModel.readList.toMutableList()
                (list.adapter as? CategoryAdapter)?.submitList(items)
            }
        )

        list.adapter = adapter
        adapter.submitList(items)

        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(r: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from >= 0 && to >= 0 && from < items.size && to < items.size) {
                    java.util.Collections.swap(items, from, to)
                    adapter.notifyItemMoved(from, to)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewModel.updateCategories(items) // Save on let go
            }
        }
        val touchHelper = androidx.recyclerview.widget.ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(list)

        addBtn.setOnClickListener {
            val name = addInput.text.toString().trim()
            if (name.isNotEmpty()) {
                val customCount = viewModel.readList.filter { !it.isSystem }.size
                if (customCount >= 5) {
                    android.widget.Toast.makeText(requireContext(), "Max 5 custom categories allowed", android.widget.Toast.LENGTH_SHORT).show()
                } else if (viewModel.readList.size >= 10) {
                     android.widget.Toast.makeText(requireContext(), "Max 10 total categories allowed", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addCategory(name)
                    items = viewModel.readList.toMutableList()
                    adapter.submitList(items)
                    addInput.setText("")
                }
            }
        }

        dialog.show()
    }
}