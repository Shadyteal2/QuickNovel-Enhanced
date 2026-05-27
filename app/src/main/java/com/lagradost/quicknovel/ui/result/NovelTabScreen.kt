package com.lagradost.quicknovel.ui.result

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.theme.glassCard

import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NovelTabScreen(
    viewModel: ResultViewModel,
    res: LoadResponse,
    activity: Activity
) {
    // Observers
    val readState by viewModel.readState.observeAsState(ReadType.NONE)
    val userNote by viewModel.userNote.observeAsState("")
    val downloadState by viewModel.downloadState.observeAsState()
    val isSyncEnabled by viewModel.isSyncEnabledDisplay.observeAsState(false)
    val chapters by viewModel.chapters.observeAsState(emptyList())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(shape = RoundedCornerShape(28.dp))
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                icon = R.drawable.ic_baseline_history_24,
                text = res.views?.toString() ?: stringResource(id = R.string.no_data),
                label = "Views",
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
            StatItem(
                icon = R.drawable.ic_baseline_star_24,
                text = if (res.rating != null) String.format("%.1f", res.rating!! / 200f) else stringResource(id = R.string.no_data),
                label = "Rating",
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
            StatItem(
                icon = R.drawable.ic_baseline_list_24,
                text = "${chapters?.size ?: 0}",
                label = "Chapters",
                modifier = Modifier.weight(1f)
            )
        }

        // Synopsis Card
        var isSynopsisExpanded by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(shape = RoundedCornerShape(28.dp))
                .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.synopsis),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = res.synopsis ?: "No Synopsis Available",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            if (!isSynopsisExpanded && !res.synopsis.isNullOrEmpty()) {
                Text(
                    text = "Read More",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }

        // Tags
        if (!res.tags.isNullOrEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                res.tags!!.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .glassCard(
                                shape = RoundedCornerShape(20.dp),
                                backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                strokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tag,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Interaction Card (Notes & Downloads)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(shape = RoundedCornerShape(28.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val view = androidx.compose.ui.platform.LocalView.current
            val coroutineScope = rememberCoroutineScope()
            val bringIntoViewRequester = remember { BringIntoViewRequester() }
            var textFieldY by remember { mutableFloatStateOf(0f) }

            // Notes Field
            OutlinedTextField(
                value = userNote ?: "",
                onValueChange = { viewModel.updateNote(it) },
                label = { Text(if (readState == ReadType.DROPPED) stringResource(R.string.dropped_reason) else stringResource(R.string.notes)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .onGloballyPositioned { coordinates ->
                        textFieldY = coordinates.positionInRoot().y
                    }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                bringIntoViewRequester.bringIntoView()
                            }
                            view.post {
                                var parent = view.parent
                                var accumY = textFieldY.toInt()
                                while (parent != null) {
                                    if (parent is android.view.View) {
                                        if (parent is androidx.core.widget.NestedScrollView) {
                                            val scrollY = (accumY - 100).coerceAtLeast(0)
                                            parent.smoothScrollTo(0, scrollY)
                                        } else if (parent is android.widget.ScrollView) {
                                            val scrollY = (accumY - 100).coerceAtLeast(0)
                                            parent.smoothScrollTo(0, scrollY)
                                        }
                                        val nextParent = (parent as? android.view.ViewParent)?.parent
                                        if (nextParent is android.view.View) {
                                            accumY += parent.top
                                        }
                                    }
                                    parent = (parent as? android.view.ViewParent)?.parent
                                }
                            }
                        }
                    },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = if (readState == ReadType.DROPPED) Color.Red else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    unfocusedBorderColor = if (readState == ReadType.DROPPED) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    focusedLabelColor = if (readState == ReadType.DROPPED) Color.Red else MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                maxLines = 5
            )

            // Sync Toggle (if applicable)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleSyncEnabled() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_autorenew_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sync Progress",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Auto-sync reading progress with library",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isSyncEnabled,
                    onCheckedChange = { viewModel.toggleSyncEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            // Download Status
            if (downloadState != null) {
                val state = downloadState!!
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download Status",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${state.progress} / ${state.total} Chapters",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val progressFraction = if (state.total > 0) state.progress.toFloat() / state.total.toFloat() else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (downloadState?.progress ?: 0 > 0) {
                    OutlinedButton(
                        onClick = { viewModel.readEpub() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_history_24),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Read EPUB",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val canDownload = (downloadState?.progress ?: 0) < (downloadState?.total ?: 0) || downloadState == null
                val buttonText = when (downloadState?.state) {
                    DownloadState.IsDone -> stringResource(R.string.manage)
                    DownloadState.IsDownloading -> stringResource(R.string.pause)
                    DownloadState.IsPaused -> stringResource(R.string.resume)
                    DownloadState.IsFailed -> stringResource(R.string.re_downloaded)
                    DownloadState.IsStopped -> stringResource(R.string.resume)
                    DownloadState.IsPending -> stringResource(R.string.loading)
                    else -> if (canDownload) stringResource(R.string.download) else stringResource(R.string.manage)
                }

                Button(
                    onClick = { viewModel.downloadOrPause() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Text(
                        text = buttonText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun StatItem(icon: Int, text: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
