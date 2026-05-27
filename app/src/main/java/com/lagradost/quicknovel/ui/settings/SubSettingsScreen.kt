package com.lagradost.quicknovel.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubSettingsScreen(
    xmlRes: Int,
    iconRes: Int,
    titleRes: Int,
    onBack: () -> Unit,
    onPreferenceClick: (key: String) -> Unit,
    onPreferenceChange: (key: String, value: Any) -> Unit
) {
    val context = LocalContext.current
    val title = stringResource(id = titleRes)

    // Observe SharedPreferences
    val sharedPrefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var changeTrigger by remember { mutableStateOf(0) }

    // Unified helper to read preference values dynamically
    fun getBoolean(key: String, default: Boolean): Boolean {
        return changeTrigger.run { 
            try {
                sharedPrefs.getBoolean(key, default)
            } catch (e: Exception) {
                default
            }
        }
    }

    fun getInt(key: String, default: Int): Int {
        return changeTrigger.run { 
            try {
                sharedPrefs.getInt(key, default)
            } catch (e: ClassCastException) {
                try {
                    // Gracefully fallback to Float coercion if Int read fails
                    sharedPrefs.getFloat(key, default.toFloat()).toInt()
                } catch (e2: Exception) {
                    default
                }
            } catch (e: Exception) {
                default
            }
        }
    }

    fun getString(key: String, default: String): String {
        return changeTrigger.run { 
            try {
                sharedPrefs.getString(key, default) ?: default
            } catch (e: Exception) {
                default
            }
        }
    }

    QuickNovelTheme {
        val imageUri = remember(sharedPrefs, changeTrigger) { sharedPrefs.getString(context.getString(R.string.background_image_key), null) }
        val hasBackground = !imageUri.isNullOrBlank()
        val containerColor = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = title,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            },
            containerColor = containerColor,
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Render specific section items based on xmlRes
                when (xmlRes) {
                    R.xml.settings_appearance -> {
                        // ─── Background Customizations Category ───
                        item { PreferenceHeader("Background & treatment") }

                        item {
                            ActionPreferenceCard(
                                title = "Custom Background Image",
                                summary = getString("background_image", "Tap to pick a custom background image"),
                                iconRes = R.drawable.ic_baseline_public_24,
                                onClick = { onPreferenceClick("background_image") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Background Effect Preset",
                                summary = getBackgroundEffectLabel(context, getString("background_effect_mode", "none")),
                                iconRes = R.drawable.ic_baseline_tune_24,
                                onClick = { onPreferenceClick("background_effect_mode") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Reset Background",
                                summary = "Remove custom background and return to default",
                                iconRes = R.drawable.baseline_restore_page_24,
                                onClick = { onPreferenceClick("reset_background") }
                            )
                        }

                        item {
                            StepSelectorPreferenceCard(
                                title = "Background Blur",
                                value = getInt("background_blur", 0),
                                min = 0,
                                max = 100,
                                step = 5,
                                valueSuffix = "%",
                                iconRes = R.drawable.ic_baseline_tune_24,
                                presets = listOf(0, 10, 25, 50, 80),
                                onValueChange = { value ->
                                    sharedPrefs.edit().putInt("background_blur", value).apply()
                                    onPreferenceChange("background_blur", value)
                                    changeTrigger++
                                }
                            )
                        }

                        item {
                            StepSelectorPreferenceCard(
                                title = "Background Dim",
                                value = getInt("background_dim", 0),
                                min = 0,
                                max = 100,
                                step = 5,
                                valueSuffix = "%",
                                iconRes = R.drawable.ic_baseline_tune_24,
                                presets = listOf(0, 10, 25, 50, 80),
                                onValueChange = { value ->
                                    sharedPrefs.edit().putInt("background_dim", value).apply()
                                    onPreferenceChange("background_dim", value)
                                    changeTrigger++
                                }
                            )
                        }

                        item {
                            StepSelectorPreferenceCard(
                                title = "Background Grain",
                                value = getInt("background_grain", 0),
                                min = 0,
                                max = 100,
                                step = 5,
                                valueSuffix = "%",
                                iconRes = R.drawable.ic_baseline_tune_24,
                                presets = listOf(0, 10, 25, 50, 80),
                                onValueChange = { value ->
                                    sharedPrefs.edit().putInt("background_grain", value).apply()
                                    onPreferenceChange("background_grain", value)
                                    changeTrigger++
                                }
                            )
                        }

                        item {
                            StepSelectorPreferenceCard(
                                title = "Background Vignette",
                                value = getInt("background_vignette", 0),
                                min = 0,
                                max = 100,
                                step = 5,
                                valueSuffix = "%",
                                iconRes = R.drawable.ic_baseline_tune_24,
                                presets = listOf(0, 10, 25, 50, 80),
                                onValueChange = { value ->
                                    sharedPrefs.edit().putInt("background_vignette", value).apply()
                                    onPreferenceChange("background_vignette", value)
                                    changeTrigger++
                                }
                            )
                        }

                        // ─── Theme & Branding Category ───
                        item { PreferenceHeader("Theme & font layout") }

                        item {
                            ActionPreferenceCard(
                                title = "Novel Detail Layout",
                                summary = getDetailScreenStyleLabel(getString("detail_screen_style", "0")),
                                iconRes = R.drawable.ic_baseline_menu_book_24,
                                onClick = { onPreferenceClick("detail_screen_style") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "App Theme",
                                summary = getString("theme_key", "Amoled"),
                                iconRes = R.drawable.ic_baseline_color_lens_24,
                                onClick = { onPreferenceClick("theme_key") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Accent Color",
                                summary = getString("primary_color_key", "Banana"),
                                iconRes = R.drawable.ic_stars_special,
                                onClick = { onPreferenceClick("primary_color_key") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "App Font Style",
                                summary = getFontLabel(getString("app_font_key", "default")),
                                iconRes = R.drawable.ic_baseline_font_download_24,
                                onClick = { onPreferenceClick("app_font_key") }
                            )
                        }

                        // ─── Our NEW Premium Dynamic Font Size Step Selector! ───
                        item {
                            StepSelectorPreferenceCard(
                                title = "Global Font Size Scale",
                                value = getInt("app_font_scale", 100),
                                min = 80,
                                max = 130,
                                step = 5,
                                valueSuffix = "%",
                                iconRes = R.drawable.ic_baseline_font_download_24,
                                onValueChange = { value ->
                                    sharedPrefs.edit().putInt("app_font_scale", value).apply()
                                    onPreferenceChange("app_font_scale", value)
                                    changeTrigger++
                                }
                            )
                        }

                        item {
                            SwitchPreferenceCard(
                                title = "Custom Background in Reader",
                                summary = "Apply global background image and effect presets to the reader",
                                checked = getBoolean("reader_background", false),
                                iconRes = R.drawable.ic_baseline_menu_book_24,
                                onCheckedChange = { checked ->
                                    sharedPrefs.edit().putBoolean("reader_background", checked).apply()
                                    onPreferenceChange("reader_background", checked)
                                    changeTrigger++
                                }
                            )
                        }

                        item {
                            SwitchPreferenceCard(
                                title = "Tactile Cover Response",
                                summary = "Enable premium 3D physical tilt effect when touching book covers",
                                checked = getBoolean("library_tactile_response", true),
                                iconRes = R.drawable.ic_baseline_stay_current_portrait_24,
                                onCheckedChange = { checked ->
                                    sharedPrefs.edit().putBoolean("library_tactile_response", checked).apply()
                                    onPreferenceChange("library_tactile_response", checked)
                                    changeTrigger++
                                }
                            )
                        }

                        item {
                            ExpressiveSliderPreferenceCard(
                                title = "Navigation Bar Width",
                                value = getInt("navbar_width", 330),
                                min = 220,
                                max = 440,
                                valueSuffix = "dp",
                                step = 5,
                                iconRes = R.drawable.ic_baseline_tune_24,
                                onValueChange = { value ->
                                    sharedPrefs.edit().putInt("navbar_width", value).apply()
                                    onPreferenceChange("navbar_width", value)
                                    changeTrigger++
                                }
                            )
                        }
                    }

                    R.xml.settings_vibe -> {
                        // ─── Vibe & Aura Settings Category ───
                        item { PreferenceHeader("Experimental Aesthetics") }

                        item {
                            SwitchPreferenceCard(
                                title = "Experimental Visuals",
                                summary = "Enable premium generative visuals (Higher Power Consumption)",
                                checked = getBoolean("experimental_visuals", false),
                                iconRes = R.drawable.ic_baseline_star_24,
                                onCheckedChange = { checked ->
                                    sharedPrefs.edit().putBoolean("experimental_visuals", checked).apply()
                                    onPreferenceChange("experimental_visuals", checked)
                                    changeTrigger++
                                }
                            )
                        }

                        if (getBoolean("experimental_visuals", false)) {
                            item { PreferenceHeader("Living Elements") }

                            item {
                                SwitchPreferenceCard(
                                    title = "Living Glass Background",
                                    summary = "Fluid organic gradients that morph with your interactions",
                                    checked = getBoolean("living_glass", false),
                                    iconRes = R.drawable.ic_baseline_star_24,
                                    onCheckedChange = { checked ->
                                        sharedPrefs.edit().putBoolean("living_glass", checked).apply()
                                        onPreferenceChange("living_glass", checked)
                                        changeTrigger++
                                    }
                                )
                            }

                            if (getBoolean("living_glass", false)) {
                                item {
                                    ExpressiveSliderPreferenceCard(
                                        title = "Aura Visibility Intensity",
                                        value = getInt("aura_intensity", 70),
                                        min = 0,
                                        max = 100,
                                        valueSuffix = "%",
                                        iconRes = R.drawable.ic_baseline_tune_24,
                                        onValueChange = { value ->
                                            sharedPrefs.edit().putInt("aura_intensity", value).apply()
                                            onPreferenceChange("aura_intensity", value)
                                            changeTrigger++
                                        }
                                    )
                                }

                                item {
                                    ActionPreferenceCard(
                                        title = "Aura Color Palette",
                                        summary = getAuraPaletteLabel(getString("aura_palette", "nebula")),
                                        iconRes = R.drawable.ic_baseline_color_lens_24,
                                        onClick = { onPreferenceClick("aura_palette") }
                                    )
                                }

                                item {
                                    ExpressiveSliderPreferenceCard(
                                        title = "Animation Velocity",
                                        value = getInt("aura_speed", 100),
                                        min = 50,
                                        max = 200,
                                        valueSuffix = "%",
                                        iconRes = R.drawable.ic_baseline_tune_24,
                                        onValueChange = { value ->
                                            sharedPrefs.edit().putInt("aura_speed", value).apply()
                                            onPreferenceChange("aura_speed", value)
                                            changeTrigger++
                                        }
                                    )
                                }
                            }
                        }

                        item { PreferenceHeader("Luminescent Engine") }

                        item {
                            SwitchPreferenceCard(
                                title = "Luminescent Reader",
                                summary = "Adds a soft atmospheric glow to text and screen edges",
                                checked = getBoolean("luminescent_reader", false),
                                iconRes = R.drawable.ic_baseline_menu_book_24,
                                onCheckedChange = { checked ->
                                    sharedPrefs.edit().putBoolean("luminescent_reader", checked).apply()
                                    onPreferenceChange("luminescent_reader", checked)
                                    changeTrigger++
                                }
                            )
                        }

                        if (getBoolean("luminescent_reader", false)) {
                            item {
                                ExpressiveSliderPreferenceCard(
                                    title = "Glow Intensity",
                                    value = getInt("luminescent_intensity", 50),
                                    min = 0,
                                    max = 100,
                                    valueSuffix = "%",
                                    iconRes = R.drawable.ic_baseline_tune_24,
                                    onValueChange = { value ->
                                        sharedPrefs.edit().putInt("luminescent_intensity", value).apply()
                                        onPreferenceChange("luminescent_intensity", value)
                                        changeTrigger++
                                    }
                                )
                            }
                        }
                    }

                    R.xml.settings_general -> {
                        // ─── Reader / General Settings Category ───
                        item { PreferenceHeader("General preferences") }

                        item {
                            ActionPreferenceCard(
                                title = "App Display Language",
                                summary = "Change the application display language",
                                iconRes = R.drawable.ic_baseline_language_24,
                                onClick = { onPreferenceClick("locale_key") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Book Rating Format",
                                summary = if (getString("rating_format", "star") == "star") "Star (1-5)" else "Decimal (1-10)",
                                iconRes = R.drawable.ic_baseline_star_24,
                                onClick = { onPreferenceClick("rating_format") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Library Display Mode",
                                summary = if (getString("download_format", "list") == "list") "List View" else "Grid View",
                                iconRes = R.drawable.ic_baseline_grid_view_24,
                                onClick = { onPreferenceClick("download_format") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Library Navigation Style",
                                summary = if (getString("library_nav_style", "0") == "0") "Pill Drawer" else "Swipe View",
                                iconRes = R.drawable.ic_baseline_edit_24,
                                onClick = { onPreferenceClick("library_nav_style") }
                            )
                        }

                        item {
                            SwitchPreferenceCard(
                                title = "Show app updates",
                                summary = "Automatically search for new updates on start",
                                checked = getBoolean("auto_update", true),
                                iconRes = R.drawable.ic_baseline_notifications_active_24,
                                onCheckedChange = { checked ->
                                    sharedPrefs.edit().putBoolean("auto_update", checked).apply()
                                    onPreferenceChange("auto_update", checked)
                                    changeTrigger++
                                }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Updates Check Interval",
                                summary = getIntervalLabel(getString("updates_sync_interval", "12")),
                                iconRes = R.drawable.ic_baseline_autorenew_24,
                                onClick = { onPreferenceClick("updates_sync_interval") }
                            )
                        }

                        item {
                            SwitchPreferenceCard(
                                title = "Remove Bloat",
                                summary = "Removes the title and translator from all pages, will cause longer generation time",
                                checked = getBoolean("remove_bloat_key", true),
                                iconRes = R.drawable.ic_baseline_delete_outline_24,
                                onCheckedChange = { checked ->
                                    sharedPrefs.edit().putBoolean("remove_bloat_key", checked).apply()
                                    onPreferenceChange("remove_bloat_key", checked)
                                    changeTrigger++
                                }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Check for Update",
                                summary = "Force scan GitHub for newer versions of the app",
                                iconRes = R.drawable.ic_baseline_system_update_24,
                                onClick = { onPreferenceClick("manual_check_update") }
                            )
                        }
                    }

                    R.xml.settings_storage -> {
                        // ─── Storage Settings Category ───
                        item { PreferenceHeader("Backup & storage locations") }

                        item {
                            ActionPreferenceCard(
                                title = "Download path",
                                summary = getString("download_path_pref", "Downloads/Epub"),
                                iconRes = R.drawable.netflix_download,
                                onClick = { onPreferenceClick("download_path_key") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Backup data",
                                summary = "Backup your library and settings locally",
                                iconRes = R.drawable.baseline_save_as_24,
                                onClick = { onPreferenceClick("backup_key") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Restore settings",
                                summary = "Restore data from local backup file",
                                iconRes = R.drawable.baseline_restore_page_24,
                                onClick = { onPreferenceClick("restore_key") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Manage Notes & Aliases",
                                summary = "View and delete saved notes and character renames",
                                iconRes = R.drawable.ic_baseline_collections_bookmark_24,
                                onClick = { onPreferenceClick("manage_data_key") }
                            )
                        }
                    }

                    R.xml.settings_dev -> {
                        // ─── Advanced Developer Settings Category ───
                        item { PreferenceHeader("Diagnostics & system overrides") }

                        item {
                            ActionPreferenceCard(
                                title = "Show Logcat 🐈",
                                summary = "View and copy real-time debug output logs",
                                iconRes = R.drawable.baseline_description_24,
                                onClick = { onPreferenceClick("show_logcat_key") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Check for Plugin Updates",
                                summary = "Download and update novel providers from GitHub",
                                iconRes = R.drawable.ic_baseline_autorenew_24,
                                onClick = { onPreferenceClick("plugin_sync_key") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Import Provider APK",
                                summary = "Pick a provider APK file directly — bypass GitHub sync",
                                iconRes = R.drawable.ic_baseline_add_24,
                                onClick = { onPreferenceClick("provider_apk_import_key") }
                            )
                        }

                        item {
                            SwitchPreferenceCard(
                                title = "Automatic Cloudflare Solving",
                                summary = "Automatically attempt to solve Cloudflare challenges using WebView",
                                checked = getBoolean("cloudflare_auto_solve", false),
                                iconRes = R.drawable.ic_baseline_cloud_24,
                                onCheckedChange = { checked ->
                                    sharedPrefs.edit().putBoolean("cloudflare_auto_solve", checked).apply()
                                    onPreferenceChange("cloudflare_auto_solve", checked)
                                    changeTrigger++
                                }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Resolve Cloudflare Challenge",
                                summary = "Manually open a WebView to solve a challenge for a specific site",
                                iconRes = R.drawable.ic_baseline_public_24,
                                onClick = { onPreferenceClick("cloudflare_resolve_manual") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Clear Network Cookies",
                                summary = "Removes all stored Cloudflare and site cookies",
                                iconRes = R.drawable.ic_baseline_warning_24,
                                onClick = { onPreferenceClick("clear_cookies_key") }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun PreferenceHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun SwitchPreferenceCard(
    title: String,
    summary: String,
    checked: Boolean,
    iconRes: Int,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (summary.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun StepSelectorPreferenceCard(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int = 5,
    valueSuffix: String,
    iconRes: Int,
    presets: List<Int> = listOf(80, 100, 115, 130),
    onValueChange: (Int) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Glowing value indicator
                Box(
                    modifier = Modifier
                        .glassCard(
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            strokeWidth = 0.5.dp
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$value$valueSuffix",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Decrement Button
                OutlinedIconButton(
                    onClick = {
                        if (value > min) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onValueChange(value - step)
                        }
                    },
                    enabled = value > min,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "-",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Preset Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = value == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onValueChange(preset)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$preset$valueSuffix",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Increment Button
                OutlinedIconButton(
                    onClick = {
                        if (value < max) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onValueChange(value + step)
                        }
                    },
                    enabled = value < max,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "+",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressiveSliderPreferenceCard(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    valueSuffix: String,
    step: Int = 1,
    iconRes: Int,
    useRuler: Boolean = false,
    onValueChange: (Int) -> Unit
) {
    // Local live value state for smooth UI updates during drag
    var liveValue by remember(value) { mutableStateOf(value) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Glowing expressive float indicator
                Box(
                    modifier = Modifier
                        .glassCard(
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            strokeWidth = 0.5.dp
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$liveValue$valueSuffix",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (useRuler) {
                TactileRulerSliderCompose(
                    value = value.toFloat(),
                    valueFrom = min.toFloat(),
                    valueTo = max.toFloat(),
                    stepSize = step.toFloat(),
                    onValueChange = { 
                        liveValue = it.roundToInt()
                        onValueChange(it.roundToInt())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                )
            } else {
                // Premium Material 3 Expressive Slider with Horizontal Drag Scroll Safety
                PremiumExpressiveSlider(
                    value = value.toFloat(),
                    onValueChangeLive = { liveValue = it.roundToInt() },
                    onValueChangeFinished = { onValueChange(it.roundToInt()) },
                    valueRange = min.toFloat()..max.toFloat(),
                    step = step,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                )
            }
        }
    }
}

@Composable
fun TactileRulerSliderCompose(
    value: Float,
    valueFrom: Float,
    valueTo: Float,
    stepSize: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            com.lagradost.quicknovel.ui.custom.TactileRulerSlider(context).apply {
                this.valueFrom = valueFrom
                this.valueTo = valueTo
                this.stepSize = stepSize
                this.value = value
                setOnValueChangeListener { _, newValue, fromUser ->
                    if (fromUser) {
                        onValueChange(newValue)
                    }
                }
            }
        },
        update = { view ->
            view.valueFrom = valueFrom
            view.valueTo = valueTo
            view.stepSize = stepSize
            view.value = value
        },
        modifier = modifier.height(60.dp)
    )
}

@Composable
fun PremiumExpressiveSlider(
    value: Float,
    onValueChangeLive: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedRange<Float>,
    step: Int = 1,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Maintain local drag value so the slider updates at 120 FPS without SharedPreferences lag
    var dragValue by remember(value) { mutableStateOf(value) }

    val animatedThumbScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 1.25f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "thumb_scale"
    )

    val animatedTrackHeight by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 16f else 12f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "track_height"
    )

    val rangeMin = valueRange.start
    val rangeMax = valueRange.endInclusive
    val rangeLength = rangeMax - rangeMin

    // Use dragValue when dragging, otherwise use the parent value
    val activeValue = if (isDragging) dragValue else value
    val proportion = if (rangeLength > 0f) {
        ((activeValue - rangeMin) / rangeLength).coerceIn(0f, 1f)
    } else {
        0f
    }

    val thumbSizeDp = 24.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(valueRange, step) { // Re-bind pointerInput when range or step changes
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragValue = value
                    },
                    onDragEnd = {
                        isDragging = false
                        val finalValue = if (step > 1) {
                            (Math.round(dragValue / step) * step).toFloat().coerceIn(rangeMin, rangeMax)
                        } else {
                            dragValue
                        }
                        onValueChangeFinished(finalValue)
                    },
                    onDragCancel = {
                        isDragging = false
                        val finalValue = if (step > 1) {
                            (Math.round(dragValue / step) * step).toFloat().coerceIn(rangeMin, rangeMax)
                        } else {
                            dragValue
                        }
                        onValueChangeFinished(finalValue)
                    },
                    onHorizontalDrag = { change: PointerInputChange, dragAmount: Float ->
                        change.consume()
                        val trackWidth = widthPx - thumbSizePx
                        if (trackWidth > 0) {
                            // Accumulate relative drag delta in value coordinates
                            val deltaValue = (dragAmount / trackWidth) * rangeLength
                            dragValue = (dragValue + deltaValue).coerceIn(rangeMin, rangeMax)
                            
                            val roundedValue = if (step > 1) {
                                (Math.round(dragValue / step) * step).toFloat().coerceIn(rangeMin, rangeMax)
                            } else {
                                dragValue
                            }
                            onValueChangeLive(roundedValue)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Inactive Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = thumbSizeDp / 2)
                .height(animatedTrackHeight.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        )

        // Active Track (Gradient)
        Box(
            modifier = Modifier
                .padding(horizontal = thumbSizeDp / 2)
                .fillMaxWidth(fraction = proportion)
                .height(animatedTrackHeight.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
        )

        // Thumb
        val thumbOffsetPx = proportion * (widthPx - thumbSizePx)
        
        Box(
            modifier = Modifier
                .offset { IntOffset(x = thumbOffsetPx.roundToInt(), y = 0) }
                .size((24f * animatedThumbScale).dp)
                .shadow(
                    elevation = if (isDragging) 8.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary,
                    spotColor = MaterialTheme.colorScheme.primary
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

@Composable
fun ActionPreferenceCard(
    title: String,
    summary: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (summary.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_arrow_forward_24),
                contentDescription = "Navigate icon",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─── Human Readable String Formatting Helpers ────────────────────────────────────

fun getBackgroundEffectLabel(context: Context, mode: String): String {
    return when (mode) {
        "none" -> "None"
        "noir" -> "Noir"
        "sepia" -> "Sepia"
        "blockify" -> "Blockify"
        "threshold" -> "Threshold"
        "edge_detection" -> "Edge Detection"
        "dots" -> "Dots"
        "dithering" -> "Dithering"
        "voronoi" -> "Voronoi"
        else -> mode
    }
}

fun getDetailScreenStyleLabel(style: String): String {
    return when (style) {
        "classic", "2" -> "Classic Layout"
        "modern", "1" -> "Modern Layout"
        else -> "Default Layout"
    }
}

fun getAuraPaletteLabel(palette: String): String {
    return when (palette) {
        "nebula" -> "Deep Nebula"
        "garden" -> "Zen Garden"
        "minimal" -> "Minimalist Earth"
        "shady" -> "Shady"
        "browny" -> "Browny"
        else -> palette
    }
}

fun getIntervalLabel(hours: String): String {
    return when (hours) {
        "0" -> "Disabled"
        "1" -> "Every hour"
        "3" -> "Every 3 hours"
        "6" -> "Every 6 hours"
        "12" -> "Every 12 hours"
        "24" -> "Every 24 hours"
        else -> "Every $hours hours"
    }
}

fun getFontLabel(key: String): String {
    return when (key) {
        "productsans" -> "Product Sans"
        "comico" -> "Comico"
        "instrument_serif" -> "Instrument Serif"
        "manosque" -> "Manosque"
        "orbitron" -> "Orbitron"
        "skyscapers" -> "Skyscapers"
        "struggle" -> "Struggle"
        "typefesse_claire_obscure" -> "Typefesse Claire-Obscure"
        "typefesse_pleine" -> "Typefesse Pleine"
        "unique" -> "Unique"
        "nevis" -> "Nevis"
        "nightydemo" -> "Nighty Demo"
        "ostrich_sans_bold" -> "Ostrich Sans Bold"
        "ostrich_sans_inline" -> "Ostrich Sans Inline"
        "rude" -> "Rude"
        "shadowhand" -> "Shadow Hand"
        else -> "System Default"
    }
}
