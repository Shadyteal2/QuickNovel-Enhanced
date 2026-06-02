package com.lagradost.quicknovel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lagradost.quicknovel.R

// Curated font families loaded dynamically from standard /res/font resources
val ProductSansFontFamily = FontFamily(
    Font(R.font.productsans_bold, FontWeight.Normal),
    Font(R.font.productsans_bold, FontWeight.Medium),
    Font(R.font.productsans_bold, FontWeight.SemiBold),
    Font(R.font.productsans_bold, FontWeight.Bold),
    Font(R.font.productsans_black, FontWeight.Black)
)

val ComicoFontFamily = FontFamily(Font(R.font.comico_regular, FontWeight.Normal))
val InstrumentSerifFontFamily = FontFamily(Font(R.font.instrument_serif_regular, FontWeight.Normal))
val ManosqueFontFamily = FontFamily(Font(R.font.manosque_regular, FontWeight.Normal))
val OrbitronFontFamily = FontFamily(Font(R.font.orbitron_bold, FontWeight.Bold))
val SkyscapersFontFamily = FontFamily(Font(R.font.skyscapers, FontWeight.Normal))
val TypefesseClaireObscureFontFamily = FontFamily(Font(R.font.typefesse_claire_obscure, FontWeight.Normal))
val TypefessePleineFontFamily = FontFamily(Font(R.font.typefesse_pleine, FontWeight.Normal))
val UniqueFontFamily = FontFamily(
    Font(R.font.unique_bold, FontWeight.Normal),
    Font(R.font.unique_bold, FontWeight.Bold)
)
val StruggleFontFamily = FontFamily(Font(R.font.struggle_regular, FontWeight.Normal))

val NevisFontFamily = FontFamily(Font(R.font.nevis, FontWeight.Normal))
val NightyDemoFontFamily = FontFamily(Font(R.font.nightydemo, FontWeight.Normal))
val OstrichSansBoldFontFamily = FontFamily(Font(R.font.ostrich_sans_bold, FontWeight.Bold))
val OstrichSansInlineFontFamily = FontFamily(Font(R.font.ostrich_sans_inline_regular, FontWeight.Normal))
val RudeFontFamily = FontFamily(Font(R.font.rude, FontWeight.Normal))
val ShadowHandFontFamily = FontFamily(Font(R.font.shadowhand, FontWeight.Normal))
val AlexandriaFlfFontFamily = FontFamily(Font(R.font.alexandriaflf, FontWeight.Normal))

/**
 * Returns a dynamically themed Typography based on a selected FontFamily and optional scale.
 */
fun getTypography(fontFamily: FontFamily, fontScale: Float = 1.0f): Typography {
    return Typography(
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (16 * fontScale).sp,
            lineHeight = (24 * fontScale).sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * fontScale).sp,
            lineHeight = (20 * fontScale).sp,
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (12 * fontScale).sp,
            lineHeight = (16 * fontScale).sp,
            letterSpacing = 0.4.sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (22 * fontScale).sp,
            lineHeight = (28 * fontScale).sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (16 * fontScale).sp,
            lineHeight = (24 * fontScale).sp,
            letterSpacing = 0.15.sp
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (14 * fontScale).sp,
            lineHeight = (20 * fontScale).sp,
            letterSpacing = 0.1.sp
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (14 * fontScale).sp,
            lineHeight = (20 * fontScale).sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * fontScale).sp,
            lineHeight = (16 * fontScale).sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (11 * fontScale).sp,
            lineHeight = (16 * fontScale).sp,
            letterSpacing = 0.5.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (32 * fontScale).sp,
            lineHeight = (40 * fontScale).sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (28 * fontScale).sp,
            lineHeight = (36 * fontScale).sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (24 * fontScale).sp,
            lineHeight = (32 * fontScale).sp,
            letterSpacing = 0.sp
        ),
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (57 * fontScale).sp,
            lineHeight = (64 * fontScale).sp,
            letterSpacing = (-0.25).sp
        ),
        displayMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (45 * fontScale).sp,
            lineHeight = (52 * fontScale).sp,
            letterSpacing = 0.sp
        ),
        displaySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (36 * fontScale).sp,
            lineHeight = (44 * fontScale).sp,
            letterSpacing = 0.sp
        )
    )
}

// Keep the standard Typography compatible as System Default
val Typography = getTypography(FontFamily.Default)

