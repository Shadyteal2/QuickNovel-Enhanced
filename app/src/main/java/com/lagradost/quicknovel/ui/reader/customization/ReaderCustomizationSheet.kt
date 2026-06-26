package com.lagradost.quicknovel.ui.reader.customization

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ReadActivityViewModel
import com.lagradost.quicknovel.ui.txt
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.util.UIHelper.clipboardHelper
import com.lagradost.quicknovel.util.applyGlassStyle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ReaderCustomizationSheet : BottomSheetDialogFragment() {
    companion object {
        private const val ARG_INITIAL_TAB = "arg_initial_tab"

        fun newInstance(initialTab: Int = 0): ReaderCustomizationSheet {
            return ReaderCustomizationSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_INITIAL_TAB, initialTab)
                }
            }
        }
    }

    private var viewModel: ReadActivityViewModel? = null

    fun setViewModel(vm: ReadActivityViewModel) {
        viewModel = vm
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vm = viewModel ?: throw IllegalStateException("ViewModel must be set before showing this bottom sheet")
        val initialTab = arguments?.getInt(ARG_INITIAL_TAB, 0) ?: 0
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuickNovelTheme {
                    CustomizationSheetCompose(
                        viewModel = vm,
                        initialTab = initialTab,
                        onDismiss = { dismiss() },
                        onOpenThemeEditor = { themeName ->
                            val sheet = ThemeEditorSheet.newInstance(themeName)
                            sheet.setViewModel(vm) {
                                // Redraw / Reload customization store
                            }
                            sheet.show(parentFragmentManager, "theme_editor")
                        },
                        onOpenRuleEditor = { ruleId ->
                            val sheet = ContentRuleEditorSheet.newInstance(ruleId)
                            sheet.setViewModel(vm) {
                                // Redraw / Reload customization store
                            }
                            sheet.show(parentFragmentManager, "rule_editor")
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
fun CustomizationSheetCompose(
    viewModel: ReadActivityViewModel,
    initialTab: Int,
    onDismiss: () -> Unit,
    onOpenThemeEditor: (String?) -> Unit,
    onOpenRuleEditor: (String?) -> Unit
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    val tabs = listOf("Themes", "Cleaner", "Replace Words")

    val themes by ReaderCustomizationStore.themes.collectAsState()
    val rules by ReaderCustomizationStore.rules.collectAsState()

    // Observe global settings for rules
    val context = LocalContext.current
    val sharedPrefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var rulesEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean(com.lagradost.quicknovel.ReaderPrefs.CONTENT_RULES_ENABLED, true))
    }

    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f),
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
                Text("Reader Customization", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(targetState = selectedTab, label = "TabChangeTransition") { tabIndex ->
                    when (tabIndex) {
                        0 -> ThemesTabContent(
                            themes = themes,
                            onApplyTheme = { theme ->
                                ReaderCustomizationStore.applyTheme(theme, viewModel)
                            },
                            onDeleteTheme = { name ->
                                coroutineScope.launch {
                                    ReaderCustomizationStore.deleteTheme(context, name)
                                }
                            },
                            onExportTheme = { theme ->
                                val json = ReaderCustomizationStore.exportThemeToString(theme)
                                if (json != null) {
                                    clipboardHelper(txt("Theme: ${theme.name}"), json)
                                    try {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_TEXT, json)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Theme"))
                                    } catch (t: Throwable) {
                                        com.lagradost.quicknovel.mvvm.logError(t)
                                    }
                                }
                            },
                            onImportTheme = {
                                // Copy-paste import prompt dialog could be nice, or simple Clipboard reading
                                // Let's implement import from Clipboard directly in a button!
                            },
                            onOpenThemeEditor = onOpenThemeEditor
                        )
                        1 -> CleanerTabContent(
                            rules = rules,
                            rulesEnabled = rulesEnabled,
                            onRulesEnabledChange = { enabled ->
                                rulesEnabled = enabled
                                sharedPrefs.edit().putBoolean(com.lagradost.quicknovel.ReaderPrefs.CONTENT_RULES_ENABLED, enabled).apply()
                            },
                            onDeleteRule = { rule ->
                                coroutineScope.launch {
                                    val currentRules = ReaderCustomizationStore.rules.value.filter { it.id != rule.id }
                                    ReaderCustomizationStore.saveRules(context, currentRules)
                                }
                            },
                            onToggleRule = { rule, isEnabled ->
                                coroutineScope.launch {
                                    val currentRules = ReaderCustomizationStore.rules.value.map {
                                        if (it.id == rule.id) it.copy(enabled = isEnabled) else it
                                    }
                                    ReaderCustomizationStore.saveRules(context, currentRules)
                                }
                            },
                            onOpenRuleEditor = onOpenRuleEditor
                        )
                        2 -> ReplaceWordsTabContent(
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemesTabContent(
    themes: List<ReaderTheme>,
    onApplyTheme: (ReaderTheme) -> Unit,
    onDeleteTheme: (String) -> Unit,
    onExportTheme: (ReaderTheme) -> Unit,
    onImportTheme: (String) -> Unit,
    onOpenThemeEditor: (String?) -> Unit
) {
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saved Themes", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clipData = clipboard?.primaryClip
                    val clipboardText = if (clipData != null && clipData.itemCount > 0) {
                        clipData.getItemAt(0).text?.toString() ?: ""
                    } else {
                        ""
                    }
                    importText = clipboardText
                    showImportDialog = true
                }) {
                    Text("Import from Clipboard")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (themes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No themes saved yet. Click + to create one!")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(themes, key = { it.name }) { theme ->
                        ThemeCard(
                            theme = theme,
                            onApply = { onApplyTheme(theme) },
                            onEdit = { onOpenThemeEditor(theme.name) },
                            onDelete = { onDeleteTheme(theme.name) },
                            onExport = { onExportTheme(theme) }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onOpenThemeEditor(null) },
            icon = { Icon(Icons.Default.Add, contentDescription = "Add Theme") },
            text = { Text("New Theme") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Theme") },
            text = {
                Column {
                    Text("Paste your theme JSON code below:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val theme = ReaderCustomizationStore.importThemeFromString(importText)
                        if (theme != null) {
                            coroutineScope.launch {
                                ReaderCustomizationStore.saveTheme(context, theme)
                            }
                            showImportDialog = false
                            importText = ""
                            com.lagradost.quicknovel.CommonActivity.showToast("Theme '${theme.name}' imported successfully")
                        } else {
                            com.lagradost.quicknovel.CommonActivity.showToast("Invalid Theme JSON")
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeCard(
    theme: ReaderTheme,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    // Spring scaling physics-based interaction feedback
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "PressedScaling"
    )

    val cardBg = remember(theme.backgroundColor) { Color(theme.backgroundColor ?: Color.Black.toArgb()) }
    val cardText = remember(theme.textColor) { Color(theme.textColor ?: Color.White.toArgb()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .glassCard()
            .combinedClickable(
                onClick = { onApply() },
                onLongClick = { expandedMenu = true }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBg.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = theme.name,
                fontWeight = FontWeight.Bold,
                color = cardText,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Sample Reading Text",
                color = cardText.copy(alpha = 0.7f),
                fontSize = 12.sp,
                maxLines = 2,
                lineHeight = 14.sp
            )
        }

        DropdownMenu(
            expanded = expandedMenu,
            onDismissRequest = { expandedMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit Theme") },
                onClick = {
                    onEdit()
                    expandedMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Share / Export") },
                onClick = {
                    onExport()
                    expandedMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onDelete()
                    expandedMenu = false
                }
            )
        }
    }
}

@Composable
fun CleanerTabContent(
    rules: List<ContentCleanRule>,
    rulesEnabled: Boolean,
    onRulesEnabledChange: (Boolean) -> Unit,
    onDeleteRule: (ContentCleanRule) -> Unit,
    onToggleRule: (ContentCleanRule, Boolean) -> Unit,
    onOpenRuleEditor: (String?) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Clean Scraped Content", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Removes annoying ads, watermark text, etc.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = rulesEnabled,
                    onCheckedChange = onRulesEnabledChange
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (rules.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Content Cleaner",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Content Cleaner automatically removes unwanted text and website elements from scraped novel chapters.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("For example, it can remove:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        val examples = listOf(
                            "Ads",
                            "\"Read only on XYZNovel\"",
                            "Telegram/Discord promotions",
                            "Watermarks",
                            "Sponsor messages"
                        )
                        examples.forEach { example ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Text(example, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Example", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Before", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Read only on XYZNovel.com\n\nThe autumn sun is warm...\n\nJoin our Telegram",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .width(1.dp)
                                        .height(80.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("After", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "The autumn sun is warm...",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How to use:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        val steps = listOf(
                            "Tap Add Rule.",
                            "Choose the novel provider (or \"All Providers\").",
                            "Enter the website element and text.",
                            "Reload the chapter to see the changes."
                        )
                        steps.forEachIndexed { idx, step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "${idx + 1}.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(step, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Advanced: CSS Selectors and Regex are for advanced users and usually aren't needed for removing simple text.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            onToggle = { isEnabled -> onToggleRule(rule, isEnabled) },
                            onEdit = { onOpenRuleEditor(rule.id) },
                            onDelete = { onDeleteRule(rule) }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onOpenRuleEditor(null) },
            icon = { Icon(Icons.Default.Add, contentDescription = "Add Rule") },
            text = { Text("Add Rule") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
fun RuleCard(
    rule: ContentCleanRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .clickable { onEdit() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (rule.providerApiName == "*") "All Providers" else rule.providerApiName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    if (rule.selectorsToRemove.isNotEmpty()) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Removes: ${rule.selectorsToRemove.size}") },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    if (rule.replacements.isNotEmpty()) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Replaces: ${rule.replacements.size}") }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun TutorialStepRow(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ReplaceWordsTabContent(
    viewModel: ReadActivityViewModel
) {
    val currentAliases by viewModel.aliases.observeAsState(emptyMap())
    
    var showInputCard by remember { mutableStateOf(false) }
    var editOriginalKey by remember { mutableStateOf<String?>(null) }
    
    var originalText by remember { mutableStateOf("") }
    var replacementText by remember { mutableStateOf("") }

    LaunchedEffect(editOriginalKey) {
        val key = editOriginalKey
        if (key != null) {
            originalText = key
            replacementText = currentAliases[key] ?: ""
            showInputCard = true
        } else {
            originalText = ""
            replacementText = ""
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Novel Replaced Words", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Character name renames, word replacements, etc.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = showInputCard,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (editOriginalKey == null) "Add Replace Word" else "Edit Replace Word",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = originalText,
                        onValueChange = { originalText = it },
                        label = { Text("Original text / name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = replacementText,
                        onValueChange = { replacementText = it },
                        label = { Text("Replace with") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showInputCard = false
                                editOriginalKey = null
                            }
                        ) {
                            Text("Cancel", color = Color.Gray)
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                val orig = originalText.trim()
                                val rep = replacementText.trim()
                                if (orig.isNotEmpty() && rep.isNotEmpty()) {
                                    val prevKey = editOriginalKey
                                    if (prevKey != null && prevKey != orig) {
                                        viewModel.removeAlias(prevKey)
                                    }
                                    viewModel.addAlias(orig, rep)
                                    showInputCard = false
                                    editOriginalKey = null
                                }
                            }
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (currentAliases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .glassCard(shape = RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_font_download_24),
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No character name renames or replaced words added yet.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentAliases.keys.toList()) { key ->
                        val aliasVal = currentAliases[key] ?: ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(shape = RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = key,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_arrow_forward_24),
                                    contentDescription = "maps to",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp).padding(vertical = 2.dp)
                                )
                                Text(
                                    text = aliasVal,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            IconButton(
                                onClick = {
                                    editOriginalKey = key
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_edit_24),
                                    contentDescription = "Edit",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.removeAlias(key)
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_delete_outline_24),
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                editOriginalKey = null
                showInputCard = true
            },
            icon = { Icon(Icons.Default.Add, contentDescription = "Add Replace Word") },
            text = { Text("Add Word") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}
