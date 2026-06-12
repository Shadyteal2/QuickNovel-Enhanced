package com.lagradost.quicknovel.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.util.DrawerHelper
import com.lagradost.quicknovel.util.applyGlassStyle
import kotlinx.coroutines.launch

class ChapterListBottomSheet : BottomSheetDialogFragment() {
    companion object {
        private const val ARG_TITLES = "arg_titles"
        private const val ARG_CURRENT = "arg_current"
        private const val ARG_BG_ID = "arg_bg_id"

        fun newInstance(
            titles: ArrayList<String>,
            currentIndex: Int,
            backgroundViewId: Int
        ): ChapterListBottomSheet {
            return ChapterListBottomSheet().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_TITLES, titles)
                    putInt(ARG_CURRENT, currentIndex)
                    putInt(ARG_BG_ID, backgroundViewId)
                }
            }
        }
    }

    private var onChapterSelectedListener: ((Int) -> Unit)? = null

    fun setOnChapterSelectedListener(listener: (Int) -> Unit) {
        onChapterSelectedListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val titles = arguments?.getStringArrayList(ARG_TITLES) ?: arrayListOf()
        val currentIndex = arguments?.getInt(ARG_CURRENT) ?: -1

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuickNovelTheme {
                    ChapterSelectionCompose(
                        titles = titles,
                        currentIndex = currentIndex,
                        onChapterSelected = { index ->
                            onChapterSelectedListener?.invoke(index)
                            dismiss()
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }.also { view ->
            view.setViewTreeLifecycleOwner(viewLifecycleOwner)
            view.setViewTreeViewModelStoreOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupScaling()
    }

    private fun setupScaling() {
        val bgId = arguments?.getInt(ARG_BG_ID) ?: return
        if (bgId == 0) return
        val backgroundView = activity?.findViewById<View>(bgId) ?: return

        val dialog = dialog as? BottomSheetDialog ?: return
        dialog.applyGlassStyle()
        val behavior = dialog.behavior

        DrawerHelper.applyScalingAnimation(backgroundView, 1f)

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    DrawerHelper.resetScaling(backgroundView)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                DrawerHelper.applyScalingAnimation(backgroundView, slideOffset)
            }
        })
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        val bgId = arguments?.getInt(ARG_BG_ID) ?: return
        if (bgId == 0) return
        val backgroundView = activity?.findViewById<View>(bgId) ?: return
        DrawerHelper.resetScaling(backgroundView)
    }

    override fun getTheme(): Int {
        return R.style.BottomSheetDrawerTheme
    }
}

@Composable
fun ChapterSelectionCompose(
    titles: List<String>,
    currentIndex: Int,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredChapters = remember(searchQuery, titles) {
        titles.mapIndexed { index, title -> index to title }
            .filter { (_, title) -> title.contains(searchQuery, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to current chapter on load
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < titles.size) {
            val matchingFilteredIndex = filteredChapters.indexOfFirst { it.first == currentIndex }
            if (matchingFilteredIndex >= 0) {
                coroutineScope.launch {
                    listState.scrollToItem(matchingFilteredIndex)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f) // Limit sheet height beautifully to 75%
            .padding(16.dp)
    ) {
        // Drag handle indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .glassCard(shape = RoundedCornerShape(2.dp))
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Header & Search
        Text(
            text = stringResource(R.string.select_chapter),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Custom Glassmorphism Search Bar
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search chapters...", color = Color.Gray) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_search_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sharp_clear_24),
                            contentDescription = "Clear",
                            tint = Color.Gray
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Chapters List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(filteredChapters) { _, (origIndex, title) ->
                val isSelected = origIndex == currentIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else null,
                            strokeColor = if (isSelected) MaterialTheme.colorScheme.primary else null,
                            strokeWidth = if (isSelected) 1.5.dp else 1.dp
                        )
                        .clickable { onChapterSelected(origIndex) }
                        .padding(16.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
