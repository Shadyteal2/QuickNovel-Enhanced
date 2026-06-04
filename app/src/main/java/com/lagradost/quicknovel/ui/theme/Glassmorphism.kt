package com.lagradost.quicknovel.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute

/**
 * Applies the QuickNovel "Spatial Glass" aesthetic to any composable.
 * Dynamically respects:
 *  - Active theme (AMOLED / Light / standard Dark)
 *  - Glass Opacity preference (VibePrefs.GLASS_OPACITY)
 *  - Card Border Style preference (VibePrefs.CARD_BORDER_STYLE)
 */

private val amoledCache = java.util.concurrent.atomic.AtomicBoolean(true)
private val lastThemeCheck = java.util.concurrent.atomic.AtomicLong(0L)

// Throttle-cached theme check — max once per 2.5 seconds
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

    // ─── Base glass background ────────────────────────────────────────────────
    val baseAlpha: Float = rememberGlassOpacityAlpha()

    val resolvedBg = backgroundColor ?: run {
        if (isLightTheme) {
            Color(0xFFFFFFFF).copy(alpha = 0.8f * baseAlpha)
        } else {
            val isAmoled = checkIsAmoledCached(context)
            if (isAmoled) {
                // Frosted AMOLED glass — a rich white overlay creating a highly visible, premium glass sheet on black backgrounds
                Color(0xFFFFFFFF).copy(alpha = 0.18f * baseAlpha)
            } else {
                Color(0xFFFFFFFF).copy(alpha = 0.22f * baseAlpha)
            }
        }
    }

    // ─── Border resolution ────────────────────────────────────────────────────
    // Priority: explicit strokeColor param → vibeStyle border → default stroke
    val defaultStroke = strokeColor ?: run {
        if (isLightTheme) Color(0x1F000000) else Color(0x26FFFFFF)
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val borderStyle = rememberCardBorderStyle()
    val vibeBorderBrush: Brush? = rememberBorderBrush(style = borderStyle, accentColor = accentColor)

    // Only apply vibe border when no explicit strokeColor was passed in (respects overrides)
    val finalBorderBrush: Brush = if (strokeColor == null && vibeBorderBrush != null) {
        vibeBorderBrush
    } else {
        SolidColor(defaultStroke)
    }

    this
        .clip(shape)
        .background(resolvedBg)
        .border(strokeWidth, finalBorderBrush, shape)
}
