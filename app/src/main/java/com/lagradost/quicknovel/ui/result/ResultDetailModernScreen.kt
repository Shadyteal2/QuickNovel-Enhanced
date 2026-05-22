package com.lagradost.quicknovel.ui.result

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.lagradost.quicknovel.ui.theme.rememberImageRequest
import com.lagradost.quicknovel.util.SettingsHelper.getRating

// ─── Hero dimensions ──────────────────────────────────────────────────────────
private val HERO_HEIGHT  = 380.dp
private val CARD_OVERLAP = 0.dp
private val HERO_CORNER  = 32.dp

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
    val loadResponse     by viewModel.loadResponse.observeAsState()
    val isSyncEnabled    by viewModel.isSyncEnabledDisplay.observeAsState(false)
    val isSelectionMode  by viewModel.isInSelectionMode.observeAsState(false)
    val selectedChapters by viewModel.selectedChapters.observeAsState(emptySet())
    val readState        by viewModel.readState.observeAsState()
    val duplicateBookmark by viewModel.duplicateBookmarkState.observeAsState()
    val chapters         by viewModel.chapters.observeAsState(emptyList())

    var selectedTab          by remember { mutableIntStateOf(0) }
    var bookmarkMenuExpanded by remember { mutableStateOf(false) }
    var chaptersMenuExpanded by remember { mutableStateOf(false) }
    val haptic  = LocalHapticFeedback.current
    val context = LocalContext.current

    val defaultBookmarkLabel = stringResource(R.string.bookmark)
    val bookmarkTitle = remember(readState, duplicateBookmark) {
        resolveBookmarkTitle(context, viewModel)
    }
    val hasBookmark = bookmarkTitle != defaultBookmarkLabel

    // ── Root box fills entire screen ──────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {

        when (val state = loadResponse) {

            // ── Loading ───────────────────────────────────────────────────────
            null, is Resource.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
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
                val res        = state.value
                val ratingText = res.rating?.let { context.getRating(it) }
                val chapterCount = (res as? StreamResponse)?.data?.size

                // ── Fixed-layout: hero on top, content fills remaining ─────────
                // This avoids any nested-scroll issues — chapters RecyclerView gets
                // its own full height and scrolls independently.
                Column(modifier = Modifier.fillMaxSize()) {

                    // ── Hero (fixed height) ───────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HERO_HEIGHT)
                    ) {
                        // Full-bleed cover – FillWidth preserves aspect ratio,
                        // no artificial upscale blurring
                        AsyncImage(
                            model = rememberHighQualityRequest(res.image, context),
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

                        // Gradient scrim for text readability
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
                                        0.0f  to Color.Transparent,
                                        0.40f to Color.Transparent,
                                        1.0f  to Color(0xD5000000)
                                    )
                                )
                        )

                        // ── Back pill (top-left) ──────────────────────────────
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

                        // ── Action pills (top-right): Share, Browser, Bell ────
                        Row(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(end = 16.dp, top = 12.dp)
                                .align(Alignment.TopEnd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HeroPill(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onShare()
                            }) {
                                Icon(Icons.Default.Share, null, tint = Color.White,
                                    modifier = Modifier.size(18.dp))
                            }
                            HeroPill(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenInBrowser()
                            }) {
                                Icon(Icons.Default.Public, null, tint = Color.White,
                                    modifier = Modifier.size(18.dp))
                            }
                            HeroPill(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleSync()
                            }) {
                                Icon(
                                    if (isSyncEnabled) Icons.Default.Notifications
                                    else Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = if (isSyncEnabled)
                                        MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // ── Title + author overlay (bottom of hero) ───────────
                        // Tap title → copy, tap author → copy
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                        ) {
                            Text(
                                text = res.name,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                lineHeight = 28.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable {
                                    copyToClipboard(context, "Novel Title", res.name)
                                }
                            )
                            Spacer(Modifier.height(4.dp))
                            val authorVal = res.author
                            val authorText = authorVal ?: stringResource(R.string.no_author)
                            Text(
                                text = authorText,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable {
                                    if (authorVal != null) {
                                        copyToClipboard(context, "Author", authorVal)
                                    }
                                }
                            )
                        }
                    } // end Hero Box

                    // ── Content card — fills ALL remaining space ──────────────
                    // offset upward by CARD_OVERLAP so it overlaps the hero bottom
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .offset(y = -CARD_OVERLAP)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            )
                    ) {
                        // ── Pill stat chips ───────────────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PillChip(
                                icon = {
                                    Icon(Icons.Default.Public, null,
                                        Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                },
                                text = apiName
                            )
                            if (!ratingText.isNullOrBlank()) {
                                PillChip(
                                    icon = {
                                        Icon(Icons.Default.Star, null,
                                            Modifier.size(13.dp),
                                            tint = Color(0xFFFFC107))
                                    },
                                    text = ratingText
                                )
                            }
                            if (chapterCount != null) {
                                PillChip(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_baseline_list_24),
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    text = "$chapterCount ch"
                                )
                            }
                        }

                        // ── Pill tab row ──────────────────────────────────────
                        PremiumTabRow(
                            selectedTab = selectedTab,
                            tabs = listOf(
                                stringResource(R.string.novel),
                                stringResource(R.string.read_action_chapters)
                            ),
                            onSelect = { selectedTab = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        )

                        // ── Tab content fills ALL remaining height ────────────
                        // Novel tab scrolls via its own verticalScroll.
                        // Chapters tab: RecyclerView gets full height — no nesting!
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                        ) {
                            when (selectedTab) {
                                0 -> {
                                    // Novel tab — scrollable column
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        NovelTabScreen(viewModel, res, activity)
                                        // Extra bottom padding for action bar
                                        Spacer(Modifier.height(100.dp))
                                    }
                                }
                                1 -> {
                                    // Chapters tab — RecyclerView owns its own scroll,
                                    // no wrapping scroll → all chapters accessible
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Chapter toolbar (sort/filter)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box {
                                                TextButton(
                                                    onClick = { chaptersMenuExpanded = true }
                                                ) {
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
                                        // RecyclerView — fillMaxSize, no height cap, no nested scroll
                                        AndroidView(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            factory = { ctx ->
                                                RecyclerView(ctx).apply {
                                                    layoutManager = LinearLayoutManager(ctx)
                                                    adapter = chapterAdapter
                                                    setHasFixedSize(true)
                                                    // Don't disable nested scrolling here —
                                                    // there's no outer scroll on this path so
                                                    // RecyclerView handles everything natively
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
                        }
                    } // end content card Column
                } // end root Column

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
                                onClose      = { viewModel.setSelectionMode(false) },
                                onSelectAll  = { viewModel.selectAll() },
                                onBookmark   = { viewModel.executeBatchBookmark(true) },
                                onUnbookmark = { viewModel.executeBatchBookmark(false) },
                                onMarkRead   = { viewModel.executeBatchMarkRead(true) },
                                onMarkUnread = { viewModel.executeBatchMarkRead(false) },
                            )
                        }
                        apiName != "OceanOfPDF" -> {
                            PremiumActionBar(
                                continueLabel        = continueReadingLabel(res, chapters.orEmpty(), viewModel),
                                bookmarkLabel        = bookmarkTitle,
                                hasBookmark          = hasBookmark,
                                bookmarkMenuExpanded = bookmarkMenuExpanded,
                                onBookmarkMenuChange = { bookmarkMenuExpanded = it },
                                onContinue           = onContinueReading,
                                onBookmarkSelect     = { id ->
                                    viewModel.bookmark(id)
                                    bookmarkMenuExpanded = false
                                },
                                viewModel = viewModel,
                            )
                        }
                    }
                }
            } // end Resource.Success
        }
    } // end root Box
}

