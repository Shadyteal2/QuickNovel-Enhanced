package com.lagradost.quicknovel.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute

/**
 * Applies the QuickNovel "Spatial Glass" aesthetic to any composable.
 * This dynamically adapts based on the active theme (AMOLED, Light, or standard Dark).
 */
private val amoledCache = java.util.concurrent.atomic.AtomicBoolean(true)
private val lastThemeCheck = java.util.concurrent.atomic.AtomicLong(0L)

private fun checkIsAmoledCached(context: android.content.Context): Boolean {
    val now = System.currentTimeMillis()
    if (now - lastThemeCheck.get() < 2500L) {
        return amoledCache.get()
    }
    try {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val themeKey = prefs.getString(context.getString(R.string.theme_key), "Amoled") ?: "Amoled"
        val isAmoled = (themeKey == "Amoled" || themeKey == "Black")
        amoledCache.set(isAmoled)
        lastThemeCheck.set(now)
    } catch (e: Exception) {
        // Fallback silently to cache
    }
    return amoledCache.get()
}

fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color? = null,
    strokeColor: Color? = null,
    strokeWidth: Dp = 1.dp
): Modifier = composed {
    val context = LocalContext.current
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    
    val resolvedBg = backgroundColor ?: run {
        if (isLightTheme) {
            // Gorgeous high-contrast translucent white card for Light/Flashbang mode
            Color(0xEEFFFFFF)
        } else {
            // Check for AMOLED theme using our high-performance cache
            val isAmoled = checkIsAmoledCached(context)
            
            if (isAmoled) {
                // Pure AMOLED translucent black for rich deep blacks
                Color(0xCC000000)
            } else {
                // Standard Spatial Glass dark charcoal
                Color(0xCC121215)
            }
        }
    }

    val resolvedStroke = strokeColor ?: run {
        if (isLightTheme) {
            // Soft slate stroke for light theme
            Color(0x1F000000)
        } else {
            // Elegant white glass stroke for dark themes
            Color(0x26FFFFFF)
        }
    }

    this
        .clip(shape)
        .background(resolvedBg)
        .border(strokeWidth, resolvedStroke, shape)
}
