package com.lagradost.quicknovel.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.quicknovel.ReadActivityViewModel
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.util.applyGlassStyle

class TapZoneCustomizationSheet : BottomSheetDialogFragment() {
    companion object {
        fun newInstance(): TapZoneCustomizationSheet {
            return TapZoneCustomizationSheet()
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
        val vm = viewModel ?: throw IllegalStateException("ViewModel must be set")
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuickNovelTheme {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        TapZoneCustomizer(
                            viewModel = vm,
                            onDismiss = { dismiss() }
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.applyGlassStyle()
    }
}

@Composable
fun TapZoneCustomizer(
    viewModel: ReadActivityViewModel,
    onDismiss: () -> Unit
) {
    var zonesEnabled by remember { mutableStateOf(viewModel.tapZonesEnabled) }
    var zoneLeft by remember { mutableStateOf(viewModel.tapZoneLeft) }
    var zoneRight by remember { mutableStateOf(viewModel.tapZoneRight) }
    var zoneTop by remember { mutableStateOf(viewModel.tapZoneTop) }
    var zoneCenter by remember { mutableStateOf(viewModel.tapZoneCenter) }
    var zoneBottom by remember { mutableStateOf(viewModel.tapZoneBottom) }
    
    val context = androidx.compose.ui.platform.LocalContext.current

    fun validateAndSave(zoneName: String, newAction: String, onApproved: () -> Unit) {
        if (newAction == "Toggle UI") {
            onApproved()
            return
        }
        var count = 0
        if (zoneTop == "Toggle UI") count++
        if (zoneBottom == "Toggle UI") count++
        if (zoneLeft == "Toggle UI") count++
        if (zoneRight == "Toggle UI") count++
        if (zoneCenter == "Toggle UI") count++

        val isChangingFromToggleUI = when (zoneName) {
            "Top" -> zoneTop == "Toggle UI"
            "Bottom" -> zoneBottom == "Toggle UI"
            "Left" -> zoneLeft == "Toggle UI"
            "Right" -> zoneRight == "Toggle UI"
            "Center" -> zoneCenter == "Toggle UI"
            else -> false
        }

        if (isChangingFromToggleUI && count <= 1) {
            android.widget.Toast.makeText(
                context,
                "At least one zone must be set to 'Toggle UI' to prevent settings lockout!",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } else {
            onApproved()
        }
    }

    val actions = listOf("Prev Chapter", "Next Chapter", "Toggle UI", "Toggle TTS", "Scroll Up", "Scroll Down", "Nothing")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tap Zones", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Switch to enable/disable
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(shape = RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Enable Tap Zones", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Tap screen regions to trigger actions", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = zonesEnabled,
                onCheckedChange = {
                    zonesEnabled = it
                    viewModel.tapZonesEnabled = it
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (zonesEnabled) {
            Text(
                "Tap a zone to customize its action:",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Visual Phone Layout preview
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(380.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top zone
                    ZoneBox(
                        label = "Top Zone",
                        currentAction = zoneTop,
                        actions = actions,
                        onActionSelected = { action ->
                            validateAndSave("Top", action) {
                                zoneTop = action
                                viewModel.tapZoneTop = action
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.2f)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                    )

                    // Middle row (Left, Center, Right)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.6f)
                    ) {
                        ZoneBox(
                            label = "Left Zone",
                            currentAction = zoneLeft,
                            actions = actions,
                            onActionSelected = { action ->
                                validateAndSave("Left", action) {
                                    zoneLeft = action
                                    viewModel.tapZoneLeft = action
                                }
                            },
                            modifier = Modifier
                                .weight(0.3f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f))
                        )

                        ZoneBox(
                            label = "Center Zone",
                            currentAction = zoneCenter,
                            actions = actions,
                            onActionSelected = { action ->
                                validateAndSave("Center", action) {
                                    zoneCenter = action
                                    viewModel.tapZoneCenter = action
                                }
                            },
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f))
                        )

                        ZoneBox(
                            label = "Right Zone",
                            currentAction = zoneRight,
                            actions = actions,
                            onActionSelected = { action ->
                                validateAndSave("Right", action) {
                                    zoneRight = action
                                    viewModel.tapZoneRight = action
                                }
                            },
                            modifier = Modifier
                                .weight(0.3f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f))
                        )
                    }

                    // Bottom zone
                    ZoneBox(
                        label = "Bottom Zone",
                        currentAction = zoneBottom,
                        actions = actions,
                        onActionSelected = { action ->
                            validateAndSave("Bottom", action) {
                                zoneBottom = action
                                viewModel.tapZoneBottom = action
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.2f)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                    )
                }
            }
        }
    }
}

@Composable
fun ZoneBox(
    label: String,
    currentAction: String,
    actions: List<String>,
    onActionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clickable { expanded = true }
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(4.dp)
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(currentAction, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action) },
                    onClick = {
                        onActionSelected(action)
                        expanded = false
                    }
                )
            }
        }
    }
}
