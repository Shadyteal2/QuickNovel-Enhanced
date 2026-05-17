package com.lagradost.quicknovel.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color? = null,
    strokeColor: Color? = null,
    strokeWidth: Dp = 1.dp
): Modifier = composed {
    val context = LocalContext.current
    
    val resolvedBg = backgroundColor ?: run {
        val textColorInt = context.colorFromAttribute(R.attr.textColor)
        val isLightTheme = (android.graphics.Color.red(textColorInt) + android.graphics.Color.green(textColorInt) + android.graphics.Color.blue(textColorInt)) < 400

        if (isLightTheme) {
            // Gorgeous high-contrast translucent white card for Light/Flashbang mode
            Color(0xEEFFFFFF)
        } else {
            // Check for AMOLED theme
            val isAmoled = try {
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
                val themeKey = settingsManager.getString(context.getString(R.string.theme_key), "Amoled")
                themeKey == "Amoled" || themeKey == "Black"
            } catch (e: Exception) {
                true
            }
            
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
        val textColorInt = context.colorFromAttribute(R.attr.textColor)
        val isLightTheme = (android.graphics.Color.red(textColorInt) + android.graphics.Color.green(textColorInt) + android.graphics.Color.blue(textColorInt)) < 400

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
