package com.lagradost.quicknovel.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
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

    val themeKey = remember(context) {
        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
            settingsManager.getString(context.getString(R.string.theme_key), "Amoled") ?: "Amoled"
        } catch (e: Exception) {
            "Amoled"
        }
    }

    val backgroundColor = remember(context, darkTheme, themeKey) {
        try {
            if (themeKey == "Amoled" || themeKey == "Black") {
                Color.Black
            } else if (darkTheme) {
                Color(context.colorFromAttribute(R.attr.primaryBlackBackground))
            } else {
                Color(ContextCompat.getColor(context, R.color.lightPrimaryGrayBackground))
            }
        } catch (e: Exception) {
            if (darkTheme) {
                if (themeKey == "Amoled" || themeKey == "Black") Color.Black else DarkPrimaryBlackBackground
            } else {
                LightPrimaryGrayBackground
            }
        }
    }

    val surfaceColor = remember(context, darkTheme, themeKey) {
        try {
            if (themeKey == "Amoled" || themeKey == "Black") {
                Color.Black
            } else if (darkTheme) {
                Color(context.colorFromAttribute(R.attr.iconGrayBackground))
            } else {
                Color(ContextCompat.getColor(context, R.color.lightBitDarkerGrayBackground))
            }
        } catch (e: Exception) {
            if (darkTheme) {
                if (themeKey == "Amoled" || themeKey == "Black") Color.Black else DarkIconGrayBackground
            } else {
                LightBitDarkerGrayBackground
            }
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
    val isMonet = remember(context, themeKey) {
        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
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

    val settingsManager = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    
    var fontKey by remember { 
        mutableStateOf(
            try {
                settingsManager.getString(context.getString(R.string.app_font_key), "default") ?: "default"
            } catch (e: Exception) {
                "default"
            }
        )
    }
    
    var fontScaleInt by remember { 
        mutableStateOf(
            try {
                settingsManager.getInt("app_font_scale", 100)
            } catch (e: Exception) {
                100
            }
        )
    }
    
    DisposableEffect(settingsManager) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == context.getString(R.string.app_font_key)) {
                fontKey = settingsManager.getString(key, "default") ?: "default"
            } else if (key == "app_font_scale") {
                fontScaleInt = settingsManager.getInt(key, 100)
            }
        }
        settingsManager.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            settingsManager.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val resolvedFontFamily = remember(fontKey) {
        when (fontKey) {
            "productsans" -> ProductSansFontFamily
            "comico" -> ComicoFontFamily
            "instrument_serif" -> InstrumentSerifFontFamily
            "manosque" -> ManosqueFontFamily
            "orbitron" -> OrbitronFontFamily
            "skyscapers" -> SkyscapersFontFamily
            "struggle" -> StruggleFontFamily
            "typefesse_claire_obscure" -> TypefesseClaireObscureFontFamily
            "typefesse_pleine" -> TypefessePleineFontFamily
            "unique" -> UniqueFontFamily
            "nevis" -> NevisFontFamily
            "nightydemo" -> NightyDemoFontFamily
            "ostrich_sans_bold" -> OstrichSansBoldFontFamily
            "ostrich_sans_inline" -> OstrichSansInlineFontFamily
            "rude" -> RudeFontFamily
            "shadowhand" -> ShadowHandFontFamily
            else -> androidx.compose.ui.text.font.FontFamily.Default
        }
    }
    
    val fontScale = fontScaleInt / 100f
    
    val dynamicTypography = remember(resolvedFontFamily, fontScale) {
        getTypography(resolvedFontFamily, fontScale)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = dynamicTypography,
        content = content
    )
}
