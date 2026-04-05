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
            when {
                a is ResultCached && b is ResultCached -> a.id == b.id
                a is DownloadFragment.DownloadDataLoaded && b is DownloadFragment.DownloadDataLoaded -> a.id == b.id
                else -> false
            }
        },
        contentSame = { a, b ->
            when {
                a is ResultCached && b is ResultCached -> a.id == b.id && a.source == b.source
                a is DownloadFragment.DownloadDataLoaded && b is DownloadFragment.DownloadDataLoaded -> a.id == b.id && a.source == b.source
                else -> a == b
            }
        }
    )
) {
    private var isBento3x3: Boolean = false
    private var usePinterest: Boolean = false
    private var isCompact: Boolean = false

    private fun updatePrefs() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(resView.context)
        isBento3x3 = prefs.getBoolean("library_bento_3x3", false)
        usePinterest = prefs.getBoolean("library_pinterest_bento", false)
        isCompact = resView.context.getDownloadIsCompact()
    }

    override fun submitList(list: Collection<Any>?, commitCallback: Runnable?) {
        super.submitList(list, commitCallback)
        updatePrefs()
    }

    init {
        setHasStableIds(true)
        updatePrefs()
    }

    companion object {
        val sharedPool =
            newSharedPool {
                setMaxRecycledViews(RESULT_CACHED, 25)
                setMaxRecycledViews(DOWNLOAD_DATA_LOADED, 25)
            }

        const val RESULT_CACHED: Int = 1
        const val DOWNLOAD_DATA_LOADED: Int = 2
    }

    private fun getCorrectHeight(position: Int): Int {
        val manager = resView.layoutManager
        val baseWidth = resView.itemWidth
        val context = resView.context
        
        return when (manager) {
            is StaggeredGridLayoutManager -> {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val usePinterest = prefs.getBoolean("library_pinterest_bento", false)
                
                // Pinterest True Masonry Style: 
                // Deterministic variance based on position to create the "Staggered" look without gaps.
                // We oscillate the aspect ratio slightly around the 2:3 (0.68) standard.
                val variance = if (usePinterest) {
                    when (position % 8) {
                        0 -> 0.60f // XL-Tall
                        1 -> 0.75f // XX-Short
                        2 -> 0.68f // Standard
                        3 -> 0.63f // Tall
                        4 -> 0.85f // Tiny
                        5 -> 0.66f // Medium-Tall
                        6 -> 0.72f // Short
                        else -> 0.68f // Standard
                    }
                } else {
                    when (position % 5) {
                        0 -> 0.64f // standard-tall
                        1 -> 0.72f // standard-short
                        2 -> 0.68f // standard
                        3 -> 0.61f // tall
                        else -> 0.75f // short
                    }
                }
                (baseWidth / variance).roundToInt()
            }
            is GridLayoutManager -> {
                val spanSize = manager.spanSizeLookup.getSpanSize(position)
                val actualWidth = baseWidth * spanSize
                
                if (isBento3x3) {
                    // Bento 3x3 Logic: Big items (2 spans) are 16:9 for premium look, Square items are adjusted to match height
                    // Heights must match to avoid gaps in standard GridLayoutManager rows.
                    // Height(Big) = actualWidth(Big) / ratio(Big) = (2 * baseWidth) / ratio(Big)
                    // Height(Small) = actualWidth(Small) / ratio(Small) = (1 * baseWidth) / ratio(Small)
                    // To align: (2 / ratioBig) == (1 / ratioSmall)
                    // If ratioBig = 2.0 (standard 2:1), ratioSmall = 1.0 (Square). 
                    // Let's use 2.0 and 1.0 for perfect row alignment.
                    val ratio = if (spanSize > 1) 2.0f else 1.0f
                    (actualWidth / ratio).roundToInt()
                } else {
                    (actualWidth / 0.68).roundToInt()
                }
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
        val inflater = LayoutInflater.from(parent.context)
        
        // BITMASK EXTRACTION:
        // Bits 0-3: Base Type (1=CACHED, 2=LOADED)
        // Bits 4-7: Layout Type (0=Masonry, 1=Bento-Sq, 2=Bento-Wide)
        // Bit 8: Compact Flag
        val baseType = viewType and 0x0F
        val layoutType = (viewType shr 4) and 0x0F
        val isViewCompact = (viewType and 0x100) != 0

        val binding = if (isViewCompact) {
            if (baseType == RESULT_CACHED) {
                HistoryResultCompactBinding.inflate(inflater, parent, false)
            } else {
                DownloadResultCompactBinding.inflate(inflater, parent, false)
            }
        } else {
            val gridBinding = DownloadResultGridBinding.inflate(inflater, parent, false)
            
            // PRE-LAYOUT SETUP: Set ratios once during creation!
            (gridBinding.backgroundCard.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let { lp ->
                when (layoutType) {
                    1 -> lp.dimensionRatio = "1:1" // Bento Square
                    2 -> lp.dimensionRatio = "2:1" // Bento Wide
                    else -> lp.dimensionRatio = "9:14" // Default Novel Portrait
                }
                gridBinding.backgroundCard.layoutParams = lp
            }
            
            // Add premium hardware feedback
            KineticTiltHelper.applyKineticTilt(gridBinding.backgroundCard)
            
            gridBinding
        }

        return ViewHolderState(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindContent(holder: ViewHolderState<Any>, item: Any, position: Int) {
        when (val view = holder.view) {
            is HistoryResultCompactBinding -> {
                val card = item as ResultCached
                view.apply {
                    if (imageText.text != card.name) imageText.text = card.name
                    
                    val progressText = "${card.lastChapterRead}/${card.currentTotalChapters} ${
                        root.context.getString(R.string.read_action_chapters)
                    }"
                    if (historyExtraText.text != progressText) historyExtraText.text = progressText

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
                            
                            val isGenerating = item.generating
                            if (downloadProgressbarIndeterment.isVisible != isGenerating) downloadProgressbarIndeterment.isVisible = isGenerating
                            
                            val showDownloadLoading = item.state == DownloadState.IsPending
                            val isAPdfDownloading = item.apiName == IMPORT_SOURCE_PDF && (item.downloadedTotal != item.downloadedCount)
                            val showUpdateLoading = showDownloadLoading || isAPdfDownloading
                            if (downloadUpdateLoading.isVisible != showUpdateLoading) downloadUpdateLoading.isVisible = showUpdateLoading

                            // DISK I/O REMOVED: Using card.readCount instead of getKey()
                            val diff = item.downloadedCount - item.readCount
                            val diffText = "+$diff "
                            val showDiff = diff > 0 && !showDownloadLoading && !item.isImported
                            
                            if (imageTextMore.text != diffText) imageTextMore.text = diffText
                            if (imageTextMore.isVisible != showDiff) imageTextMore.isVisible = showDiff
                            
                            if (imageText.text != item.name) imageText.text = item.name

                            val targetAlpha = if (isAPdfDownloading) 0.6f else 1.0f
                            if (imageView.alpha != targetAlpha) imageView.alpha = targetAlpha
                            imageView.setImage(item.image)

                            if (progressReading.isVisible) progressReading.isVisible = false
                        }
                    }

                    is ResultCached -> {
                        view.apply {
                            backgroundCard.apply {
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
                            
                            imageView.setImage(item.image)

                            if (imageText.text != item.name) imageText.text = item.name
                            if (imageTextMore.isVisible) imageTextMore.isVisible = false

                            val progressText = "${item.lastChapterRead}/${item.currentTotalChapters}"
                            if (progressReading.text != progressText) progressReading.text = progressText
                            if (!progressReading.isVisible) progressReading.isVisible = true
                        }
                    }

                    else -> throw NotImplementedError()
                }
            }

            is DownloadResultCompactBinding -> {
                val card = item as DownloadFragment.DownloadDataLoaded
                view.apply {
                    val isImportedPdf = card.isImported && card.apiName == IMPORT_SOURCE_PDF
                    val isDone = card.downloadedTotal == card.downloadedCount
                    
                    // 1. LAZY UPDATES & POLLUTION PREVENTION
                    val updateHidden = card.isImported && (!isImportedPdf || isDone)
                    if (downloadUpdateHolder.isGone != updateHidden) downloadUpdateHolder.isGone = updateHidden
                    if (!downloadDeleteTrash.isVisible) downloadDeleteTrash.isVisible = true
                    if (downloadHolder.isGone) downloadHolder.isGone = false
                    
                    val currentName = card.name
                    val sameName = imageText.text == currentName
                    
                    backgroundCard.apply {
                        setOnClickListener {
                            if (isImportedPdf && card.downloadedCount < card.downloadedTotal)
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
                            if (!card.isImported) downloadViewModel.load(card)
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

                    // 2. DISK I/O REMOVED: Using card.readCount instead of getKey()
                    val diff = card.downloadedCount - card.readCount
                    val diffText = if (diff > 0) "+$diff " else ""
                    if (imageTextMore.text != diffText) imageTextMore.text = diffText

                    imageView.setImage(card.image)

                    val progressLabel = "${card.downloadedCount}/${card.downloadedTotal}${if (card.ETA == "") "" else " - ${card.ETA}"}"
                    if (downloadProgressText.text != progressLabel) downloadProgressText.text = progressLabel

                    // 3. CPU SPIKE REMOVAL: Smooth setProgress vs ObjectAnimator
                    downloadProgressbar.apply {
                        max = card.downloadedTotal.toInt() * 100
                        val targetProgress = card.downloadedCount.toInt() * 100
                        
                        if (targetProgress != progress) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                setProgress(targetProgress, true)
                            } else {
                                progress = targetProgress
                            }
                        }
                        
                        if (isIndeterminate != card.generating) isIndeterminate = card.generating
                        val shouldShowBar = card.generating || (card.downloadedCount < card.downloadedTotal)
                        if (isVisible != shouldShowBar) isVisible = shouldShowBar
                    }

                    if (!sameName) imageText.text = currentName
                    
                    val realState = card.state
                    downloadUpdate.alpha = 1f
                    downloadUpdate.isEnabled = true
                    
                    val contentDesc = when (realState) {
                        DownloadState.IsDone -> "Done"
                        DownloadState.IsDownloading -> "Pause"
                        DownloadState.IsPaused -> "Resume"
                        DownloadState.IsFailed -> "Re-Download"
                        DownloadState.IsStopped -> "Update"
                        DownloadState.IsPending -> "Pending"
                        DownloadState.Nothing -> "Update"
                    }
                    if (downloadUpdate.contentDescription != contentDesc) downloadUpdate.contentDescription = contentDesc

                    val iconRes = when (realState) {
                        DownloadState.IsDownloading -> R.drawable.ic_baseline_pause_24
                        DownloadState.IsPaused -> R.drawable.netflix_play
                        DownloadState.IsStopped -> R.drawable.ic_baseline_autorenew_24
                        DownloadState.IsFailed -> R.drawable.ic_baseline_autorenew_24
                        DownloadState.IsDone -> R.drawable.ic_baseline_check_24
                        DownloadState.IsPending -> R.drawable.nothing
                        DownloadState.Nothing -> R.drawable.ic_baseline_autorenew_24
                    }
                    // Avoid redundant image resource setting
                    downloadUpdate.setImageResource(iconRes)
                    
                    downloadUpdate.setOnClickListener {
                        when (realState) {
                            DownloadState.IsDownloading -> downloadViewModel.pause(card)
                            DownloadState.IsPaused -> downloadViewModel.resume(card)
                            DownloadState.IsPending -> {}
                            else -> downloadViewModel.refreshCard(card)
                        }
                    }

                    val pendingVisibility = realState == DownloadState.IsPending
                    if (downloadUpdateLoading.isVisible != pendingVisibility) downloadUpdateLoading.isVisible = pendingVisibility
                }
            }

            else -> throw NotImplementedError()
        }
    }

    override fun customContentViewType(item: Any, position: Int): Int {
        val baseType = if (item is ResultCached) {
            RESULT_CACHED
        } else if (item is DownloadFragment.DownloadDataLoaded) {
            DOWNLOAD_DATA_LOADED
        } else {
            throw NotImplementedError()
        }

        // Bit 8: Compact Flag
        var type = baseType
        if (isCompact) type = type or 0x100

        // Bits 4-7: Bento Layout Type
        if (!isCompact && isBento3x3) {
            val layoutType = when (position % 7) {
                0, 5 -> 2 // Wide (2:1)
                else -> 1 // Square (1:1)
            }
            type = type or (layoutType shl 4)
        }
        
        return type
    }
}