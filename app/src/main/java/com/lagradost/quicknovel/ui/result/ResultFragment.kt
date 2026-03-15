package com.lagradost.quicknovel.ui.result

import android.animation.ObjectAnimator
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainActivity.Companion.navigate
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.databinding.*
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.mvvm.observeNullable
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.mainpage.MainAdapter
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import com.lagradost.quicknovel.util.SingleSelectionHelper.showBottomDialog
import com.lagradost.quicknovel.util.UIHelper
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.getStatusBarHeight
import com.lagradost.quicknovel.util.UIHelper.hideKeyboard
import com.lagradost.quicknovel.util.UIHelper.html
import com.lagradost.quicknovel.util.UIHelper.humanReadableByteCountSI
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx

const val MAX_SYNO_LENGH = 300

class ResultFragment : Fragment() {
    lateinit var binding: FragmentResultBinding
    private val viewModel: ResultViewModel by viewModels()

    private var novelTabBinding: ResultNovelTabBinding? = null
    private var chaptersTabBinding: ResultChaptersTabBinding? = null

    private var chapterAdapter: ChapterAdapter? = null

    companion object {
        fun newInstance(url: String, apiName: String, startAction: Int = 0): Bundle =
            Bundle().apply {
                putString("url", url)
                putString("apiName", apiName)
                putInt("startAction", startAction)
            }
    }

