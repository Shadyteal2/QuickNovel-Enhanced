package com.lagradost.quicknovel.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.util.DrawerHelper
import com.lagradost.quicknovel.util.UIHelper.clipboardHelper
import com.lagradost.quicknovel.util.UIHelper.dismissSafe

class TranslationBottomSheet(private val originalText: String, private val backgroundViewId: Int? = null) : BottomSheetDialogFragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuickNovelTheme {
                    TranslationSheetCompose(
                        originalText = originalText,
                        onCopy = { translated ->
                            clipboardHelper(txt(R.string.translation), translated)
                            this@TranslationBottomSheet.dialog.dismissSafe(activity)
                        }
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
        val bgId = backgroundViewId ?: return
        if (bgId == 0) return
        val backgroundView = activity?.findViewById<View>(bgId) ?: return
        
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog ?: return
        val behavior = dialog.behavior
        
        behavior.addBottomSheetCallback(object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN ||
                    newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED) {
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
        val bgId = backgroundViewId ?: return
        if (bgId == 0) return
        val backgroundView = activity?.findViewById<View>(bgId) ?: return
        DrawerHelper.resetScaling(backgroundView)
    }

    override fun getTheme(): Int {
        return R.style.BottomSheetDrawerTheme
    }
}
