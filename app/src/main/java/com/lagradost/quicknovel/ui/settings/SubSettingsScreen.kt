package com.lagradost.quicknovel.ui.settings

import com.lagradost.quicknovel.ui.theme.LoadingIndicator

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
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
import com.lagradost.quicknovel.ui.theme.VibePrefs
import com.lagradost.quicknovel.ui.theme.rememberAccentGradientBrush
import com.lagradost.quicknovel.ui.theme.staggeredEntrance
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.lagradost.quicknovel.util.GoogleDriveSyncManager
import com.lagradost.quicknovel.util.BackupUtils
import com.lagradost.quicknovel.GoogleSyncWorkHelper
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.launchSafe
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.Activity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lagradost.quicknovel.ui.theme.rememberShimmerBrush
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.lagradost.quicknovel.TelegramBackupPrefs
import com.lagradost.quicknovel.sync.TelegramBackupWorker


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

    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            changeTrigger++
        }
    }

    DisposableEffect(sharedPrefs) {
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    var showCustomRateLimitDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val settingsViewModel: SettingsViewModel = viewModel()
    val imageCacheSize by settingsViewModel.imageCacheSize.collectAsStateWithLifecycle()
    val networkCacheSize by settingsViewModel.networkCacheSize.collectAsStateWithLifecycle()
    val codeCacheSize by settingsViewModel.codeCacheSize.collectAsStateWithLifecycle()
    val crashLogSize by settingsViewModel.crashLogSize.collectAsStateWithLifecycle()
    val chapterCacheSize by settingsViewModel.chapterCacheSize.collectAsStateWithLifecycle()
    val webViewCacheSize by settingsViewModel.webViewCacheSize.collectAsStateWithLifecycle()
    var cacheTrigger by remember { mutableStateOf(0) }
    var isCacheExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(cacheTrigger) {
        settingsViewModel.loadSizes(context)
    }

    // Cache all preferences in memory, refreshed ONLY when changeTrigger is incremented.
    // This prevents slow disk lookups/mutex locking on sharedPrefs inside scrolling compositions!
    val cachedPrefs = remember(changeTrigger) {
        sharedPrefs.all.toMap()
    }

    // Unified helper to read preference values dynamically from memory cache
    fun getBoolean(key: String, default: Boolean): Boolean {
        return try {
            cachedPrefs[key] as? Boolean ?: default
        } catch (e: Exception) {
            default
        }
    }

    fun getInt(key: String, default: Int): Int {
        val raw = cachedPrefs[key] ?: return default
        if (raw is Int) return raw
        if (raw is Float) return raw.toInt()
        if (raw is Long) return raw.toInt()
        if (raw is String) return raw.toIntOrNull() ?: default
        return default
    }

    fun getString(key: String, default: String): String {
        return cachedPrefs[key] as? String ?: default
    }

    QuickNovelTheme {
        if (showCustomRateLimitDialog) {
            val rateLimitCurrent = getInt("custom_rate_limit", 0)
            CustomRateLimitDialog(
                initialValue = rateLimitCurrent,
                onDismiss = { showCustomRateLimitDialog = false },
                onConfirm = { value ->
                    sharedPrefs.edit().putInt("custom_rate_limit", value).apply()
                    onPreferenceChange("custom_rate_limit", value)
                    changeTrigger++
                }
            )
        }

        val imageUri = remember(cachedPrefs) { getString(context.getString(R.string.background_image_key), "") }
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
                    .padding(paddingValues)
                    .imePadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val staggeredScope = StaggeredLazyListScope(this)
                with(staggeredScope) {
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
                            SwitchPreferenceCard(
                                title = "Accent Gradient",
                                summary = "Blend accent into a two-stop gradient on buttons and indicators",
                                checked = getBoolean(VibePrefs.ACCENT_GRADIENT_ENABLED, false),
                                iconRes = R.drawable.ic_baseline_color_lens_24,
                                onCheckedChange = { checked ->
                                    sharedPrefs.edit().putBoolean(VibePrefs.ACCENT_GRADIENT_ENABLED, checked).apply()
                                    onPreferenceChange(VibePrefs.ACCENT_GRADIENT_ENABLED, checked)
                                    changeTrigger++
                                }
                            )
                        }

                        if (getBoolean(VibePrefs.ACCENT_GRADIENT_ENABLED, false)) {
                            item {
                                ActionPreferenceCard(
                                    title = "Gradient End Color",
                                    summary = "Tap to pick the second color of the accent gradient",
                                    iconRes = R.drawable.ic_baseline_color_lens_24,
                                    onClick = { onPreferenceClick("accent_gradient_end_color") }
                                )
                            }
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
                        // ─── Vibe & Aura Settings ───────────────────────────────

                        item { PreferenceHeader("Performance Settings") }

                        item {
                            SwitchPreferenceCard(
                                title = "Performance Mode (Eco Mode)",
                                summary = "Disable all visual shimmers, background blur, and spring animations to save battery and speed up older devices",
                                checked = getBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false),
                                iconRes = R.drawable.ic_baseline_tune_24,
                                onCheckedChange = { checked ->
                                    sharedPrefs.edit().putBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, checked).apply()
                                    onPreferenceChange(VibePrefs.PERFORMANCE_MODE_ENABLED, checked)
                                    changeTrigger++
                                }
                            )
                        }



                        // ─ PREMIUM VISUALS GROUP ────────────────────────────
                        item { PreferenceHeader("Premium Visuals") }

                        item {
                            val isPremiumVisualsEnabled = getBoolean(VibePrefs.PREMIUM_VISUALS_ENABLED, false)
                            NestedSettingsGroupCard {
                                MasterSwitchPreferenceRow(
                                    title = "Premium Visuals",
                                    summary = "Unlock Cover Aura Glow & Reader Ink Flow — best on high-end devices",
                                    checked = isPremiumVisualsEnabled,
                                    iconRes = R.drawable.ic_baseline_star_24,
                                    onCheckedChange = { checked ->
                                        sharedPrefs.edit().putBoolean(VibePrefs.PREMIUM_VISUALS_ENABLED, checked).apply()
                                        onPreferenceChange(VibePrefs.PREMIUM_VISUALS_ENABLED, checked)
                                        changeTrigger++
                                    }
                                )

                                AnimatedVisibility(
                                    visible = isPremiumVisualsEnabled,
                                    enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                    exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)
                                        NestedSwitchRow(
                                            title = "Cover Aura Glow",
                                            summary = "Each book cover radiates its own unique dominant color glow",
                                            checked = getBoolean(VibePrefs.COVER_AURA_GLOW, false),
                                            iconRes = R.drawable.ic_baseline_color_lens_24,
                                            onCheckedChange = { checked ->
                                                sharedPrefs.edit().putBoolean(VibePrefs.COVER_AURA_GLOW, checked).apply()
                                                onPreferenceChange(VibePrefs.COVER_AURA_GLOW, checked)
                                                changeTrigger++
                                            }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)
                                        NestedSwitchRow(
                                            title = "Reader Ink Flow",
                                            summary = "Reading surface shifts between cool and warm tones as you scroll",
                                            checked = getBoolean(VibePrefs.READER_INK_FLOW, false),
                                            iconRes = R.drawable.ic_baseline_menu_book_24,
                                            onCheckedChange = { checked ->
                                                sharedPrefs.edit().putBoolean(VibePrefs.READER_INK_FLOW, checked).apply()
                                                onPreferenceChange(VibePrefs.READER_INK_FLOW, checked)
                                                changeTrigger++
                                            }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)
                                        NestedSwitchRow(
                                            title = "Asymmetric & Morphing Shapes",
                                            summary = "Give cards and covers modern asymmetric shapes and dynamic tap-morphs",
                                            checked = getBoolean(VibePrefs.ASYMMETRIC_SHAPES_ENABLED, false),
                                            iconRes = R.drawable.ic_baseline_grid_view_24,
                                            onCheckedChange = { checked ->
                                                sharedPrefs.edit().putBoolean(VibePrefs.ASYMMETRIC_SHAPES_ENABLED, checked).apply()
                                                onPreferenceChange(VibePrefs.ASYMMETRIC_SHAPES_ENABLED, checked)
                                                changeTrigger++
                                            }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)
                                        NestedSwitchRow(
                                            title = "Staggered Content Entrances",
                                            summary = "Fade and cascade list items into layout view sequentially",
                                            checked = getBoolean(VibePrefs.STAGGERED_ENTRANCES_ENABLED, false),
                                            iconRes = R.drawable.ic_baseline_autorenew_24,
                                            onCheckedChange = { checked ->
                                                sharedPrefs.edit().putBoolean(VibePrefs.STAGGERED_ENTRANCES_ENABLED, checked).apply()
                                                onPreferenceChange(VibePrefs.STAGGERED_ENTRANCES_ENABLED, checked)
                                                changeTrigger++
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // ─ AESTHETIC PERSONALIZATION GROUP ─────────────────
                        item { PreferenceHeader("Aesthetic Personalization") }

                        item {
                            val isAestheticPersonaEnabled = getBoolean(VibePrefs.AESTHETIC_PERSONA_ENABLED, false)
                            NestedSettingsGroupCard {
                                MasterSwitchPreferenceRow(
                                    title = "Aesthetic Personalization",
                                    summary = "Glass Opacity, Card Border Style & Noise Texture surface controls",
                                    checked = isAestheticPersonaEnabled,
                                    iconRes = R.drawable.ic_baseline_tune_24,
                                    onCheckedChange = { checked ->
                                        sharedPrefs.edit().putBoolean(VibePrefs.AESTHETIC_PERSONA_ENABLED, checked).apply()
                                        onPreferenceChange(VibePrefs.AESTHETIC_PERSONA_ENABLED, checked)
                                        changeTrigger++
                                    }
                                )

                                AnimatedVisibility(
                                    visible = isAestheticPersonaEnabled,
                                    enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                    exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)

                                        // Glass Opacity Control
                                        val isGlassOpacityEnabled = getBoolean(VibePrefs.GLASS_OPACITY + "_enabled", false)
                                        NestedSwitchRow(
                                            title = "Glass Opacity Control",
                                            summary = "Tune the transparency depth of all glass surfaces",
                                            checked = isGlassOpacityEnabled,
                                            iconRes = R.drawable.ic_baseline_tune_24,
                                            onCheckedChange = { checked ->
                                                sharedPrefs.edit().putBoolean(VibePrefs.GLASS_OPACITY + "_enabled", checked).apply()
                                                onPreferenceChange(VibePrefs.GLASS_OPACITY + "_enabled", checked)
                                                changeTrigger++
                                            }
                                        )

                                        AnimatedVisibility(
                                            visible = isGlassOpacityEnabled,
                                            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                            exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                NestedSliderRow(
                                                    title = "Glass Opacity",
                                                    value = getInt(VibePrefs.GLASS_OPACITY, 80),
                                                    min = 5,
                                                    max = 95,
                                                    valueSuffix = "%",
                                                    iconRes = R.drawable.ic_baseline_tune_24,
                                                    onValueChange = { value ->
                                                        sharedPrefs.edit().putInt(VibePrefs.GLASS_OPACITY, value).apply()
                                                        onPreferenceChange(VibePrefs.GLASS_OPACITY, value)
                                                        changeTrigger++
                                                    }
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)

                                        // Card Border Style
                                        val isCardBorderEnabled = getBoolean(VibePrefs.CARD_BORDER_STYLE + "_enabled", false)
                                        NestedSwitchRow(
                                            title = "Card Border Style",
                                            summary = "Add animated border accents to all glass cards",
                                            checked = isCardBorderEnabled,
                                            iconRes = R.drawable.ic_baseline_tune_24,
                                            onCheckedChange = { checked ->
                                                sharedPrefs.edit().putBoolean(VibePrefs.CARD_BORDER_STYLE + "_enabled", checked).apply()
                                                onPreferenceChange(VibePrefs.CARD_BORDER_STYLE + "_enabled", checked)
                                                changeTrigger++
                                            }
                                        )

                                        AnimatedVisibility(
                                            visible = isCardBorderEnabled,
                                            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                            exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                NestedActionRow(
                                                    title = "Border Style",
                                                    summary = when (getString(VibePrefs.CARD_BORDER_STYLE, "none")) {
                                                        "glow" -> "Glow — soft accent halo"
                                                        "gradient" -> "Gradient — rotating sweep"
                                                        "neon" -> "Neon — electric pulse"
                                                        else -> "None"
                                                    },
                                                    iconRes = R.drawable.ic_baseline_color_lens_24,
                                                    onClick = { onPreferenceClick("card_border_style_picker") }
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)

                                        // Noise Texture
                                        val isNoiseEnabled = getBoolean(VibePrefs.NOISE_TEXTURE_ENABLED, false)
                                        NestedSwitchRow(
                                            title = "Noise Texture Overlay",
                                            summary = "Subtle paper-like grain rendered once and cached — zero frame-rate impact",
                                            checked = isNoiseEnabled,
                                            iconRes = R.drawable.ic_baseline_tune_24,
                                            onCheckedChange = { checked ->
                                                sharedPrefs.edit().putBoolean(VibePrefs.NOISE_TEXTURE_ENABLED, checked).apply()
                                                onPreferenceChange(VibePrefs.NOISE_TEXTURE_ENABLED, checked)
                                                changeTrigger++
                                            }
                                        )

                                        AnimatedVisibility(
                                            visible = isNoiseEnabled,
                                            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                            exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                NestedSliderRow(
                                                    title = "Grain Intensity",
                                                    value = getInt(VibePrefs.NOISE_TEXTURE_INTENSITY, 30),
                                                    min = 5,
                                                    max = 80,
                                                    valueSuffix = "%",
                                                    iconRes = R.drawable.ic_baseline_tune_24,
                                                    onValueChange = { value ->
                                                        sharedPrefs.edit().putInt(VibePrefs.NOISE_TEXTURE_INTENSITY, value).apply()
                                                        onPreferenceChange(VibePrefs.NOISE_TEXTURE_INTENSITY, value)
                                                        changeTrigger++
                                                    }
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ─ LUMINESCENT ENGINE (existing) ──────────────────
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
                                title = "Library Navigation Style",
                                summary = if (getString("library_nav_style", "1") == "0") "Pill Drawer" else "Swipe View",
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

                        // ─── Translation Settings Category ───
                        item { PreferenceHeader("Translation Settings") }

                        item {
                            TranslationSettingsCard(sharedPrefs, { changeTrigger++ })
                        }
                    }

                    R.xml.settings_storage -> {
                        // ─── Storage Settings Category ───
                        item { PreferenceHeader("Backup & storage locations") }

                        item {
                            CloudSyncPreferencesCard()
                        }

                        item {
                            TelegramBackupPreferencesCard()
                        }

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
                                title = "Automatic Backup",
                                summary = when (getString("auto_backup_interval", "never")) {
                                    "daily" -> "Daily"
                                    "weekly" -> "Weekly"
                                    "monthly" -> "Monthly"
                                    else -> "Never (Disabled)"
                                },
                                iconRes = R.drawable.baseline_save_as_24,
                                onClick = { onPreferenceClick("auto_backup_interval") }
                            )
                        }

                        if (getString("auto_backup_interval", "never") != "never") {
                            item {
                                ActionPreferenceCard(
                                    title = "Auto Backup Path",
                                    summary = getString("auto_backup_path_pref", "No folder selected"),
                                    iconRes = R.drawable.ic_baseline_public_24,
                                    onClick = { onPreferenceClick("auto_backup_path") }
                                )
                            }
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
                                title = "Local Wi-Fi Sync",
                                summary = "Synchronize bookmarks and settings directly between devices over local Wi-Fi",
                                iconRes = R.drawable.ic_baseline_public_24,
                                onClick = { onPreferenceClick("wifi_sync_key") }
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

                        // ─── Cache Management ───
                        item { PreferenceHeader("Cache Management") }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassCard(shape = RoundedCornerShape(20.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                ) {
                                    val animatedRotation by animateFloatAsState(
                                        targetValue = if (isCacheExpanded) 180f else 0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                                        label = "chevron_rotate"
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isCacheExpanded = !isCacheExpanded }
                                            .padding(horizontal = 20.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_baseline_delete_outline_24),
                                                contentDescription = "Cache Management",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Cache Management",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Covers, network, plugins, logs, chapter & WebView cache",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                                )
                                            }
                                        }
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24),
                                            contentDescription = if (isCacheExpanded) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .rotate(animatedRotation)
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isCacheExpanded,
                                        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                        exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)
                                            
                                            NestedCacheActionRow(
                                                title = "Clear Poster Image Cache",
                                                size = imageCacheSize,
                                                summary = "Clears cached novel covers to free up space.",
                                                iconRes = R.drawable.ic_baseline_color_lens_24,
                                                onClick = {
                                                    coroutineScope.launch {
                                                        com.lagradost.quicknovel.util.StorageCacheHelper.clearImageCache(context)
                                                        cacheTrigger++
                                                        com.lagradost.quicknovel.CommonActivity.showToast("Image cache cleared!")
                                                    }
                                                }
                                            )
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)
                                            
                                            NestedCacheActionRow(
                                                title = "Clear Network Cache",
                                                size = networkCacheSize,
                                                summary = "Clears OkHttp network response cache.",
                                                iconRes = R.drawable.ic_baseline_autorenew_24,
                                                onClick = {
                                                    coroutineScope.launch {
                                                        com.lagradost.quicknovel.util.StorageCacheHelper.clearNetworkCache(context)
                                                        cacheTrigger++
                                                        com.lagradost.quicknovel.CommonActivity.showToast("Network cache cleared!")
                                                    }
                                                }
                                            )
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)
                                            
                                            NestedCacheActionRow(
                                                title = "Clear Provider Plugin Cache",
                                                size = codeCacheSize,
                                                summary = "Clears cached optimized DEX files for loaded extensions.",
                                                iconRes = R.drawable.ic_baseline_system_update_24,
                                                onClick = {
                                                    coroutineScope.launch {
                                                        com.lagradost.quicknovel.util.StorageCacheHelper.clearCodeCache(context)
                                                        cacheTrigger++
                                                        com.lagradost.quicknovel.CommonActivity.showToast("Provider plugin cache cleared!")
                                                    }
                                                }
                                            )
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)
                                            
                                            NestedCacheActionRow(
                                                title = "Clear Crash Logs",
                                                size = crashLogSize,
                                                summary = "Deletes the saved crash trace logs.",
                                                iconRes = R.drawable.baseline_description_24,
                                                onClick = {
                                                    coroutineScope.launch {
                                                        com.lagradost.quicknovel.util.StorageCacheHelper.clearCrashLog(context)
                                                        cacheTrigger++
                                                        com.lagradost.quicknovel.CommonActivity.showToast("Crash logs cleared!")
                                                    }
                                                }
                                            )
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)
                                            
                                            NestedCacheActionRow(
                                                title = "Clear Temporary Download Cache",
                                                size = chapterCacheSize,
                                                summary = "Deletes temporary chapter files left over from generating books.",
                                                iconRes = R.drawable.ic_baseline_delete_outline_24,
                                                onClick = {
                                                    com.google.android.material.dialog.MaterialAlertDialogBuilder(context, com.lagradost.quicknovel.R.style.AlertDialogCustom)
                                                        .setTitle("Delete Temporary Downloads?")
                                                        .setMessage("This will delete all locally cached chapter text files. Saved books in your library that haven't been compiled to EPUB will need to be downloaded again.")
                                                        .setCancelable(true)
                                                        .setPositiveButton("Delete") { dialog, _ ->
                                                            dialog.dismiss()
                                                            coroutineScope.launch {
                                                                com.lagradost.quicknovel.util.StorageCacheHelper.clearChapterCache(context)
                                                                cacheTrigger++
                                                                com.lagradost.quicknovel.CommonActivity.showToast("Temporary downloads cleared!")
                                                            }
                                                        }
                                                        .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                                                        .show()
                                                }
                                            )
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)
                                            
                                            NestedCacheActionRow(
                                                title = "Clear WebView Cache & Cookies",
                                                size = webViewCacheSize,
                                                summary = "Deletes WebView cached pages, session state, and all cookies.",
                                                iconRes = R.drawable.ic_baseline_warning_24,
                                                onClick = {
                                                    coroutineScope.launch {
                                                        com.lagradost.quicknovel.util.StorageCacheHelper.clearWebViewCache(context)
                                                        cacheTrigger++
                                                        com.lagradost.quicknovel.CommonActivity.showToast("WebView Cache & Cookies cleared!")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    R.xml.settings_dev -> {
                        // ─── Advanced Developer Settings Category ───
                        item { PreferenceHeader("Diagnostics & system overrides") }

                        item {
                            ActionPreferenceCard(
                                title = "Share Crash Log 📋",
                                summary = "Share the stack trace of the last fatal crash",
                                iconRes = R.drawable.baseline_description_24,
                                onClick = { onPreferenceClick("show_logcat_key") }
                            )
                        }

                        item {
                            ActionPreferenceCard(
                                title = "Share App Logs 🐛",
                                summary = "Export recent app activity to help developers diagnose lag or bugs",
                                iconRes = R.drawable.baseline_description_24,
                                onClick = {
                                    android.widget.Toast.makeText(context, "Generating logs...", android.widget.Toast.LENGTH_SHORT).show()
                                    coroutineScope.launchSafe {
                                        com.lagradost.quicknovel.util.LogcatExporter.exportAndShareLogs(context)
                                    }
                                }
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

                        item {
                            ActionPreferenceCard(
                                title = "DNS over HTTPS",
                                summary = when (getString(com.lagradost.quicknovel.NetworkPrefs.DOH_PROVIDER, "none")) {
                                    "cloudflare" -> "Cloudflare"
                                    "google" -> "Google"
                                    "adguard" -> "AdGuard (Ad-blocking)"
                                    "quad9" -> "Quad9"
                                    "dnspod" -> "DNSPod"
                                    "mullvad" -> "Mullvad"
                                    else -> "None (System default)"
                                },
                                iconRes = R.drawable.ic_baseline_public_24,
                                onClick = { onPreferenceClick(com.lagradost.quicknovel.NetworkPrefs.DOH_PROVIDER) }
                            )
                        }

                        item { PreferenceHeader("Advanced download configurations") }

                        item {
                            WarningBannerCard(
                                message = "Only touch these if you know what you're doing. Wrong settings can get you IP-banned by providers or make downloads unusably slow."
                            )
                        }

                        item {
                            AdvancedStepSelectorPreferenceCard(
                                title = "Parallel Downloads",
                                value = getInt("custom_parallel_downloads", 5),
                                min = 1,
                                max = 20,
                                step = 1,
                                valueSuffix = "",
                                iconRes = R.drawable.netflix_download,
                                presets = listOf(1, 3, 5, 10, 15, 20),
                                infoDescription = "Controls the number of chapters downloaded simultaneously (Default: 5). Higher concurrency speeds up downloads but heavily increases the risk of being blocked, rate-limited, or permanently IP-banned by providers.",
                                onValueChange = { value ->
                                    sharedPrefs.edit().putInt("custom_parallel_downloads", value).apply()
                                    onPreferenceChange("custom_parallel_downloads", value)
                                    changeTrigger++
                                }
                            )
                        }

                        item {
                            val rateLimitCurrent = getInt("custom_rate_limit", 0)
                            AdvancedStepSelectorPreferenceCard(
                                title = "Custom Rate Limit",
                                value = rateLimitCurrent,
                                min = 0,
                                max = 3000,
                                step = 250,
                                valueSuffix = "ms",
                                iconRes = R.drawable.ic_baseline_tune_24,
                                presets = listOf(0, 500, 1000, 1500, 2000, 3000),
                                customValueLabel = when {
                                    rateLimitCurrent == 0 -> "Auto"
                                    rateLimitCurrent >= 1000 -> {
                                        val secs = rateLimitCurrent / 1000.0
                                        if (secs == secs.toLong().toDouble()) "${secs.toLong()}s" else "${secs}s"
                                    }
                                    else -> "$rateLimitCurrent ms"
                                },
                                infoDescription = "Adds a delay (in milliseconds) between chapter requests. Default: Auto/0ms (respects provider defaults). Setting a delay (e.g., 500ms or 1000ms) helps bypass strict Cloudflare checks or anti-scraping blocks on rate-limited providers, though it slows down downloads.",
                                onCustomClick = { showCustomRateLimitDialog = true },
                                onValueChange = { value ->
                                    sharedPrefs.edit().putInt("custom_rate_limit", value).apply()
                                    onPreferenceChange("custom_rate_limit", value)
                                    changeTrigger++
                                }
                            )
                        }

                        item {
                            ProxySettingsCard(
                                sharedPrefs = sharedPrefs,
                                changeTrigger = changeTrigger,
                                onPreferenceChange = onPreferenceChange,
                                onTriggerChange = { changeTrigger++ }
                            )
                        }
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
    customValueLabel: String? = null,
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
                        text = customValueLabel ?: "$value$valueSuffix",
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
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
                                    text = if (preset == 0) "Auto" else "$preset$valueSuffix",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
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
                    onValueChangeLive = { 
                        liveValue = it.roundToInt()
                    },
                    onValueChangeFinished = {
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
    onValueChangeLive: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
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
                        onValueChangeLive(newValue)
                    }
                }
                setOnValueChangeFinishedListener { newValue ->
                    onValueChangeFinished(newValue)
                }
            }
        },
        update = { view ->
            view.valueFrom = valueFrom
            view.valueTo = valueTo
            view.stepSize = stepSize
            if (!view.isDragging()) {
                view.value = value
            }
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
    val accentBrush = rememberAccentGradientBrush(accentColor = MaterialTheme.colorScheme.primary)
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
            .pointerInput(valueRange, step, value) { // Re-bind pointerInput when range, step, or value changes
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
                .background(accentBrush)
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
                .background(accentBrush)
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
        "alexandriaflf" -> "Alexandria FLF"
        else -> "System Default"
    }
}

@Composable
fun CloudSyncPreferencesCard() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Google Drive Cloud Sync, 1 = Sync & Backup Preferences
    
    // Shared and specific states
    var syncBookmarks by remember { mutableStateOf(BackupUtils.getSyncBookmarks(context)) }
    var syncSettings by remember { mutableStateOf(BackupUtils.getSyncSettings(context)) }
    var syncHistory by remember { mutableStateOf(BackupUtils.getSyncHistory(context)) }

    var accountEmail by remember { mutableStateOf(GoogleDriveSyncManager.getAccountEmail(context)) }
    var isAutoSyncEnabled by remember { mutableStateOf(GoogleDriveSyncManager.isAutoSyncEnabled(context)) }
    var lastSyncedTime by remember { mutableStateOf(GoogleDriveSyncManager.getLastSyncedTime(context)) }
    var isSyncing by remember { mutableStateOf(false) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val email = GoogleDriveSyncManager.handleSignInResult(context, result.data)
            accountEmail = email
            if (email != null) {
                com.lagradost.quicknovel.CommonActivity.showToast("Google account connected successfully!")
                GoogleSyncWorkHelper.scheduleSyncWorker(context)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header clickable to expand/collapse
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .glassCard(
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_cloud_24),
                            contentDescription = "Cloud Sync & Preferences",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Cloud Sync & Preferences",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val summaryText = if (accountEmail != null) "Linked to $accountEmail" else "Configure backup & sync"
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24),
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                var dropdownExpanded by remember { mutableStateOf(false) }
                val options = listOf("Google Drive Cloud Sync", "Sync & Backup Preferences")

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                strokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                strokeWidth = 1.dp
                            )
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = options[selectedTab],
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24),
                            contentDescription = "Select Section",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(if (dropdownExpanded) 180f else 0f)
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    ) {
                        options.forEachIndexed { index, title ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = title, 
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ) 
                                },
                                onClick = {
                                    selectedTab = index
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    Text(
                        text = "Automatically backup and sync your reading library, bookmarks, and settings privately to your Google Drive account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(16.dp))

                    if (accountEmail == null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val signInIntent = GoogleDriveSyncManager.getSignInIntent(context)
                                    launcher.launch(signInIntent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Link Google Account")
                            }

                            Text(
                                text = "Note: Because NeoQN is not on the Play Store, Google MAY show an 'Unverified App' warning. Click 'Advanced -> Continue' to link your account safely.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Account Connected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = accountEmail ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        GoogleDriveSyncManager.logout(context) {
                                            accountEmail = null
                                            isAutoSyncEnabled = false
                                            lastSyncedTime = 0L
                                            GoogleSyncWorkHelper.scheduleSyncWorker(context)
                                        }
                                    }
                                ) {
                                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-Sync Library",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Keep bookmarks and settings synced in the background automatically.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Switch(
                                    checked = isAutoSyncEnabled,
                                    onCheckedChange = { checked ->
                                        isAutoSyncEnabled = checked
                                        GoogleDriveSyncManager.setAutoSyncEnabled(context, checked)
                                        GoogleSyncWorkHelper.scheduleSyncWorker(context)
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lastSyncedTime > 0L) {
                                            val date = Date(lastSyncedTime)
                                            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                            "Last synced: ${sdf.format(date)}"
                                        } else {
                                            "Not synced yet"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Note: If restoring to another device, restart the app on that device to fully apply the restored library and preferences.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isSyncing) {
                                            com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.AlertDialogCustom)
                                                .setTitle("Restore from Cloud?")
                                                .setMessage("This will overwrite your local bookmarks, settings, and reading progress with the backup stored on Google Drive.")
                                                .setNegativeButton("Cancel", null)
                                                .setPositiveButton("Restore") { _, _ ->
                                                    isSyncing = true
                                                    coroutineScope.launch {
                                                        val success = GoogleDriveSyncManager.restoreFromCloud(context)
                                                        isSyncing = false
                                                        if (success) {
                                                            com.lagradost.quicknovel.CommonActivity.showToast("Restored from cloud successfully!")
                                                            com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.AlertDialogCustom)
                                                                .setTitle(R.string.backup_restored_title)
                                                                .setMessage(R.string.backup_restored_message)
                                                                .setCancelable(false)
                                                                .setPositiveButton(R.string.got_it) { _, _ ->
                                                                    (context as? Activity)?.finishAffinity()
                                                                }.show()
                                                        } else {
                                                            com.lagradost.quicknovel.CommonActivity.showToast("Cloud restore failed or no backup found.")
                                                        }
                                                    }
                                                }.show()
                                        }
                                    },
                                    enabled = !isSyncing,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Restore")
                                }

                                Button(
                                    onClick = {
                                        if (!isSyncing) {
                                            com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.AlertDialogCustom)
                                                .setTitle("Sync to Cloud?")
                                                .setMessage("This will upload your current local library, bookmarks, settings, and progress, completely overwriting the backup stored on Google Drive.")
                                                .setNegativeButton("Cancel", null)
                                                .setPositiveButton("Sync") { _, _ ->
                                                    isSyncing = true
                                                    coroutineScope.launch {
                                                        val success = GoogleDriveSyncManager.syncNow(context)
                                                        isSyncing = false
                                                        lastSyncedTime = GoogleDriveSyncManager.getLastSyncedTime(context)
                                                        if (success) {
                                                            com.lagradost.quicknovel.CommonActivity.showToast("Cloud sync completed successfully!")
                                                        } else {
                                                            com.lagradost.quicknovel.CommonActivity.showToast("Cloud sync failed.")
                                                        }
                                                    }
                                                }.show()
                                        }
                                    },
                                    enabled = !isSyncing,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isSyncing) "Syncing..." else "Sync Now")
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Select what data is included when syncing or backing up your library. These options apply globally to Google Drive Sync, manual backups, and transfers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sync Bookmarks & Library",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Include novel bookmarks, categories, and custom sorting metadata.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = syncBookmarks,
                            onCheckedChange = { checked ->
                                syncBookmarks = checked
                                PreferenceManager.getDefaultSharedPreferences(context).edit()
                                    .putBoolean(BackupUtils.SYNC_BOOKMARKS_KEY, checked)
                                    .apply()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sync App Settings",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Include visual themes, fonts, reader configuration, and layout settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = syncSettings,
                            onCheckedChange = { checked ->
                                syncSettings = checked
                                PreferenceManager.getDefaultSharedPreferences(context).edit()
                                    .putBoolean(BackupUtils.SYNC_SETTINGS_KEY, checked)
                                    .apply()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sync Progress & History",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Include reading history, last read chapters, scroll positions, and TTS state.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = syncHistory,
                            onCheckedChange = { checked ->
                                syncHistory = checked
                                PreferenceManager.getDefaultSharedPreferences(context).edit()
                                    .putBoolean(BackupUtils.SYNC_HISTORY_KEY, checked)
                                    .apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TranslationSettingsCard(
    sharedPrefs: android.content.SharedPreferences,
    onChanged: () -> Unit
) {
    var provider by remember {
        mutableStateOf(sharedPrefs.getString(com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_PROVIDER, "gemini") ?: "gemini")
    }
    var apiUrl by remember {
        mutableStateOf(sharedPrefs.getString(com.lagradost.quicknovel.util.PrefKeys.TRANSLATION_API_URL, "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent") ?: "")
    }
    var apiKey by remember {
        mutableStateOf(sharedPrefs.getString(com.lagradost.quicknovel.util.PrefKeys.TRANSLATION_API_KEY, "") ?: "")
    }
    var apiModel by remember {
        mutableStateOf(sharedPrefs.getString(com.lagradost.quicknovel.util.PrefKeys.TRANSLATION_API_MODEL, "gemini-2.0-flash") ?: "")
    }

    // ─── Rate-limit tuning state ───────────────────────────────────────────────
    var maxParallel by remember {
        mutableStateOf(sharedPrefs.getInt(com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_MAX_PARALLEL, com.lagradost.quicknovel.util.CloudAITranslator.DEFAULT_MAX_PARALLEL))
    }
    var delayMs by remember {
        mutableStateOf(sharedPrefs.getInt(com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_DELAY_MS, com.lagradost.quicknovel.util.CloudAITranslator.DEFAULT_DELAY_MS.toInt()))
    }
    var batchSize by remember {
        mutableStateOf(sharedPrefs.getInt(com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_BATCH_SIZE, com.lagradost.quicknovel.util.CloudAITranslator.DEFAULT_BATCH_SIZE))
    }

    var isExpanded by remember { mutableStateOf(false) }

    val animatedRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "chevron_rotate"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(24.dp))
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .glassCard(
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_language_24),
                        contentDescription = "Cloud AI Translation (BYOK)",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Cloud AI Translation (BYOK)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val summaryText = remember(provider, apiModel, apiKey) {
                        if (apiKey.isBlank()) {
                            "API Setup required — configure custom translation endpoints"
                        } else {
                            val keyLabel = when (provider) {
                                "gemini" -> "Gemini"
                                "openrouter" -> "OpenRouter"
                                "nvidia" -> "NVIDIA"
                                else -> "Custom"
                            }
                            "$keyLabel — $apiModel"
                        }
                    }
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24),
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(animatedRotation)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 4.dp))

                // ─── AI Provider Presets segmented selector ───────────────────────
                Text(
                    text = "AI Provider Preset",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                var dropdownExpanded by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentLabel = when (provider) {
                            "gemini" -> "Gemini"
                            "openrouter" -> "OpenRouter"
                            "nvidia" -> "NVIDIA"
                            else -> "Custom"
                        }
                        Text(
                            text = currentLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24),
                            contentDescription = "Select Provider",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(if (dropdownExpanded) 180f else 0f)
                        )
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        val options = listOf(
                            "gemini" to "Gemini",
                            "openrouter" to "OpenRouter",
                            "nvidia" to "NVIDIA",
                            "custom" to "Custom"
                        )
                        options.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (provider == key) FontWeight.Bold else FontWeight.Normal,
                                        color = if (provider == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    provider = key
                                    sharedPrefs.edit()
                                        .putString(com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_PROVIDER, key).apply()

                                    // Auto-populate defaults
                                    when (key) {
                                        "gemini" -> {
                                            apiUrl =
                                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
                                            apiModel = "gemini-2.0-flash"
                                            maxParallel = 1
                                            delayMs = 5000
                                            batchSize = 0 // Auto
                                        }

                                        "openrouter" -> {
                                            apiUrl = "https://openrouter.ai/api/v1/chat/completions"
                                            apiModel = "openrouter/auto"
                                            maxParallel = 2
                                            delayMs = 2000
                                            batchSize = 0 // Auto
                                        }

                                        "nvidia" -> {
                                            apiUrl = "https://integrate.api.nvidia.com/v1/chat/completions"
                                            apiModel = "meta/llama-3.1-8b-instruct"
                                            maxParallel = 2
                                            delayMs = 2000
                                            batchSize = 0 // Auto
                                        }
                                    }
                                    sharedPrefs.edit().apply {
                                        putString(com.lagradost.quicknovel.util.PrefKeys.TRANSLATION_API_URL, apiUrl)
                                        putString(
                                            com.lagradost.quicknovel.util.PrefKeys.TRANSLATION_API_MODEL,
                                            apiModel
                                        )
                                        putInt(
                                            com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_MAX_PARALLEL,
                                            maxParallel
                                        )
                                        putInt(com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_DELAY_MS, delayMs)
                                        putInt(
                                            com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_BATCH_SIZE,
                                            batchSize
                                        )
                                    }.apply()
                                    onChanged()
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = {
                        apiUrl = it
                        provider = "custom"
                        sharedPrefs.edit()
                            .putString(com.lagradost.quicknovel.util.PrefKeys.TRANSLATION_API_URL, it)
                            .putString(com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_PROVIDER, "custom")
                            .apply()
                        onChanged()
                    },
                    label = { Text("API URL") },
                    placeholder = { Text("e.g. Gemini or OpenRouter endpoint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        sharedPrefs.edit().putString(com.lagradost.quicknovel.util.PrefKeys.TRANSLATION_API_KEY, it)
                            .apply()
                        onChanged()
                    },
                    label = { Text("API Key") },
                    placeholder = { Text("Enter your API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                OutlinedTextField(
                    value = apiModel,
                    onValueChange = {
                        apiModel = it
                        provider = "custom"
                        sharedPrefs.edit()
                            .putString(com.lagradost.quicknovel.util.PrefKeys.TRANSLATION_API_MODEL, it)
                            .putString(com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_PROVIDER, "custom")
                            .apply()
                        onChanged()
                    },
                    label = { Text("Model Name (Optional)") },
                    placeholder = { Text("e.g. gemini-2.0-flash or google/gemini-2.5-flash") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // ─── Rate-limit & throughput tuning ───────────────────────────────────
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )

                Text(
                    text = "Rate Limit Tuning",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "Adjust these to match your API plan. Free-tier Gemini: keep Parallel=1, Delay≥2000ms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                StepSelectorPreferenceCard(
                    title = "Parallel Requests",
                    value = maxParallel,
                    min = 1,
                    max = 5,
                    step = 1,
                    valueSuffix = "",
                    iconRes = R.drawable.ic_baseline_tune_24,
                    presets = listOf(1, 2, 3, 5),
                    onValueChange = { v ->
                        maxParallel = v
                        sharedPrefs.edit().putInt(com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_MAX_PARALLEL, v)
                            .apply()
                        onChanged()
                    }
                )

                StepSelectorPreferenceCard(
                    title = "Request Delay (ms)",
                    value = delayMs,
                    min = 0,
                    max = 10000,
                    step = 500,
                    valueSuffix = "ms",
                    iconRes = R.drawable.ic_baseline_tune_24,
                    presets = listOf(0, 1000, 2000, 3000, 5000),
                    onValueChange = { v ->
                        delayMs = v
                        sharedPrefs.edit().putInt(com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_DELAY_MS, v).apply()
                        onChanged()
                    }
                )

                StepSelectorPreferenceCard(
                    title = "Paragraphs per Batch",
                    value = batchSize,
                    min = 0,
                    max = 20,
                    step = 1,
                    valueSuffix = "",
                    iconRes = R.drawable.ic_baseline_tune_24,
                    presets = listOf(0, 1, 5, 10, 15),
                    customValueLabel = if (batchSize == 0) "Auto (Dynamic)" else null,
                    onValueChange = { v ->
                        batchSize = v
                        sharedPrefs.edit().putInt(com.lagradost.quicknovel.util.PrefKeys.CLOUD_AI_BATCH_SIZE, v).apply()
                        onChanged()
                    }
                )
            }
        }
    }
}

