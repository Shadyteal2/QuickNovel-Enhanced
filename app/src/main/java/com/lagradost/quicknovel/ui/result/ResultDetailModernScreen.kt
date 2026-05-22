package com.lagradost.quicknovel.ui.result

import android.app.Activity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.lagradost.quicknovel.ui.theme.rememberImageRequest
import com.lagradost.quicknovel.util.SettingsHelper.getRating

// ─── Hero dimensions ──────────────────────────────────────────────────────────
private val HERO_HEIGHT      = 420.dp
private val CARD_OVERLAP     = 36.dp   // how many dp the content card peeks above image bottom
private val HERO_CORNER      = 36.dp   // bottom-corner radius on the hero image

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultDetailModernScreen(
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
    val loadResponse    by viewModel.loadResponse.observeAsState()
    val isSyncEnabled   by viewModel.isSyncEnabledDisplay.observeAsState(false)
    val isSelectionMode by viewModel.isInSelectionMode.observeAsState(false)
    val selectedChapters by viewModel.selectedChapters.observeAsState(emptySet())
    val readState       by viewModel.readState.observeAsState()
    val duplicateBookmark by viewModel.duplicateBookmarkState.observeAsState()
    val chapters        by viewModel.chapters.observeAsState(emptyList())

    var selectedTab           by remember { mutableIntStateOf(0) }
    var bookmarkMenuExpanded  by remember { mutableStateOf(false) }
    var chaptersMenuExpanded  by remember { mutableStateOf(false) }
    val haptic   = LocalHapticFeedback.current
    val context  = LocalContext.current

    val defaultBookmarkLabel = stringResource(R.string.bookmark)
    val bookmarkTitle = remember(readState, duplicateBookmark) {
        resolveBookmarkTitle(context, viewModel)
    }
    val hasBookmark = bookmarkTitle != defaultBookmarkLabel

    // ── Root: fills the entire screen, transparent so MainActivity background shows ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        when (val state = loadResponse) {

            // ── Loading ────────────────────────────────────────────────────────
            null, is Resource.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // ── Error ──────────────────────────────────────────────────────────
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

            // ── Success ────────────────────────────────────────────────────────
            is Resource.Success -> {
                val res = state.value
                val ratingText = res.rating?.let { context.getRating(it) }
                val chapterCount = (res as? StreamResponse)?.data?.size

                // ── Scrollable body (hero + content card) ───────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {

                    // ── Hero poster with rounded bottom ─────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HERO_HEIGHT)
                    ) {
                        // Full-bleed cover
                        AsyncImage(
                            model = rememberImageRequest(res.image),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(
                                    RoundedCornerShape(
                                        bottomStart = HERO_CORNER,
                                        bottomEnd   = HERO_CORNER
                                    )
                                )
                        )

                        // Dark gradient → title legibility at bottom of hero
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(
                                    RoundedCornerShape(
                                        bottomStart = HERO_CORNER,
                                        bottomEnd   = HERO_CORNER
                                    )
                                )
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.45f to Color.Transparent,
                                        1.0f to Color(0xCC000000)
                                    )
                                )
                        )

                        // ── Floating back pill (top-left) ───────────────────
                        Box(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(start = 16.dp, top = 12.dp)
                                .align(Alignment.TopStart)
                                .size(42.dp)
                                .shadow(8.dp, CircleShape)
                                .background(Color(0xBB000000), CircleShape)
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // ── Floating action pills (top-right) ────────────────
                        Row(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(end = 16.dp, top = 12.dp)
                                .align(Alignment.TopEnd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Share
                            HeroPill(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onShare()
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            // Open in browser
                            HeroPill(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenInBrowser()
                                }
                            ) {
                                Icon(Icons.Default.Public, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            // Bell / notifications
                            HeroPill(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleSync()
                                }
                            ) {
                                Icon(
                                    if (isSyncEnabled) Icons.Default.Notifications
                                    else Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = if (isSyncEnabled)
                                        MaterialTheme.colorScheme.primary
                                    else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // ── Novel title + author overlay at bottom of hero ───
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(0.70f)           // leave right side for thumb strip
                                .padding(start = 20.dp, bottom = CARD_OVERLAP + 14.dp)
                        ) {
                            Text(
                                text = res.name,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                lineHeight = 28.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = res.author ?: stringResource(R.string.no_author),
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // ── Thumbnail strip on the right ─────────────────────
                        // Shows cover again as first tile + extra decorative tiles
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            repeat(3) { i ->
                                AsyncImage(
                                    model = rememberImageRequest(res.image),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 70.dp, height = 76.dp)
                                        .shadow(6.dp, RoundedCornerShape(18.dp))
                                        .clip(RoundedCornerShape(18.dp))
                                        .graphicsLayer {
                                            alpha = 1f - i * 0.25f
                                        }
                                )
                            }
                        }
                    } // end hero Box

                    // ── Content card that overlaps the bottom of the hero ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = -CARD_OVERLAP)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            )
                            .padding(top = 8.dp)
                    ) {

                        // ── Provider + rating pill row ──────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Provider chip
                            PillChip(
                                icon = {
                                    Icon(Icons.Default.Public, null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                },
                                text = apiName
                            )
                            // Rating chip
                            if (!ratingText.isNullOrBlank()) {
                                PillChip(
                                    icon = {
                                        Icon(Icons.Default.Star, null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color(0xFFFFC107))
                                    },
                                    text = ratingText
                                )
                            }
                            // Chapter count chip
                            if (chapterCount != null) {
                                PillChip(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_baseline_list_24),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    text = "$chapterCount ch"
                                )
                            }
                        }

                        // ── Pill tab row (Novel / Chapters) ─────────────────
                        PremiumTabRow(
                            selectedTab = selectedTab,
                            tabs = listOf(
                                stringResource(R.string.novel),
                                stringResource(R.string.read_action_chapters)
                            ),
                            onSelect = { selectedTab = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        // ── Tab content ─────────────────────────────────────
                        when (selectedTab) {
                            0 -> {
                                // Novel info tab
                                NovelTabScreen(viewModel, res, activity)
                            }
                            1 -> {
                                // Chapters tab
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Chapters toolbar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box {
                                            TextButton(
                                                onClick = { chaptersMenuExpanded = true }
                                            ) {
                                                Icon(Icons.Default.MoreVert, null,
                                                    modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.mainpage_sort_by_button_text))
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
                                    // Chapter list — fixed height so it scrolls inside main scroll
                                    AndroidView(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(600.dp),
                                        factory = { ctx ->
                                            RecyclerView(ctx).apply {
                                                layoutManager = LinearLayoutManager(ctx)
                                                adapter = chapterAdapter
                                                setHasFixedSize(true)
                                                isNestedScrollingEnabled = false
                                                onChapterRecyclerReady(this)
                                            }
                                        },
                                        update = {
                                            val items = chapters.orEmpty()
                                            if (items.size > 300) {
                                                chapterAdapter.submitIncomparableList(items)
                                            } else {
                                                chapterAdapter.submitList(items)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // ── Spacer for bottom action bar ─────────────────────
                        Spacer(Modifier.height(120.dp))
                    }
                } // end scrollable column

                // ── Floating bottom action bar ────────────────────────────────
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    when {
                        isSelectionMode == true -> {
                            ModernSelectionBar(
                                selectedCount = selectedChapters.size,
                                onClose    = { viewModel.setSelectionMode(false) },
                                onSelectAll= { viewModel.selectAll() },
                                onBookmark = { viewModel.executeBatchBookmark(true) },
                                onUnbookmark = { viewModel.executeBatchBookmark(false) },
                                onMarkRead   = { viewModel.executeBatchMarkRead(true) },
                                onMarkUnread = { viewModel.executeBatchMarkRead(false) },
                            )
                        }
                        apiName != "OceanOfPDF" -> {
                            PremiumActionBar(
                                continueLabel  = continueReadingLabel(res, chapters.orEmpty(), viewModel),
                                bookmarkLabel  = bookmarkTitle,
                                hasBookmark    = hasBookmark,
                                bookmarkMenuExpanded  = bookmarkMenuExpanded,
                                onBookmarkMenuChange  = { bookmarkMenuExpanded = it },
                                onContinue     = onContinueReading,
                                onBookmarkSelect = { id ->
                                    viewModel.bookmark(id)
                                    bookmarkMenuExpanded = false
                                },
                                viewModel = viewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Small floating pill button used over the hero ────────────────────────────
@Composable
private fun HeroPill(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(6.dp, CircleShape)
            .background(Color(0xBB000000), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ─── Rounded pill chip for stats / metadata ───────────────────────────────────
@Composable
private fun PillChip(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Rounded-pill tab row ─────────────────────────────────────────────────────
@Composable
private fun PremiumTabRow(
    selectedTab: Int,
    tabs: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
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
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Bottom CTA bar with Continue + Bookmark pills ───────────────────────────
@Composable
private fun PremiumActionBar(
    continueLabel: String,
    bookmarkLabel: String,
    hasBookmark: Boolean,
    bookmarkMenuExpanded: Boolean,
    onBookmarkMenuChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onBookmarkSelect: (Int) -> Unit,
    viewModel: ResultViewModel,
) {
    val context = LocalContext.current
    val categories = remember { loadBookmarkCategories(context) }
    val currentStateId = remember(viewModel.loadId) {
        BaseApplication.getKey<Int>(RESULT_BOOKMARK_STATE, viewModel.loadId.toString()) ?: -1
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Bookmark pill (square-ish, on the left) ────────────────────
            Box {
                OutlinedButton(
                    onClick = { onBookmarkMenuChange(true) },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (hasBookmark) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(
                        if (hasBookmark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = bookmarkLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DropdownMenu(
                    expanded = bookmarkMenuExpanded,
                    onDismissRequest = { onBookmarkMenuChange(false) }
                ) {
                    categories.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (id == currentStateId) {
                                        Text("✓ ", color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold)
                                    }
                                    Text(label)
                                }
                            },
                            onClick = { onBookmarkSelect(id) }
                        )
                    }
                    if (currentStateId != -1) {
                        DropdownMenuItem(
                            text = { Text("Unbookmark") },
                            onClick = { onBookmarkSelect(-1) }
                        )
                    }
                }
            }

            // ── Continue reading pill (expands to fill remaining space) ────
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = continueLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── Selection mode bottom bar ────────────────────────────────────────────────
@Composable
private fun ModernSelectionBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onBookmark: () -> Unit,
    onUnbookmark: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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

// ─── Helpers (kept identical to original so no backend is changed) ─────────────
private fun continueReadingLabel(
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

private fun resolveBookmarkTitle(
    context: android.content.Context,
    viewModel: ResultViewModel,
): String {
    val currentStateId = BaseApplication.getKey<Int>(RESULT_BOOKMARK_STATE, viewModel.loadId.toString()) ?: -1
    if (currentStateId != -1) {
        DownloadViewModel.systemCategories.find { it.id == currentStateId }?.stringRes?.let {
            return context.getString(it)
        }
        val json    = BaseApplication.getKey<String>(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", "[]") ?: "[]"
        val mapper  = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val customCats = try {
            mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<List<CategoryItem>>() {})
        } catch (_: Throwable) { emptyList() }
        customCats.find { it.id == currentStateId }?.name?.let { return it }
    }
    viewModel.duplicateBookmarkState.value?.let { duplicateState ->
        val systemCat = DownloadViewModel.systemCategories.find { it.id == duplicateState }
        return if (systemCat != null) {
            "In Library (${context.getString(systemCat.stringRes ?: R.string.bookmark)})"
        } else {
            "In Library"
        }
    }
    return context.getString(R.string.bookmark)
}

private fun loadBookmarkCategories(
    context: android.content.Context,
): List<Pair<Int, String>> {
    val json   = BaseApplication.getKey<String>(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", "[]") ?: "[]"
    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    val customCats = try {
        mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<List<CategoryItem>>() {})
    } catch (_: Throwable) { emptyList() }
    val orderJson = BaseApplication.getKey<String>(DOWNLOAD_SETTINGS, "CATEGORIES_ORDER", "[]") ?: "[]"
    val order  = try {
        mapper.readValue(orderJson, object : com.fasterxml.jackson.core.type.TypeReference<List<Int>>() {})
    } catch (_: Throwable) { emptyList() }
    val allCats = DownloadViewModel.systemCategories + customCats
    val sorted  = if (order.isNotEmpty()) {
        allCats.sortedBy { order.indexOf(it.id).takeIf { idx -> idx >= 0 } ?: Int.MAX_VALUE }
    } else {
        allCats
    }
    return sorted.map { cat -> cat.id to (cat.stringRes?.let { context.getString(it) } ?: cat.name) }
}
