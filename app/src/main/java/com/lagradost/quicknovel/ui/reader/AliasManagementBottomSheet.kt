package com.lagradost.quicknovel.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.util.DrawerHelper
import com.lagradost.quicknovel.util.applyGlassStyle

class AliasManagementBottomSheet : BottomSheetDialogFragment() {
    companion object {
        private const val ARG_BG_ID = "arg_bg_id"

        fun newInstance(backgroundViewId: Int): AliasManagementBottomSheet {
            return AliasManagementBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_BG_ID, backgroundViewId)
                }
            }
        }
    }

    private lateinit var viewModel: ReadActivityViewModel

    fun setViewModel(vm: ReadActivityViewModel) {
        viewModel = vm
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuickNovelTheme {
                    AliasManagementCompose(
                        viewModel = viewModel,
                        onDismiss = { dismiss() }
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
        setupScaling()
    }

    private fun setupScaling() {
        val bgId = arguments?.getInt(ARG_BG_ID) ?: return
        if (bgId == 0) return
        val backgroundView = activity?.findViewById<View>(bgId) ?: return

        val dialog = dialog as? BottomSheetDialog ?: return
        dialog.applyGlassStyle()
        val behavior = dialog.behavior
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        DrawerHelper.applyScalingAnimation(backgroundView, 1f)

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    DrawerHelper.resetScaling(backgroundView)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                DrawerHelper.applyScalingAnimation(backgroundView, slideOffset)
            }
        })
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        val bgId = arguments?.getInt(ARG_BG_ID) ?: return
        if (bgId == 0) return
        val backgroundView = activity?.findViewById<View>(bgId) ?: return
        DrawerHelper.resetScaling(backgroundView)
    }

    override fun getTheme(): Int {
        return R.style.BottomSheetDrawerTheme
    }
}

@Composable
fun AliasManagementCompose(
    viewModel: ReadActivityViewModel,
    onDismiss: () -> Unit
) {
    val currentAliases by viewModel.aliases.observeAsState(emptyMap())
    
    var showInputCard by remember { mutableStateOf(false) }
    var editOriginalKey by remember { mutableStateOf<String?>(null) }
    
    var originalText by remember { mutableStateOf("") }
    var replacementText by remember { mutableStateOf("") }

    // Prefill inputs when entering edit mode
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        // Drag handle indicator
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
                    .glassCard(shape = RoundedCornerShape(2.dp))
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Header Title and Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.character_aliases),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (!showInputCard) {
                FilledTonalButton(
                    onClick = {
                        editOriginalKey = null
                        showInputCard = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_add_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Alias", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Animated add/edit input form card
        AnimatedVisibility(
            visible = showInputCard,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = if (editOriginalKey == null) stringResource(R.string.add_alias) else stringResource(R.string.edit_alias),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = originalText,
                    onValueChange = { originalText = it },
                    label = { Text("Original text (name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = replacementText,
                    onValueChange = { replacementText = it },
                    label = { Text("Alias replacement") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    )
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
                        Text(stringResource(R.string.cancel), color = Color.Gray)
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
                        Text(stringResource(R.string.sort_apply))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Aliases list or empty state
        if (currentAliases.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .glassCard(shape = RoundedCornerShape(24.dp))
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
                        text = "No character aliases added yet.",
                        color = Color.Gray,
                        fontSize = 15.sp
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
                            .glassCard(shape = RoundedCornerShape(20.dp))
                            .padding(14.dp),
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

                        // Direct Action buttons
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
}