    val repo get() = viewModel.repo

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentResultBinding.inflate(inflater)
        return binding.root
    }

    private fun setupGridView() {
        // Only for recommendations which are removed now, keeping it empty for potential future use or removing if fully unused
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.resultHolder.post {
            updateScrollHeight()
        }
    }

    override fun onResume() {
        super.onResume()
        if(viewModel.isResume){
            chapterAdapter?.notifyDataSetChanged()
            viewModel.isResume = false
        }

        activity?.apply {
            window?.navigationBarColor =
                colorFromAttribute(R.attr.primaryBlackBackground)
        }

        val savedNote = viewModel.getNote() ?: ""
        novelTabBinding?.resultNotesEdittext?.let { et ->
            if (et.text?.toString() != savedNote) {
                et.setText(savedNote)
            }
        }
    }

    private fun updateScrollHeight() {
        val displayMetrics = resources.displayMetrics
        val parameter = binding.resultViewpager.layoutParams
        parameter.height =
            displayMetrics.heightPixels - binding.viewsAndRating.height - binding.resultTabs.height - binding.resultScrollPadding.paddingTop

        binding.resultViewpager.layoutParams = parameter
    }

    private fun updateTabData() {
        val loadResponse = viewModel.loadResponse.value
        if (loadResponse !is Resource.Success) return
        val res = loadResponse.value

        novelTabBinding?.apply {
            downloadWarning.isVisible = (repo?.rateLimitTime ?: 0) > 2000

            resultRatingVotedCount.text = getString(R.string.no_data)
            res.rating?.let { rating ->
                resultRating.text = context?.getRating(rating)
                val votes = res.peopleVoted
                if (votes != null) {
                    resultRatingVotedCount.text = getString(R.string.votes_format).format(votes)
                }
            }
            resultViews.text = res.views?.let { views -> humanReadableByteCountSI(views) }
                ?: getString(R.string.no_data)

            resultTag.removeAllViews()
            res.tags?.forEach { tag ->
                val chip = Chip(requireContext())
                val chipDrawable = ChipDrawable.createFromAttributes(requireContext(), null, 0, R.style.ChipFilled)
                chip.setChipDrawable(chipDrawable)
                chip.text = tag
                chip.isClickable = false
                
                val tagIndex = repo?.tags?.indexOfFirst { it.first == tag }?.takeIf { it != -1 }
                if (tagIndex != null) {
                    chip.isClickable = true
                    chip.setOnClickListener {
                        val currentApi = repo ?: return@setOnClickListener
                        activity?.navigate(
                            R.id.global_to_navigation_mainpage,
                            MainPageFragment.newInstance(currentApi.name, tag = tagIndex)
                        )
                    }
                }
                chip.setTextColor(requireContext().colorFromAttribute(R.attr.textColor))
                resultTag.addView(chip)
            }

            res.synopsis?.let { synopsis ->
                val syno = if (synopsis.length > MAX_SYNO_LENGH) {
                    synopsis.substring(0, MAX_SYNO_LENGH) + "..."
                } else {
                    synopsis
                }
                resultSynopsisText.text = syno.html()
            } ?: run {
                resultSynopsisText.text = "..."
            }

            if (res is StreamResponse) {
                resultChaptersInfoHolder.isVisible = true
                resultChapters.text = res.data.size.toString()
                resultChaptersInfo.text = if (res.data.size == 1) getString(R.string.chapter) else getString(R.string.chapters)
                resultQuickstream.isVisible = true
            } else {
                resultChaptersInfoHolder.isVisible = false
                resultQuickstream.isVisible = false
            }

            // Notes text restoration managed by onResume and LiveData observer
        }
        
        // Populate chapter count in its tab if available
        chaptersTabBinding?.apply {
           // If we need extra population logic here
        }
    }

    private fun newState(loadResponse: Resource<LoadResponse>?) {
        if (loadResponse == null) return

        when (loadResponse) {
            is Resource.Failure -> {
                binding.apply {
                    resultLoading.isVisible = false
                    resultLoadingError.isVisible = true
                    resultHolder.isVisible = false
                    resultErrorText.text = loadResponse.errorString
                    resultPosterBlur.isVisible = false
                }
            }

            is Resource.Loading -> {
                binding.apply {
                    resultLoading.isVisible = true
                    resultLoadingError.isVisible = false
                    resultHolder.isVisible = false
                    resultPosterBlur.isVisible = false
                }
            }

            is Resource.Success -> {
                val res = loadResponse.value

                binding.apply {
                    res.image?.let { img ->
                        resultEmptyView.setOnClickListener {
                            UIHelper.showImage(it.context, img)
                        }
                    }

                    resultPoster.setImage(res.image)
                    resultPosterBlur.setImage(res.image, radius = 100, sample = 3)

                    resultTitle.text = res.name
                    resultAuthor.text = res.author ?: getString(R.string.no_author)
                    
                    resultStickyTitle.text = res.name
                    resultStickyAuthor.text = res.author ?: getString(R.string.no_author)
                    
                    val readStatusText = res.status?.resource?.let { getString(it) } ?: ""
                    resultStatus.text = readStatusText
                    resultStatus.isVisible = readStatusText.isNotBlank()
                    
                    if (res is StreamResponse) {
                        resultTotalChapters.text = res.data.size.toString()
                        resultTotalChapters.isVisible = true
                    } else {
                        resultTotalChapters.isVisible = false
                    }

                    resultBack.setColorFilter(Color.WHITE)
                    
                    updateTabData()

                    val target = viewModel.currentTabIndex.value
                    if (target != null) {
                        resultTabs.getTabAt(target)?.select()
                        resultViewpager.setCurrentItem(target, false)
                    }

                    resultLoading.isVisible = false
                    resultLoadingError.isVisible = false
                    resultHolder.isVisible = true
                    resultPosterBlur.isVisible = true
                    resultHolder.post { updateScrollHeight() }
                }
            }
        }
    }

    private fun doAction(action: Int) {
        when (action) {
            R.string.resume -> viewModel.download()
            R.string.download -> viewModel.downloadFrom(null)
            R.string.re_downloaded -> viewModel.download()
            R.string.download_from_chapter -> {
                val chapters = ((viewModel.loadResponse.value as? Resource.Success)?.value as? StreamResponse)?.data ?: return
                val act = CommonActivity.activity ?: return
                val builder: AlertDialog.Builder = AlertDialog.Builder(act, R.style.AlertDialogCustom)
                val diagBinding = ChapterDialogBinding.inflate(layoutInflater, null, false)
                val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        viewModel.downloadFrom(diagBinding.chapterEdit.text?.toString()?.toIntOrNull())
                    }
                }
                builder.setView(diagBinding.root)
                    .setTitle(R.string.download_from_chapter)
                    .setPositiveButton(R.string.download, dialogClickListener)
                    .setNegativeButton(R.string.cancel, dialogClickListener)
                    .show()
                diagBinding.chapterEdit.doOnTextChanged { text, _, _, _ ->
                    val parsedInt = text?.toString()?.toIntOrNull()
                    if (parsedInt == null || parsedInt < 0 || parsedInt >= chapters.size) {
                        diagBinding.chapterEdit.error = act.getString(R.string.error_outside_chapter)
                    } else {
                        diagBinding.chapterEdit.error = null
                    }
                }
            }
            R.string.delete -> viewModel.deleteAlert()
            R.string.pause -> viewModel.pause()
            R.string.stop -> viewModel.stop()
        }
    }

    private fun getActions(): List<Int>? {
        val items = mutableListOf<Int>()
        val progressState = viewModel.downloadState.value ?: return null
        val canDownload = progressState.progress < progressState.total
        val canPartialDownload = progressState.downloaded < progressState.total && progressState.total > 1

        when (progressState.state) {
            DownloadState.IsPaused -> {
                items.add(R.string.resume)
                items.add(R.string.stop)
            }
            DownloadState.IsDownloading -> {
                items.add(R.string.pause)
                items.add(R.string.stop)
            }
            DownloadState.IsDone -> {
                if (canPartialDownload) {
                    items.add(R.string.download_from_chapter)
                }
            }
            DownloadState.IsPending -> {}
            DownloadState.IsFailed, DownloadState.IsStopped -> {
                if (canDownload) items.add(R.string.re_downloaded)
                if (canPartialDownload) items.add(R.string.download_from_chapter)
            }
            DownloadState.Nothing -> {
                if (canDownload) items.add(R.string.download)
                if (canPartialDownload) items.add(R.string.download_from_chapter)
            }
        }
        if (progressState.progress > 0) items.add(R.string.delete)
        return items
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val url = savedInstanceState?.getString("url") ?: arguments?.getString("url") ?: throw NotImplementedError()
        val apiName = savedInstanceState?.getString("apiName") ?: arguments?.getString("apiName") ?: throw NotImplementedError()

        activity?.window?.decorView?.clearFocus()
        binding.resultTitle.isSelected = true

        binding.resultSourceNotice.isVisible = apiName.contains("NovelFull", ignoreCase = true)

        if (viewModel.loadResponse.value == null)
            viewModel.initState(apiName, url)

        binding.apply {
            activity?.fixPaddingStatusbar(resultInfoHeader)

            resultReloadConnectionerror.setOnClickListener { viewModel.initState(apiName, url) }
            resultOpeninbrower.setOnClickListener { viewModel.openInBrowser() }
            resultReloadConnectionOpenInBrowser.setOnClickListener { viewModel.openInBrowser() }

            val backParameter = resultBack.layoutParams as CoordinatorLayout.LayoutParams
            backParameter.setMargins(
                backParameter.leftMargin,
                backParameter.topMargin + (activity?.getStatusBarHeight() ?: 0),
                backParameter.rightMargin,
                backParameter.bottomMargin
            )
            resultBack.layoutParams = backParameter
            resultBack.setOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }

            activity?.fixPaddingStatusbar(resultStickyHeader)

            val parameter = resultEmptyView.layoutParams as LinearLayout.LayoutParams
            parameter.setMargins(
                parameter.leftMargin,
                parameter.topMargin + (activity?.getStatusBarHeight() ?: 0),
                parameter.rightMargin,
                parameter.bottomMargin
            )
            resultEmptyView.layoutParams = parameter

            resultShare.setOnClickListener { viewModel.share() }

            // Using indices 0 (Novel) and 3 (Chapters) for tabIds to maintain existing mapping but filter to only two tabs
            resultViewpager.adapter = ResultFragmentAdapter(
                tabIds = listOf(0, 3),
                bindingGetter = { tabView, tabId ->
                    when (tabId) {
                        0 -> {
                            onBindingCreated(tabView)
                            updateTabData()
                        }
                        3 -> {
                            chaptersTabBinding = ResultChaptersTabBinding.bind(tabView)
                            chaptersTabBinding?.apply {
                                val adapter = ChapterAdapter(viewModel)
                                chapterAdapter = adapter
                                chapterList.adapter = adapter
                                
                                viewModel.chapters.value?.let { chapters ->
                                    if (chapters.size > 300) {
                                        adapter.submitIncomparableList(chapters)
                                    } else {
                                        adapter.submitList(chapters)
                                    }
                                }
                                chaptersFab.setOnClickListener {
                                    val act = activity ?: return@setOnClickListener
                                    val bottomSheetDialog = BottomSheetDialog(act)
                                    val filterBinding = ChapterFilterPopupBinding.inflate(act.layoutInflater, null, false)
                                    bottomSheetDialog.setContentView(filterBinding.root)
                                    
                                    val filterTab = filterBinding.filterTabs.newTab().setText(getString(R.string.mainpage_filter))
                                    val sortTab = filterBinding.filterTabs.newTab().setText(getString(R.string.mainpage_sort_by_button_text))
                                    filterBinding.filterTabs.addTab(filterTab)
                                    filterBinding.filterTabs.addTab(sortTab)
                                    filterBinding.filterTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                                        override fun onTabSelected(tab: TabLayout.Tab?) {
                                            filterBinding.filterContent.isVisible = tab?.position == 0
                                            filterBinding.sortContent.isVisible = tab?.position == 1
                                        }
                                        override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
                                        override fun onTabReselected(tab: TabLayout.Tab?) = Unit
                                    })
                                    
                                    filterBinding.filterBookmarked.isChecked = ResultViewModel.filterChapterByBookmarked
                                    filterBinding.filterRead.isChecked = ResultViewModel.filterChapterByRead
                                    filterBinding.filterUnread.isChecked = ResultViewModel.filterChapterByUnread
                                    filterBinding.filterDownloaded.isChecked = ResultViewModel.filterChapterByDownloads
                                    
                                    filterBinding.filterBookmarked.setOnCheckedChangeListener { _, isChecked ->
                                        ResultViewModel.filterChapterByBookmarked = isChecked
                                        viewModel.reorderChapters()
                                    }
                                    filterBinding.filterRead.setOnCheckedChangeListener { _, isChecked ->
                                        ResultViewModel.filterChapterByRead = isChecked
                                        viewModel.reorderChapters()
                                    }
                                    filterBinding.filterUnread.setOnCheckedChangeListener { _, isChecked ->
                                        ResultViewModel.filterChapterByUnread = isChecked
                                        viewModel.reorderChapters()
                                    }
                                    filterBinding.filterDownloaded.setOnCheckedChangeListener { _, isChecked ->
                                        ResultViewModel.filterChapterByDownloads = isChecked
                                        viewModel.reorderChapters()
                                    }
                                    bottomSheetDialog.show()
                                }
                            }
                            updateTabData()
                        }
                    }
                }
            )

            TabLayoutMediator(resultTabs, resultViewpager) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.novel)
                    1 -> getString(R.string.read_action_chapters)
                    else -> ""
                }
            }.attach()

            resultViewpager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    // Map positions: 0 -> 0 (Novel), 1 -> 3 (Chapters)
                    val tabId = if (position == 0) 0 else 3
                    viewModel.switchTab(position, tabId)
                }
            })

            resultBookmark.setOnClickListener { view ->
                val context = view.context ?: return@setOnClickListener
                context.showBottomDialog(
                    ReadType.entries.map { context.getString(it.stringRes) },
                    selectedIndex = ReadType.entries.map { it.prefValue }
                        .indexOf(viewModel.readState.value?.prefValue),
                    context.getString(R.string.bookmark), false, {}
                ) { selected ->
                    viewModel.bookmark(ReadType.entries[selected].prefValue)
                }
            }
        }

        observe(viewModel.loadResponse) { newState(it) }

        observe(viewModel.currentTabIndex) { pos ->
            binding.resultViewpager.setCurrentItem(pos, true)
        }

        observe(viewModel.currentTabPosition) { pos ->
            if (binding.resultTabs.selectedTabPosition != pos) {
                binding.resultTabs.selectTab(binding.resultTabs.getTabAt(pos))
            }
        }

        observe(viewModel.readState) { state ->
            val stringRes = if (state == ReadType.NONE) R.string.bookmark else state.stringRes
            binding.resultBookmark.text = getString(stringRes)
            binding.resultBookmark.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, 0,
                if (state == ReadType.NONE) R.drawable.ic_baseline_bookmark_border_24 else R.drawable.ic_baseline_bookmark_24
            )

            // Update notes UI when read status changes
            novelTabBinding?.apply {
                val isDropped = state == ReadType.DROPPED
                resultNotesHeader.text = if (isDropped) getString(R.string.dropped_reason) else getString(R.string.notes)
                val accentColor = context?.colorFromAttribute(R.attr.colorPrimary) ?: Color.DKGRAY
                resultNotesHeader.setTextColor(if (isDropped) Color.RED else accentColor)
                resultNotesUnderline.setBackgroundColor(if (isDropped) Color.RED else context?.colorFromAttribute(R.attr.textColor) ?: Color.BLACK)
            }
        }

        observeNullable(viewModel.userNote) { note ->
            novelTabBinding?.apply {
                val current = resultNotesEdittext.text?.toString() ?: ""
                val saved = note ?: ""
                if (current != saved) {
                    resultNotesEdittext.setText(saved)
                }
            }
        }

        observeNullable(viewModel.chapters) { chapters ->
            chapterAdapter?.let { adapter ->
                if (chapters == null || chapters.size > 300) {
                    adapter.submitIncomparableList(chapters)
                } else {
                    adapter.submitList(chapters)
                }
            }

            val streamResponse = (viewModel.loadResponse.value as? Resource.Success)?.value as? StreamResponse
            if (streamResponse != null && !chapters.isNullOrEmpty()) {
                val total = chapters.size
                val readCount = chapters.count { viewModel.hasReadChapter(it) }
                novelTabBinding?.apply {
                    if (readCount > 0) {
                        resultProgressLayout.isVisible = true
                        val progress = (readCount * 100) / total
                        resultProgressBar.progress = progress
                        resultProgressText.text = getString(R.string.latest_format).format("$progress% Read ($readCount/$total)")
                    } else {
                        resultProgressLayout.isVisible = false
                    }
                }
            }
        }

        observe(viewModel.downloadState) { progressState ->
            if (progressState == null) return@observe
            
            novelTabBinding?.apply {
                resultDownloadProgressText.text = "${progressState.progress}/${progressState.total}"

                resultDownloadProgressBarNotDownloaded.apply {
                    max = progressState.total.toInt() * 100
                    val animation: ObjectAnimator = ObjectAnimator.ofInt(
                        this, "progress", this.progress,
                        (progressState.progress - progressState.downloaded).toInt() * 100
                    )
                    animation.duration = 500
                    animation.setAutoCancel(true)
                    animation.interpolator = DecelerateInterpolator()
                    animation.start()
                }

                resultDownloadProgressBar.apply {
                    max = progressState.total.toInt() * 100
                    val animation: ObjectAnimator = ObjectAnimator.ofInt(
                        this, "progress", this.progress,
                        progressState.progress.toInt() * 100
                    )
                    animation.duration = 500
                    animation.setAutoCancel(true)
                    animation.interpolator = DecelerateInterpolator()
                    animation.start()
                }

                val ePubGeneration = progressState.progress > 0
                resultDownloadGenerateEpub.apply {
                    isClickable = ePubGeneration
                    alpha = if (ePubGeneration) 1f else 0.5f
                }

                val canDownload = progressState.progress < progressState.total
                val canClick = progressState.total > 0
                resultDownloadBtt.apply {
                    isClickable = canClick
                    alpha = if (canClick) 1f else 0.5f

                    setText(
                        when (progressState.state) {
                            DownloadState.IsDone -> R.string.manage
                            DownloadState.IsDownloading -> R.string.pause
                            DownloadState.IsPaused -> R.string.resume
                            DownloadState.IsFailed -> R.string.re_downloaded
                            DownloadState.IsStopped -> R.string.downloaded
                            DownloadState.Nothing -> if (canDownload) R.string.download else R.string.manage
                            DownloadState.IsPending -> R.string.loading
                        }
                    )
                    setIconResource(
                        when (progressState.state) {
                            DownloadState.IsDownloading -> R.drawable.ic_baseline_pause_24
                            DownloadState.IsPaused -> R.drawable.netflix_play
                            DownloadState.IsFailed -> R.drawable.ic_baseline_autorenew_24
                            DownloadState.IsDone -> R.drawable.ic_outline_settings_24
                            DownloadState.Nothing -> if (canDownload) R.drawable.netflix_download else R.drawable.ic_outline_settings_24
                            else -> R.drawable.netflix_download
                        }
                    )
                }
            }
        }

        binding.resultMainscroll.setOnScrollChangeListener { v: NestedScrollView, _, scrollY, _, oldScrollY ->
            // Removed reviewsFab alpha logic since it's gone
            
            val scrollFade = maxOf(0f, 1 - scrollY / 170.toPx.toFloat())
            binding.resultInfoHeader.apply {
                alpha = scrollFade
                scaleX = 0.95f + scrollFade * 0.05f
                scaleY = 0.95f + scrollFade * 0.05f
            }

            val crossFade = maxOf(0f, 1 - scrollY / 140.toPx.toFloat())
            binding.resultBack.apply {
                alpha = crossFade
                isEnabled = crossFade > 0
            }

            val stickyFade = minOf(1f, scrollY / 170.toPx.toFloat())
            binding.resultStickyHeader.alpha = stickyFade

            /*val dy = scrollY - oldScrollY
            if (dy > 0) {
                val max = (v.getChildAt(0).measuredHeight - v.measuredHeight)
                if (scrollY >= max) {
                    viewModel.loadMoreReviews()
                }
            }*/
        }
    }


    private fun onBindingCreated(tabView: View) {
        val binding = ResultNovelTabBinding.bind(tabView)
        novelTabBinding = binding
        
        binding.apply {
            resultSynopsisText.setOnClickListener {
                val res = (viewModel.loadResponse.value as? Resource.Success)?.value ?: return@setOnClickListener
                val syno = if (res.synopsis?.length ?: 0 > MAX_SYNO_LENGH) {
                    res.synopsis?.substring(0, MAX_SYNO_LENGH) + "..."
                } else {
                    res.synopsis
                }
                val isExpanded = resultSynopsisText.text.length > (syno?.length ?: 0)
                resultSynopsisText.text = if (!isExpanded) res.synopsis?.html() else syno?.html()
            }
            resultDownloadGenerateEpub.setOnClickListener { viewModel.readEpub() }
            resultDownloadBtt.setOnClickListener { v ->
                val actions = getActions()
                if (actions == null) {
                    viewModel.downloadOrPause()
                } else if (actions.size == 1) {
                    doAction(actions[0])
                } else if (actions.contains(R.string.download) || actions.contains(R.string.pause)) {
                    viewModel.downloadOrPause()
                } else {
                    v.popupMenu(actions.map { it to it }, null) { doAction(itemId) }
                }
            }
            resultDownloadBtt.setOnLongClickListener { v ->
                val items = getActions() ?: return@setOnLongClickListener true
                v.popupMenu(items.map { it to it }, null) { doAction(itemId) }
                true
            }
            resultQuickstream.setOnClickListener { viewModel.streamRead() }

            // Initial Notes Population
            val currentNote = resultNotesEdittext.text?.toString() ?: ""
            val savedNote = viewModel.getNote() ?: ""
            if (currentNote != savedNote) {
                resultNotesEdittext.setText(savedNote)
            }

            // Setup Notes Update Trigger
            resultNotesEdittext.doOnTextChanged { text, _, _, _ ->
                if (viewModel.hasLoaded) {
                    viewModel.updateNote(text?.toString())
                }
            }

            // Keyboard Scrolling Focus Listener
            resultNotesEdittext.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    this@ResultFragment.binding.resultMainscroll.postDelayed({
                        this@ResultFragment.binding.resultMainscroll.smoothScrollTo(0, resultNotesLayout.top)
                    }, 200)
                }
            }
        }
    }
}