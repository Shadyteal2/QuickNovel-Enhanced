package com.lagradost.quicknovel.ui.reader.customization

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ReadActivityViewModel
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.util.applyGlassStyle
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class ThemeEditorSheet : BottomSheetDialogFragment() {
    companion object {
        private const val ARG_THEME_NAME = "arg_theme_name"

        fun newInstance(themeName: String? = null): ThemeEditorSheet {
            return ThemeEditorSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_THEME_NAME, themeName)
                }
            }
        }
    }

    private var viewModel: ReadActivityViewModel? = null
    private var onThemeSaved: (() -> Unit)? = null

    fun setViewModel(vm: ReadActivityViewModel, onSaved: () -> Unit) {
        viewModel = vm
        onThemeSaved = onSaved
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val themeName = arguments?.getString(ARG_THEME_NAME)
        val initialTheme = if (themeName != null) {
            ReaderCustomizationStore.themes.value.firstOrNull { it.name == themeName }
        } else {
            null
        }

        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuickNovelTheme {
                    ThemeEditorCompose(
                        initialTheme = initialTheme,
                        onDismiss = { dismiss() },
                        onSave = { theme ->
                            val vm = viewModel
                            if (vm != null) {
                                vm.viewModelScope.launch {
                                    ReaderCustomizationStore.saveTheme(requireContext(), theme)
                                    onThemeSaved?.invoke()
                                    dismiss()
                                }
                            } else {
                                dismiss()
                            }
                        },
                        onShowColorPicker = { initialColor, onColorSelected ->
                            com.jaredrummler.android.colorpicker.ColorPickerDialog.newBuilder()
                                .setColor(initialColor)
                                .setShowAlphaSlider(false)
                                .setDialogTitle(R.string.reading_color)
                                .create()
                                .also { dialog ->
                                    dialog.setColorPickerDialogListener(object : com.jaredrummler.android.colorpicker.ColorPickerDialogListener {
                                        override fun onColorSelected(dialogId: Int, color: Int) {
                                            onColorSelected(color)
                                        }
                                        override fun onDialogDismissed(dialogId: Int) {}
                                    })
                                    dialog.show(parentFragmentManager, "theme_color_picker")
                                }
                        }
                    )
                }
            }
        }.also { view ->
            view.setViewTreeLifecycleOwner(viewLifecycleOwner)
            view.setViewTreeViewModelStoreOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dialog = dialog as? BottomSheetDialog ?: return
        dialog.applyGlassStyle()
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
}

