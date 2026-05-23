package com.lagradost.quicknovel.ui.history

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.ui.theme.rememberImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val cardsState = viewModel.cards.observeAsState(initial = arrayListOf())
    val cards = cardsState.value
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val context = LocalContext.current
    val settings = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val imageUri = remember(settings) { settings.getString(context.getString(R.string.background_image_key), null) }
    val hasBackground = !imageUri.isNullOrBlank()

    // Read the active density preference (history_compact_view)
    val isCompactState = remember { mutableStateOf(settings.getBoolean("history_compact_view", false)) }
    var isCompact by isCompactState

    QuickNovelTheme {
        val containerColor = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background
        val onDeleteAllClick = remember(viewModel) { { viewModel.deleteAllAlert() } }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.history),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    actions = {
                        // Premium layout density toggle button
                        IconButton(
                            onClick = {
                                isCompact = !isCompact
                                settings.edit().putBoolean("history_compact_view", isCompact).apply()
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = if (isCompact) R.drawable.density_small_24px else R.drawable.density_medium_24px),
                                contentDescription = stringResource(if (isCompact) R.string.history_density_compact else R.string.history_density_comfortable),
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (cards.isNotEmpty()) {
                            IconButton(
                                onClick = onDeleteAllClick
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete All History",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            },
            containerColor = containerColor,
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (cards.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .padding(24.dp)
                                .glassCard(RoundedCornerShape(24.dp))
                                .padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .glassCard(CircleShape, strokeWidth = 2.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = R.drawable.monke_eating),
                                    contentDescription = "Nothing Read",
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = stringResource(R.string.nothing_read_recently),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    
                    // Prefetch cover images of the upcoming 12 novels as the user scrolls
                    LaunchedEffect(gridState.firstVisibleItemIndex, cards) {
                        val totalItems = cards.size
                        val startIndex = (gridState.firstVisibleItemIndex + 10).coerceAtMost(totalItems)
                        val endIndex = (startIndex + 12).coerceAtMost(totalItems)
                        for (i in startIndex until endIndex) {
                            val card = cards.getOrNull(i) ?: continue
                            val req = com.lagradost.quicknovel.ui.theme.buildImageRequest(context, card)
                            coil3.SingletonImageLoader.get(context).enqueue(req)
                        }
                    }

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(if (isLandscape) 2 else 1),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = cards,
                            key = { _, item -> item.id }
                        ) { _, item ->
                            val currentOnClick = remember(item, viewModel) { { viewModel.open(item) } }
                            val currentOnLongClick = remember(item, viewModel) { { viewModel.showMetadata(item) } }
                            val onStreamClick = remember(item, viewModel) { { viewModel.stream(item) } }
                            val onDeleteClick = remember(item, viewModel) { { viewModel.deleteAlert(item) } }

                            HistoryItemCard(
                                item = item,
                                isCompact = isCompact,
                                onClick = currentOnClick,
                                onLongClick = currentOnLongClick,
                                onStreamClick = onStreamClick,
                                onDeleteClick = onDeleteClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    item: ResultCached,
    isCompact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStreamClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val currentOnLongClickWithHaptic = remember(onLongClick, haptic) {
        {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onLongClick()
        }
    }

    val cardShape = remember(isCompact) { RoundedCornerShape(if (isCompact) 12.dp else 20.dp) }
    val posterShape = remember(isCompact) {
        RoundedCornerShape(
            topStart = if (isCompact) 12.dp else 20.dp,
            bottomStart = if (isCompact) 12.dp else 20.dp
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompact) 80.dp else 115.dp)
            .let { modifier ->
                if (isCompact) {
                    modifier
                        .clip(cardShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                } else {
                    modifier.glassCard(cardShape)
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = currentOnLongClickWithHaptic
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Poster Image
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (isCompact) 56.dp else 82.dp)
                .clip(posterShape)
        ) {
            AsyncImage(
                model = rememberImageRequest(data = item),
                contentDescription = item.name,
                imageLoader = SingletonImageLoader.get(LocalContext.current),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(if (isCompact) 10.dp else 12.dp))

        // Metadata Column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = if (isCompact) 6.dp else 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.name,
                style = if (isCompact) {
                    MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 19.sp
                    )
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 6.dp))
            Text(
                text = "${item.totalChapters} ${stringResource(R.string.read_action_chapters)}",
                style = if (isCompact) {
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    )
                },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }

        // Controls Column
        Row(
            modifier = Modifier
                .padding(end = if (isCompact) 6.dp else 12.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
        ) {
            // Play Button
            val playInteractionSource = remember { MutableInteractionSource() }
            val playPressed by playInteractionSource.collectIsPressedAsState()
            val playScale by animateFloatAsState(if (playPressed) 0.88f else 1.0f, label = "play")

            IconButton(
                onClick = onStreamClick,
                interactionSource = playInteractionSource,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
                    }
                    .size(if (isCompact) 34.dp else 42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.stream_read),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isCompact) 22.dp else 28.dp)
                )
            }

            // Delete Button
            val deleteInteractionSource = remember { MutableInteractionSource() }
            val deletePressed by deleteInteractionSource.collectIsPressedAsState()
            val deleteScale by animateFloatAsState(if (deletePressed) 0.88f else 1.0f, label = "del")

            IconButton(
                onClick = onDeleteClick,
                interactionSource = deleteInteractionSource,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = deleteScale
                        scaleY = deleteScale
                    }
                    .size(if (isCompact) 34.dp else 42.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    modifier = Modifier.size(if (isCompact) 18.dp else 24.dp)
                )
            }
        }
    }
}
