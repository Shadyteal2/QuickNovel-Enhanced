package com.lagradost.quicknovel.ui.reader

import android.content.res.ColorStateList
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ReadActivityViewModel
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.ReadingType
import com.lagradost.quicknovel.ui.custom.TactileRulerSlider
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.util.TranslationEngineType
import kotlin.math.roundToInt

@Composable
fun ReaderSettingsSheet(
    viewModel: ReadActivityViewModel,
    onHardReset: () -> Unit,
    onShowCustomization: () -> Unit,
    onReadingTypeClick: () -> Unit,
    onShowFonts: () -> Unit,
    onLanguageClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onMlFromClick: () -> Unit,
    onMlToClick: () -> Unit,
    onApplyTranslationClick: () -> Unit,
    onMlInfoClick: () -> Unit,
    onColorCustomClick: () -> Unit,
    onColorSelect: (bgColor: Int, txtColor: Int) -> Unit,
    onDismiss: () -> Unit = {}
) {
    var scrollWithVolume by remember { mutableStateOf(viewModel.scrollWithVolume) }
    var ttsLock by remember { mutableStateOf(viewModel.ttsLock) }
    var showTime by remember { mutableStateOf(viewModel.showTime) }
    var showBattery by remember { mutableStateOf(viewModel.showBattery) }
    var showBionic by remember { mutableStateOf(viewModel.bionicReading) }
    var isTextSelectable by remember { mutableStateOf(viewModel.isTextSelectable) }
    var keepScreenActive by remember { mutableStateOf(viewModel.screenAwake) }
    var authorNotes by remember { mutableStateOf(viewModel.authorNotes) }
    var showProgress by remember { mutableStateOf(viewModel.showReaderProgress) }
    var paginatedSwipeEnabled by remember { mutableStateOf(viewModel.paginatedSwipeEnabled) }
    var dynamicLuminanceEnabled by remember { mutableStateOf(viewModel.dynamicLuminanceEnabled) }
    var autoScroll by remember { mutableStateOf(viewModel.autoScroll) }
    var autoScrollSpeed by remember { mutableStateOf(viewModel.autoScrollSpeed.toFloat()) }
    var showReadingTimer by remember { mutableStateOf(viewModel.showReadingTimer) }
    
    var textSize by remember { mutableStateOf(viewModel.textSize.toFloat()) }
    var textPadding by remember { mutableStateOf(viewModel.paddingHorizontal.toFloat()) }
    var textPaddingTop by remember { mutableStateOf(viewModel.paddingVertical.toFloat()) }
    var textVerticalPadding by remember { mutableStateOf(viewModel.textVerticalPadding) }
    
    var ttsSpeed by remember { mutableStateOf(viewModel.ttsSpeed) }
    var ttsPitch by remember { mutableStateOf(viewModel.ttsPitch) }
    val useGoogleTts by viewModel.ttsUseGoogleLive.observeAsState(viewModel.ttsUseGoogle)
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Drag handle indicator
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray.copy(alpha = 0.5f))
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Display settings card
        item {
            Text(
                text = stringResource(R.string.read_display_settings),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                if (viewModel.canReload()) {
                    SettingsButton(
                        text = stringResource(R.string.reload_chapter),
                        iconRes = R.drawable.ic_baseline_autorenew_24,
                        onClick = onHardReset
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                SettingsSwitchRow(stringResource(R.string.scroll_with_volume_keys), scrollWithVolume) {
                    scrollWithVolume = it; viewModel.scrollWithVolume = it
                }
                SettingsSwitchRow(stringResource(R.string.lock_tts), ttsLock) {
                    ttsLock = it; viewModel.ttsLock = it
                }
                SettingsSwitchRow(stringResource(R.string.show_time), showTime) {
                    showTime = it; viewModel.showTime = it
                }
                SettingsSwitchRow(stringResource(R.string.show_battery), showBattery) {
                    showBattery = it; viewModel.showBattery = it
                }
                SettingsSwitchRow(stringResource(R.string.bionic_reading), showBionic) {
                    showBionic = it; viewModel.bionicReading = it
                }
                SettingsSwitchRow(stringResource(R.string.selectable_text), isTextSelectable) {
                    isTextSelectable = it; viewModel.isTextSelectable = it
                }
                SettingsSwitchRow(stringResource(R.string.keep_screen_active), keepScreenActive) {
                    keepScreenActive = it; viewModel.screenAwake = it
                }
                SettingsSwitchRow("Auto Scroll", autoScroll) {
                    autoScroll = it; viewModel.autoScroll = it
                }
                if (autoScroll) {
                    SettingsSliderRow(
                        title = "Auto Scroll Speed",
                        value = autoScrollSpeed,
                        valueFrom = 1f,
                        valueTo = 20f,
                        stepSize = 1f,
                        leftIcon = R.drawable.pace_24px,
                        rightIcon = R.drawable.acute_24px,
                        onValueChange = { autoScrollSpeed = it; viewModel.autoScrollSpeed = it.roundToInt() }
                    )
                }
                SettingsSwitchRow(stringResource(R.string.show_authors_notes), authorNotes) {
                    authorNotes = it; viewModel.authorNotes = it; viewModel.refreshChapters()
                }
                SettingsSwitchRow(stringResource(R.string.show_reading_progress), showProgress) {
                    showProgress = it; viewModel.showReaderProgress = it
                }
                SettingsSwitchRow("Reading Timer Overlay", showReadingTimer) {
                    showReadingTimer = it; viewModel.showReadingTimer = it
                }
                SettingsSwitchRow("Paginated Swipe Mode", paginatedSwipeEnabled) {
                    paginatedSwipeEnabled = it; viewModel.paginatedSwipeEnabled = it
                }
                SettingsSwitchRow("Auto-Fix Bright Backgrounds", dynamicLuminanceEnabled) {
                    dynamicLuminanceEnabled = it; viewModel.dynamicLuminanceEnabled = it
                }

                Spacer(modifier = Modifier.height(8.dp))
                SettingsButton(
                    text = "Reader Customization",
                    iconRes = R.drawable.ic_baseline_settings_24,
                    onClick = onShowCustomization
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Text & Font card
        item {
            Text(
                text = stringResource(R.string.text_font),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.scroll_type), fontSize = 15.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                SettingsButton(
                    text = stringResource(viewModel.readerType.stringRes),
                    iconRes = R.drawable.swipe_vertical_24px,
                    onClick = onReadingTypeClick
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.text_font), fontSize = 15.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                SettingsButton(
                    text = stringResource(R.string.reader_font),
                    iconRes = R.drawable.ic_baseline_font_download_24,
                    onClick = onShowFonts
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // TTS & Advanced values card (including rulers)
        item {
            Text(
                text = stringResource(R.string.tts_voice_title),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsButton(
                        text = stringResource(R.string.tts_locale),
                        iconRes = R.drawable.ic_baseline_language_24,
                        onClick = onLanguageClick,
                        modifier = Modifier.weight(1f)
                    )
                    SettingsButton(
                        text = stringResource(R.string.tts_voice),
                        iconRes = R.drawable.ic_baseline_volume_up_24,
                        onClick = onVoiceClick,
                        modifier = Modifier.weight(1f)
                    )
                    SettingsButton(
                        text = stringResource(R.string.sleep_timer),
                        iconRes = R.drawable.nights_stay_24px,
                        onClick = onSleepTimerClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSwitchRow("Use Google TTS (Neural Online)", useGoogleTts) {
                    viewModel.ttsUseGoogle = it
                }

                SettingsSliderRow(
                    title = stringResource(R.string.tts_speed),
                    value = ttsSpeed,
                    valueFrom = 0.1f,
                    valueTo = 5.0f,
                    stepSize = 0.1f,
                    leftIcon = R.drawable.pace_24px,
                    rightIcon = R.drawable.acute_24px,
                    onValueChange = { ttsSpeed = it; viewModel.ttsSpeed = it }
                )
                
                SettingsSliderRow(
                    title = stringResource(R.string.tts_pitch),
                    value = ttsPitch,
                    valueFrom = 0.1f,
                    valueTo = 3.0f,
                    stepSize = 0.1f,
                    leftIcon = R.drawable.hiking_24px,
                    rightIcon = R.drawable.directions_run_24px,
                    onValueChange = { ttsPitch = it; viewModel.ttsPitch = it }
                )
                
                SettingsSliderRow(
                    title = stringResource(R.string.text_size),
                    value = textSize,
                    valueFrom = 10f,
                    valueTo = 30f,
                    stepSize = 1f,
                    leftIcon = R.drawable.smaller_font,
                    rightIcon = R.drawable.bigger_font,
                    onValueChange = { textSize = it; viewModel.textSize = it.roundToInt() }
                )
                
                SettingsSliderRow(
                    title = stringResource(R.string.text_padding),
                    value = textPadding,
                    valueFrom = 0f,
                    valueTo = 50f,
                    stepSize = 1f,
                    leftIcon = R.drawable.format_padding_decrease_white_24dp,
                    rightIcon = R.drawable.format_padding_increase_white_24dp,
                    onValueChange = { textPadding = it; viewModel.paddingHorizontal = it.roundToInt() }
                )
                
                SettingsSliderRow(
                    title = stringResource(R.string.text_padding_top),
                    value = textPaddingTop,
                    valueFrom = 0f,
                    valueTo = 50f,
                    stepSize = 1f,
                    leftIcon = R.drawable.text_top_bottom_margin,
                    rightIcon = R.drawable.text_top_bottom_margin_expand,
                    onValueChange = { textPaddingTop = it; viewModel.paddingVertical = it.roundToInt() }
                )
                
                SettingsSliderRow(
                    title = stringResource(R.string.paragraph_spacing),
                    value = textVerticalPadding,
                    valueFrom = 0f,
                    valueTo = 30f,
                    stepSize = 0.5f,
                    leftIcon = R.drawable.density_small_24px,
                    rightIcon = R.drawable.density_medium_24px,
                    onValueChange = { textVerticalPadding = it; viewModel.textVerticalPadding = it }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Google ML Translation card
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (viewModel.isTranslationActive) {
                        "${stringResource(R.string.google_ml)} (${viewModel.mlSettings.fromDisplay} -> ${viewModel.mlSettings.toDisplay})"
                    } else {
                        stringResource(R.string.google_ml)
                    },
                    color = Color.Gray,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onMlInfoClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_google_ml),
                        contentDescription = stringResource(R.string.a11y_info),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "Downloading can take upto 10mins depending on google server",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsButton(
                        text = ReadActivityViewModel.MLSettings.fromShortToDisplay(viewModel.mlFromLanguage),
                        iconRes = R.drawable.ic_baseline_menu_book_24,
                        onClick = onMlFromClick,
                        modifier = Modifier.weight(1f)
                    )
                    SettingsButton(
                        text = ReadActivityViewModel.MLSettings.fromShortToDisplay(viewModel.mlToLanguage),
                        iconRes = R.drawable.fiber_new_24px,
                        onClick = onMlToClick,
                        modifier = Modifier.weight(1f)
                    )
                    SettingsButton(
                        text = stringResource(R.string.sort_apply),
                        iconRes = R.drawable.translate_24px,
                        onClick = onApplyTranslationClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                val context = androidx.compose.ui.platform.LocalContext.current
                val prefs = remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
                
                // Get configured credentials
                val apiUrl = remember(prefs) { 
                    prefs.getString("pref_translation_api_url", "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")?.trim() ?: "" 
                }
                val apiKey = remember(prefs) { prefs.getString("pref_translation_api_key", "")?.trim() ?: "" }
                val isCloudConfigured = apiUrl.isNotEmpty() && apiKey.isNotEmpty()
                
                val engineKey = remember(context) { context.getString(R.string.translation_engine_key) }
                var currentEngineValue by remember(engineKey) {
                    mutableStateOf(prefs.getInt(engineKey, 1))
                }
                
                // If current selected is Cloud AI but not configured, fallback to Google ML Kit (1)
                LaunchedEffect(isCloudConfigured, currentEngineValue) {
                    if (currentEngineValue == 4 && !isCloudConfigured) {
                        currentEngineValue = 1
                        prefs.edit().putInt(engineKey, 1).apply()
                    }
                }
                
                val availableEngines = remember(isCloudConfigured) {
                    buildList {
                        add(TranslationEngineType.GoogleMLKit)
                        add(TranslationEngineType.GoogleGTX)
                        add(TranslationEngineType.Yandex)
                        if (isCloudConfigured) {
                            add(TranslationEngineType.CloudAI)
                        }
                    }
                }
                
                fun getEngineName(type: TranslationEngineType): String {
                    return when (type) {
                        TranslationEngineType.GoogleMLKit -> "On-Device (Google ML Kit)"
                        TranslationEngineType.GoogleGTX -> "Online (Google GTX Scraper)"
                        TranslationEngineType.Yandex -> "Online (Yandex Scraper)"
                        TranslationEngineType.CloudAI -> "Cloud AI (API Key)"
                        else -> "None"
                    }
                }
                
                var dropdownExpanded by remember { mutableStateOf(false) }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { dropdownExpanded = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Translation Engine",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = getEngineName(TranslationEngineType.fromInt(currentEngineValue)),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24),
                            contentDescription = "Select Engine",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        ) {
                            availableEngines.forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = getEngineName(type),
                                            fontWeight = if (currentEngineValue == type.value) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentEngineValue == type.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        currentEngineValue = type.value
                                        prefs.edit().putInt(engineKey, type.value).apply()
                                        dropdownExpanded = false
                                        if (viewModel.isTranslationActive) {
                                            onApplyTranslationClick()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                var rememberTranslationState by remember { 
                    mutableStateOf(prefs.getBoolean("reader_remember_translation_state", true)) 
                }
                SettingsSwitchRow("Remember Translation State", rememberTranslationState) { checked ->
                    rememberTranslationState = checked
                    prefs.edit().putBoolean("reader_remember_translation_state", checked).apply()
                    if (!checked) {
                        viewModel.mlSettings = viewModel.mlSettings
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Color Picker card
        item {
            Text(
                text = stringResource(R.string.read_color),
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp))
                    .padding(16.dp)
                    .padding(bottom = 24.dp)
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val bgColors = context.resources.getIntArray(R.array.readerBgColors).toList()
                val textColors = context.resources.getIntArray(R.array.readerTextColors).toList()
                val currentBgColor by viewModel.backgroundColorLive.observeAsState(viewModel.backgroundColor)
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(bgColors.indices.toList()) { index ->
                        val bgColor = bgColors[index]
                        val txtColor = textColors[index]
                        val isSelected = currentBgColor == bgColor
                        
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(bgColor))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    onColorSelect(bgColor, txtColor)
                                }
                        )
                    }
                    
                    // Custom color addition
                    item {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray)
                                .clickable {
                                    onColorCustomClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_add_24),
                                contentDescription = "Add Custom Color",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSwitchRow(
    text: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Black,
        contentColor = Color.White
    )
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 14.sp)
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
            TactileRulerSlider(context).apply {
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
fun SettingsSliderRow(
    title: String,
    value: Float,
    valueFrom: Float,
    valueTo: Float,
    stepSize: Float,
    leftIcon: Int,
    rightIcon: Int,
    onValueChange: (Float) -> Unit
) {
    var liveValue by remember(value) { mutableStateOf(value) }
    
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = if (stepSize >= 1f) liveValue.toInt().toString() else liveValue.toString(),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(leftIcon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            TactileRulerSliderCompose(
                value = value,
                valueFrom = valueFrom,
                valueTo = valueTo,
                stepSize = stepSize,
                onValueChangeLive = { liveValue = it },
                onValueChangeFinished = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            Icon(
                painter = painterResource(rightIcon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
