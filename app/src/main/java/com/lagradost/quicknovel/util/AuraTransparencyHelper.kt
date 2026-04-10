package com.lagradost.quicknovel.util

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * AuraTransparencyHelper
 *
 * Recursively makes structural shell containers transparent so the Aura
 * background shines through. Protects functional UI elements (buttons, nav, cards).
 */
object AuraTransparencyHelper {

    /**
     * Recursively makes [view] and its children transparent if they are structural shells.
     * Stops recursion upon hitting functional UI or non-shell components to protect
     * the visibility of content.
     */
    fun forceTransparent(view: View?, depth: Int = 0) {
        if (view == null || depth > 15) return
        
        if (isShellContainer(view)) {
            view.setBackgroundColor(Color.TRANSPARENT)
            
            // Recurse into children to find hidden opaque containers
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    forceTransparent(view.getChildAt(i), depth + 1)
                }
            }
        }

        // Special handling for technical shell wrappers like ViewPager2 internals
        if (view is ViewPager2) {
            try {
                val inner = view.getChildAt(0)
                if (inner is RecyclerView) {
                    inner.setBackgroundColor(Color.TRANSPARENT)
                    // We don't recurse into RecyclerView items generically to avoid 
                    // clearing cards in other parts of the app, but in the reader,
                    // we target them specifically via ID in isShellContainer.
                }
            } catch (e: Exception) {}
        }
    }

    /**
     * Returns true only for views that are structural wrappers with no visual content
     * of their own. Excludes any view that is or contains functional UI.
     */
    private fun isShellContainer(view: View): Boolean {
        // PRIORITY 1: Never touch views whose IDs suggest functional UI that MUST be visible
        val idName = try { view.resources.getResourceEntryName(view.id) } catch (e: Exception) { "" }
        val protected = listOf("nav", "search", "tab", "header", "bar", "chip", "pill",
                              "fab", "button", "card", "toolbar", "download", "bookmark",
                              "history", "menu", "bottom_sheet", "dialog", "settings", "preference",
                              "holder", "wrap", "content", "progress", "control", "footer", "overlay")
                              
        if (protected.any { idName.contains(it, ignoreCase = true) }) return false
        
        // PRIORITY 2: Never touch material component containers that usually have shadows/colors
        if (view is com.google.android.material.card.MaterialCardView) return false
        if (view is com.google.android.material.navigation.NavigationView) return false
        if (view is com.google.android.material.appbar.AppBarLayout) return false
        if (view is com.google.android.material.tabs.TabLayout) return false
        if (view is androidx.appcompat.widget.SearchView) return false

        // PRIORITY 3: Whitelist reader-specific layout components for absolute transparency
        if (idName.contains("reader", ignoreCase = true) || idName.contains("read", ignoreCase = true)) {
            // Protected check already failed above, so we know this isn't a settings root
            
            // real_text (RecyclerView) and real_text_item (Paragraph TextView) must be transparent
            if (idName.contains("real_text", ignoreCase = true)) return true
            // structural containers like read_normal_layout, reader_lin_container
            if (view is ViewGroup) return true
            // The actual paragraph items
            if (view is android.widget.TextView && idName.contains("item", ignoreCase = true)) return true
        }

        // Default: Allow plain structural containers through, block everything else
        return view is ViewGroup && view !is RecyclerView && view !is ViewPager2
    }
}
