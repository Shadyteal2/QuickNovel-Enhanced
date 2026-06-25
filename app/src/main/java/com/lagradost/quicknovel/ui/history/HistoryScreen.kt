package com.lagradost.quicknovel.ui.history

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
    val resumeCard by viewModel.resumeCard.observeAsState()
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
                    
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(if (isLandscape) 2 else 1),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        resumeCard?.let { heroItem ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ResumeReadingCard(
                                    item = heroItem,
                                    onClick = { viewModel.open(heroItem) },
                                    onResumeClick = { viewModel.stream(heroItem) },
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }

                        val remainingCards = if (resumeCard != null) cards.drop(1) else cards

                        itemsIndexed(
                            items = remainingCards,
                            key = { _, item: ResultCached -> item.id }
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

private val compactCardShape = RoundedCornerShape(12.dp)
private val looseCardShape = RoundedCornerShape(20.dp)

private val compactPosterShape = RoundedCornerShape(
    topStart = 12.dp,
    bottomStart = 12.dp
)
private val loosePosterShape = RoundedCornerShape(
    topStart = 20.dp,
    bottomStart = 20.dp
)

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

    val cardShape = if (isCompact) compactCardShape else looseCardShape
    val posterShape = if (isCompact) compactPosterShape else loosePosterShape

    // Memoize Date Math & String Formats to prevent re-allocation during scrolls
    val timeMs = item.cachedTime
    val lastRead = item.lastChapterRead
    val total = item.totalChapters

    val formattedTime = remember(timeMs) { formatHistoryTimestamp(timeMs) }
    val compactProgressText = remember(lastRead, total, formattedTime) {
        "Ch. $lastRead/$total • $formattedTime"
    }
    val looseProgressText = remember(lastRead, total) {
        "Chapter $lastRead of $total"
    }
    val looseTimeText = remember(formattedTime) {
        "Read $formattedTime"
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
                .padding(vertical = if (isCompact) 4.dp else 10.dp),
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
            Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 4.dp))
            if (isCompact) {
                Text(
                    text = compactProgressText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            } else {
                Text(
                    text = looseProgressText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = looseTimeText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                )
            }
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

fun formatHistoryTimestamp(timeMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timeMs
    return when {
        diff < 60_000L -> "Just now"
        diff < 3600_000L -> "${diff / 60_000L}m ago"
        diff < 86400_000L -> "${diff / 3600_000L}h ago"
        else -> {
            val date = java.util.Date(timeMs)
            val dayFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            val today = dayFormat.format(java.util.Date(now))
            val targetDay = dayFormat.format(date)
            
            if (today == targetDay) {
                "Today at " + java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(date)
            } else {
                val yesterday = dayFormat.format(java.util.Date(now - 86400_000L))
                if (targetDay == yesterday) {
                    "Yesterday at " + java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(date)
                } else {
                    java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US).format(date)
                }
            }
        }
    }
}

@Composable
fun ResumeReadingCard(
    item: ResultCached,
    onClick: () -> Unit,
    onResumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(item.cachedTime) { formatHistoryTimestamp(item.cachedTime) }
    val progress = remember(item.lastChapterRead, item.totalChapters) {
        if (item.totalChapters > 0) {
            item.lastChapterRead.toFloat() / item.totalChapters.toFloat()
        } else {
            0f
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Poster
            Box(
                modifier = Modifier
                    .size(width = 82.dp, height = 130.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                AsyncImage(
                    model = rememberImageRequest(data = item),
                    contentDescription = item.name,
                    imageLoader = SingletonImageLoader.get(LocalContext.current),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Right: Content Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Continue Reading",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (!item.author.isNullOrBlank()) {
                    Text(
                        text = item.author ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chapter ${item.lastChapterRead} of ${item.totalChapters} • $formattedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    
                    FilledTonalButton(
                        onClick = onResumeClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

