package com.lagradost.quicknovel.ui.reader

import com.lagradost.quicknovel.LiveChapterData
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.TTSHelper
import com.lagradost.quicknovel.ui.ScrollIndex

/**
 * Single Source of Truth for the Reader UI.
 * This state is immutable and should only be updated via the ViewModel using [ReaderAction].
 */
data class ReaderState(
    val bookTitle: String? = null,
    val currentIndex: Int = 0,
    val desiredIndex: ScrollIndex? = null,
    val isTranslationActive: Boolean = false,
    val isTTSActive: Boolean = false,
    val ttsLine: Int = 0,
    val isShowingOriginal: Boolean = false,
    val bottomVisibility: Boolean = false,
    val ttsStatus: TTSHelper.TTSStatus = TTSHelper.TTSStatus.IsStopped,
    val loadingStatus: Resource<String>? = null,

    // We store the data references, not the 500-page text, as per "Avoid State Bloat" tip.
    val chapterDataMap: Map<Int, Resource<LiveChapterData>?> = emptyMap()
)

/**
 * Sealed class representing all possible UI-triggered actions for the Reader.
 */
sealed class ReaderAction {
    data class SetBookTitle(val title: String?) : ReaderAction()
    data class SetCurrentIndex(val index: Int) : ReaderAction()
    data class SetDesiredIndex(val index: ScrollIndex?) : ReaderAction()
    data class ToggleTranslation(val active: Boolean) : ReaderAction()
    data class ToggleTTS(val active: Boolean) : ReaderAction()
    data class SetTTSLine(val line: Int) : ReaderAction()
    data class ToggleOriginal(val showOriginal: Boolean) : ReaderAction()
    object SwitchVisibility : ReaderAction()
    data class UpdateTTSStatus(val status: TTSHelper.TTSStatus) : ReaderAction()
    data class UpdateLoadingStatus(val status: Resource<String>?) : ReaderAction()
    data class UpdateChapterData(val index: Int, val data: Resource<LiveChapterData>?) : ReaderAction()
    object ClearChapterData : ReaderAction()
}

/**
 * Pure state reducer — no side effects, no Android dependencies, fully unit-testable.
 *
 * Given a [ReaderState] and a [ReaderAction], returns the next [ReaderState].
 * This is the single place where ALL state transitions are defined.
 *
 * Usage in ViewModel: `_state.update { it.reduce(action) }`
 */
fun ReaderState.reduce(action: ReaderAction): ReaderState = when (action) {
    is ReaderAction.SetBookTitle        -> copy(bookTitle = action.title)
    is ReaderAction.SetCurrentIndex     -> copy(currentIndex = action.index)
    is ReaderAction.SetDesiredIndex     -> copy(desiredIndex = action.index)
    is ReaderAction.ToggleTranslation   -> copy(isTranslationActive = action.active)
    is ReaderAction.ToggleTTS           -> copy(isTTSActive = action.active)
    is ReaderAction.SetTTSLine          -> copy(ttsLine = action.line)
    is ReaderAction.ToggleOriginal      -> copy(isShowingOriginal = action.showOriginal)
    is ReaderAction.SwitchVisibility    -> copy(bottomVisibility = !bottomVisibility)
    is ReaderAction.UpdateTTSStatus     -> copy(ttsStatus = action.status)
    is ReaderAction.UpdateLoadingStatus -> copy(loadingStatus = action.status)
    is ReaderAction.UpdateChapterData   -> {
        val updated = chapterDataMap.toMutableMap().also { it[action.index] = action.data }
        copy(chapterDataMap = updated)
    }
    is ReaderAction.ClearChapterData    -> copy(chapterDataMap = emptyMap())
}
