package com.lagradost.quicknovel.ui.download

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.abs
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
import com.lagradost.quicknovel.ui.theme.coverAuraGlow
import com.lagradost.quicknovel.ui.theme.extractAuraColor
import com.lagradost.quicknovel.ui.theme.rememberAuraEnabled
import com.lagradost.quicknovel.ui.theme.VibePrefs
import com.lagradost.quicknovel.ui.theme.generateNoiseBitmap
import com.lagradost.quicknovel.ui.theme.noiseTextureOverlay
import com.lagradost.quicknovel.ui.theme.rememberPreferenceInt
import androidx.compose.ui.graphics.SolidColor
import coil3.request.allowHardware
import androidx.compose.ui.graphics.ImageBitmap

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    onBookClick: (ResultCached) -> Unit,
    onBookClickLoaded: (DownloadFragment.DownloadDataLoaded) -> Unit,
    onBookLongClick: (ResultCached) -> Unit,
    onBookLongClickLoaded: (DownloadFragment.DownloadDataLoaded) -> Unit,
    onImportEpubClick: () -> Unit,
    onPdfToEpubClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val pagerScrollJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }

    val isTactileEnabledState = rememberPreferenceBoolean("library_tactile_response", true)

    val onPauseClick = remember(viewModel) {
        { card: DownloadFragment.DownloadDataLoaded ->
            viewModel.pause(card)
        }
    }
    val onResumeClick = remember(viewModel) {
        { card: DownloadFragment.DownloadDataLoaded ->
            viewModel.resume(card)
        }
    }
    val onRefreshClick = remember(viewModel) {
        { card: DownloadFragment.DownloadDataLoaded ->
            viewModel.refreshCard(card)
        }
    }
    val onDeleteClick = remember(viewModel) {
        { card: Any ->
            if (card is DownloadFragment.DownloadDataLoaded) {
                viewModel.deleteAlert(card)
            } else if (card is ResultCached) {
                viewModel.deleteAlert(card)
            }
        }
    }

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
    val navStyleState = rememberPreferenceString(context.getString(R.string.library_nav_style_key), "1")
    val isSwipeMode = navStyleState.value == "1"
    var isSwipingPage by remember { mutableStateOf(false) }
    var isScrollingList by remember { mutableStateOf(false) }

    // Hoist Noise texture states to the parent level to prevent per-card loading delays
    val performanceMode by rememberPreferenceBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false)
    val isTactileEnabled = isTactileEnabledState.value && !performanceMode
    val aestheticPersonaEnabled by rememberPreferenceBoolean(VibePrefs.AESTHETIC_PERSONA_ENABLED, false)
    val noiseTextureEnabled by rememberPreferenceBoolean(VibePrefs.NOISE_TEXTURE_ENABLED, false)
    val noiseTextureIntensity by rememberPreferenceInt(VibePrefs.NOISE_TEXTURE_INTENSITY, 30)

    val isNoiseEnabled = !performanceMode && aestheticPersonaEnabled && noiseTextureEnabled
    val noiseIntensity = if (isNoiseEnabled) noiseTextureIntensity else 0
    var noiseBitmap by remember(noiseIntensity) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(noiseIntensity, isNoiseEnabled) {
        if (isNoiseEnabled && noiseIntensity > 0) {
            noiseBitmap = generateNoiseBitmap(noiseIntensity)
        } else {
            noiseBitmap = null
        }
    }

    // Dialog & Bottom Sheet triggers
    var showCategorySheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedNovels = remember { mutableStateListOf<Int>() }
    var isPullRefreshing by remember { mutableStateOf(false) }
    var showMultiSelectCategorySheet by remember { mutableStateOf(false) }
    var showMultiSelectDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) {
            selectedNovels.clear()
        }
    }

    // Set up tabs list directly from pages
    val allTabs = remember(pages) {
        val baseTabs = pages?.map { page ->
            if (page.title == com.lagradost.quicknovel.ui.ReadType.NONE.name) {
                context.getString(R.string.title_download)
            } else {
                page.title
            }
        } ?: emptyList()
        if (baseTabs.isNotEmpty()) {
            val list = baseTabs.toMutableList()
            list.add(1, "NeoShelf")
            list
        } else {
            listOf("NeoShelf")
        }
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

    var activeTargetPage by remember { mutableStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) {
        activeTargetPage = pagerState.currentPage
    }

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

                        // Toggle Selection mode
                        ModernIconButton(
                            icon = Icons.Default.DoneAll,
                            contentDescription = "Toggle Selection Mode",
                            isActive = isSelectionMode,
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                isSelectionMode = !isSelectionMode
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
                        edgePadding = 16.dp,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {},
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        allTabs.forEachIndexed { index, tabName ->
                            val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val isSelected = pagerState.currentPage == index

                            val performanceMode by rememberPreferenceBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false)
                            val unselectedBgColor = if (isLightTheme) Color(0x1F000000) else Color(0x1FFFFFFF)
                            
                            val tabBgColor = if (performanceMode) {
                                if (isSelected) primaryColor else unselectedBgColor
                            } else {
                                animateColorAsState(
                                     targetValue = if (isSelected) primaryColor else unselectedBgColor,
                                     animationSpec = tween(durationMillis = 200),
                                     label = "tabBgColor"
                                 ).value
                            }
                            
                            val gradientEnabled by rememberPreferenceBoolean(VibePrefs.ACCENT_GRADIENT_ENABLED, false)
                            val endColorInt by rememberPreferenceInt(VibePrefs.ACCENT_GRADIENT_END_COLOR, 0)
                            val isGradientActive = gradientEnabled && endColorInt != 0

                            val tabBgBrush = remember(isSelected, tabBgColor, isGradientActive, endColorInt) {
                                if (isSelected && isGradientActive) {
                                    Brush.linearGradient(
                                        colors = listOf(tabBgColor, Color(endColorInt))
                                    )
                                } else {
                                    SolidColor(tabBgColor)
                                }
                            }
                            
                            val unselectedStrokeColor = if (isLightTheme) Color(0x0A000000) else Color(0x1AFFFFFF)
                            val targetTextColor = MaterialTheme.colorScheme.onPrimary
                            val unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

                            val tabStrokeColor = if (performanceMode) {
                                if (isSelected) primaryColor.copy(alpha = 0.5f) else unselectedStrokeColor
                            } else {
                                animateColorAsState(
                                     targetValue = if (isSelected) primaryColor.copy(alpha = 0.5f) else unselectedStrokeColor,
                                     animationSpec = tween(durationMillis = 200),
                                     label = "tabStrokeColor"
                                 ).value
                            }
                            
                            val tabTextColor = if (performanceMode) {
                                if (isSelected) targetTextColor else unselectedTextColor
                            } else {
                                animateColorAsState(
                                    targetValue = if (isSelected) targetTextColor else unselectedTextColor,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "tabTextColor"
                                ).value
                            }
                            
                            val tabScale = if (performanceMode) {
                                1.0f
                            } else {
                                animateFloatAsState(
                                    targetValue = if (isSelected) 1.04f else 0.96f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "tabScale"
                                ).value
                            }

                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp, bottom = 4.dp)
                                    .graphicsLayer {
                                        scaleX = tabScale
                                        scaleY = tabScale
                                    }
                                    .drawBehind {
                                        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())

                                        // Draw spatial glass capsule background
                                        drawRoundRect(
                                            brush = tabBgBrush,
                                            cornerRadius = cornerRadius
                                        )

                                        // Draw sleek high-fidelity border contour
                                        drawRoundRect(
                                            color = tabStrokeColor,
                                            cornerRadius = cornerRadius,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                        )
                                    }
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (activeTargetPage != index) {
                                            activeTargetPage = index
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                            pagerScrollJob[0]?.cancel()
                                            pagerScrollJob[0] = scope.launch {
                                                if (performanceMode) {
                                                    pagerState.scrollToPage(index)
                                                } else {
                                                    pagerState.animateScrollToPage(
                                                        page = index,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = 1200f
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = tabTextColor
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = if (!isSwipeMode) 156.dp else 84.dp) // Perfect safe height clearance above nav/pill sliders
                    .size(56.dp)
                    .glassCard(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        strokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                    .clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        showSortSheet = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort",
                    tint = MaterialTheme.colorScheme.primary
                )
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
                beyondViewportPageCount = if (allTabs.isNotEmpty()) allTabs.size else 0,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds() // Crucial: clips all graphicsLayer translated pages to the viewport boundaries to prevent edge bleeding!
                    .lockGesturePriority(
                        enabled = isSwipeMode,
                        onHorizontalDragDetected = { isSwipingPage = true },
                        onVerticalDragDetected = { isScrollingList = true },
                        onRelease = {
                            isSwipingPage = false
                            isScrollingList = false
                        }
                    ),
                userScrollEnabled = isSwipeMode && !isScrollingList // Disable pager swipe when scrolling list
            ) { page ->
                val neoShelfIndex = allTabs.indexOf("NeoShelf")
                if (page == neoShelfIndex) {
                    val shelves by viewModel.neoShelfPage.observeAsState(emptyList())
                    NeoShelfPageContent(
                        shelves = shelves,
                        isCompact = isCompact,
                        isBento3x3 = isBento3x3,
                        bottomListPadding = bottomListPadding,
                        onBookClickLoaded = onBookClickLoaded,
                        onBookClick = onBookClick,
                        onBookLongClickLoaded = onBookLongClickLoaded
                    )
                } else {
                    val actualPageIndex = if (page > neoShelfIndex) page - 1 else page
                    val list = cardsByPage[actualPageIndex] ?: emptyList()
                val isDownloadsPage = actualPageIndex == 0
                val pullState = rememberPullToRefreshState()

                PullToRefreshBox(
                    isRefreshing = isPullRefreshing,
                    onRefresh = {
                        scope.launch {
                            isPullRefreshing = true
                            viewModel.loadAllData(true).join()
                            isPullRefreshing = false
                        }
                    },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        ) {
                            val progress = pullState.distanceFraction
                            if (isPullRefreshing || progress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .graphicsLayer {
                                            if (!isPullRefreshing) {
                                                alpha = progress.coerceIn(0f, 1f)
                                                scaleX = progress.coerceIn(0f, 1f)
                                                scaleY = progress.coerceIn(0f, 1f)
                                            }
                                        }
                                        .glassCard(
                                            shape = CircleShape,
                                            backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                            strokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPullRefreshing) {
                                        com.lagradost.quicknovel.ui.theme.LoadingIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer {
                                                    rotationZ = progress * 360f
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) {
                    // Premium visual sliding parallax depth fade transition (100% GPU-accelerated, zero overdraw/layer overhead)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                            // Calculate current page position offset relative to focus point
                            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            
                            // Apply custom depth fade & parallax scaling
                            if (pageOffset < 0) {
                                // Outgoing page: shrinks (to 90% scale), fades out, moves slowly (20% speed)
                                val fraction = 1f + pageOffset // goes from 1 to 0
                                alpha = fraction.coerceIn(0f, 1f)
                                
                                val scale = 0.9f + (fraction * 0.1f).coerceIn(0f, 0.1f)
                                scaleX = scale
                                scaleY = scale
                                
                                // Cancel 80% of default horizontal translation to make it feel stationary/sinking
                                translationX = -pageOffset * size.width * 0.8f
                            } else {
                                // Incoming page: slides in over it normally, fading in, full scale
                                val fraction = 1f - pageOffset.coerceIn(0f, 1f) // goes from 0 to 1
                                alpha = fraction
                                
                                scaleX = 1f
                                scaleY = 1f
                                
                                // Standard sliding translation
                                translationX = 0f
                            }
                        }
                ) {
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
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = onPdfToEpubClick,
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("PDF to EPUB", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        androidx.compose.runtime.key(isCompact, isBento3x3) {
                            if (isCompact) {
                                val listState = rememberLazyListState()
                                
                                // Prefetch cover images of the upcoming 12 novels as the user scrolls
                                LaunchedEffect(listState.firstVisibleItemIndex) {
                                    val totalItems = list.size
                                    val startIndex = (listState.firstVisibleItemIndex + 8).coerceAtMost(totalItems)
                                    val endIndex = (startIndex + 12).coerceAtMost(totalItems)
                                    for (i in startIndex until endIndex) {
                                        val card = list.getOrNull(i) ?: continue
                                        val posterUrl = when (card) {
                                            is ResultCached -> card.poster
                                            is DownloadFragment.DownloadDataLoaded -> card.posterUrl
                                            else -> null
                                        } ?: continue
                                        val req = ImageRequest.Builder(context)
                                            .data(posterUrl)
                                            .size(coil3.size.Size.ORIGINAL)
                                            .allowHardware(true)
                                            .build()
                                        coil3.SingletonImageLoader.get(context).enqueue(req)
                                    }
                                }
                                
                                LazyColumn(
                                    state = listState,
                                    userScrollEnabled = !isSwipingPage,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomListPadding),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = list,
                                        key = { card ->
                                            val id = when (card) {
                                                is ResultCached -> card.id
                                                is DownloadFragment.DownloadDataLoaded -> card.id
                                                else -> card.hashCode()
                                            }
                                            "novel_$id"
                                        },
                                        contentType = { card ->
                                            when (card) {
                                                is ResultCached -> "cached_card"
                                                is DownloadFragment.DownloadDataLoaded -> "loaded_card"
                                                else -> "generic_card"
                                            }
                                        }
                                    ) { card ->
                                        val cardId = remember(card) {
                                            when (card) {
                                                is ResultCached -> card.id
                                                is DownloadFragment.DownloadDataLoaded -> card.id
                                                else -> -1
                                            }
                                        }
                                        val isSelectedState = remember(cardId) {
                                            derivedStateOf { selectedNovels.contains(cardId) }
                                        }
                                        val isSelected = isSelectedState.value
                                        val currentOnClick = remember<() -> Unit>(card, isSelectionMode, isSelected, onBookClick, onBookClickLoaded) {
                                            {
                                                if (isSelectionMode) {
                                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                                    if (isSelected) {
                                                        selectedNovels.remove(cardId)
                                                    } else {
                                                        selectedNovels.add(cardId)
                                                    }
                                                } else {
                                                    if (card is ResultCached) onBookClick(card)
                                                    else if (card is DownloadFragment.DownloadDataLoaded) onBookClickLoaded(card)
                                                    else {}
                                                }
                                                Unit
                                            }
                                        }
                                        val currentOnLongClick = remember<() -> Unit>(card, isSelectionMode, isSelected, onBookLongClick, onBookLongClickLoaded) {
                                            {
                                                if (isSelectionMode) {
                                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                                    if (isSelected) {
                                                        selectedNovels.remove(cardId)
                                                    } else {
                                                        selectedNovels.add(cardId)
                                                    }
                                                } else {
                                                    if (card is ResultCached) onBookLongClick(card)
                                                    else if (card is DownloadFragment.DownloadDataLoaded) onBookLongClickLoaded(card)
                                                    else {}
                                                }
                                                Unit
                                            }
                                        }

                                        CompactCardItem(
                                            card = card,
                                            isTactileEnabled = isTactileEnabled,
                                            onClick = currentOnClick,
                                            onLongClick = currentOnLongClick,
                                            onPauseClick = { if (card is DownloadFragment.DownloadDataLoaded) onPauseClick(card) },
                                            onResumeClick = { if (card is DownloadFragment.DownloadDataLoaded) onResumeClick(card) },
                                            onRefreshClick = { if (card is DownloadFragment.DownloadDataLoaded) onRefreshClick(card) },
                                            onDeleteClick = { onDeleteClick(card) },
                                            isSelectionMode = isSelectionMode,
                                            isSelected = isSelected,
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                    // Bottom Import item inside downloads page
                                    if (isDownloadsPage) {
                                        item(key = "import_item_column") {
                                            ImportCardItem(onClick = onImportEpubClick)
                                        }
                                        item(key = "pdf_to_epub_item_column") {
                                            ImportCardItem(
                                                title = "PDF to EPUB",
                                                icon = Icons.Default.PictureAsPdf,
                                                onClick = onPdfToEpubClick
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Grid layout (Pinterest/Bento style)
                                val totalCount = list.size + (if (isDownloadsPage) 1 else 0)
                                val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                                
                                // Prefetch cover images of the upcoming 18 novels as the user scrolls
                                LaunchedEffect(gridState.firstVisibleItemIndex) {
                                    val totalItems = list.size
                                    val startIndex = (gridState.firstVisibleItemIndex + 12).coerceAtMost(totalItems)
                                    val endIndex = (startIndex + 18).coerceAtMost(totalItems)
                                    for (i in startIndex until endIndex) {
                                        val card = list.getOrNull(i) ?: continue
                                        val posterUrl = when (card) {
                                            is ResultCached -> card.poster
                                            is DownloadFragment.DownloadDataLoaded -> card.posterUrl
                                            else -> null
                                        } ?: continue
                                        val req = ImageRequest.Builder(context)
                                            .data(posterUrl)
                                            .size(coil3.size.Size.ORIGINAL)
                                            .allowHardware(true)
                                            .build()
                                        coil3.SingletonImageLoader.get(context).enqueue(req)
                                    }
                                }

                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Fixed(3),
                                    userScrollEnabled = !isSwipingPage,
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomListPadding),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(
                                        items = list,
                                        key = { _, card ->
                                            val id = when (card) {
                                                is ResultCached -> card.id
                                                is DownloadFragment.DownloadDataLoaded -> card.id
                                                else -> card.hashCode()
                                            }
                                            if (isBento3x3) "bento_novel_$id" else "normal_novel_$id"
                                        },
                                        span = { index, _ ->
                                            val spanSize = if (isBento3x3) {
                                                when (index % 7) {
                                                    0, 5 -> 2
                                                    else -> 1
                                                }
                                            } else 1
                                            GridItemSpan(spanSize)
                                        },
                                        contentType = { _, card ->
                                            when (card) {
                                                is ResultCached -> "cached_grid"
                                                is DownloadFragment.DownloadDataLoaded -> "loaded_grid"
                                                else -> "generic_grid"
                                            }
                                        }
                                    ) { index, card ->
                                        val spanSize = if (isBento3x3) {
                                            when (index % 7) {
                                                0, 5 -> 2
                                                else -> 1
                                            }
                                        } else 1
                                        val cardId = remember(card) {
                                            when (card) {
                                                is ResultCached -> card.id
                                                is DownloadFragment.DownloadDataLoaded -> card.id
                                                else -> -1
                                            }
                                        }
                                        val isSelectedState = remember(cardId) {
                                            derivedStateOf { selectedNovels.contains(cardId) }
                                        }
                                        val isSelected = isSelectedState.value
                                        val currentOnClick = remember<() -> Unit>(card, isSelectionMode, isSelected, onBookClick, onBookClickLoaded) {
                                            {
                                                if (isSelectionMode) {
                                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                                    if (isSelected) {
                                                        selectedNovels.remove(cardId)
                                                    } else {
                                                        selectedNovels.add(cardId)
                                                    }
                                                } else {
                                                    if (card is ResultCached) onBookClick(card)
                                                    else if (card is DownloadFragment.DownloadDataLoaded) onBookClickLoaded(card)
                                                    else {}
                                                }
                                                Unit
                                            }
                                        }
                                        val currentOnLongClick = remember<() -> Unit>(card, isSelectionMode, isSelected, onBookLongClick, onBookLongClickLoaded) {
                                            {
                                                if (isSelectionMode) {
                                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                                    if (isSelected) {
                                                        selectedNovels.remove(cardId)
                                                    } else {
                                                        selectedNovels.add(cardId)
                                                    }
                                                } else {
                                                    if (card is ResultCached) onBookLongClick(card)
                                                    else if (card is DownloadFragment.DownloadDataLoaded) onBookLongClickLoaded(card)
                                                    else {}
                                                }
                                                Unit
                                            }
                                        }
                                        GridCardItem(
                                            card = card,
                                            isBento = isBento3x3,
                                            span = spanSize,
                                            isTactileEnabled = isTactileEnabled,
                                            noiseBitmap = noiseBitmap,
                                            isNoiseEnabled = isNoiseEnabled,
                                            onClick = currentOnClick,
                                            onLongClick = currentOnLongClick,
                                            isSelectionMode = isSelectionMode,
                                            isSelected = isSelected,
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                    if (isDownloadsPage) {
                                        item(key = if (isBento3x3) "import_item_bento" else "import_item_normal", span = { GridItemSpan(3) }) {
                                            ImportCardItem(onClick = onImportEpubClick)
                                        }
                                        item(key = if (isBento3x3) "pdf_to_epub_item_bento" else "pdf_to_epub_item_normal", span = { GridItemSpan(3) }) {
                                            ImportCardItem(
                                                title = "PDF to EPUB",
                                                icon = Icons.Default.PictureAsPdf,
                                                onClick = onPdfToEpubClick
                                            )
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

            // Clean, non-glitchy Pill Slider — only shown in Pill Drawer mode
            if (!isSwipeMode) {
                val numTabs = allTabs.size
                if (numTabs > 0) {
                    val sliderWidth = 240.dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 84.dp) // Perfect safe height clearance
                            .width(sliderWidth)
                            .height(72.dp) // Generous, comfortable touch targets
                            .pointerInput(numTabs) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val initialFraction = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val initialTarget = (initialFraction * numTabs).toInt().coerceIn(0, numTabs - 1)
                                        
                                        // Immediately trigger page switch on touch down! Tapping becomes 100% responsive
                                        if (activeTargetPage != initialTarget) {
                                            activeTargetPage = initialTarget
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                            pagerScrollJob[0]?.cancel()
                                            pagerScrollJob[0] = scope.launch {
                                                pagerState.animateScrollToPage(
                                                    page = initialTarget,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                        stiffness = 1200f
                                                    )
                                                )
                                            }
                                        }

                                        // Track any subsequent drag movements
                                        var dragChange: PointerInputChange?
                                        do {
                                            val event = awaitPointerEvent()
                                            dragChange = event.changes.firstOrNull { it.pressed }
                                            if (dragChange != null) {
                                                val fraction = (dragChange.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                val dragTarget = (fraction * numTabs).toInt().coerceIn(0, numTabs - 1)
                                                if (activeTargetPage != dragTarget) {
                                                    activeTargetPage = dragTarget
                                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                                    
                                                    // While actively dragging, animate smoothly to target page
                                                    pagerScrollJob[0]?.cancel()
                                                    pagerScrollJob[0] = scope.launch {
                                                        pagerState.animateScrollToPage(
                                                            page = dragTarget,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = 1200f
                                                            )
                                                        )
                                                    }
                                                }
                                                dragChange.consume()
                                            }
                                        } while (dragChange != null)
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

                        // Traveling capsule — driven purely by real-time pagerState with GPU-accelerated graphicsLayer
                        val segmentWidth = sliderWidth / numTabs
                        val capsuleWidth = 24.dp

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(capsuleWidth)
                                .graphicsLayer {
                                    val segmentWidthPx = segmentWidth.toPx()
                                    val capsuleWidthPx = capsuleWidth.toPx()
                                    val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                    translationX = (segmentWidthPx * (pos + 0.5f)) - (capsuleWidthPx / 2)
                                },
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

            // Multi-Select Action Rail (Vertical Floating Capsule on Right Edge)
            AnimatedVisibility(
                visible = isSelectionMode && selectedNovels.isNotEmpty(),
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                ) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .wrapContentHeight()
                        .glassCard(
                            shape = RoundedCornerShape(24.dp),
                            backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            strokeWidth = 1.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Count Badge
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${selectedNovels.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        // Divider line (using a basic Box for full Compose Material compatibility)
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        )

                        // Change Category button
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                showMultiSelectCategorySheet = true
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = "Move Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Delete button
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                showMultiSelectDeleteDialog = true
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Cancel selection button
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                selectedNovels.clear()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Selection",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
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

    // Modal Bottom Sheet: Multi-Select Category Move
    if (showMultiSelectCategorySheet) {
        val isSheetLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
        val sheetBgColor = if (isSheetLightTheme) Color(0xEEFFFFFF) else {
            val themeKey = settings.getString(context.getString(R.string.theme_key), "Amoled")
            if (themeKey == "Amoled" || themeKey == "Black") Color(0xEE000000) else Color(0xEE121215)
        }

        ModalBottomSheet(
            onDismissRequest = { showMultiSelectCategorySheet = false },
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
                    text = "Move Selected to Category",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories, key = { it.id }) { categoryItem ->
                        val categoryName = if (categoryItem.isSystem && categoryItem.stringRes != null) context.getString(categoryItem.stringRes) else categoryItem.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    viewModel.changeCategoryMultiple(selectedNovels.toList(), categoryItem.id)
                                    selectedNovels.clear()
                                    isSelectionMode = false
                                    showMultiSelectCategorySheet = false
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }
    }

    // Alert Dialog: Multi-Select Delete Confirmation
    if (showMultiSelectDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showMultiSelectDeleteDialog = false },
            title = { Text("Delete Novels") },
            text = { Text("Are you sure you want to permanently delete the ${selectedNovels.size} selected novels?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        viewModel.deleteMultiple(selectedNovels.toList())
                        selectedNovels.clear()
                        isSelectionMode = false
                        showMultiSelectDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiSelectDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun rememberPreferenceBoolean(key: String, defaultValue: Boolean): State<Boolean> {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val state = remember(key) { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
    val listener = remember(key, prefs) {
        SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) {
                state.value = prefs.getBoolean(key, defaultValue)
            }
        }
    }
    DisposableEffect(prefs, key, listener) {
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
    val state = remember(key) { mutableStateOf(prefs.getString(key, defaultValue) ?: defaultValue) }
    val listener = remember(key, prefs) {
        SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) {
                state.value = prefs.getString(key, defaultValue) ?: defaultValue
            }
        }
    }
    DisposableEffect(prefs, key, listener) {
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

    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tactileScale"
    )

    val rotateXState = animateFloatAsState(
        targetValue = if (isPressed) -3f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tactileRotateX"
    )

    val rotateYState = animateFloatAsState(
        targetValue = if (isPressed) 3f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tactileRotateY"
    )

    return this.graphicsLayer {
        this.scaleX = scaleState.value
        this.scaleY = scaleState.value
        this.rotationX = rotateXState.value
        this.rotationY = rotateYState.value
        this.cameraDistance = 12f * density
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridCardItem(
    card: Any,
    isBento: Boolean,
    span: Int = 1,
    isTactileEnabled: Boolean,
    noiseBitmap: ImageBitmap?,
    isNoiseEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
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

    val diffCount = remember(card, com.lagradost.quicknovel.DataStore.mutationCounter.value) {
        if (card is DownloadFragment.DownloadDataLoaded) {
            val realReadCount = card.lastChapterRead
            (card.downloadedCount - realReadCount).coerceAtLeast(0).toInt()
        } else {
            0
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

    // ─── Cover Aura Glow ─────────────────────────────────────────────────────
    val isAuraEnabled = rememberAuraEnabled()
    var auraColor by remember(posterUrl) { mutableStateOf(Color.Unspecified) }

    if (isAuraEnabled) {
        LaunchedEffect(card) {
            // Resolve the bitmap for palette extraction via Coil's cache (IO-safe)
            val cacheKey = posterUrl?.toString() ?: return@LaunchedEffect
            if (cacheKey.isBlank()) return@LaunchedEffect
            try {
                val loader = coil3.SingletonImageLoader.get(context)
                // Use buildImageRequest to inherit local cache path resolution & custom header logic
                val req = com.lagradost.quicknovel.ui.theme.buildImageRequest(context, card).newBuilder(context)
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

    Column(
        modifier = modifier.fillMaxWidth()
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
                .padding(8.dp) // padded outer box to let the aura glow bleed out without clipping
                .coverAuraGlow(auraColor = auraColor, enabled = isAuraEnabled)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .tactileResponse(isTactileEnabled, interactionSource)
                    .glassCard(shape = RoundedCornerShape(12.dp), strokeWidth = 0.5.dp)
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
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(12.dp)
                    }
                    .noiseTextureOverlay(noiseBitmap = noiseBitmap, enabled = isNoiseEnabled)
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

            if (isSelectionMode) {
                val strokeColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val overlayColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayColor, shape = RoundedCornerShape(12.dp))
                        .border(
                            width = 3.dp,
                            color = strokeColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x66000000),
                        )
                        .border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
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
    isTactileEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
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

    val diffCount = remember(card, com.lagradost.quicknovel.DataStore.mutationCounter.value) {
        if (card is DownloadFragment.DownloadDataLoaded) {
            val realReadCount = card.lastChapterRead
            (card.downloadedCount - realReadCount).coerceAtLeast(0).toInt()
        } else {
            0
        }
    }

    Row(
        modifier = modifier
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
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(8.dp)
                }
                .clickable {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    onLongClick()
                }
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

            if (card is DownloadFragment.DownloadDataLoaded && (card.generating || card.state == DownloadState.IsPending)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    com.lagradost.quicknovel.ui.theme.LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                }
            }

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

        // Titles and reading progress status
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

            val counts = remember(card, com.lagradost.quicknovel.DataStore.mutationCounter.value) {
                when (card) {
                    is ResultCached -> {
                        card.lastChapterRead to card.currentTotalChapters
                    }
                    is DownloadFragment.DownloadDataLoaded -> {
                        card.lastChapterRead to card.downloadedTotal.toInt()
                    }
                    else -> 0 to 0
                }
            }
            val (readCount, totalCount) = counts

            Spacer(modifier = Modifier.height(4.dp))

            val progressTextToShow = remember(readCount, totalCount) {
                if (totalCount > 0) {
                    "$readCount / $totalCount chapters read"
                } else if (readCount > 0) {
                    "$readCount chapters read"
                } else {
                    "Not started reading"
                }
            }

            Text(
                text = progressTextToShow,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (card is DownloadFragment.DownloadDataLoaded && progressText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                val badgeColor = when (card.state) {
                    DownloadState.IsDownloading -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    DownloadState.IsPaused, DownloadState.IsStopped -> Color(0xFFFFB300).copy(alpha = 0.12f)
                    DownloadState.IsDone -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                }

                val textTint = when (card.state) {
                    DownloadState.IsDownloading -> MaterialTheme.colorScheme.primary
                    DownloadState.IsPaused, DownloadState.IsStopped -> Color(0xFFFFB300)
                    DownloadState.IsDone -> Color(0xFF4CAF50)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
                }

                Box(
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Downloaded: $progressText",
                        style = MaterialTheme.typography.labelSmall,
                        color = textTint,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Reading progress indicator
            val progressFraction = remember(readCount, totalCount) {
                if (totalCount > 0) readCount.toFloat() / totalCount.toFloat() else 0.0f
            }
            if (progressFraction > 0f) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            }

            // Downloading status progress bar (secondary color)
            if (card is DownloadFragment.DownloadDataLoaded) {
                val showProgressbar = card.generating || (card.downloadedCount < card.downloadedTotal)
                if (showProgressbar) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (card.generating) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        val dlProgress = if (card.downloadedTotal > 0) card.downloadedCount.toFloat() / card.downloadedTotal.toFloat() else 0.0f
                        LinearProgressIndicator(
                            progress = { dlProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Action Buttons (Pause / Resume / Delete)
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else if (card is DownloadFragment.DownloadDataLoaded) {
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
                if (card.generating || realState == DownloadState.IsPending) {
                    Box(
                        modifier = Modifier
                            .size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        com.lagradost.quicknovel.ui.theme.LoadingIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        when (realState) {
                            DownloadState.IsDownloading -> onPauseClick()
                            DownloadState.IsPaused -> onResumeClick()
                            DownloadState.IsStopped -> onResumeClick()
                            DownloadState.IsFailed -> onResumeClick()
                            DownloadState.IsPending -> {}
                            DownloadState.IsDone -> onRefreshClick()
                            else -> onRefreshClick()
                        }
                    }) {
                        Icon(
                            imageVector = iconRes,
                            contentDescription = "Status action",
                            tint = if (realState == DownloadState.IsDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Delete Trash Alert
                IconButton(onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    onDeleteClick()
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else if (card is ResultCached) {
            IconButton(onClick = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onDeleteClick()
            }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete bookmark",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ImportCardItem(
    title: String = "Import EPUB / PDF",
    icon: ImageVector = Icons.Default.AddCircle,
    onClick: () -> Unit
) {
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
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

/**
 * Custom touch gesture lock that prioritizes horizontal category switching swipes
 * over vertical novel list scrolling, preventing horizontal/vertical gesture fighting.
 */
@Composable
private fun Modifier.lockGesturePriority(
    enabled: Boolean,
    onHorizontalDragDetected: () -> Unit,
    onVerticalDragDetected: () -> Unit,
    onRelease: () -> Unit
): Modifier {
    if (!enabled) return this
    val viewConfiguration = LocalViewConfiguration.current
    val touchSlop = viewConfiguration.touchSlop
    
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false)
                var accumX = 0f
                var accumY = 0f
                var locked = false
                
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val changes = event.changes
                    var anyPositionChanged = false
                    
                    for (change in changes) {
                        if (change.pressed && change.previousPressed) {
                            val dragAmount = change.position - change.previousPosition
                            accumX += dragAmount.x
                            accumY += dragAmount.y
                            
                            if (!locked) {
                                val absX = abs(accumX)
                                val absY = abs(accumY)
                                if (absX > touchSlop || absY > touchSlop) {
                                    locked = true
                                    if (absX > absY) {
                                        onHorizontalDragDetected()
                                    } else {
                                        onVerticalDragDetected()
                                    }
                                }
                            }
                            anyPositionChanged = true
                        }
                    }
                } while (anyPositionChanged && changes.any { it.pressed })
                
                onRelease()
            }
        }
    }
}

@Composable
fun NeoShelfPageContentEmpty() {
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
                imageVector = Icons.Default.Inventory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your NeoShelf is clear!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Read, complete, or update books to see auto-shelves.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun NeoShelfCardItem(
    card: DownloadFragment.DownloadDataLoaded,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title = card.name

    Column(
        modifier = modifier
            .width(110.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(160.dp)
                .glassCard(shape = RoundedCornerShape(12.dp), strokeWidth = 0.5.dp)
        ) {
            AsyncImage(
                model = rememberImageRequest(data = card),
                contentDescription = title,
                imageLoader = SingletonImageLoader.get(context),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(12.dp)
                    }
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun NeoShelfPageContent(
    shelves: List<NeoShelfEngine.NeoShelf>,
    isCompact: Boolean,
    isBento3x3: Boolean,
    bottomListPadding: androidx.compose.ui.unit.Dp,
    onBookClickLoaded: (DownloadFragment.DownloadDataLoaded) -> Unit,
    onBookClick: (ResultCached) -> Unit,
    onBookLongClickLoaded: (DownloadFragment.DownloadDataLoaded) -> Unit,
    modifier: Modifier = Modifier
) {
    if (shelves.isEmpty()) {
        NeoShelfPageContentEmpty()
        return
    }

    // Keep track of which shelf indexes are expanded. Default to all expanded.
    val expandedStates = remember { mutableStateMapOf<String, Boolean>().apply {
        shelves.forEach { put(it.title, true) }
    } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomListPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(shelves, key = { it.title }) { shelf ->
            val isExpanded = expandedStates[shelf.title] ?: true
            
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedStates[shelf.title] = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val icon = when (shelf.title) {
                        "Currently Reading" -> Icons.Default.MenuBook
                        "Almost Done" -> Icons.Default.Star
                        "Long Abandoned" -> Icons.Default.HourglassEmpty
                        "Completed Recently" -> Icons.Default.CheckCircle
                        "Worth Revisiting" -> Icons.Default.History
                        else -> Icons.Default.FolderOpen
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = shelf.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    // Count Badge
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = shelf.items.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shelf.items, key = { it.id }) { card ->
                        NeoShelfCardItem(
                            card = card,
                            onClick = { onBookClickLoaded(card) },
                            onLongClick = { onBookLongClickLoaded(card) }
                        )
                    }
                }
            }
        }
    }
}