// ─── High-quality image request — no size constraint so Coil loads
// at the server's full resolution without artificial downscale ─────────────────
@Composable
private fun rememberHighQualityRequest(data: Any?, context: Context): ImageRequest {
    val baseRequest = rememberImageRequest(data)
    return remember(data) {
        baseRequest.newBuilder(context)
            .allowHardware(false)   // allows software rendering for crisper upscale
            .crossfade(300)
            .build()
    }
}

// ─── Clipboard helper ─────────────────────────────────────────────────────────
private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    com.lagradost.quicknovel.CommonActivity.showToast("$label copied")
}

// ─── Floating circular pill for hero overlay ──────────────────────────────────
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
    ) { content() }
}

// ─── Rounded pill chip (metadata row) ────────────────────────────────────────
@Composable
private fun PillChip(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
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

// ─── Pill-style tab row ───────────────────────────────────────────────────────
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(4.dp),
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

// ─── Bottom CTA bar ───────────────────────────────────────────────────────────
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
    val categories    = remember { loadBookmarkCategories(context) }
    val currentStateId = remember(viewModel.loadId) {
        BaseApplication.getKey<Int>(RESULT_BOOKMARK_STATE, viewModel.loadId.toString()) ?: -1
    }

    Surface(
        modifier      = Modifier.fillMaxWidth(),
        color         = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bookmark pill
            Box {
                OutlinedButton(
                    onClick = { onBookmarkMenuChange(true) },
                    shape   = RoundedCornerShape(50),
                    border  = BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (hasBookmark) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(
                        if (hasBookmark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = bookmarkLabel,
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

            // Continue / Start reading pill
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
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(19.dp))
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

// ─── Chapter selection mode bottom bar ───────────────────────────────────────
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
        tonalElevation  = 8.dp,
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

// ─── Pure helper functions (no UI, backend unchanged) ─────────────────────────

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
    context: Context,
    viewModel: ResultViewModel,
): String {
    val currentStateId =
        BaseApplication.getKey<Int>(RESULT_BOOKMARK_STATE, viewModel.loadId.toString()) ?: -1
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
        return if (systemCat != null) {
            "In Library (${context.getString(systemCat.stringRes ?: R.string.bookmark)})"
        } else {
            "In Library"
        }
    }
    return context.getString(R.string.bookmark)
}

private fun loadBookmarkCategories(context: Context): List<Pair<Int, String>> {
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
