package com.lagradost.quicknovel.ui.result

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.DOWNLOAD_SETTINGS
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_READ_AT
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.ui.download.CategoryItem
import com.lagradost.quicknovel.ui.download.DownloadViewModel
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberImageRequest
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.util.SettingsHelper.getRating

// ─── Dimensions ───────────────────────────────────────────────────────────────
private val POSTER_WIDTH  = 120.dp
private val POSTER_HEIGHT = 180.dp
private val POSTER_RADIUS = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultDetailDefaultScreen(
    viewModel: ResultViewModel,
    apiName: String,
    url: String,
    activity: Activity,
    chapterAdapter: ChapterAdapter,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShare: () -> Unit,
    onToggleSync: () -> Unit,
    onContinueReading: () -> Unit,
    onShowFilterSort: () -> Unit,
    onScrollToLatestChapter: () -> Unit,
    onScrollToLastRead: () -> Unit,
    onChapterRecyclerReady: (RecyclerView) -> Unit,
) {
    val loadResponse     by viewModel.loadResponse.observeAsState()
    val isSyncEnabled    by viewModel.isSyncEnabledDisplay.observeAsState(false)
    val isSelectionMode  by viewModel.isInSelectionMode.observeAsState(false)
    val selectedChapters by viewModel.selectedChapters.observeAsState(emptySet())
    val chapters         by viewModel.chapters.observeAsState(emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedTab) {
        viewModel.switchTab(selectedTab, if (selectedTab == 0) 0 else 3)
    }

    var bookmarkMenuExpanded   by remember { mutableStateOf(false) }
    var chaptersMenuExpanded   by remember { mutableStateOf(false) }
    val haptic  = LocalHapticFeedback.current
    val context = LocalContext.current

    val defaultBookmarkLabel = stringResource(R.string.bookmark)
    val duplicateBookmark    by viewModel.duplicateBookmarkState.observeAsState()
    val readState            by viewModel.readState.observeAsState()
    val currentId            by viewModel.id.observeAsState(-1)

    val bookmarkTitle = remember(readState, duplicateBookmark, currentId) {
        resolveBookmarkTitle(context, viewModel, currentId, readState)
    }
    val hasBookmark = bookmarkTitle != defaultBookmarkLabel

    // ── Root Box fills entire screen ──────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        when (val state = loadResponse) {

            // ── Loading ───────────────────────────────────────────────────────
            null, is Resource.Loading -> {
                DefaultShimmerSkeletonScreen()
            }

            // ── Error ─────────────────────────────────────────────────────────
            is Resource.Failure -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .systemBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.errorString, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onReload) { Text(stringResource(R.string.reload_error)) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenInBrowser) {
                        Text(stringResource(R.string.open_in_browser))
                    }
                }
            }

            // ── Success ───────────────────────────────────────────────────────
            is Resource.Success -> {
                val res          = state.value
                val ratingText   = res.rating?.let { context.getRating(it) }
                val chapterCount = (res as? StreamResponse)?.data?.size

                // ── Blurred full-screen ambient backdrop ──────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    AsyncImage(
                        model = rememberDefaultImageRequest(res.image, context),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(30.dp)
                            .graphicsLayer(alpha = 0.45f)
                    )
                    // Gradient scrim blending toward the theme background color
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.80f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                }

                // ── Foreground scrollable content ─────────────────────────────
                Box(modifier = Modifier.fillMaxSize()) {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        // ── Sticky top toolbar ────────────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenInBrowser()
                            }) {
                                Icon(Icons.Default.Public, null,
                                    tint = MaterialTheme.colorScheme.onBackground)
                            }
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onShare()
                            }) {
                                Icon(Icons.Default.Share, null,
                                    tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }

                        // ── Scrollable body ───────────────────────────────────
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .imePadding()
                        ) {
                            // Scrollable novel tab content
                            when (selectedTab) {
                                0 -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        // ── Header row: poster + info ─────────
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp, bottom = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            // Poster card
                                            Card(
                                                shape = RoundedCornerShape(POSTER_RADIUS),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                                                modifier = Modifier
                                                    .width(POSTER_WIDTH)
                                                    .height(POSTER_HEIGHT)
                                            ) {
                                                AsyncImage(
                                                    model = rememberDefaultImageRequest(res.image, context),
                                                    contentDescription = res.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }

                                            // Info column
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(POSTER_HEIGHT),
                                                verticalArrangement = Arrangement.Top
                                            ) {
                                                // Provider chip badge
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(50))
                                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = apiName,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))

                                                // Title (tap to copy)
                                                Text(
                                                    text = res.name,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 19.sp,
                                                    lineHeight = 25.sp,
                                                    maxLines = 4,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.clickable {
                                                        copyToClipboardDefault(context, "Novel Title", res.name)
                                                    }
                                                )
                                                Spacer(Modifier.height(4.dp))

                                                // Author (tap to copy)
                                                val authorVal  = res.author
                                                val authorText = authorVal ?: stringResource(R.string.no_author)
                                                Text(
                                                    text = authorText,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.clickable {
                                                        if (authorVal != null) {
                                                            copyToClipboardDefault(context, "Author", authorVal)
                                                        }
                                                    }
                                                )

                                                // Latest chapter count
                                                if (chapterCount != null) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = "Latest Chapter: $chapterCount",
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }

                                        // ── Stacked CTA buttons ───────────────
                                        // Continue reading pill
                                        Button(
                                            onClick = onContinueReading,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                                                contentColor   = MaterialTheme.colorScheme.background
                                            ),
                                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null,
                                                modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = defaultContinueReadingLabel(res, chapters.orEmpty(), viewModel),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))

                                        // Bookmark pill (outlined)
                                        Box {
                                            OutlinedButton(
                                                onClick = { bookmarkMenuExpanded = true },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(50.dp),
                                                shape = RoundedCornerShape(14.dp),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.5.dp,
                                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                                ),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = if (hasBookmark)
                                                        MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Icon(
                                                    if (hasBookmark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                    null, modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = bookmarkTitle,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = bookmarkMenuExpanded,
                                                onDismissRequest = { bookmarkMenuExpanded = false }
                                            ) {
                                                val categories = remember { loadDefaultBookmarkCategories(context) }
                                                val currentStateId = if (readState == null || readState == ReadType.NONE) -1 else readState!!.prefValue
                                                categories.forEach { (id, label) ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                if (id == currentStateId) {
                                                                    Text("✓ ",
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        fontWeight = FontWeight.Bold)
                                                                }
                                                                Text(label)
                                                            }
                                                        },
                                                        onClick = {
                                                            viewModel.bookmark(id)
                                                            bookmarkMenuExpanded = false
                                                        }
                                                    )
                                                }
                                                if (currentStateId != -1) {
                                                    DropdownMenuItem(
                                                        text = { Text("Unbookmark") },
                                                        onClick = {
                                                            viewModel.bookmark(-1)
                                                            bookmarkMenuExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))

                                        // ── Pill tab row ──────────────────────
                                        DefaultTabRow(
                                            selectedTab = selectedTab,
                                            tabs = listOf(
                                                stringResource(R.string.novel),
                                                stringResource(R.string.read_action_chapters)
                                            ),
                                            onSelect = { selectedTab = it },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(12.dp))

                                        // ── Novel tab content (stats, synopsis, tags, notes) ──
                                        NovelTabScreen(viewModel, res, activity)

                                        Spacer(Modifier.height(32.dp))
                                    }
                                }

                                1 -> {
                                    // ── Chapters tab ──────────────────────────
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Tab row pinned at top
                                        DefaultTabRow(
                                            selectedTab = selectedTab,
                                            tabs = listOf(
                                                stringResource(R.string.novel),
                                                stringResource(R.string.read_action_chapters)
                                            ),
                                            onSelect = { selectedTab = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        // Chapter sort/filter toolbar
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box {
                                                TextButton(onClick = { chaptersMenuExpanded = true }) {
                                                    Icon(Icons.Default.MoreVert, null,
                                                        modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.mainpage_sort_by_button_text),
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = chaptersMenuExpanded,
                                                    onDismissRequest = { chaptersMenuExpanded = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Filter & Sort") },
                                                        onClick = {
                                                            chaptersMenuExpanded = false
                                                            onShowFilterSort()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Go to Latest Chapter") },
                                                        onClick = {
                                                            chaptersMenuExpanded = false
                                                            onScrollToLatestChapter()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Go to Last Read") },
                                                        onClick = {
                                                            chaptersMenuExpanded = false
                                                            onScrollToLastRead()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        // RecyclerView — full height, no nested scroll
                                        AndroidView(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            factory = { ctx ->
                                                RecyclerView(ctx).apply {
                                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                                    )
                                                    layoutManager = LinearLayoutManager(ctx)
                                                    adapter = chapterAdapter
                                                    setHasFixedSize(true)
                                                    onChapterRecyclerReady(this)
                                                }
                                            },
                                            update = { rv ->
                                                val list = chapters
                                                if (list != null && list.isNotEmpty()) {
                                                    if (list.size > 300) {
                                                        chapterAdapter.submitIncomparableList(list)
                                                    } else {
                                                        chapterAdapter.submitList(list)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Floating bottom selection bar ─────────────────────────
                    if (isSelectionMode == true) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            DefaultSelectionBar(
                                selectedCount = selectedChapters.size,
                                onClose      = { viewModel.setSelectionMode(false) },
                                onSelectAll  = { viewModel.selectAll() },
                                onBookmark   = { viewModel.executeBatchBookmark(true) },
                                onUnbookmark = { viewModel.executeBatchBookmark(false) },
                                onMarkRead   = { viewModel.executeBatchMarkRead(true) },
                                onMarkUnread = { viewModel.executeBatchMarkRead(false) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Pill tab row (identical style to the screenshot) ────────────────────────
@Composable
private fun DefaultTabRow(
    selectedTab: Int,
    tabs: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(4.dp),
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
    }
}

// ─── Chapter selection mode bottom bar ───────────────────────────────────────
@Composable
private fun DefaultSelectionBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onBookmark: () -> Unit,
    onUnbookmark: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
) {
    Surface(
        tonalElevation  = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                Text(
                    if (selectedCount == 0) stringResource(R.string.no_data)
                    else "$selectedCount Selected",
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onSelectAll) { Text("All") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onBookmark)   { Text("Bookmark") }
                TextButton(onClick = onUnbookmark) { Text("Unbookmark") }
                TextButton(onClick = onMarkRead)   { Text("Read") }
                TextButton(onClick = onMarkUnread) { Text("Unread") }
            }
        }
    }
}

// ─── Shimmer skeleton for Default layout ─────────────────────────────────────
@Composable
private fun DefaultShimmerSkeletonScreen() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val anim = transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1000f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start  = Offset.Zero,
        end    = Offset(anim.value, anim.value)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Toolbar placeholder
        Spacer(Modifier.height(56.dp))
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(POSTER_WIDTH)
                    .height(POSTER_HEIGHT)
                    .clip(RoundedCornerShape(POSTER_RADIUS))
                    .background(brush)
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(70.dp, 20.dp).clip(RoundedCornerShape(10.dp)).background(brush))
                Box(Modifier.size(160.dp, 22.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                Box(Modifier.size(110.dp, 16.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                Box(Modifier.size(90.dp, 14.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(brush))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(brush))
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(50)).background(brush))
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).background(brush))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)).background(brush))
    }
}

// ─── High-quality image request ───────────────────────────────────────────────
@Composable
private fun rememberDefaultImageRequest(data: Any?, context: Context): ImageRequest {
    val base = rememberImageRequest(data)
    return remember(data) {
        base.newBuilder(context)
            .allowHardware(true)
            .size(coil3.size.Size.ORIGINAL)
            .crossfade(300)
            .build()
    }
}

// ─── Clipboard helper ─────────────────────────────────────────────────────────
private fun copyToClipboardDefault(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    com.lagradost.quicknovel.CommonActivity.showToast("$label copied")
}

// ─── Continue reading label ───────────────────────────────────────────────────
private fun defaultContinueReadingLabel(
    res: LoadResponse,
    chapters: List<ChapterData>,
    viewModel: ResultViewModel,
): String {
    val stream = res as? StreamResponse ?: return "Start Reading"
    if (chapters.isEmpty()) return "Start Reading"
    val name = stream.name
    val lastReadIndex = chapters.indexOfLast { ch ->
        val idx = viewModel.chapterIndex(ch) ?: -1
        if (idx == -1) return@indexOfLast false
        BaseApplication.getKey<Long>(EPUB_CURRENT_POSITION_READ_AT, "$name/$idx") != null
    }
    return if (lastReadIndex != -1) "Continue Ch. ${lastReadIndex + 1}" else "Start Reading"
}

// ─── Bookmark categories loader ───────────────────────────────────────────────
private fun loadDefaultBookmarkCategories(context: Context): List<Pair<Int, String>> {
    val json   = BaseApplication.getKey<String>(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", "[]") ?: "[]"
    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    val customCats = try {
        mapper.readValue(
            json,
            object : com.fasterxml.jackson.core.type.TypeReference<List<CategoryItem>>() {}
        )
    } catch (_: Throwable) { emptyList() }
    val orderJson = BaseApplication.getKey<String>(DOWNLOAD_SETTINGS, "CATEGORIES_ORDER", "[]") ?: "[]"
    val order = try {
        mapper.readValue(
            orderJson,
            object : com.fasterxml.jackson.core.type.TypeReference<List<Int>>() {}
        )
    } catch (_: Throwable) { emptyList() }
    val allCats = DownloadViewModel.systemCategories + customCats
    val sorted  = if (order.isNotEmpty()) {
        allCats.sortedBy { order.indexOf(it.id).takeIf { idx -> idx >= 0 } ?: Int.MAX_VALUE }
    } else allCats
    return sorted.map { cat ->
        cat.id to (cat.stringRes?.let { context.getString(it) } ?: cat.name)
    }
}

private fun resolveBookmarkTitle(
    context: Context,
    viewModel: ResultViewModel,
    currentId: Int,
    readState: ReadType?,
): String {
    val currentStateId = if (readState == null || readState == ReadType.NONE) -1 else readState.prefValue
    if (currentStateId != -1) {
        DownloadViewModel.systemCategories
            .find { it.id == currentStateId }?.stringRes
            ?.let { return context.getString(it) }
        val json = BaseApplication.getKey<String>(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", "[]") ?: "[]"
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val customCats = try {
            mapper.readValue(
                json,
                object : com.fasterxml.jackson.core.type.TypeReference<List<CategoryItem>>() {}
            )
        } catch (_: Throwable) { emptyList() }
        customCats.find { it.id == currentStateId }?.name?.let { return it }
    }
    viewModel.duplicateBookmarkState.value?.let { duplicateState ->
        val systemCat = DownloadViewModel.systemCategories.find { it.id == duplicateState }
        if (systemCat != null) {
            return "In Library (${context.getString(systemCat.stringRes ?: R.string.bookmark)})"
        } else {
            val json = BaseApplication.getKey<String>(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", "[]") ?: "[]"
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val customCats = try {
                mapper.readValue(
                    json,
                    object : com.fasterxml.jackson.core.type.TypeReference<List<CategoryItem>>() {}
                )
            } catch (_: Throwable) { emptyList() }
            val customCat = customCats.find { it.id == duplicateState }
            if (customCat != null) {
                return "In Library (${customCat.name})"
            }
        }
    }
    return context.getString(R.string.bookmark)
}
