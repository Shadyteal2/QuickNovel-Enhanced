package com.lagradost.quicknovel.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import androidx.core.content.ContextCompat
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = ColorAccent,
    tertiary = ColorOngoing,
    background = DarkPrimaryBlackBackground,
    surface = DarkIconGrayBackground,
    onPrimary = DarkTextColor,
    onSecondary = DarkTextColor,
    onTertiary = DarkTextColor,
    onBackground = DarkTextColor,
    onSurface = DarkTextColor,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = ColorAccent,
    tertiary = ColorOngoing,
    background = LightPrimaryGrayBackground,
    surface = LightBitDarkerGrayBackground,
    onPrimary = LightBitDarkerGrayBackground,
    onSecondary = LightBitDarkerGrayBackground,
    onTertiary = LightBitDarkerGrayBackground,
    onBackground = LightTextColor,
    onSurface = LightTextColor,
)

@Composable
fun QuickNovelTheme(
    darkTheme: Boolean = run {
        val context = LocalContext.current
        val settingsManager = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
        val themeKey = remember(settingsManager) { settingsManager.getString(context.getString(R.string.theme_key), "Amoled") }
        when (themeKey) {
            "Light", "AmoledLight" -> false
            "Black", "Amoled" -> true
            else -> isSystemInDarkTheme()
        }
    },
    // Dynamic color is available on Android 12+ (will be true if user theme is Monet)
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // Resolve theme colors dynamically from the active Android Theme
    val primaryColor = remember(context, darkTheme) {
        try {
            Color(context.colorFromAttribute(androidx.appcompat.R.attr.colorPrimary))
        } catch (e: Exception) {
            if (darkTheme) DarkPrimary else LightPrimary
        }
    }
    
    val secondaryColor = remember(context) {
        try {
            Color(context.colorFromAttribute(androidx.appcompat.R.attr.colorAccent))
        } catch (e: Exception) {
            ColorAccent
        }
    }

    val backgroundColor = remember(context, darkTheme) {
        try {
            if (darkTheme) {
                Color(context.colorFromAttribute(R.attr.primaryBlackBackground))
            } else {
                Color(ContextCompat.getColor(context, R.color.lightPrimaryGrayBackground))
            }
        } catch (e: Exception) {
            if (darkTheme) DarkPrimaryBlackBackground else LightPrimaryGrayBackground
        }
    }

    val surfaceColor = remember(context, darkTheme) {
        try {
            if (darkTheme) {
                Color(context.colorFromAttribute(R.attr.iconGrayBackground))
            } else {
                Color(ContextCompat.getColor(context, R.color.lightBitDarkerGrayBackground))
            }
        } catch (e: Exception) {
            if (darkTheme) DarkIconGrayBackground else LightBitDarkerGrayBackground
        }
    }

    val textColor = remember(context, darkTheme) {
        try {
            if (darkTheme) {
                Color(context.colorFromAttribute(R.attr.textColor))
            } else {
                Color(ContextCompat.getColor(context, R.color.lightTextColor))
            }
        } catch (e: Exception) {
            if (darkTheme) DarkTextColor else LightTextColor
        }
    }

    // Check if the current theme is Monet (which allows dynamic wallpaper-based colors)
    val isMonet = remember(context) {
        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
            val themeKey = settingsManager.getString(context.getString(R.string.theme_key), "Amoled")
            val primaryColorKey = settingsManager.getString(context.getString(R.string.primary_color_key), "Banana")
            (themeKey == "Monet" || primaryColorKey == "Monet" || primaryColorKey == "Monet2")
        } catch (e: Exception) {
            false
        }
    }

    val colorScheme = when {
        dynamicColor && isMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            tertiary = ColorOngoing,
            background = backgroundColor,
            surface = surfaceColor,
            onPrimary = textColor,
            onSecondary = textColor,
            onTertiary = textColor,
            onBackground = textColor,
            onSurface = textColor,
        )
        else -> lightColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            tertiary = ColorOngoing,
            background = backgroundColor,
            surface = surfaceColor,
            onPrimary = textColor,
            onSecondary = textColor,
            onTertiary = textColor,
            onBackground = textColor,
            onSurface = textColor,
        )
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
