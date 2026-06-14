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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.lagradost.quicknovel.ui.theme.coverAuraGlow
import com.lagradost.quicknovel.ui.theme.extractAuraColor
import com.lagradost.quicknovel.ui.theme.rememberAuraEnabled
import com.lagradost.quicknovel.ui.theme.rememberAccentGradientBrush

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
    val loadResponse       by viewModel.loadResponse.observeAsState()
    val isSyncEnabled      by viewModel.isSyncEnabledDisplay.observeAsState(false)
    val isMigrating        by viewModel.isMigrating.observeAsState(false)
    val isSelectionMode    by viewModel.isInSelectionMode.observeAsState(false)
    val selectedChapters   by viewModel.selectedChapters.observeAsState(emptySet())
    val isBatchDownloading by viewModel.isBatchDownloading.observeAsState(false)
    val chapters           by viewModel.chapters.collectAsStateWithLifecycle()

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
    val bookmarkState        by viewModel.bookmarkState.observeAsState(-1)

    val bookmarkLabel by viewModel.bookmarkLabel.collectAsStateWithLifecycle()
    val continueLabel by viewModel.continueReadingLabel.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val hasBookmark = bookmarkLabel != defaultBookmarkLabel

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

                var showPosterViewer by remember { mutableStateOf(false) }

                // Full-screen dialog viewer for the cover poster
                if (showPosterViewer) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showPosterViewer = false },
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { showPosterViewer = false },
                            contentAlignment = Alignment.Center
                        ) {
                            // Blurred Background Cover Image
                            AsyncImage(
                                model = rememberDefaultImageRequest(res.image, context),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(24.dp)
                            )
                            // Semi-transparent overlay to ensure good contrast and focus
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f))
                            )
                            // Sharp cover image in foreground
                            AsyncImage(
                                model = rememberDefaultImageRequest(res.image, context),
                                contentDescription = "Full Cover Poster",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                            IconButton(
                                onClick = { showPosterViewer = false },
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .padding(16.dp)
                                    .align(Alignment.TopEnd)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Viewer",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

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
                            if (hasBookmark) {
                                var showMigrationSheet by remember { mutableStateOf(false) }
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showMigrationSheet = true
                                }) {
                                    Icon(
                                        Icons.Default.CompareArrows,
                                        contentDescription = "Migrate Provider",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                if (showMigrationSheet) {
                                    MigrationBottomSheet(
                                        viewModel = viewModel,
                                        novelName = res.name,
                                        onDismiss = { showMigrationSheet = false }
                                    )
                                }
                            }
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
                                            // ─── Cover Aura Glow ─────────────────────────────────────────────────────
                                            val isAuraEnabled = rememberAuraEnabled()
                                            var auraColor by remember(res.image) { mutableStateOf(Color.Unspecified) }

                                            if (isAuraEnabled) {
                                                LaunchedEffect(res.image) {
                                                    val cacheKey = res.image?.toString() ?: return@LaunchedEffect
                                                    if (cacheKey.isBlank()) return@LaunchedEffect
                                                    try {
                                                        val loader = coil3.SingletonImageLoader.get(context)
                                                        // Use buildImageRequest to inherit cache path resolution & custom header logic
                                                        val req = com.lagradost.quicknovel.ui.theme.buildImageRequest(context, res.image).newBuilder(context)
                                                            .allowHardware(false)
                                                            .size(32) // tiny decode — just need dominant color
                                                            .build()
                                                        val result = loader.execute(req)
                                                        val drawable = (result as? coil3.request.SuccessResult)?.image
                                                        val bmp = (drawable as? coil3.BitmapImage)?.bitmap
                                                        if (bmp != null) {
                                                            auraColor = extractAuraColor(
                                                                bitmap = bmp,
                                                                cacheKey = cacheKey
                                                            )
                                                        }
                                                    } catch (_: Throwable) { /* fail silently — aura is cosmetic */ }
                                                }
                                            }

                                            // Poster card wrapper Box (allows cover aura glow to bleed out unclipped)
                                            Box(
                                                modifier = Modifier
                                                    .width(POSTER_WIDTH + 16.dp)
                                                    .height(POSTER_HEIGHT + 16.dp)
                                                    .padding(8.dp)
                                                    .coverAuraGlow(auraColor = auraColor, enabled = isAuraEnabled)
                                            ) {
                                                Card(
                                                    shape = RoundedCornerShape(POSTER_RADIUS),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clickable {
                                                            showPosterViewer = true
                                                        }
                                                ) {
                                                    AsyncImage(
                                                        model = rememberDefaultImageRequest(res.image, context),
                                                        contentDescription = res.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
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
                                                        text = res.apiName,
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
                                        val accentBrush = rememberAccentGradientBrush(accentColor = MaterialTheme.colorScheme.primary)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .background(accentBrush, shape = RoundedCornerShape(14.dp))
                                                .clickable { onContinueReading() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = continueLabel,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
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
                                                    text = if (hasBookmark) bookmarkLabel else "Add to Library",
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
                                                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                                                // categories is observed from viewModel
                                                val currentStateId = remember(readState, currentId, bookmarkState) {
                                                    BaseApplication.getKey<Int>(RESULT_BOOKMARK_STATE, currentId.toString()) ?: -1
                                                }
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
                                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                            viewModel.bookmark(id)
                                                            bookmarkMenuExpanded = false
                                                        }
                                                    )
                                                }
                                                if (currentStateId != -1) {
                                                    DropdownMenuItem(
                                                        text = { Text("Unbookmark") },
                                                        onClick = {
                                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
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
                                                // Reference selection states to force recomposing update block on selection change
                                                val selMode = isSelectionMode
                                                val selChapters = selectedChapters
                                                if (list != null && list.isNotEmpty()) {
                                                    if (chapterAdapter.immutableCurrentList != list) {
                                                        if (chapterAdapter.immutableCurrentList.isEmpty()) {
                                                            chapterAdapter.submitIncomparableList(list)
                                                        } else {
                                                            chapterAdapter.submitList(list)
                                                        }
                                                    }
                                                    chapterAdapter.updateSelectionStates(selMode ?: false, selChapters ?: emptySet())
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
                            ChapterSelectionBar(
                                selectedCount      = selectedChapters.size,
                                isBatchDownloading = isBatchDownloading,
                                topCornerRadius    = 20.dp,
                                onClose            = { viewModel.setSelectionMode(false) },
                                onSelectAll        = { viewModel.selectAll() },
                                onBookmark         = { viewModel.executeBatchBookmark(true) },
                                onUnbookmark       = { viewModel.executeBatchBookmark(false) },
                                onMarkRead         = { viewModel.executeBatchMarkRead(true) },
                                onMarkUnread       = { viewModel.executeBatchMarkRead(false) },
                                onDownload         = { viewModel.executeBatchDownload() },
                            )
                        }
                    }
                }
            }
        }
    }
    
    if (isMigrating) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .size(width = 280.dp, height = 180.dp)
                    .glassCard(
                        shape = RoundedCornerShape(20.dp),
                        strokeWidth = 1.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Migrating Provider",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Transferring bookmarks & notes...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
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
    val accentBrush = rememberAccentGradientBrush(accentColor = MaterialTheme.colorScheme.primary)
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
                    .then(
                        if (isSelected) Modifier.background(accentBrush)
                        else Modifier
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
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
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
    return remember(data, base) {
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


