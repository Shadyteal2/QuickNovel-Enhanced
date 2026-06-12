package com.lagradost.quicknovel.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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

class OptionsSelectionBottomSheet : BottomSheetDialogFragment() {
    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_ITEMS = "arg_items"
        private const val ARG_SELECTED = "arg_selected"
        private const val ARG_BG_ID = "arg_bg_id"

        fun newInstance(
            title: String,
            items: ArrayList<String>,
            selectedIndex: Int,
            backgroundViewId: Int
        ): OptionsSelectionBottomSheet {
            return OptionsSelectionBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putStringArrayList(ARG_ITEMS, items)
                    putInt(ARG_SELECTED, selectedIndex)
                    putInt(ARG_BG_ID, backgroundViewId)
                }
            }
        }
    }

    private var onItemSelectedListener: ((Int) -> Unit)? = null

    fun setOnItemSelectedListener(listener: (Int) -> Unit) {
        onItemSelectedListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val title = arguments?.getString(ARG_TITLE) ?: ""
        val items = arguments?.getStringArrayList(ARG_ITEMS) ?: arrayListOf()
        val selectedIndex = arguments?.getInt(ARG_SELECTED) ?: -1

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuickNovelTheme {
                    OptionsSelectionCompose(
                        title = title,
                        items = items,
                        selectedIndex = selectedIndex,
                        onItemSelected = { index ->
                            onItemSelectedListener?.invoke(index)
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
fun OptionsSelectionCompose(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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

        // Title
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Options List
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else null,
                            strokeColor = if (isSelected) MaterialTheme.colorScheme.primary else null,
                            strokeWidth = if (isSelected) 1.5.dp else 1.dp
                        )
                        .clickable { onItemSelected(index) }
                        .padding(16.dp)
                ) {
                    Text(
                        text = item,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
