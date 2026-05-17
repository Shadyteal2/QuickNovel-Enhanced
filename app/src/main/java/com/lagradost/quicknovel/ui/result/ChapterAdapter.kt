package com.lagradost.quicknovel.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.databinding.SimpleChapterBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.newSharedPool
import android.view.HapticFeedbackConstants
import androidx.core.view.isVisible
import android.graphics.Color
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState

class ChapterAdapter(val viewModel: ResultViewModel) :
    NoStateAdapter<ChapterData>(
        diffCallback = BaseDiffCallback(
            itemSame = { a, b -> a.url == b.url },
            contentSame = { a, b -> a == b }
        )) {

    companion object {
        val sharedPool =
            newSharedPool {
                setMaxRecycledViews(CONTENT, 10)
            }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            SimpleChapterBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    private fun refresh(
        binding: SimpleChapterBinding,
        card: ChapterData,
        viewModel: ResultViewModel
    ) {
        val isRead = viewModel.hasReadChapter(chapter = card)
        val alpha = if (isRead) 0.5F else 1.0F

        binding.name.alpha = alpha
        binding.releaseDate.alpha = alpha

        val isInSelectionMode = viewModel.isInSelectionMode.value ?: false
        val isSelected = viewModel.selectedChapters.value?.contains(card.url) ?: false
        val isBookmarked = viewModel.isChapterBookmarked(card)

        binding.chapterCheckbox.isVisible = isInSelectionMode
        binding.chapterCheckbox.isChecked = isSelected
        binding.selectionTint.isVisible = isSelected
        binding.chapterBookmarkIcon.isVisible = isBookmarked
    }

    private var lastSelectedPosition = -1

    override fun onBindContent(holder: ViewHolderState<Any>, item: ChapterData, position: Int) {
        val binding = holder.view as? SimpleChapterBinding ?: return
        binding.apply {
            val index = viewModel.chapterIndex(item)
            name.text = if (index != null) "${index + 1}. ${item.name}" else item.name
            releaseDate.text = item.dateOfRelease
            releaseDate.isGone = item.dateOfRelease.isNullOrBlank()
            
            root.setOnClickListener {
                if (viewModel.isInSelectionMode.value == true) {
                    viewModel.toggleSelection(item.url)
                    lastSelectedPosition = position
                    refresh(binding, item, viewModel)
                } else {
                    viewModel.streamRead(item)
                    viewModel.isResume = true
                }
            }
            
            root.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                if (viewModel.isInSelectionMode.value != true) {
                    viewModel.setSelectionMode(true)
                    viewModel.toggleSelection(item.url)
                    lastSelectedPosition = position
                } else {
                    // Range select logic
                    if (lastSelectedPosition != -1) {
                        val start = minOf(lastSelectedPosition, position)
                        val end = maxOf(lastSelectedPosition, position)
                        val chapters = viewModel.chapters.value ?: emptyList()
                        val rangeUrls = chapters.subList(start, end + 1).map { it.url }
                        viewModel.selectRange(rangeUrls)
                    }
                }
                notifyDataSetChanged() // Full refresh to show checkboxes
                return@setOnLongClickListener true
            }
            
            chapterCheckbox.setOnClickListener {
                viewModel.toggleSelection(item.url)
                lastSelectedPosition = position
                refresh(binding, item, viewModel)
            }
            
            chapterBookmarkIcon.setOnClickListener {
                viewModel.toggleChapterBookmark(item)
                refresh(binding, item, viewModel)
            }
            
            refresh(binding, item, viewModel)
        }
    }
}