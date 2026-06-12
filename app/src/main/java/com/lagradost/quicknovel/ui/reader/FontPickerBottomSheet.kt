package com.lagradost.quicknovel.ui.reader

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.quicknovel.FontFile
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.util.DrawerHelper
import com.lagradost.quicknovel.util.applyGlassStyle
import com.lagradost.quicknovel.util.UIHelper.parseFontFileName
import java.io.File

class FontPickerBottomSheet : BottomSheetDialogFragment() {
    companion object {
        private const val ARG_CURRENT_FONT = "arg_current_font"
        private const val ARG_BG_ID = "arg_bg_id"

        fun newInstance(
            currentFont: String,
            backgroundViewId: Int
        ): FontPickerBottomSheet {
            return FontPickerBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_CURRENT_FONT, currentFont)
                    putInt(ARG_BG_ID, backgroundViewId)
                }
            }
        }
    }

    private var onFontSelectedListener: ((FontFile) -> Unit)? = null
    private var onDeleteFontListener: ((FontFile) -> Unit)? = null
    private var onAddCustomFontListener: (() -> Unit)? = null

    fun setListeners(
        onFontSelected: (FontFile) -> Unit,
        onDeleteFont: (FontFile) -> Unit,
        onAddCustomFont: () -> Unit
    ) {
        onFontSelectedListener = onFontSelected
        onDeleteFontListener = onDeleteFont
        onAddCustomFontListener = onAddCustomFont
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val currentFont = arguments?.getString(ARG_CURRENT_FONT) ?: ""

        val customFontsFolder = File(requireContext().filesDir, "fonts")
        val customFonts = if (customFontsFolder.exists()) customFontsFolder.listFiles() ?: emptyArray() else emptyArray()
        val fonts = customFonts + com.lagradost.quicknovel.util.UIHelper.systemFonts
        val items = listOf(FontFile(null)) + fonts.map { FontFile(it) }

        val checkedIndex = items.indexOfFirst { (it.file?.name ?: "") == currentFont }

        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuickNovelTheme {
                    FontPickerCompose(
                        items = items,
                        checkedIndex = checkedIndex,
                        onFontSelected = { font ->
                            onFontSelectedListener?.invoke(font)
                            dismiss()
                        },
                        onDeleteFont = { font ->
                            onDeleteFontListener?.invoke(font)
                            dismiss()
                        },
                        onAddCustomFont = {
                            onAddCustomFontListener?.invoke()
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
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FontPickerCompose(
    items: List<FontFile>,
    checkedIndex: Int,
    onFontSelected: (FontFile) -> Unit,
    onDeleteFont: (FontFile) -> Unit,
    onAddCustomFont: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
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

        // Title and Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Reader Fonts",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            FilledTonalButton(
                onClick = onAddCustomFont,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_add_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Font", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Fonts List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == checkedIndex
                val fontName = parseFontFileName(item.file?.name)
                val customFolder = File(context.filesDir, "fonts")
                val isCustom = item.file != null && item.file.parentFile?.absolutePath == customFolder.absolutePath
                
                val fontFamily = remember(item.file) {
                    if (item.file != null) {
                        try {
                            FontFamily(Typeface.createFromFile(item.file))
                        } catch (t: Throwable) {
                            FontFamily.Default
                        }
                    } else {
                        FontFamily.Default
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else null,
                            strokeColor = if (isSelected) MaterialTheme.colorScheme.primary else null,
                            strokeWidth = if (isSelected) 1.5.dp else 1.dp
                        )
                        .clickable { onFontSelected(item) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fontName,
                        fontSize = 16.sp,
                        fontFamily = fontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    if (isCustom) {
                        IconButton(
                            onClick = {
                                onDeleteFont(item)
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_delete_outline_24),
                                contentDescription = "Delete custom font",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
