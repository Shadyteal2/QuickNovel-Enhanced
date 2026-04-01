package com.lagradost.quicknovel.ui.download

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.BookDownloader2.preloadPartialImportedPdf
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.DOWNLOAD_EPUB_SIZE
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.DownloadImportBinding
import com.lagradost.quicknovel.databinding.DownloadImportCardBinding
import com.lagradost.quicknovel.databinding.DownloadResultCompactBinding
import com.lagradost.quicknovel.databinding.DownloadResultGridBinding
import com.lagradost.quicknovel.databinding.HistoryResultCompactBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.ui.newSharedPool
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.util.UIHelper.hideKeyboard
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import com.lagradost.quicknovel.util.KineticTiltHelper
import kotlin.math.roundToInt
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.preference.PreferenceManager

class AnyAdapter(
    private val resView: AutofitRecyclerView,
    private val downloadViewModel: DownloadViewModel,
) : NoStateAdapter<Any>(
    diffCallback = BaseDiffCallback(
        itemSame = { a, b ->
            if (a is ResultCached && b is ResultCached) {
                a.source == b.source
            } else if (a is DownloadFragment.DownloadDataLoaded && b is DownloadFragment.DownloadDataLoaded) {
                a.source == b.source
            } else {
                false
            }
        },
        contentSame = { a, b ->
            a == b
        }
    )
) {
    companion object {
        val sharedPool =
            newSharedPool {
                setMaxRecycledViews(RESULT_CACHED, 20)
                setMaxRecycledViews(DOWNLOAD_DATA_LOADED, 20)
            }

        const val RESULT_CACHED: Int = 1
        const val DOWNLOAD_DATA_LOADED: Int = 2
    }

    private fun getCorrectHeight(position: Int): Int {
        val manager = resView.layoutManager
        val baseWidth = resView.itemWidth
        
        return when (manager) {
            is StaggeredGridLayoutManager -> {
                // Pinterest True Masonry Style: 
                // Deterministic variance based on position to create the "Staggered" look without gaps.
                // We oscillate the aspect ratio slightly around the 2:3 (0.68) standard.
                val variance = when (position % 5) {
                    0 -> 0.64f // standard-tall
                    1 -> 0.72f // standard-short
                    2 -> 0.68f // standard
                    3 -> 0.61f // tall
                    else -> 0.75f // short
                }
                (baseWidth / variance).roundToInt()
            }
            is GridLayoutManager -> {
                val spanSize = manager.spanSizeLookup.getSpanSize(position)
                val actualWidth = baseWidth * spanSize
                (actualWidth / 0.68).roundToInt()
            }
            else -> (baseWidth / 0.68).roundToInt()
        }
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItemOrNull(position)) {
            is ResultCached -> item.id.toLong()
            is DownloadFragment.DownloadDataLoaded -> item.id.toLong()
            else -> 0L
        }
    }


    override fun onCreateFooter(parent: ViewGroup): ViewHolderState<Any> {
        val compact = parent.context.getDownloadIsCompact()

        return ViewHolderState(
            if (compact) {
                DownloadImportBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            } else {
                DownloadImportCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            }
        )
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        when (val binding = holder.view) {
            is DownloadResultGridBinding -> {
                clearImage(binding.imageView)
            }

            is HistoryResultCompactBinding -> {
                clearImage(binding.imageView)
            }

            is DownloadResultCompactBinding -> {
                clearImage(binding.imageView)
            }
        }
    }

    override fun onBindFooter(holder: ViewHolderState<Any>) {
        when (val binding = holder.view) {
            is DownloadImportBinding -> {
                binding.backgroundCard.setOnClickListener {
                    binding.backgroundCard.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    downloadViewModel.importEpub()
                }
            }

            is DownloadImportCardBinding -> {
                binding.backgroundCard.apply {
                    setOnClickListener {
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        downloadViewModel.importEpub()
                    }
                    // QN-Enhanced: REMOVED layoutParams modification here to avoid layout thrashing.
                    // The footer height is now handled by the layout XML.
                }
            }
        }
    }

    override fun onCreateCustomContent(parent: ViewGroup, viewType: Int): ViewHolderState<Any> {
        val compact = parent.context.getDownloadIsCompact()
        
        // Extract base type and ratio variant from the consolidated viewType
        // baseType is normalized back by BaseAdapter (viewType & CUSTOM_MASK)
        val cleanType = viewType
        val isCompactType = cleanType >= 1000
        val typeWithoutCompact = if (isCompactType) cleanType - 1000 else cleanType
        
        val baseType = typeWithoutCompact % 100
        val ratioVariant = typeWithoutCompact / 100 // 0 to 4
        
        val binding = when (baseType) {
            RESULT_CACHED -> {
                if (compact) {
                    HistoryResultCompactBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                } else {
                    DownloadResultGridBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ).apply {
                        // QN-Enhanced: Set Aspect Ratio ONLY ONCE during creation
                        // Deterministic Masonry Style (Pinterest variance)
                        val ratio = when (ratioVariant) {
                            0 -> "9:14"   // Standard
                            1 -> "9:15"   // Tall
                            2 -> "9:13"   // Short
                            3 -> "9:14.5" // Medium-Tall
                            else -> "9:13.5" // Medium-Short
                        }
                        (backgroundCard.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let {
                            it.dimensionRatio = ratio
                            backgroundCard.layoutParams = it
                        }
                        
                        // High Quality Physical Feedback: Attach ONLY ONCE
                        KineticTiltHelper.applyKineticTilt(backgroundCard)
                    }
                }
            }

            DOWNLOAD_DATA_LOADED -> {
                if (compact) {
                    DownloadResultCompactBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                } else {
                    DownloadResultGridBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ).apply {
                        // QN-Enhanced: Set Aspect Ratio ONLY ONCE
                        val ratio = when (ratioVariant) {
                            0 -> "9:14"
                            1 -> "9:15"
                            2 -> "9:13"
                            3 -> "9:14.5"
                            else -> "9:13.5"
                        }
                        (backgroundCard.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let {
                            it.dimensionRatio = ratio
                            backgroundCard.layoutParams = it
                        }
                        
                        KineticTiltHelper.applyKineticTilt(backgroundCard)
                    }
                }
            }

            else -> throw NotImplementedError()
        }

        return ViewHolderState(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindContent(holder: ViewHolderState<Any>, item: Any, position: Int) {
        when (val view = holder.view) {
            is HistoryResultCompactBinding -> {
                val card = item as ResultCached
                view.apply {
                    imageText.text = card.name
                    historyExtraText.text =
                        "${card.lastChapterRead}/${card.currentTotalChapters} ${
                            root.context.getString(
                                R.string.read_action_chapters
                            )
                        }"

                    imageView.setImage(card.poster)

                    historyPlay.setOnClickListener {
                        downloadViewModel.stream(card)
                    }
                    backgroundCard.setOnClickListener { v ->
                        v.postDelayed({
                            downloadViewModel.load(card)
                        }, 50)
                    }
                    historyDelete.setOnClickListener {
                        downloadViewModel.deleteAlert(card)
                    }
                    backgroundCard.setOnLongClickListener { v ->
                        hideKeyboard(v)
                        downloadViewModel.showMetadata(card)
                        return@setOnLongClickListener true
                    }
                }
            }

            is DownloadResultGridBinding -> {
                when (item) {
                    is DownloadFragment.DownloadDataLoaded -> {
                        view.apply {
                            backgroundCard.apply {
                                // QN-Enhanced: NO LayoutParams modifications here.
                                // Masonry is handled via ViewTypes and Ratios in onCreate.
                                setOnClickListener {
                                    if (item.apiName == IMPORT_SOURCE_PDF && item.downloadedCount < item.downloadedTotal) {
                                        preloadPartialImportedPdf(item, context)
                                        if (item.state != DownloadState.IsDownloading && item.state != DownloadState.IsPaused) {
                                            downloadViewModel.refreshCard(item)
                                        }
                                    }
                                    downloadViewModel.readEpub(item)
                                }
                                setOnLongClickListener { v ->
                                    hideKeyboard(v)
                                    downloadViewModel.showMetadata(item)
                                    return@setOnLongClickListener true
                                }
                            }
                            
                            downloadProgressbarIndeterment.isVisible = item.generating
                            val showDownloadLoading = item.state == DownloadState.IsPending

                            val isAPdfDownloading =
                                item.apiName == IMPORT_SOURCE_PDF && (item.downloadedTotal != item.downloadedCount)
                            downloadUpdateLoading.isVisible =
                                showDownloadLoading || isAPdfDownloading

                            val epubSize = getKey(DOWNLOAD_EPUB_SIZE, item.id.toString()) ?: 0
                            val diff = item.downloadedCount - epubSize
                            imageTextMore.text = "+$diff "
                            imageTextMore.isVisible =
                                diff > 0 && !showDownloadLoading && !item.isImported
                            imageText.text = item.name

                            imageView.alpha = if (isAPdfDownloading) 0.6f else 1.0f
                            imageView.setImage(item.image)

                            progressReading.isVisible = false
                        }
                    }

                    is ResultCached -> {
                        view.apply {
                            backgroundCard.apply {
                                // QN-Enhanced: NO LayoutParams modifications here.
                                setOnClickListener { v ->
                                    v.postDelayed({
                                        val transitionUrl = item.source
                                        imageView.transitionName = transitionUrl
                                        val extras = androidx.navigation.fragment.FragmentNavigatorExtras(imageView to transitionUrl)
                                        val act = com.lagradost.quicknovel.CommonActivity.activity
                                        if (act is androidx.fragment.app.FragmentActivity) {
                                            act.loadResult(transitionUrl, item.apiName, 0, null, extras)
                                        } else {
                                            downloadViewModel.load(item)
                                        }
                                    }, 50)
                                }
                                setOnLongClickListener { v ->
                                    hideKeyboard(v)
                                    downloadViewModel.showMetadata(item)
                                    return@setOnLongClickListener true
                                }
                            }
                            
                            imageView.setImage(
                                item.image,
                            ) // skipCache = false

                            imageText.text = item.name
                            imageTextMore.isVisible = false

                            progressReading.text =
                                "${item.lastChapterRead}/${item.currentTotalChapters}"
                        }
                    }

                    else -> throw NotImplementedError()
                }
            }

            is DownloadResultCompactBinding -> {
                val card = item as DownloadFragment.DownloadDataLoaded
                view.apply {
                    downloadUpdateHolder.isGone =
                        card.isImported && (card.apiName != IMPORT_SOURCE_PDF || card.downloadedTotal == card.downloadedCount)
                    downloadDeleteTrash.isVisible = true
                    downloadHolder.isGone = false
                    val same = imageText.text == card.name
                    backgroundCard.apply {
                        setOnClickListener {
                            if (card.apiName == IMPORT_SOURCE_PDF && card.downloadedCount < card.downloadedTotal)
                                preloadPartialImportedPdf(card, context)
                            downloadViewModel.readEpub(card)
                        }
                        setOnLongClickListener { v ->
                            hideKeyboard(v)
                            downloadViewModel.showMetadata(card)
                            return@setOnLongClickListener true
                        }
                    }
                    imageView.apply {
                        setOnClickListener {
                            if (!item.isImported)
                                downloadViewModel.load(card)
                        }
                        setOnLongClickListener { v ->
                            hideKeyboard(v)
                            downloadViewModel.showMetadata(card)
                            return@setOnLongClickListener true
                        }
                    }

                    downloadDeleteTrash.setOnClickListener {
                        downloadViewModel.deleteAlert(card)
                    }

                    val epubSize = getKey(DOWNLOAD_EPUB_SIZE, card.id.toString()) ?: 0
                    val diff = card.downloadedCount - epubSize
                    imageTextMore.text = if (diff > 0) "+$diff " else ""

                    imageView.setImage(card.image)

                    downloadProgressText.text =
                        "${card.downloadedCount}/${card.downloadedTotal}" + if (card.ETA == "") "" else " - ${card.ETA}"

                    downloadProgressbar.apply {
                        max = card.downloadedTotal.toInt() * 100

                        // shitty check for non changed
                        if (same || imageText.text.isEmpty()) {//the first time, imageText.text is empty
                            val animation: ObjectAnimator = ObjectAnimator.ofInt(
                                this,
                                "progress",
                                progress,
                                card.downloadedCount.toInt() * 100
                            )

                            animation.duration = 500
                            animation.setAutoCancel(true)
                            animation.interpolator = DecelerateInterpolator()
                            animation.start()
                        } else {
                            progress = card.downloadedCount.toInt() * 100
                        }
                        //download_progressbar.progress = card.downloadedCount
                        isIndeterminate = card.generating
                        isVisible = card.generating || (card.downloadedCount < card.downloadedTotal)
                    }

                    imageText.text = card.name
                    val realState = card.state
                    /*if (card.downloadedCount >= card.downloadedTotal) {
                        downloadUpdate.alpha = 0.5f
                        downloadUpdate.isEnabled = false
                    } else {*/
                    downloadUpdate.alpha = 1f
                    downloadUpdate.isEnabled = true
                    //}
                    downloadUpdate.contentDescription = when (realState) {
                        DownloadState.IsDone -> "Done"
                        DownloadState.IsDownloading -> "Pause"
                        DownloadState.IsPaused -> "Resume"
                        DownloadState.IsFailed -> "Re-Download"
                        DownloadState.IsStopped -> "Update"
                        DownloadState.IsPending -> "Pending"
                        DownloadState.Nothing -> "Update"
                    }

                    downloadUpdate.setImageResource(
                        when (realState) {
                            DownloadState.IsDownloading -> R.drawable.ic_baseline_pause_24
                            DownloadState.IsPaused -> R.drawable.netflix_play
                            DownloadState.IsStopped -> R.drawable.ic_baseline_autorenew_24
                            DownloadState.IsFailed -> R.drawable.ic_baseline_autorenew_24
                            DownloadState.IsDone -> R.drawable.ic_baseline_check_24
                            DownloadState.IsPending -> R.drawable.nothing
                            DownloadState.Nothing -> R.drawable.ic_baseline_autorenew_24
                        }
                    )
                    downloadUpdate.setOnClickListener {
                        when (realState) {
                            DownloadState.IsDownloading -> downloadViewModel.pause(card)
                            DownloadState.IsPaused -> downloadViewModel.resume(card)
                            DownloadState.IsPending -> {}
                            else -> downloadViewModel.refreshCard(card)//this also resume download of imported pdfs
                        }
                    }

                    downloadUpdateLoading.isVisible = realState == DownloadState.IsPending
                }
            }

            else -> throw NotImplementedError()
        }
    }

    override fun customContentViewType(item: Any): Int {
        val compact = resView.context.getDownloadIsCompact()
        val offset = if (compact) 1000 else 0
        
        val baseType = if (item is ResultCached) {
            RESULT_CACHED + offset
        } else if (item is DownloadFragment.DownloadDataLoaded) {
            DOWNLOAD_DATA_LOADED + offset
        } else {
            throw NotImplementedError()
        }

        // QN-Enhanced: Deterministic Masonry Pooling.
        // We hash the item ID to choose its bento ratio.
        // This ensures the same novel always has the same height across sessions.
        return if (!compact) {
            val id = when (item) {
                is ResultCached -> item.id
                is DownloadFragment.DownloadDataLoaded -> item.id
                else -> 0
            }.toLong()
            
            val variant = kotlin.math.abs((id % 5L).toInt())
            baseType + (variant * 100)
        } else {
            baseType
        }
    }
}