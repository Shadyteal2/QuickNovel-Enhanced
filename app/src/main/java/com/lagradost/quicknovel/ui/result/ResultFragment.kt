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
import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_READ_AT
import com.lagradost.quicknovel.ui.download.REVERSE_CHAPTER_SORT
import com.lagradost.quicknovel.ui.download.REVERSE_LAST_ACCES_SORT
import com.lagradost.quicknovel.util.UIHelper.html
import com.lagradost.quicknovel.util.UIHelper.humanReadableByteCountSI
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.UIHelper.popupMenuCustom
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
        fun newInstance(url: String, apiName: String, startAction: Int = 0, startChapterUrl: String? = null): Bundle =
            Bundle().apply {
                putString("url", url)
                putString("apiName", apiName)
                putInt("startAction", startAction)
                putString("startChapterUrl", startChapterUrl)
            }
    }

    val repo get() = viewModel.repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = android.transition.TransitionInflater.from(requireContext()).inflateTransition(android.R.transition.move)
    }

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
        viewModel.reorderChapters()


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
        val bookmarkHeight = if (binding.resultBookmark.height > 0) binding.resultBookmark.height + 24.toPx else 80.toPx
        parameter.height = displayMetrics.heightPixels - bookmarkHeight - binding.resultTabs.height

        binding.resultViewpager.layoutParams = parameter
    }

    private fun updateTabData() {
        val loadResponse = viewModel.loadResponse.value
        if (loadResponse !is Resource.Success) return
        val res = loadResponse.value

        novelTabBinding?.apply {
            val state = viewModel.readState.value ?: ReadType.NONE
            resultUpdatesToggle.isVisible = state != ReadType.NONE
            val isEnabled = viewModel.isSyncEnabledDisplay.value ?: true
            resultUpdatesToggle.alpha = if (isEnabled) 1.0f else 0.4f

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
                            MainPageFragment.newInstance(currentApi.name, tag = tagIndex),
                            options = com.lagradost.quicknovel.MainActivity.navOptions
                        )
                    }
                }
                chip.setTextColor(requireContext().colorFromAttribute(R.attr.textColor))
                resultTag.addView(chip)
            }

            res.synopsis?.let { synopsis ->
                resultSynopsisText.text = synopsis.html()
                
                var isExpanded = false
                val toggleExpand = {
                    isExpanded = !isExpanded
                    resultSynopsisText.maxLines = if (isExpanded) Integer.MAX_VALUE else 4
                    resultSynopsisTapMore.isVisible = !isExpanded
                    resultSynopsisCollapseArrow.rotation = if (isExpanded) 180f else 0f
                    root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                }
                synopsisCard.setOnClickListener { toggleExpand() }
                resultSynopsisCollapseArrow.setOnClickListener { toggleExpand() }
            } ?: run {
                resultSynopsisText.text = "..."
                resultSynopsisTapMore.isVisible = false
                resultSynopsisCollapseArrow.isVisible = false
            }

            if (res is StreamResponse) {
                resultChaptersInfoHolder.isVisible = true
                resultChapters.text = res.data.size.toString()
                resultChaptersInfo.text = if (res.data.size == 1) getString(R.string.chapter) else getString(R.string.chapters)
            } else {
                resultChaptersInfoHolder.isVisible = false
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
                    resultLoading.stopShimmer()
                    resultLoadingError.isVisible = true
                    resultHolder.isVisible = false
                    resultErrorText.text = loadResponse.errorString
                    resultPosterBlur.isVisible = false
                }
            }

            is Resource.Loading -> {
                binding.apply {
                    resultLoading.isVisible = true
                    resultLoading.startShimmer()
                    resultLoadingError.isVisible = false
                    resultHolder.isVisible = false
                    resultPosterBlur.isVisible = false
                }
            }

            is Resource.Success -> {
                val res = loadResponse.value

                binding.apply {
                    resultPoster.setOnClickListener { view ->
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        
                        val scaleXDown = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 0.95f)
                        val scaleYDown = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 0.95f)
                        scaleXDown.duration = 100
                        scaleYDown.duration = 100

                        val scaleXUp = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1.0f)
                        val scaleYUp = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1.0f)
                        scaleXUp.duration = 100
                        scaleYUp.duration = 100

                        val animatorSet = android.animation.AnimatorSet()
                        animatorSet.play(scaleXDown).with(scaleYDown)
                        animatorSet.play(scaleXUp).with(scaleYUp).after(scaleXDown)
                        animatorSet.start()
                        
                        // Animation Delay to show bounce
                        view.postDelayed({
                            res.image?.let { img ->
                                UIHelper.showImage(view.context, img)
                            }
                        }, 200)
                    }

                    resultPoster.setImage(res.image)
                    resultPosterBlur.setImage(res.image, radius = 50, sample = 2)

                    resultTitle.text = res.name
                    resultAuthor.text = res.author ?: getString(R.string.no_author)
                    
                    resultStickyTitle.text = res.name
                    resultStickyAuthor.text = res.author ?: getString(R.string.no_author)
                    
                    val readStatusText = res.status?.resource?.let { getString(it) } ?: ""
                    resultStatus.text = readStatusText
                    resultStatus.isVisible = readStatusText.isNotBlank()

                    resultProviderChip.text = arguments?.getString("apiName") ?: ""
                    resultProviderChip.isVisible = true
                    
                    if (res is StreamResponse) {
                        val prefix = "Latest Chapter: "
                        val text = "$prefix${res.data.size}"
                        val spannable = android.text.SpannableString(text)
                        spannable.setSpan(
                            android.text.style.ForegroundColorSpan(requireContext().colorFromAttribute(R.attr.colorPrimary)),
                            0, prefix.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        resultTotalChapters.text = spannable
                        resultTotalChapters.isVisible = true

                        // Instant Continue text from local keys
                        val savedIndex = com.lagradost.quicknovel.BaseApplication.getKey<Int>(
                            com.lagradost.quicknovel.EPUB_CURRENT_POSITION, res.name
                        )
                        binding.resultContinueText.text = if (savedIndex != null) "Continue Chapter ${savedIndex + 1}" else "Start Reading"
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

                    resultLoadingError.isVisible = false
                    resultHolder.isVisible = true
                    resultPosterBlur.isVisible = true
                    
                    if (resultLoading.isVisible) {
                        resultLoading.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction {
                                resultLoading.isVisible = false
                                resultLoading.stopShimmer()
                                resultLoading.alpha = 1f
                            }.start()
                    }
                    
                    if (!resultHolder.isVisible || resultHolder.alpha < 1f) {
                        resultHolder.alpha = 0f
                        resultHolder.isVisible = true
                        resultHolder.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(300)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                            
                        resultPosterBlur.alpha = 0f
                        resultPosterBlur.scaleX = 1.05f
                        resultPosterBlur.scaleY = 1.05f
                        resultPosterBlur.animate()
                            .alpha(0.5f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(600)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                    }
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
        val startAction = savedInstanceState?.getInt("startAction") ?: arguments?.getInt("startAction") ?: 0
        val startChapterUrl = savedInstanceState?.getString("startChapterUrl") ?: arguments?.getString("startChapterUrl")

        binding.resultPoster.transitionName = url

        activity?.window?.decorView?.clearFocus()
        binding.resultTitle.isSelected = true

        binding.resultTitle.setOnClickListener { view ->
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Novel Title", binding.resultTitle.text)
            clipboard.setPrimaryClip(clip)
            com.lagradost.quicknovel.CommonActivity.showToast("Title copied to clipboard")
        }
        binding.resultAuthor.setOnClickListener { view ->
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Author Name", binding.resultAuthor.text)
            clipboard.setPrimaryClip(clip)
            com.lagradost.quicknovel.CommonActivity.showToast("Author copied to clipboard")
        }

        binding.resultSourceNotice.isVisible = apiName.contains("NovelFull", ignoreCase = true)

        if (viewModel.loadResponse.value == null)
            viewModel.initState(apiName, url)

        var hasTriggeredStart = false
        observe(viewModel.loadResponse) { res ->
            if (res is Resource.Success && !hasTriggeredStart && startAction == 2 && startChapterUrl != null) {
                hasTriggeredStart = true
                val stream = res.value as? StreamResponse
                val chapter = stream?.data?.find { it.url == startChapterUrl }
                if (chapter != null) {
                    viewModel.streamRead(chapter)
                }
            }
        }

        binding.apply {
            activity?.fixPaddingStatusbar(resultInfoHeader)

            if (apiName == "OceanOfPDF") {
                resultContinueReading.visibility = android.view.View.GONE
            }

            // Theme Adaptability adjustments
            val textColor = requireContext().colorFromAttribute(R.attr.textColor)
            // If text is dark (R+G+B < 400), it's Light Theme
            val isLightTheme = (android.graphics.Color.red(textColor) + android.graphics.Color.green(textColor) + android.graphics.Color.blue(textColor)) < 400
            
            val iconTint = if (isLightTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            
            val tintList = android.content.res.ColorStateList.valueOf(iconTint)
            resultBack.imageTintList = tintList
            resultOpeninbrower.imageTintList = tintList
            resultShare.imageTintList = tintList

            // #F2F2F2 for Light theme fallback absolute backdrops setups
            resultContinueReading.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(if (isLightTheme) "#F2F2F2" else "#22FFFFFF")))
            resultContinueReading.strokeColor = android.graphics.Color.parseColor(if (isLightTheme) "#E0E0E0" else "#11FFFFFF")
            
            resultProviderChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(if (isLightTheme) "#F2F2F2" else "#22FFFFFF"))
            resultProviderChip.setTextColor(if (isLightTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE)

            resultReloadConnectionerror.setOnClickListener { viewModel.initState(apiName, url) }
            resultOpeninbrower.setOnClickListener { viewModel.openInBrowser() }
            resultReloadConnectionOpenInBrowser.setOnClickListener { viewModel.openInBrowser() }


            resultBack.setOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }

            activity?.fixPaddingStatusbar(resultStickyHeader)

            resultContinueReading.setOnClickListener {
                val responseValue = (viewModel.loadResponse.value as? Resource.Success)?.value
                val streamResponse = responseValue as? StreamResponse
                val name = streamResponse?.name ?: ""
                
                val index = streamResponse?.data?.indexOfLast { ch ->
                    val idx = streamResponse.data.indexOf(ch)
                    val key = "$name/$idx"
                    com.lagradost.quicknovel.BaseApplication.getKey<Long>(com.lagradost.quicknovel.EPUB_CURRENT_POSITION_READ_AT, key) != null
                }
                
                if (index != null && index != -1 && index < streamResponse.data.size) {
                    viewModel.streamRead(streamResponse.data[index])
                } else {
                    viewModel.streamRead()
                }
            }

            resultShare.setOnClickListener { viewModel.share() }

            resultChaptersFab.setOnClickListener { view ->
                val act = activity ?: return@setOnClickListener
                val popup = android.widget.ListPopupWindow(act)
                popup.anchorView = view
                popup.isModal = true
                
                val items = listOf("Filter & Sort", "Go to Latest Chapter", "Go to Last Read")
                val adapter = android.widget.ArrayAdapter(
                    act,
                    android.R.layout.simple_list_item_1,
                    items
                )
                popup.setAdapter(adapter)
                
                popup.setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(act, R.drawable.spatial_glass_card))
                popup.width = 220.toPx // set appropriate width (e.g., 220dp)
                
                popup.setOnItemClickListener { _, _, position, _ ->
                    when (items[position]) {
                        "Filter & Sort" -> showFilterBottomSheet(act)
                        "Go to Latest Chapter" -> chaptersTabBinding?.chapterList?.let { list ->
                             val chapters = viewModel.chapters.value ?: return@setOnItemClickListener
                             if (chapters.isNotEmpty()) {
                                  val sortType = ResultViewModel.sortChapterBy
                                  val target = if (sortType == REVERSE_CHAPTER_SORT || sortType == REVERSE_LAST_ACCES_SORT) 0 else chapters.size - 1
                                  list.scrollToPosition(target)
                             }
                        }
                        "Go to Last Read" -> chaptersTabBinding?.chapterList?.let { list ->
                             val chapters = viewModel.chapters.value ?: return@setOnItemClickListener
                             val responseValue = (viewModel.loadResponse.value as? Resource.Success)?.value
                             val name = (responseValue as? StreamResponse)?.name ?: ""
                             val index = chapters.indexOfLast { ch ->
                                  val key = "$name/${chapters.indexOf(ch)}"
                                  BaseApplication.getKey<Long>(EPUB_CURRENT_POSITION_READ_AT, key) != null
                             }
                             if (index != -1) {
                                  list.scrollToPosition(index)
                             } else {
                                  com.lagradost.quicknovel.CommonActivity.showToast(act, "No last read position found")
                             }
                        }
                    }
                    popup.dismiss()
                }
                popup.show()
            }


            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(resultChaptersFab) { view, insets ->
                val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                val params = view.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
                val baseMargin = (16 * (context?.resources?.displayMetrics?.density ?: 1f)).toInt()
                params.bottomMargin = systemBars.bottom + baseMargin
                params.rightMargin = systemBars.right + baseMargin
                view.layoutParams = params
                insets
            }

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
                                if (chapterAdapter == null) {
                                    chapterAdapter = ChapterAdapter(viewModel)
                                }
                                if (chapterList.adapter != chapterAdapter) {
                                    chapterList.adapter = chapterAdapter
                                }
                                chapterList.setHasFixedSize(true) // Optimize fastscroll lag
                                
                                viewModel.chapters.value?.let { chapters ->
                                    if (chapters.size > 300) {
                                        chapterAdapter?.submitIncomparableList(chapters)
                                    } else {
                                        chapterAdapter?.submitList(chapters)
                                    }
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
                    
                    // Toggle parent Chapters FAB visibility
                    binding.resultChaptersFab.isVisible = position == 1
                }
            })

            resultBookmark.setOnClickListener { view ->
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                val context = view.context ?: return@setOnClickListener
                val json = com.lagradost.quicknovel.BaseApplication.getKey<String>(com.lagradost.quicknovel.DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", "[]") ?: "[]"
                val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                val customCats = try { mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<List<com.lagradost.quicknovel.ui.download.CategoryItem>>() {}) } catch(t: Throwable) { emptyList<com.lagradost.quicknovel.ui.download.CategoryItem>() }
                
                val orderJson = com.lagradost.quicknovel.BaseApplication.getKey<String>(com.lagradost.quicknovel.DOWNLOAD_SETTINGS, "CATEGORIES_ORDER", "[]") ?: "[]"
                val order = try { mapper.readValue(orderJson, object : com.fasterxml.jackson.core.type.TypeReference<List<Int>>() {}) } catch(t: Throwable) { emptyList<Int>() }

                val allCats = com.lagradost.quicknovel.ui.download.DownloadViewModel.systemCategories + customCats
                val sortedCats = if (order.isNotEmpty()) {
                    allCats.sortedBy { order.indexOf(it.id).takeIf { idx -> idx >= 0 } ?: Int.MAX_VALUE }
                } else {
                    allCats
                }

                val menuItems = mutableListOf<Pair<Int, Any>>()
                sortedCats.forEach { cat ->
                     menuItems.add(cat.id to (cat.stringRes ?: cat.name))
                }

                // Add Unbookmark option if already bookmarked
                val currentState = com.lagradost.quicknovel.BaseApplication.getKey<Int>(com.lagradost.quicknovel.RESULT_BOOKMARK_STATE, viewModel.id.value.toString()) ?: -1
                if (currentState != -1) {
                     menuItems.add(-1 to "Unbookmark")
                }

                val popup = android.widget.ListPopupWindow(context)
                popup.anchorView = view
                popup.isModal = true
                
                val adapter = object : android.widget.ArrayAdapter<Pair<Int, Any>>(
                    context, 
                    android.R.layout.simple_list_item_1, 
                    menuItems
                ) {
                    override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                        val v = super.getView(position, convertView, parent) as android.widget.TextView
                        val pair = menuItems[position]
                        val title = pair.second
                        if (title is Int) {
                            v.setText(title)
                        } else {
                            v.setText(title as CharSequence)
                        }
                        
                        if (pair.first == currentState) {
                             val checkIcon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_check_24)?.mutate()?.apply {
                                 val typedValue = android.util.TypedValue()
                                 context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                                 setTint(typedValue.data)
                             }
                            v.setCompoundDrawablesWithIntrinsicBounds(checkIcon, null, null, null)
                        } else {
                            v.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                        }
                        return v
                    }
                }
                popup.setAdapter(adapter)
                popup.setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.spatial_glass_card))
                popup.width = 200.toPx
                
                popup.setOnItemClickListener { _, _, position, _ ->
                    viewModel.bookmark(menuItems[position].first)
                    popup.dismiss()
                }
                popup.show()
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
            val context = context ?: return@observe
            val currentStateId = com.lagradost.quicknovel.BaseApplication.getKey<Int>(com.lagradost.quicknovel.RESULT_BOOKMARK_STATE, viewModel.id.value.toString()) ?: -1
            
            var title = getString(R.string.bookmark)
            var hasBookmark = false

            if (currentStateId != -1) {
                val systemCat = com.lagradost.quicknovel.ui.download.DownloadViewModel.systemCategories.find { it.id == currentStateId }
                if (systemCat != null) {
                    title = getString(systemCat.stringRes ?: R.string.bookmark)
                    hasBookmark = true
                } else {
                    val json = com.lagradost.quicknovel.BaseApplication.getKey<String>(com.lagradost.quicknovel.DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", "[]") ?: "[]"
                    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    val customCats = try { mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<List<com.lagradost.quicknovel.ui.download.CategoryItem>>() {}) } catch(t: Throwable) { emptyList() }
                    val customCat = customCats.find { it.id == currentStateId }
                    if (customCat != null) {
                        title = customCat.name
                        hasBookmark = true
                    }
                }
            }

            binding.resultBookmark.text = title
            binding.resultBookmark.setIconResource(
                if (!hasBookmark) R.drawable.ic_baseline_bookmark_border_24 else R.drawable.ic_baseline_bookmark_24
            )
            binding.resultBookmark.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
            novelTabBinding?.resultUpdatesToggle?.isVisible = hasBookmark

            // Update notes UI when read status changes
            novelTabBinding?.apply {
                val isDropped = state == ReadType.DROPPED
                resultNotesLayout.hint = if (isDropped) getString(R.string.dropped_reason) else getString(R.string.notes)
                val primaryColor = requireContext().colorFromAttribute(R.attr.colorPrimary)
                resultNotesLayout.boxStrokeColor = if (isDropped) Color.RED else primaryColor
                resultNotesLayout.setHintTextColor(android.content.res.ColorStateList.valueOf(if (isDropped) Color.RED else primaryColor))
            }
        }

        observe(viewModel.isSyncEnabledDisplay) { isEnabled ->
            novelTabBinding?.resultUpdatesToggle?.alpha = if (isEnabled) 1.0f else 0.4f
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
                
                // Update Continue text with guaranteed loaded data
                val name = streamResponse.name
                val lastReadIndex = chapters.indexOfLast { ch ->
                    val idx = viewModel.chapterIndex(ch) ?: -1
                    if (idx == -1) return@indexOfLast false
                    val key = "$name/$idx"
                    com.lagradost.quicknovel.BaseApplication.getKey<Long>(com.lagradost.quicknovel.EPUB_CURRENT_POSITION_READ_AT, key) != null
                }
                binding.resultContinueText.text = if (lastReadIndex != -1) "Continue Chapter ${lastReadIndex + 1}" else "Start Reading"

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
            // Scroll backdrop with layout to fix wallpaper disconnect
            binding.resultBackdropHolder.translationY = -scrollY.toFloat()
            
            // Removed reviewsFab alpha logic since it's gone
            
            val scrollFade = maxOf(0f, 1 - scrollY / 170.toPx.toFloat())
            binding.resultInfoHeader.apply {
                alpha = scrollFade
                scaleX = 0.95f + scrollFade * 0.05f
                scaleY = 0.95f + scrollFade * 0.05f
            }

            // resultBack is disabled from fading out to stay in the TopAppBar
            binding.resultBack.apply {
                alpha = 1f
                isEnabled = true
            }

            val stickyFade = minOf(1f, scrollY / 170.toPx.toFloat())
            binding.resultStickyTitle.alpha = stickyFade

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
            resultUpdatesToggle.setOnClickListener { 
                viewModel.toggleSyncEnabled() 
                val isCurrentlyEnabled = viewModel.isSyncEnabledDisplay.value ?: false
                com.lagradost.quicknovel.CommonActivity.showToast(if (!isCurrentlyEnabled) "Updates Enabled" else "Updates Disabled")
            }
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
                val apiName = arguments?.getString("apiName") ?: ""
                if (apiName == "OceanOfPDF") {
                    val res = (viewModel.loadResponse.value as? Resource.Success)?.value as? com.lagradost.quicknovel.EpubResponse
                    val link = res?.downloadLinks?.firstOrNull()
                    if (link != null) {
                        showOceanOfPDFDownloadDialog(link)
                    } else {
                        com.lagradost.quicknovel.CommonActivity.showToast("No download links found")
                    }
                    return@setOnClickListener
                }
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
    private fun showFilterBottomSheet(act: android.app.Activity) {
         val bottomSheetDialog = BottomSheetDialog(act)
         val filterBinding = com.lagradost.quicknovel.databinding.ChapterFilterPopupBinding.inflate(act.layoutInflater, null, false)
         bottomSheetDialog.setContentView(filterBinding.root)
         
         val filterTab = filterBinding.filterTabs.newTab().setText(getString(R.string.mainpage_filter))
         val sortTab = filterBinding.filterTabs.newTab().setText(getString(R.string.mainpage_sort_by_button_text))
         filterBinding.filterTabs.addTab(filterTab)
         filterBinding.filterTabs.addTab(sortTab)
         filterBinding.filterTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
             override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                 filterBinding.filterContent.isVisible = tab?.position == 0
                 filterBinding.sortContent.isVisible = tab?.position == 1
             }
             override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
             override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
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

    private fun showOceanOfPDFDownloadDialog(link: com.lagradost.quicknovel.DownloadLink) {
        val context = context ?: return
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(context)
        val frame = android.widget.FrameLayout(context)
        val webView = android.webkit.WebView(context)
        val progressBar = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
        
        webView.settings.apply {
             javaScriptEnabled = true
             domStorageEnabled = true
             databaseEnabled = true
             @Suppress("DEPRECATION")
             mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        progressBar.isIndeterminate = true
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        frame.addView(webView)
        frame.addView(progressBar, params)

        webView.webViewClient = object : android.webkit.WebViewClient() {
             override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                  super.onPageFinished(view, url)
                  progressBar.visibility = android.view.View.GONE
             }
        }
        
        webView.setDownloadListener { url, _, _, _, _ ->
             activity?.runOnUiThread {
                  com.lagradost.quicknovel.CommonActivity.showToast("Download started: $url")
                  try {
                       val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                       request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                       
                       val title = binding.resultTitle.text.toString().replace("[^a-zA-Z0-9]".toRegex(), "_")
                       val ext = if (url.contains(".pdf", ignoreCase = true) || url.contains("type=pdf", ignoreCase = true)) ".pdf" else ".epub"
                       request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "Epub/${title}${ext}")

                       val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                       val downloadId = downloadManager.enqueue(request)

                       val res = (viewModel.loadResponse.value as? com.lagradost.quicknovel.mvvm.Resource.Success)?.value as? com.lagradost.quicknovel.EpubResponse
                       if (res != null) {
                            val id = com.lagradost.quicknovel.BookDownloader2Helper.generateId(res.apiName, res.author, res.name)
                            val downloadData = com.lagradost.quicknovel.ui.download.DownloadFragment.DownloadData(
                                source = res.url,
                                name = res.name,
                                author = res.author ?: "",
                                posterUrl = res.posterUrl,
                                rating = res.rating,
                                peopleVoted = res.peopleVoted,
                                views = res.views,
                                synopsis = res.synopsis,
                                tags = res.tags,
                                apiName = res.apiName,
                                lastUpdated = System.currentTimeMillis(),
                                lastDownloaded = System.currentTimeMillis()
                            )
                            com.lagradost.quicknovel.BaseApplication.setKey(com.lagradost.quicknovel.DOWNLOAD_FOLDER, id.toString(), downloadData)
                            com.lagradost.quicknovel.BaseApplication.setKey(com.lagradost.quicknovel.DOWNLOAD_TOTAL, id.toString(), 1)
                            
                            // 1. Force state updates for 1/1 Completed in DownloadManager
                            com.lagradost.quicknovel.BookDownloader2.downloadProgress[id] = com.lagradost.quicknovel.DownloadProgressState(
                                 state = com.lagradost.quicknovel.DownloadState.IsDone,
                                 progress = 1,
                                 downloaded = 1,
                                 total = 1,
                                 lastUpdatedMs = System.currentTimeMillis(),
                                 etaMs = null
                            )
                            com.lagradost.quicknovel.BookDownloader2.downloadProgressChanged.invoke(id to com.lagradost.quicknovel.BookDownloader2.downloadProgress[id]!!)

                            // 2. Trigger memory list sync to DownloadViewModel
                            com.lagradost.quicknovel.BookDownloader2.downloadDataChanged.invoke(id to downloadData)
                       }
                  } catch (e: Exception) {
                      com.lagradost.quicknovel.CommonActivity.showToast("Download error")
                  }
                  dialog.dismiss()
             }
        }

        val postData = link.params.map { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")
        webView.postUrl(link.url, postData.toByteArray())

        dialog.setContentView(frame)
        dialog.show()
    }
}