@Composable
fun ThemeEditorCompose(
    initialTheme: ReaderTheme?,
    onDismiss: () -> Unit,
    onSave: (ReaderTheme) -> Unit,
    onShowColorPicker: (initialColor: Int, onColorSelected: (Int) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initialTheme?.name ?: "") }
    var textColorInt by remember { mutableStateOf(initialTheme?.textColor ?: Color.White.toArgb()) }
    var backgroundColorInt by remember { mutableStateOf(initialTheme?.backgroundColor ?: Color.Black.toArgb()) }
    var textSize by remember { mutableStateOf(initialTheme?.textSize ?: 18) }
    var lineHeightMultiplier by remember { mutableStateOf(initialTheme?.lineHeightMultiplier ?: 1.3f) }
    var verticalPadding by remember { mutableStateOf(initialTheme?.verticalPadding ?: 8f) }
    var textFont by remember { mutableStateOf(initialTheme?.textFont ?: "") }
    var bionicReading by remember { mutableStateOf(initialTheme?.bionicReading ?: false) }
    var paddingHorizontal by remember { mutableStateOf(initialTheme?.paddingHorizontal ?: 24) }
    var backgroundGrain by remember { mutableStateOf(initialTheme?.backgroundGrain ?: 0) }
    var letterSpacing by remember { mutableStateOf(initialTheme?.letterSpacing ?: 0f) }
    var luminescent by remember { mutableStateOf(initialTheme?.luminescent ?: false) }
    var luminescentIntensity by remember { mutableStateOf(initialTheme?.luminescentIntensity ?: 0.5f) }

    val presetColors = listOf(
        Color(0xFFFFFFFF), Color(0xFFE0E0E0), Color(0xFFF4ECD8), Color(0xFFEADBCE),
        Color(0xFFF5F5DC), Color(0xFFE8F5E9), Color(0xFFE3F2FD), Color(0xFF5B4636),
        Color(0xFF292832), Color(0xFF1E1E1E), Color(0xFF121212), Color(0xFF000000)
    )

    // Load available fonts
    val context = LocalContext.current
    val fontsList = remember {
        val customFontsFolder = File(context.filesDir, "fonts")
        val customFonts = if (customFontsFolder.exists()) customFontsFolder.listFiles() ?: emptyArray() else emptyArray()
        listOf("") + customFonts.map { it.name } + com.lagradost.quicknovel.util.UIHelper.systemFonts.map { it.name }
    }

    var fontExpanded by remember { mutableStateOf(false) }

    // Isolate the live preview parameters so dragging sliders doesn't cause massive recomposition of the editor controls.
    val previewBg = remember(backgroundColorInt) { Color(backgroundColorInt) }
    val previewText = remember(textColorInt) { Color(textColorInt) }
    val previewTextSize = remember(textSize) { textSize.sp }
    val previewLineHeight = remember(lineHeightMultiplier) { (textSize * lineHeightMultiplier).sp }
    val previewPadding = remember(verticalPadding) { verticalPadding.dp }

    val fontFile = remember(textFont) {
        if (textFont.isBlank()) null else {
            val found = com.lagradost.quicknovel.util.UIHelper.systemFonts.firstOrNull { it.name == textFont }
            if (found != null) found else {
                val file = File(File(context.filesDir, "fonts"), textFont)
                if (file.exists()) file else null
            }
        }
    }

    val customFontFamily = remember(textFont, fontFile) {
        if (textFont.isNotEmpty()) {
            val preloaded = com.lagradost.quicknovel.util.PreloadedFontsCache.get(textFont)
            if (preloaded != null) {
                try {
                    FontFamily(preloaded)
                } catch (t: Throwable) {
                    FontFamily.Default
                }
            } else if (fontFile != null) {
                try {
                    FontFamily(android.graphics.Typeface.createFromFile(fontFile))
                } catch (t: Throwable) {
                    FontFamily.Default
                }
            } else {
                FontFamily.Default
            }
        } else {
            FontFamily.Default
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialTheme == null) "New Theme" else "Edit Theme",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live Preview Card - DETACHED (Sticky at top)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(vertical = 4.dp)
                    .drawBehind {
                        if (backgroundGrain > 0) {
                            val bitmap = com.lagradost.quicknovel.util.GrainDrawableCache.getOrCreate(context, backgroundGrain)
                            drawIntoCanvas { canvas ->
                                val paint = android.graphics.Paint().apply {
                                    shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
                                }
                                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                            }
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = previewBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = paddingHorizontal.dp, vertical = previewPadding),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Reader Preview Mode\nLine height and margins.",
                        color = previewText,
                        fontSize = previewTextSize,
                        lineHeight = previewLineHeight,
                        fontFamily = customFontFamily,
                        style = LocalTextStyle.current.copy(
                            letterSpacing = letterSpacing.sp,
                            shadow = if (luminescent) {
                                androidx.compose.ui.graphics.Shadow(
                                    color = Color(0xFFFFE8B5).copy(alpha = luminescentIntensity * 0.8f),
                                    blurRadius = luminescentIntensity * 15f
                                )
                            } else null
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Theme Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = initialTheme == null // Name is the unique file identifier, don't change it if editing
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Colors Pickers
                Text("Colors", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Text("Background Color", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                ColorPaletteRow(
                    colors = presetColors,
                    selectedColor = Color(backgroundColorInt),
                    onColorSelected = { backgroundColorInt = it.toArgb() },
                    onCustomClick = {
                        onShowColorPicker(backgroundColorInt) { color ->
                            backgroundColorInt = color
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Text Color", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                ColorPaletteRow(
                    colors = presetColors,
                    selectedColor = Color(textColorInt),
                    onColorSelected = { textColorInt = it.toArgb() },
                    onCustomClick = {
                        onShowColorPicker(textColorInt) { color ->
                            textColorInt = color
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Text Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Text Size", fontWeight = FontWeight.Medium)
                    Text("${textSize}sp", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = textSize.toFloat(),
                    onValueChange = { textSize = it.roundToInt() },
                    valueRange = 14f..28f,
                    steps = 13
                )

                // Line Height Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Line Height", fontWeight = FontWeight.Medium)
                    Text(String.format("%.2fx", lineHeightMultiplier), fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = lineHeightMultiplier,
                    onValueChange = { lineHeightMultiplier = it },
                    valueRange = 1.0f..2.0f
                )

                // Vertical Padding Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Paragraph Spacing", fontWeight = FontWeight.Medium)
                    Text("${verticalPadding.roundToInt()}dp", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = verticalPadding,
                    onValueChange = { verticalPadding = it },
                    valueRange = 8f..40f
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Font Selector
                Text("Font Family", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard()
                        .clickable { fontExpanded = true }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (textFont.isEmpty()) "System Default" else textFont)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                    }
                    DropdownMenu(
                        expanded = fontExpanded,
                        onDismissRequest = { fontExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        fontsList.forEach { fontName ->
                            DropdownMenuItem(
                                text = { Text(if (fontName.isEmpty()) "System Default" else fontName) },
                                onClick = {
                                    textFont = fontName
                                    fontExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bionic Reading Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Bionic Reading", fontWeight = FontWeight.Medium)
                        Text("Highlight first parts of words for speed reading", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = bionicReading,
                        onCheckedChange = { bionicReading = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Text Side Margin Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Text Side Margin", fontWeight = FontWeight.Medium)
                    Text("${paddingHorizontal}dp", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = paddingHorizontal.toFloat(),
                    onValueChange = { paddingHorizontal = it.roundToInt() },
                    valueRange = 0f..50f
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Background Grain & Texture Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Background Grain & Texture", fontWeight = FontWeight.Medium)
                    Text("$backgroundGrain%", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = backgroundGrain.toFloat(),
                    onValueChange = { backgroundGrain = it.roundToInt() },
                    valueRange = 0f..100f
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Letter Spacing Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Letter Spacing", fontWeight = FontWeight.Medium)
                    Text(String.format("%.2f", letterSpacing), fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = letterSpacing,
                    onValueChange = { letterSpacing = it },
                    valueRange = -0.05f..0.20f
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Text Luminescence Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Text Luminescence (Glow)", fontWeight = FontWeight.Medium)
                        Text("Adds ambient light glow to the text", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = luminescent,
                        onCheckedChange = { luminescent = it }
                    )
                }

                if (luminescent) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Luminescence Intensity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Luminescence Intensity", fontWeight = FontWeight.Medium)
                        Text(String.format("%d%%", (luminescentIntensity * 100).roundToInt()), fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = luminescentIntensity,
                        onValueChange = { luminescentIntensity = it },
                        valueRange = 0.1f..1.0f
                    )
                }
 
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            ReaderTheme(
                                name = name,
                                textColor = textColorInt,
                                backgroundColor = backgroundColorInt,
                                textSize = textSize,
                                lineHeightMultiplier = lineHeightMultiplier,
                                verticalPadding = verticalPadding,
                                textFont = textFont,
                                bionicReading = bionicReading,
                                paddingHorizontal = paddingHorizontal,
                                backgroundGrain = backgroundGrain,
                                letterSpacing = letterSpacing,
                                luminescent = luminescent,
                                luminescentIntensity = luminescentIntensity
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank()
            ) {
                Text("Save Theme", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ColorPaletteRow(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onCustomClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        colors.take(7).forEach { color ->
            ColorCircle(
                color = color,
                isSelected = color == selectedColor,
                onSelected = { onColorSelected(color) }
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        colors.drop(7).forEach { color ->
            ColorCircle(
                color = color,
                isSelected = color == selectedColor,
                onSelected = { onColorSelected(color) }
            )
        }
        CustomColorCircle(
            selectedColor = selectedColor,
            presetColors = colors,
            onCustomClick = onCustomClick
        )
        Spacer(modifier = Modifier.size(36.dp))
    }
}

@Composable
fun CustomColorCircle(
    selectedColor: Color,
    presetColors: List<Color>,
    onCustomClick: () -> Unit
) {
    val isCustomActive = selectedColor !in presetColors
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(if (isCustomActive) selectedColor else Color.Gray.copy(alpha = 0.2f), CircleShape)
            .border(
                width = if (isCustomActive) 3.dp else 1.dp,
                color = if (isCustomActive) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable { onCustomClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Custom Color",
            tint = if (isCustomActive) {
                val colorInt = selectedColor.toArgb()
                val isLight = (0.299 * ((colorInt shr 16) and 0xFF) + 0.587 * ((colorInt shr 8) and 0xFF) + 0.114 * (colorInt and 0xFF)) > 128
                if (isLight) Color.Black else Color.White
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(color, CircleShape)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable { onSelected() }
    )
}