@Composable
fun WarningBannerCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x11EF5350), // Semi-transparent soft red/pink
                strokeColor = Color(0x44EF5350),     // Soft red border
                strokeWidth = 1.dp
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_warning_24),
                contentDescription = "Warning",
                tint = Color(0xFFEF5350), // Premium red
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp
                ),
                color = Color(0xFFEF5350)
            )
        }
    }
}

@Composable
fun AdvancedStepSelectorPreferenceCard(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    valueSuffix: String,
    iconRes: Int,
    presets: List<Int>,
    customValueLabel: String? = null,
    infoDescription: String,
    onCustomClick: (() -> Unit)? = null,
    onValueChange: (Int) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showInfo by remember { mutableStateOf(false) }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
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
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            showInfo = !showInfo
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_error_outline_24),
                            contentDescription = "Info",
                            tint = if (showInfo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
                        text = customValueLabel ?: "$value$valueSuffix",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (showInfo) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp)
                        .glassCard(
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            strokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            strokeWidth = 0.5.dp
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = infoDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 16.sp
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

                // Preset Pills Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
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
                                    text = if (preset == 0) "Auto" else "$preset$valueSuffix",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        if (onCustomClick != null) {
                            val isCustomActive = value !in presets
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isCustomActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isCustomActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        onCustomClick()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isCustomActive) "Custom ($value$valueSuffix)" else "Custom...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCustomActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontWeight = if (isCustomActive) FontWeight.Bold else FontWeight.Medium
                                )
                            }
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
fun ProxySettingsCard(
    sharedPrefs: android.content.SharedPreferences,
    changeTrigger: Int,
    onPreferenceChange: (String, Any) -> Unit,
    onTriggerChange: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember(changeTrigger) { mutableStateOf(sharedPrefs.getBoolean("custom_proxy_enabled", false)) }
    var host by remember(changeTrigger) { mutableStateOf(sharedPrefs.getString("custom_proxy_host", "") ?: "") }
    var port by remember(changeTrigger) { mutableStateOf(sharedPrefs.getString("custom_proxy_port", "") ?: "") }
    var proxyType by remember(changeTrigger) { mutableStateOf(sharedPrefs.getString("custom_proxy_type", "HTTP") ?: "HTTP") }
    var username by remember(changeTrigger) { mutableStateOf(sharedPrefs.getString("custom_proxy_username", "") ?: "") }
    var password by remember(changeTrigger) { mutableStateOf(sharedPrefs.getString("custom_proxy_password", "") ?: "") }

    fun updateAndSync(key: String, value: Any, editBlock: android.content.SharedPreferences.Editor.() -> Unit) {
        sharedPrefs.edit().apply(editBlock).apply()
        onPreferenceChange(key, value)
        com.lagradost.quicknovel.network.WebViewProxyHelper.syncProxy(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_public_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Custom Proxy Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (enabled) "Route download requests through proxy" else "Disabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    updateAndSync("custom_proxy_enabled", checked) {
                        putBoolean("custom_proxy_enabled", checked)
                    }
                    onTriggerChange()
                }
            )
        }

        if (enabled) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("HTTP", "SOCKS").forEach { type ->
                    val isSelected = proxyType.uppercase() == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                proxyType = type
                                updateAndSync("custom_proxy_type", type) {
                                    putString("custom_proxy_type", type)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        updateAndSync("custom_proxy_host", it) {
                            putString("custom_proxy_host", it)
                        }
                    },
                    label = { Text("Host / IP") },
                    placeholder = { Text("e.g. 127.0.0.1") },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = {
                        port = it
                        updateAndSync("custom_proxy_port", it) {
                            putString("custom_proxy_port", it)
                        }
                    },
                    label = { Text("Port") },
                    placeholder = { Text("8080") },
                    modifier = Modifier.weight(0.8f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        updateAndSync("custom_proxy_username", it) {
                            putString("custom_proxy_username", it)
                        }
                    },
                    label = { Text("Username (Optional)") },
                    placeholder = { Text("Proxy Username") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        updateAndSync("custom_proxy_password", it) {
                            putString("custom_proxy_password", it)
                        }
                    },
                    label = { Text("Password (Optional)") },
                    placeholder = { Text("Proxy Password") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            var isTesting by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            Button(
                onClick = {
                    if (isTesting) return@Button
                    isTesting = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            safeApiCall {
                                val response = com.lagradost.quicknovel.MainActivity.app.get("https://api.ipify.org?format=json")
                                com.lagradost.quicknovel.DataStore.mapper.readValue(
                                    response.text,
                                    com.lagradost.quicknovel.network.IpifyResponse::class.java
                                )
                            }
                        }
                        withContext(Dispatchers.Main) {
                            isTesting = false
                            when (result) {
                                is Resource.Success -> {
                                    val ip = result.value.ip
                                    android.widget.Toast.makeText(
                                        context,
                                        "✅ Proxy Active! Your IP is hidden as: $ip",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                                is Resource.Failure -> {
                                    val errMsg = result.errorString ?: "Unknown error"
                                    android.widget.Toast.makeText(
                                        context,
                                        "❌ Proxy Failed: $errMsg",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                                else -> {}
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(10.dp),
                enabled = !isTesting
            ) {
                if (isTesting) {
                    LoadingIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing Connection...")
                } else {
                    Text("Test Proxy Connection")
                }
            }
        }
    }
}

@Composable
fun CustomRateLimitDialog(
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(if (initialValue == 0) "" else initialValue.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
            decorFitsSystemWindows = false
        ),
        title = { Text(text = "Custom Rate Limit") },
        text = {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Enter a custom delay in milliseconds between chapter requests (e.g., 15000 for 15s, 30000 for 30s). Enter 0 or leave blank for Auto.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            textValue = newValue
                            isError = false
                        }
                    },
                    label = { Text("Delay (ms)") },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "Please enter a valid rate limit",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = textValue.toIntOrNull()
                    if (parsed != null) {
                        onConfirm(parsed)
                        onDismiss()
                    } else if (textValue.trim().isEmpty()) {
                        onConfirm(0)
                        onDismiss()
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CacheActionPreferenceCard(
    title: String,
    size: String,
    summary: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    val isLoading = size == "Calculating..."
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
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
                Spacer(modifier = Modifier.height(4.dp))
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(rememberShimmerBrush())
                    )
                } else {
                    Text(
                        text = "Size: $size\n$summary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (!isLoading) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_arrow_forward_24),
                    contentDescription = "Navigate icon",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            } else {
                LoadingIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private class StaggeredLazyListScope(
    private val delegate: LazyListScope,
    private val startIndex: Int = 0
) : LazyListScope {
    private var staggerIndex = startIndex

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        val currentIndex = staggerIndex++
        delegate.item(key, contentType) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(currentIndex)
            ) {
                content()
            }
        }
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit
    ) {
        delegate.items(
            count = count,
            key = key,
            contentType = contentType
        ) { idx ->
            val currentIndex = this@StaggeredLazyListScope.staggerIndex++
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(currentIndex)
            ) {
                itemContent(idx)
            }
        }
    }
}

@Composable
fun NestedSettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            content = content
        )
    }
}

