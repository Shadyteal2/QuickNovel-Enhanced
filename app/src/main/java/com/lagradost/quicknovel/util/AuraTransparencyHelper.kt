package com.lagradost.quicknovel.util

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * AuraTransparencyHelper
 *
 * Makes ONLY the top-level structural shell containers transparent so the Aura
 * background shines through. Does NOT recurse into fragment content — that would
 * strip backgrounds from list cards, history rows, reader bars, etc.
 *
 * Rule: only clear the immediate view passed in, plus one level of direct children
 * that are themselves structural shells (FrameLayout, ConstraintLayout, pure ViewGroup).
 * Never touch RecyclerView children, adapter items, or anything with content.
 */
object AuraTransparencyHelper {

    /**
     * Makes [view] itself transparent, then does ONE level of shallow descent to
     * clear direct children that are pure shell containers.
     * Stops at RecyclerViews, ViewPagers with adapters, Cards, and any view whose
     * resource ID name suggests it is a functional UI component.
     */
    fun forceTransparent(view: View?) {
        if (view == null) return
        clearIfShell(view)

        // One shallow level only — do NOT recurse further
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                // Only descend into children that are themselves pure shell wrappers
                if (isShellContainer(child)) {
                    clearIfShell(child)
                    // Clear the VP2 inner RecyclerView background (but not its items)
                    if (child is ViewPager2) {
                        val inner = child.getChildAt(0)
                        if (inner is RecyclerView) inner.setBackgroundColor(Color.TRANSPARENT)
                    }
                }
            }
        }
    }

    /** Clears the background only if the view is a pure structural shell. */
    private fun clearIfShell(view: View) {
        if (isShellContainer(view)) {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    /**
     * Returns true only for views that are structural wrappers with no visual content
     * of their own. Excludes any view that is or contains functional UI.
     */
    private fun isShellContainer(view: View): Boolean {
        // Never touch cards, navigation, or material components
        if (view is com.google.android.material.card.MaterialCardView) return false
        if (view is com.google.android.material.navigation.NavigationView) return false
        if (view is com.google.android.material.appbar.AppBarLayout) return false
        if (view is com.google.android.material.tabs.TabLayout) return false
        if (view is androidx.appcompat.widget.SearchView) return false
        if (view is androidx.recyclerview.widget.RecyclerView) return false

        // Never touch views whose IDs suggest functional UI
        val idName = try { view.resources.getResourceEntryName(view.id) } catch (e: Exception) { "" }
        val protected = listOf("nav", "search", "tab", "header", "bar", "chip", "pill",
                              "fab", "button", "card", "toolbar", "download", "bookmark",
                              "history", "read", "overlay")
        if (protected.any { idName.contains(it, ignoreCase = true) }) return false

        // Only allow plain structural containers through
        return view is ViewGroup
    }
}
