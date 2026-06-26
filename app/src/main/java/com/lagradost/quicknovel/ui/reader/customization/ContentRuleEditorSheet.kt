package com.lagradost.quicknovel.ui.reader.customization

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import java.util.UUID

class ContentRuleEditorSheet : BottomSheetDialogFragment() {
    companion object {
        private const val ARG_RULE_ID = "arg_rule_id"

        fun newInstance(ruleId: String? = null): ContentRuleEditorSheet {
            return ContentRuleEditorSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_RULE_ID, ruleId)
                }
            }
        }
    }

    private var viewModel: ReadActivityViewModel? = null
    private var onRuleSaved: (() -> Unit)? = null

    fun setViewModel(vm: ReadActivityViewModel, onSaved: () -> Unit) {
        viewModel = vm
        onRuleSaved = onSaved
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ruleId = arguments?.getString(ARG_RULE_ID)
        val initialRule = if (ruleId != null) {
            ReaderCustomizationStore.rules.value.firstOrNull { it.id == ruleId }
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
                    ContentRuleEditorCompose(
                        initialRule = initialRule,
                        onDismiss = { dismiss() },
                        onSave = { rule ->
                            val vm = viewModel
                            if (vm != null) {
                                vm.viewModelScope.launch {
                                    val currentRules = ReaderCustomizationStore.rules.value.toMutableList()
                                    val index = currentRules.indexOfFirst { it.id == rule.id }
                                    if (index >= 0) {
                                        currentRules[index] = rule
                                    } else {
                                        currentRules.add(rule)
                                    }
                                    ReaderCustomizationStore.saveRules(requireContext(), currentRules)
                                    onRuleSaved?.invoke()
                                    dismiss()
                                }
                            } else {
                                dismiss()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentRuleEditorCompose(
    initialRule: ContentCleanRule?,
    onDismiss: () -> Unit,
    onSave: (ContentCleanRule) -> Unit
) {
    val id = remember { initialRule?.id ?: UUID.randomUUID().toString() }
    var providerApiName by remember { mutableStateOf(initialRule?.providerApiName ?: "*") }
    var enabled by remember { mutableStateOf(initialRule?.enabled ?: true) }
    var selectorsToRemove by remember { mutableStateOf(initialRule?.selectorsToRemove ?: emptyList()) }
    var replacements by remember { mutableStateOf(initialRule?.replacements ?: emptyList()) }

    var newSelector by remember { mutableStateOf("") }
    var providerExpanded by remember { mutableStateOf(false) }

    val providersList = remember {
        listOf("*") + com.lagradost.quicknovel.util.Apis.apis.map { it.name }
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
                    text = if (initialRule == null) "New Cleaner Rule" else "Edit Cleaner Rule",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Target Provider Dropdown
                Text("Target Provider", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard()
                        .clickable { providerExpanded = true }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (providerApiName == "*") "All Providers" else providerApiName)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                    }
                    DropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        providersList.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(if (name == "*") "All Providers" else name) },
                                onClick = {
                                    providerApiName = name
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Enabled Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rule Enabled", fontWeight = FontWeight.Medium)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Jsoup Selector Section
                Text("Remove Elements (CSS Selectors)", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSelector,
                        onValueChange = { newSelector = it },
                        placeholder = { Text("e.g. div.advertisement") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newSelector.isNotBlank() && !selectorsToRemove.contains(newSelector.trim())) {
                                selectorsToRemove = selectorsToRemove + newSelector.trim()
                                newSelector = ""
                            }
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add selector")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectorsToRemove.forEach { selector ->
                        InputChip(
                            selected = false,
                            onClick = { selectorsToRemove = selectorsToRemove - selector },
                            label = { Text(selector) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Replacements Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Text Replacements", fontWeight = FontWeight.Medium)
                    Button(
                        onClick = {
                            replacements = replacements + TextReplacement("", "", false)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add replacement", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Replacement", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                replacements.forEachIndexed { index, replacement ->
                    ReplacementRow(
                        replacement = replacement,
                        onReplacementChange = { updated ->
                            val newList = replacements.toMutableList()
                            newList[index] = updated
                            replacements = newList
                        },
                        onDelete = {
                            replacements = replacements.filterIndexed { idx, _ -> idx != index }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onSave(
                        ContentCleanRule(
                            id = id,
                            providerApiName = providerApiName,
                            enabled = enabled,
                            selectorsToRemove = selectorsToRemove,
                            replacements = replacements.filter { it.find.isNotEmpty() }
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Cleaner Rule", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ReplacementRow(
    replacement: TextReplacement,
    onReplacementChange: (TextReplacement) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replacement.find,
                    onValueChange = { onReplacementChange(replacement.copy(find = it)) },
                    placeholder = { Text("Find text") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = replacement.replace,
                    onValueChange = { onReplacementChange(replacement.copy(replace = it)) },
                    placeholder = { Text("Replace with") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = replacement.isRegex,
                    onCheckedChange = { onReplacementChange(replacement.copy(isRegex = it)) }
                )
                Text("Regex Match", fontSize = 12.sp)
            }
        }
    }
}