@Composable
fun MasterSwitchPreferenceRow(
    title: String,
    summary: String,
    checked: Boolean,
    iconRes: Int,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
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

@Composable
fun NestedSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    iconRes: Int,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (summary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.85f)
        )
    }
}

@Composable
fun NestedSliderRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    valueSuffix: String,
    step: Int = 1,
    iconRes: Int,
    onValueChange: (Int) -> Unit
) {
    var liveValue by remember(value) { mutableStateOf(value) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
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
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
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

        Spacer(modifier = Modifier.height(8.dp))

        PremiumExpressiveSlider(
            value = value.toFloat(),
            onValueChangeLive = { liveValue = it.roundToInt() },
            onValueChangeFinished = { onValueChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            step = step,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        )
    }
}

@Composable
fun NestedActionRow(
    title: String,
    summary: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (summary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
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
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
fun NestedCacheActionRow(
    title: String,
    size: String,
    summary: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    val isLoading = size == "Calculating..."
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(rememberShimmerBrush())
                )
            } else {
                Text(
                    text = "Size: $size\n$summary",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (!isLoading) {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_arrow_forward_24),
                contentDescription = "Clear",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        } else {
            LoadingIndicator(
                modifier = Modifier.size(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun TelegramBackupPreferencesCard() {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    
    var isExpanded by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    
    var botToken by remember { mutableStateOf(sharedPrefs.getString(TelegramBackupPrefs.BOT_TOKEN, "") ?: "") }
    var chatId by remember { mutableStateOf(sharedPrefs.getString(TelegramBackupPrefs.CHAT_ID, "") ?: "") }
    var deleteAfterUpload by remember { mutableStateOf(sharedPrefs.getBoolean(TelegramBackupPrefs.DELETE_AFTER_UPLOAD, false)) }
    var compileDownloads by remember { mutableStateOf(sharedPrefs.getBoolean(TelegramBackupPrefs.COMPILE_DOWNLOADS, false)) }

    // Observe WorkManager progress
    val workInfos = remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("telegram_backup_work")
    }
    val workInfoState = workInfos.observeAsState()
    val workInfo = workInfoState.value?.firstOrNull()
    
    val isWorkerRunning = workInfo?.state == WorkInfo.State.RUNNING
    
    val progressData = workInfo?.progress
    val current = progressData?.getInt("progress_current", 0) ?: 0
    val total = progressData?.getInt("progress_total", 0) ?: 0
    val status = progressData?.getString("progress_status") ?: ""
    val isActive = progressData?.getBoolean("is_active", false) ?: false
    
    val progressFraction = if (total > 0) current.toFloat() / total.toFloat() else 0f

    // Micro-scale squish animation for the button
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "backup_button_squish"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Clickable to expand, containing Title, Setup Guide and Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isExpanded = !isExpanded }
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .glassCard(
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                             ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_telegram),
                            contentDescription = "Telegram Backup",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Telegram Cloud Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isWorkerRunning) "Backup in progress..." else "Back up EPUBs to Telegram",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { showTutorialDialog = true },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "Setup Guide",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24),
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Reactive Bot Token Field (Plain Text)
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { newValue ->
                        botToken = newValue
                        sharedPrefs.edit().putString(TelegramBackupPrefs.BOT_TOKEN, newValue).apply()
                    },
                    label = { Text("Telegram Bot Token") },
                    placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Reactive Chat ID Field
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { newValue ->
                        chatId = newValue
                        sharedPrefs.edit().putString(TelegramBackupPrefs.CHAT_ID, newValue).apply()
                    },
                    label = { Text("Telegram Chat ID") },
                    placeholder = { Text("-100123456789 or 123456789") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Delete local EPUB after upload",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Frees device storage after each successful upload. The novel stays in your library and can be re-compiled anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = deleteAfterUpload,
                        onCheckedChange = { checked ->
                            deleteAfterUpload = checked
                            sharedPrefs.edit().putBoolean(TelegramBackupPrefs.DELETE_AFTER_UPLOAD, checked).apply()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Compile downloads before backup",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automatically saves downloaded novels in your library as EPUB to your phone before uploading to Telegram.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = compileDownloads,
                        onCheckedChange = { checked ->
                            compileDownloads = checked
                            sharedPrefs.edit().putBoolean(TelegramBackupPrefs.COMPILE_DOWNLOADS, checked).apply()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Progress state representation
                if (isWorkerRunning || isActive) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = status.ifEmpty { "Uploading documents..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        if (total > 0) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Backup Now Button with Squish Animation
                Button(
                    onClick = {
                        if (botToken.isNotBlank() && chatId.isNotBlank()) {
                            val backupRequest = OneTimeWorkRequestBuilder<TelegramBackupWorker>()
                                .addTag("telegram_backup_work")
                                .build()
                            WorkManager.getInstance(context).enqueueUniqueWork(
                                "telegram_backup_work",
                                ExistingWorkPolicy.REPLACE,
                                backupRequest
                            )
                        } else {
                            com.lagradost.quicknovel.CommonActivity.showToast("Please fill in both Token and Chat ID first!")
                        }
                    },
                    enabled = !isWorkerRunning && botToken.isNotBlank() && chatId.isNotBlank(),
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = if (isWorkerRunning) "Backing Up..." else "Backup Now",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    // Sleek Tutorial Dialog (Grandpa-Proof Setup Guide)
    if (showTutorialDialog) {
        AlertDialog(
            onDismissRequest = { showTutorialDialog = false },
            title = {
                Text(
                    text = "Setup Guide 📖",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Follow these simple steps to set up your Telegram Cloud Backup:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Step 1: Get a Bot Token 🤖",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Search for @BotFather in Telegram. Send /newbot to it, follow the prompt to give it a name, and copy the long token code it replies with. Paste it into the Bot Token field here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Step 2: Activate Your Bot ⚡",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Search for your bot's username in Telegram, or click the link provided by @BotFather. Open the chat and send any message (like 'Hello') to activate it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Step 3: Find Your Chat ID 🆔",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Search for @getmyid_bot in Telegram. Send /start to it, and copy your personal 'Current chat ID' (make sure to include the negative sign if there is one). Paste it into the Chat ID field here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Step 4: Prepare your EPUBs 📚",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Before your first backup, open each downloaded novel from the Library / Downloads tab and tap it once. This compiles the EPUB file on your device. Once compiled, it will appear in all future backups. You can delete the local EPUB after it is safely uploaded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTutorialDialog = false }) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp)
        )
    }
}





