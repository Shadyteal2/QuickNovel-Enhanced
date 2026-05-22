package com.lagradost.quicknovel.ui.download

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.navigate
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberImageRequest
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.luminance

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    onBookClick: (ResultCached) -> Unit,
    onBookClickLoaded: (DownloadFragment.DownloadDataLoaded) -> Unit,
    onBookLongClick: (ResultCached) -> Unit,
    onBookLongClickLoaded: (DownloadFragment.DownloadDataLoaded) -> Unit,
    onImportEpubClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Observe sorting, query, lists, categories
    // Observe sorting, query, lists, categories
    val cards by viewModel.cards.observeAsState(emptyList())
    val categories by viewModel.categories.observeAsState(emptyList<CategoryItem>())
    val pages by viewModel.pages.observeAsState(emptyList<com.lagradost.quicknovel.ui.download.Page>())
    val currentQuery by viewModel.searchQuery.observeAsState("")
    val isQuerying = currentQuery.isNotBlank()

    // Preferences & Layout modes
    val settings = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var isCompact by remember { mutableStateOf(settings.getBoolean("download_compact", false)) }
    var isBento3x3 by remember { mutableStateOf(settings.getBoolean("download_bento", false)) }
    val navStyleState = rememberPreferenceString(context.getString(R.string.library_nav_style_key), "0")
    val isSwipeMode = navStyleState.value == "1"

    // Dialog & Bottom Sheet triggers
    var showCategorySheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    // Set up tabs list directly from pages
    val allTabs = remember(pages) {
        pages?.map { page ->
            if (page.title == com.lagradost.quicknovel.ui.ReadType.NONE.name) {
                context.getString(R.string.title_download)
            } else {
                page.title
            }
        } ?: emptyList()
    }

    // Distribute cards per page directly from pages
    val cardsByPage = remember(pages) {
        val map = mutableMapOf<Int, List<Any>>()
        pages?.forEachIndexed { index, page ->
            map[index] = page.items
        }
        map
    }

    // ViewPager / HorizontalPager state
    val pagerState = rememberPagerState(pageCount = { allTabs.size })

    val imageUri = remember(settings) { settings.getString(context.getString(R.string.background_image_key), null) }
    val hasBackground = !imageUri.isNullOrBlank()
    val containerColor = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        containerColor = containerColor,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header with settings & sorting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = context.getString(R.string.title_download),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${cards.size} " + (context.getString(R.string.novel) + "s").lowercase(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Compact / Grid toggle
                        ModernIconButton(
                            icon = if (isCompact) Icons.Default.GridView else Icons.Default.List,
                            contentDescription = "Toggle Grid/Compact",
                            isActive = false,
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                val nextVal = !isCompact
                                isCompact = nextVal
                                settings.edit().putBoolean("download_compact", nextVal).apply()
                            }
                        )

                        // Bento toggle (only visible when in grid mode)
                        AnimatedVisibility(
                            visible = !isCompact,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            ModernIconButton(
                                icon = Icons.Default.Dashboard,
                                contentDescription = "Toggle Bento layout",
                                isActive = isBento3x3,
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    val nextVal = !isBento3x3
                                    isBento3x3 = nextVal
                                    settings.edit().putBoolean("download_bento", nextVal).apply()
                                }
                            )
                        }

                        // Enable novel update tracking (the "Updates" icon)
                        ModernIconButton(
                            icon = Icons.Default.NotificationsActive,
                            contentDescription = "Enable novel updates",
                            isActive = false,
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                activity.navigate(R.id.navigation_updates)
                            }
                        )

                        // Check updates / refresh
                        ModernIconButton(
                            icon = Icons.Default.Sync,
                            contentDescription = "Refresh & sync downloads",
                            isActive = false,
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                viewModel.loadAllData(true)
                                com.lagradost.quicknovel.CommonActivity.showToast(activity, "Checking for updates...")
                            }
                        )

                        // Manage categories
                        ModernIconButton(
                            icon = Icons.Default.Category,
                            contentDescription = "Manage categories",
                            isActive = false,
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                showCategorySheet = true
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sleek glassmorphic Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .glassCard(shape = RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = currentQuery,
                            onValueChange = { viewModel.search(it) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (currentQuery.isEmpty()) {
                                        Text(
                                            text = context.getString(R.string.search_hint),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (isQuerying) {
                            IconButton(onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                viewModel.search("")
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }

                // Show custom sliding pill tabs in header
                if (allTabs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {},
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        allTabs.forEachIndexed { index, tabName ->
                            val isSelected = pagerState.currentPage == index
                            val scale by animateFloatAsState(if (isSelected) 1.05f else 0.95f, label = "tabScale")
                            
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp, bottom = 4.dp)
                                    .scale(scale)
                                    .glassCard(
                                        shape = RoundedCornerShape(16.dp),
                                        backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        strokeColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else null
                                    )
                                    .clickable {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    showSortSheet = true
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = if (!isSwipeMode) 176.dp else 104.dp)
            ) {
                Icon(Icons.Default.Sort, contentDescription = "Sort")
                Spacer(modifier = Modifier.width(8.dp))
                Text(context.getString(R.string.filter_dialog_sort_by), fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        val bottomListPadding = if (!isSwipeMode) 180.dp else 120.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // HorizontalPager hosting each Tab Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = isSwipeMode // Only swipeable in Swipe View mode
            ) { page ->
                val list = cardsByPage[page] ?: emptyList()
                val isDownloadsPage = page == 0

                if (list.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isDownloadsPage) "Your downloads list is empty" else "No books in this category",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            if (isDownloadsPage) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = onImportEpubClick,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Import EPUB / PDF", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    if (isCompact) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomListPadding),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = list,
                                key = { card ->
                                    when (card) {
                                        is ResultCached -> "cached_${card.id}"
                                        is DownloadFragment.DownloadDataLoaded -> "loaded_${card.id}"
                                        else -> card.hashCode()
                                    }
                                }
                            ) { card ->
                                val currentOnClick = remember(card, onBookClick, onBookClickLoaded) {
                                    {
                                        if (card is ResultCached) onBookClick(card)
                                        else if (card is DownloadFragment.DownloadDataLoaded) onBookClickLoaded(card)
                                    }
                                }
                                val currentOnLongClick = remember(card, onBookLongClick, onBookLongClickLoaded) {
                                    {
                                        if (card is ResultCached) onBookLongClick(card)
                                        else if (card is DownloadFragment.DownloadDataLoaded) onBookLongClickLoaded(card)
                                    }
                                }
                                CompactCardItem(
                                    card = card,
                                    viewModel = viewModel,
                                    onClick = currentOnClick,
                                    onLongClick = currentOnLongClick
                                )
                            }
                            // Bottom Import item inside downloads page
                            if (isDownloadsPage) {
                                item(key = "import_item_column") {
                                    ImportCardItem(onClick = onImportEpubClick)
                                }
                            }
                        }
                    } else {
                        // Grid layout (Pinterest/Bento style)
                        val totalCount = list.size + (if (isDownloadsPage) 1 else 0)
                        
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomListPadding),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                items = list,
                                key = { _, card ->
                                    when (card) {
                                        is ResultCached -> "cached_${card.id}"
                                        is DownloadFragment.DownloadDataLoaded -> "loaded_${card.id}"
                                        else -> card.hashCode()
                                    }
                                },
                                span = { index, _ ->
                                    val spanSize = if (isBento3x3) {
                                        when (index % 7) {
                                            0, 5 -> 2
                                            else -> 1
                                        }
                                    } else 1
                                    GridItemSpan(spanSize)
                                }
                            ) { index, card ->
                                val spanSize = if (isBento3x3) {
                                    when (index % 7) {
                                        0, 5 -> 2
                                        else -> 1
                                    }
                                } else 1
                                val currentOnClick = remember(card, onBookClick, onBookClickLoaded) {
                                    {
                                        if (card is ResultCached) onBookClick(card)
                                        else if (card is DownloadFragment.DownloadDataLoaded) onBookClickLoaded(card)
                                    }
                                }
                                val currentOnLongClick = remember(card, onBookLongClick, onBookLongClickLoaded) {
                                    {
                                        if (card is ResultCached) onBookLongClick(card)
                                        else if (card is DownloadFragment.DownloadDataLoaded) onBookLongClickLoaded(card)
                                    }
                                }
                                GridCardItem(
                                    card = card,
                                    isBento = isBento3x3,
                                    span = spanSize,
                                    onClick = currentOnClick,
                                    onLongClick = currentOnLongClick
                                )
                            }
                            if (isDownloadsPage) {
                                item(key = "import_item_grid", span = { GridItemSpan(3) }) {
                                    ImportCardItem(onClick = onImportEpubClick)
                                }
                            }
                        }
                    }
                }
            }

            // Clean, non-glitchy Pill Slider — only shown in Pill Drawer mode
            if (!isSwipeMode) {
                val numTabs = allTabs.size
                if (numTabs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 96.dp)
                            .width(200.dp) // Sleek, compact size
                            .height(48.dp)
                            .pointerInput(numTabs, "drag") {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val targetPage = (fraction * numTabs).toInt().coerceIn(0, numTabs - 1)
                                        if (pagerState.currentPage != targetPage) {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                            scope.launch { pagerState.animateScrollToPage(targetPage) }
                                        }
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val targetPage = (fraction * numTabs).toInt().coerceIn(0, numTabs - 1)
                                        if (pagerState.currentPage != targetPage) {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                            scope.launch { pagerState.animateScrollToPage(targetPage) }
                                        }
                                    }
                                )
                            }
                            .pointerInput(numTabs, "tap") {
                                detectTapGestures { offset ->
                                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val targetPage = (fraction * numTabs).toInt().coerceIn(0, numTabs - 1)
                                    if (pagerState.currentPage != targetPage) {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                        scope.launch { pagerState.animateScrollToPage(targetPage) }
                                    }
                                }
                            },
                        // Crucial: CenterStart alignment so X offset starts from the left edge instead of the middle
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Track line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.Center) // Keep the track line centered
                                .background(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(1.dp)
                                )
                        )

                        // Dots row
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            allTabs.forEachIndexed { _, _ ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }

                        // Traveling capsule — driven purely by pagerState offset fraction
                        val targetPagerPos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        val animatedPagerPos by animateFloatAsState(
                            targetValue = targetPagerPos,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "capsulePos"
                        )
                        val segmentWidth = 200.dp / numTabs
                        val capsuleWidth = 24.dp
                        val capsuleOffset = (segmentWidth * (animatedPagerPos + 0.5f)) - (capsuleWidth / 2)

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(capsuleWidth)
                                .offset(x = capsuleOffset), // Now accurately placed relative to CenterStart
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = capsuleWidth, height = 8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Sort options
    if (showSortSheet) {
        val currentSelectedIdx = if (pagerState.currentPage == 0) {
            com.lagradost.quicknovel.BaseApplication.getKey<Int>(com.lagradost.quicknovel.DOWNLOAD_SETTINGS, com.lagradost.quicknovel.DOWNLOAD_SORTING_METHOD) ?: 0
        } else {
            com.lagradost.quicknovel.BaseApplication.getKey<Int>(com.lagradost.quicknovel.DOWNLOAD_SETTINGS, com.lagradost.quicknovel.DOWNLOAD_NORMAL_SORTING_METHOD) ?: 0
        }
        
        val sortingList = if (pagerState.currentPage == 0) {
            DownloadViewModel.sortingMethods
        } else {
            DownloadViewModel.normalSortingMethods
        }

        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = if (isSystemInDarkTheme()) Color(0xEE121215) else Color(0xEEFFFFFF),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
            ) {
                Text(
                    text = context.getString(R.string.filter_dialog_sort_by),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                sortingList.forEachIndexed { index, pair ->
                    val isSelected = index == currentSelectedIdx
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                if (pagerState.currentPage == 0) {
                                    com.lagradost.quicknovel.BaseApplication.setKey(com.lagradost.quicknovel.DOWNLOAD_SETTINGS, com.lagradost.quicknovel.DOWNLOAD_SORTING_METHOD, index)
                                } else {
                                    com.lagradost.quicknovel.BaseApplication.setKey(com.lagradost.quicknovel.DOWNLOAD_SETTINGS, com.lagradost.quicknovel.DOWNLOAD_NORMAL_SORTING_METHOD, index)
                                }
                                viewModel.resortAllData()
                                showSortSheet = false
                            }
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = context.getString(pair.name),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Category management
    if (showCategorySheet) {
        var newCategoryName by remember { mutableStateOf("") }
        var isEditingCategoryIdx by remember { mutableStateOf(-1) }
        var editingText by remember { mutableStateOf("") }

        val isSheetLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
        val sheetBgColor = if (isSheetLightTheme) Color(0xEEFFFFFF) else {
            val themeKey = settings.getString(context.getString(R.string.theme_key), "Amoled")
            if (themeKey == "Amoled" || themeKey == "Black") Color(0xEE000000) else Color(0xEE121215)
        }

        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            containerColor = sheetBgColor,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
            ) {
                Text(
                    text = "Manage Categories",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Add Category Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        placeholder = { Text("New Category") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                val customListCount = categories.filter { !it.isSystem }.size
                                if (customListCount >= 5) {
                                    com.lagradost.quicknovel.CommonActivity.showToast(activity, "Maximum 5 custom categories allowed")
                                } else {
                                    viewModel.addCategory(newCategoryName.trim())
                                    newCategoryName = ""
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // List of Categories
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    itemsIndexed(
                        items = categories,
                        key = { _, categoryItem -> categoryItem.id }
                    ) { index, categoryItem ->
                        val isEditing = index == isEditingCategoryIdx
                        val isSystem = categoryItem.isSystem
                        val categoryName = if (categoryItem.isSystem && categoryItem.stringRes != null) context.getString(categoryItem.stringRes) else categoryItem.name
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .glassCard(shape = RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (isEditing) {
                                OutlinedTextField(
                                    value = editingText,
                                    onValueChange = { editingText = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = {
                                    if (editingText.isNotBlank()) {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                        viewModel.renameCategory(categoryItem.id, editingText.trim())
                                        isEditingCategoryIdx = -1
                                    }
                                }) {
                                    Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )

                                Row {
                                    // Move Up
                                    IconButton(
                                        enabled = index > 0,
                                        onClick = {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                            val currentList = categories.toMutableList()
                                            val temp = currentList[index]
                                            currentList[index] = currentList[index - 1]
                                            currentList[index - 1] = temp
                                            viewModel.updateCategories(currentList)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
                                        )
                                    }
                                    // Move Down
                                    IconButton(
                                        enabled = index < categories.size - 1,
                                        onClick = {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                            val currentList = categories.toMutableList()
                                            val temp = currentList[index]
                                            currentList[index] = currentList[index + 1]
                                            currentList[index + 1] = temp
                                            viewModel.updateCategories(currentList)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move Down",
                                            tint = if (index < categories.size - 1) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
                                        )
                                    }
                                    if (!isSystem) {
                                        // Edit
                                        IconButton(onClick = {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                            isEditingCategoryIdx = index
                                            editingText = categoryName
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Rename",
                                                tint = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                        // Delete
                                        IconButton(onClick = {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                            viewModel.deleteCategory(categoryItem.id)
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberPreferenceBoolean(key: String, defaultValue: Boolean): State<Boolean> {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val state = remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) {
                state.value = prefs.getBoolean(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

@Composable
fun rememberPreferenceString(key: String, defaultValue: String): State<String> {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val state = remember { mutableStateOf(prefs.getString(key, defaultValue) ?: defaultValue) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) {
                state.value = prefs.getString(key, defaultValue) ?: defaultValue
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

@Composable
fun Modifier.tactileResponse(
    enabled: Boolean,
    interactionSource: MutableInteractionSource
): Modifier {
    if (!enabled) return this

    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tactileScale"
    )

    val rotateX by animateFloatAsState(
        targetValue = if (isPressed) -3f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tactileRotateX"
    )

    val rotateY by animateFloatAsState(
        targetValue = if (isPressed) 3f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tactileRotateY"
    )

    return this.graphicsLayer {
        this.scaleX = scale
        this.scaleY = scale
        this.rotationX = rotateX
        this.rotationY = rotateY
        this.cameraDistance = 12f * density
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridCardItem(
    card: Any,
    isBento: Boolean,
    span: Int = 1,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val isTactileEnabled by rememberPreferenceBoolean("library_tactile_response", true)
    val interactionSource = remember { MutableInteractionSource() }
    
    val posterUrl = remember(card) {
        when (card) {
            is ResultCached -> card.poster
            is DownloadFragment.DownloadDataLoaded -> card.posterUrl
            else -> ""
        }
    }

    val title = remember(card) {
        when (card) {
            is ResultCached -> card.name
            is DownloadFragment.DownloadDataLoaded -> card.name
            else -> ""
        }
    }

    val diffCount = remember(card) {
        when (card) {
            is ResultCached -> {
                // Determine read count vs total (simple cached badge)
                0
            }
            is DownloadFragment.DownloadDataLoaded -> {
                card.downloadedCount - card.readCount
            }
            else -> 0
        }
    }

    val progressValue = remember(card) {
        when (card) {
            is ResultCached -> {
                0.0f
            }
            is DownloadFragment.DownloadDataLoaded -> {
                if (card.downloadedTotal > 0) card.downloadedCount.toFloat() / card.downloadedTotal.toFloat() else 0.0f
            }
            else -> 0.0f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                },
                onLongClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    onLongClick()
                }
            )
            .tactileResponse(isTactileEnabled, interactionSource)
    ) {
        val cardAspectRatio = if (isBento) {
            if (span == 2) 1.32f else 0.66f
        } else {
            0.66f
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cardAspectRatio)
                .glassCard(shape = RoundedCornerShape(12.dp), strokeWidth = 0.5.dp)
        ) {
            // Premium typographic placeholder for missing/loading covers
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1E1E24),
                                Color(0xFF2E303D),
                                Color(0xFF14151B)
                            )
                        )
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AsyncImage(
                model = rememberImageRequest(data = card),
                contentDescription = title,
                imageLoader = SingletonImageLoader.get(LocalContext.current),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )

            // Dim overlay at the bottom for smooth text legibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )

            // Diff unread count badge
            if (diffCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .glassCard(
                            shape = RoundedCornerShape(6.dp),
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            strokeWidth = 0.5.dp
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "+$diffCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Category card specific visual indicators
            if (card is DownloadFragment.DownloadDataLoaded) {
                // If downloading progress indicator
                if (progressValue < 1f || card.generating) {
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactCardItem(
    card: Any,
    viewModel: DownloadViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isTactileEnabled by rememberPreferenceBoolean("library_tactile_response", true)
    val interactionSource = remember { MutableInteractionSource() }

    val title = remember(card) {
        when (card) {
            is ResultCached -> card.name
            is DownloadFragment.DownloadDataLoaded -> card.name
            else -> ""
        }
    }

    val posterUrl = remember(card) {
        when (card) {
            is ResultCached -> card.poster
            is DownloadFragment.DownloadDataLoaded -> card.posterUrl
            else -> ""
        }
    }

    val progressText = remember(card) {
        when (card) {
            is ResultCached -> {
                ""
            }
            is DownloadFragment.DownloadDataLoaded -> {
                "${card.downloadedCount}/${card.downloadedTotal}${if (card.ETA == "") "" else " - ${card.ETA}"}"
            }
            else -> ""
        }
    }

    val diffCount = remember(card) {
        when (card) {
            is ResultCached -> 0
            is DownloadFragment.DownloadDataLoaded -> card.downloadedCount - card.readCount
            else -> 0
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                },
                onLongClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    onLongClick()
                }
            )
            .tactileResponse(isTactileEnabled, interactionSource)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 76.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1E1E24),
                                Color(0xFF2E303D)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            AsyncImage(
                model = rememberImageRequest(data = card),
                contentDescription = null,
                imageLoader = SingletonImageLoader.get(LocalContext.current),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (diffCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "+$diffCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Titles and downloading status
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (card is DownloadFragment.DownloadDataLoaded) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Loading bar
                val showProgressbar = card.generating || (card.downloadedCount < card.downloadedTotal)
                if (showProgressbar) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (card.generating) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        val progress = if (card.downloadedTotal > 0) card.downloadedCount.toFloat() / card.downloadedTotal.toFloat() else 0.0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Action Buttons (Pause / Resume / Delete)
        if (card is DownloadFragment.DownloadDataLoaded) {
            val isDoneByCount = card.downloadedCount >= card.downloadedTotal && card.downloadedTotal > 0
            val realState = if (isDoneByCount && card.state != DownloadState.IsDownloading) DownloadState.IsDone else card.state

            val iconRes = when (realState) {
                DownloadState.IsDownloading -> Icons.Default.Pause
                DownloadState.IsPaused -> Icons.Default.PlayArrow
                DownloadState.IsStopped -> Icons.Default.Refresh
                DownloadState.IsFailed -> Icons.Default.Refresh
                DownloadState.IsDone -> Icons.Default.CheckCircle
                DownloadState.IsPending -> Icons.Default.Refresh
                DownloadState.Nothing -> Icons.Default.Refresh
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Action: Pause / Resume / Refresh
                IconButton(onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    when (realState) {
                        DownloadState.IsDownloading -> viewModel.pause(card)
                        DownloadState.IsPaused -> viewModel.resume(card)
                        DownloadState.IsPending -> {}
                        DownloadState.IsDone -> viewModel.refreshCard(card)
                        else -> viewModel.refreshCard(card)
                    }
                }) {
                    Icon(
                        imageVector = iconRes,
                        contentDescription = "Status action",
                        tint = if (realState == DownloadState.IsDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }

                // Delete Trash Alert
                IconButton(onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    viewModel.deleteAlert(card)
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun ImportCardItem(onClick: () -> Unit) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .glassCard(
                shape = RoundedCornerShape(16.dp),
                backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                strokeWidth = 1.dp
            )
            .clickable {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AddCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Import EPUB / PDF",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Modern animated pill icon button with glassy background and press scale animation.
 * Active state shows a primary-colored glow highlight.
 */
@Composable
fun ModernIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "iconScale"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else if (isPressed) 0.25f else 0.12f,
        animationSpec = tween(150),
        label = "iconBgAlpha"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(200),
        label = "iconTint"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = if (isActive)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = bgAlpha),
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
    }
}
