package com.lagradost.quicknovel.ui.mainpage

import com.lagradost.quicknovel.ui.theme.LoadingIndicator

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberImageRequest
import com.lagradost.quicknovel.util.SingleSelectionHelper.showDialog
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.lagradost.quicknovel.ui.theme.rememberShimmerBrush
import kotlinx.coroutines.launch

// Spring scaling press effect for high-fidelity micro-animations
fun Modifier.springScalePress(): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "spring_scale"
    )
    this
        .graphicsLayer {
            scaleX = scaleState.value
            scaleY = scaleState.value
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                }
            )
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPageScreen(
    viewModel: MainPageViewModel,
    apiName: String,
    onBack: () -> Unit,
    onNovelClick: (SearchResponse) -> Unit,
    onNovelLongClick: (SearchResponse) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPullRefreshing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Hoist LazyGridState to monitor scroll offsets for the collapsing header
    val gridState = rememberLazyGridState()

    // Collapse fraction computed completely outside layout/draw loops to maximize rendering performance on 1GB RAM
    val maxCollapseOffset = 140f // Collapse over 140 pixels of scrolling
    val collapseFractionState = remember {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val scrollOffset = gridState.firstVisibleItemScrollOffset
                (scrollOffset.toFloat() / maxCollapseOffset).coerceIn(0f, 1f)
            }
        }
    }

    // Observe settings and backgrounds
    // Observe ViewModel LiveData
    val currentCardsState = viewModel.currentCards.observeAsState(initial = Resource.Loading())
    val currentMainCategory by viewModel.currentMainCategory.observeAsState(initial = null)
    val currentOrderBy by viewModel.currentOrderBy.observeAsState(initial = null)
    val currentTag by viewModel.currentTag.observeAsState(initial = null)
    val loadingMoreItems by viewModel.loadingMoreItems.observeAsState(initial = false)
    val isInSearch by viewModel.isInSearch.observeAsState(initial = false)
    val isStale by viewModel.isStale.observeAsState(initial = false)
    var showFilters by remember { mutableStateOf(true) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val hasFilters = remember(viewModel.api) {
        viewModel.api.mainCategories.isNotEmpty() ||
        viewModel.api.tags.isNotEmpty() ||
        viewModel.api.orderBys.isNotEmpty()
    }

    var searchQuery by remember { mutableStateOf("") }

    QuickNovelTheme {
        val settings = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
        val imageUri = remember(settings) { settings.getString(context.getString(R.string.background_image_key), null) }
        val hasBackground = !imageUri.isNullOrBlank()
        val containerColor = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background

        Scaffold(
            containerColor = containerColor,
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            // Use an overlap Box where the scrolling library novels list slides seamlessly beneath the floating collapsing header
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding()) // Only pad bottom (for navigation bar if any)
            ) {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isPullRefreshing,
                    onRefresh = {
                        scope.launch {
                            isPullRefreshing = true
                            viewModel.load(0, currentMainCategory, currentOrderBy, currentTag, isUserRefresh = true).join()
                            isPullRefreshing = false
                        }
                    },
                    state = pullState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Render list contents depending on active Resource State
                    when (val data = currentCardsState.value) {
                    is Resource.Loading -> {
                        MainPageShimmerSkeleton(isLandscape = isLandscape)
                    }

                    is Resource.Failure -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 175.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .glassCard(RoundedCornerShape(24.dp))
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_warning_24),
                                    contentDescription = "Error Icon",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "Provider Connection Failed",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                val friendlyMessage = remember(data.errorString) {
                                    if (data.errorString.contains("NullPointerException") || data.errorString.contains("IndexOutOfBoundsException")) {
                                        "The provider's website layout might have changed, or a parser update is required."
                                    } else if (data.errorString.contains("Timeout") || data.errorString.contains("SocketTimeoutException")) {
                                        "The request timed out. The provider's server might be under heavy load or blocked by Cloudflare."
                                    } else if (data.errorString.contains("connect") || data.errorString.contains("Host")) {
                                        "Cannot connect to the server. Please check your internet connection or try again later."
                                    } else {
                                        "An unexpected error occurred while parsing the provider content."
                                    }
                                }
                                
                                Text(
                                    text = friendlyMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                var showDetails by remember { mutableStateOf(false) }
                                Text(
                                    text = if (showDetails) "Hide technical details" else "Show technical details",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { showDetails = !showDetails }
                                        .padding(8.dp)
                                )
                                
                                if (showDetails) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 120.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                            item {
                                                Text(
                                                    text = data.errorString,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { viewModel.load(0, currentMainCategory, currentOrderBy, currentTag) },
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(stringResource(id = R.string.reload_error))
                                }
                            }
                        }
                    }

                    is Resource.Success -> {
                        val response = data.value
                        val items = response.items
                        val uniqueItems = remember(response.id) { items.distinctBy { it.url } }

                        if (items.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 175.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.no_data),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            // Scroll listener for Infinite Pagination
                            val shouldLoadMore = remember {
                                derivedStateOf {
                                    val layoutInfo = gridState.layoutInfo
                                    val totalItemsCount = layoutInfo.totalItemsCount
                                    val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    lastVisibleItemIndex >= totalItemsCount - 6
                                }
                            }
                            
                            LaunchedEffect(shouldLoadMore.value) {
                                if (shouldLoadMore.value && !loadingMoreItems && !isInSearch) {
                                    viewModel.load(null, currentMainCategory, currentOrderBy, currentTag)
                                }
                            }

                            // Dynamic cover prefetching during scrolls
                            LaunchedEffect(gridState.firstVisibleItemIndex, response.id) {
                                val totalItems = items.size
                                val startIndex = (gridState.firstVisibleItemIndex + 12).coerceAtMost(totalItems)
                                val endIndex = (startIndex + 15).coerceAtMost(totalItems)
                                for (i in startIndex until endIndex) {
                                    val card = items.getOrNull(i) ?: continue
                                    val req = com.lagradost.quicknovel.ui.theme.buildImageRequest(context, card)
                                    coil3.SingletonImageLoader.get(context).enqueue(req)
                                }
                            }

                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(if (isLandscape) 6 else 3),
                                contentPadding = PaddingValues(top = 175.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = uniqueItems,
                                    key = { item -> item.url }
                                ) { item ->
                                    ProviderNovelGridCard(
                                        item = item,
                                        onClick = { onNovelClick(item) },
                                        onLongClick = { onNovelLongClick(item) }
                                    )
                                }

                                // Bottom pagination spinner loading state
                                if (loadingMoreItems) {
                                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            LoadingIndicator(
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

                // ─── Floating Collapsing Header (Overlay on Top) ──────────────────
                val collapseFraction = collapseFractionState.value
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.background.copy(alpha = if (hasBackground) 0.8f else 1f)
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Back Button
                            IconButton(
                                onClick = {
                                    if (isInSearch) {
                                        searchQuery = ""
                                        viewModel.switchToMain()
                                    } else {
                                        onBack()
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(48.dp)
                                    .graphicsLayer {
                                        alpha = 1f - collapseFraction
                                        translationX = -56.dp.toPx() * collapseFraction
                                    }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Expanding Search Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        // Shift up slightly during collapse for a tight sticky header feel
                                        translationY = -4.dp.toPx() * collapseFraction
                                    }
                                    .layout { measurable, constraints ->
                                        // Animate padding from 56.dp (when expanded) to 0.dp (when collapsed) completely off the main thread
                                        val paddingPx = (56.dp.toPx() * (1f - collapseFraction)).toInt()
                                        val targetMinWidth = (constraints.minWidth - paddingPx * 2).coerceAtLeast(0)
                                        val targetMaxWidth = (constraints.maxWidth - paddingPx * 2).coerceAtLeast(targetMinWidth)
                                        val childConstraints = constraints.copy(
                                            minWidth = targetMinWidth,
                                            maxWidth = targetMaxWidth
                                        )
                                        val placeable = measurable.measure(childConstraints)
                                        layout(constraints.maxWidth, constraints.maxHeight) {
                                            placeable.place(paddingPx, 0)
                                        }
                                    }
                            ) {
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            if (searchQuery.isNotBlank()) {
                                                focusManager.clearFocus()
                                                viewModel.search(searchQuery)
                                                viewModel.addToHistory(searchQuery, apiName)
                                            }
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .onFocusChanged { isSearchFocused = it.isFocused },
                                    decorationBox = { innerTextField ->
                                        OutlinedTextFieldDefaults.DecorationBox(
                                            value = searchQuery,
                                            innerTextField = innerTextField,
                                            enabled = true,
                                            singleLine = true,
                                            visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                                            interactionSource = remember { MutableInteractionSource() },
                                            placeholder = {
                                                Text(
                                                    text = "Search $apiName…",
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            trailingIcon = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(end = 4.dp)
                                                ) {
                                                    if (hasFilters) {
                                                        IconButton(
                                                            onClick = { showFilters = !showFilters }
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(id = R.drawable.ic_baseline_filter_list_24),
                                                                contentDescription = "Toggle Filters",
                                                                tint = if (showFilters) {
                                                                    MaterialTheme.colorScheme.primary
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                                }
                                                            )
                                                        }
                                                    }
                                                    if (searchQuery.isNotEmpty()) {
                                                        IconButton(
                                                            onClick = {
                                                                searchQuery = ""
                                                                viewModel.switchToMain()
                                                            }
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(id = R.drawable.ic_sharp_clear_24),
                                                                contentDescription = "Clear Search",
                                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                            )
                                                        }
                                                    } else {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_baseline_search_24),
                                                            contentDescription = "Search icon",
                                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                            modifier = Modifier.size(20.dp).padding(end = 8.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.02f)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                            container = {
                                                OutlinedTextFieldDefaults.ContainerBox(
                                                    enabled = true,
                                                    isError = false,
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
                                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.02f)
                                                    ),
                                                    shape = RoundedCornerShape(24.dp),
                                                    focusedBorderThickness = 1.dp,
                                                    unfocusedBorderThickness = 1.dp
                                                )
                                            }
                                        )
                                    }
                                )
                            }

                            // Open in Browser Button
                            IconButton(
                                onClick = { viewModel.openInBrowser() },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(48.dp)
                                    .graphicsLayer {
                                        alpha = 1f - collapseFraction
                                        translationX = 56.dp.toPx() * collapseFraction
                                    }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_open_in_new_24),
                                    contentDescription = "Open in Browser",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Search History Chips Row
                        val historyList by viewModel.searchHistory.observeAsState(initial = emptyList())
                        AnimatedVisibility(
                            visible = isSearchFocused && searchQuery.isEmpty() && historyList.isNotEmpty() && collapseFraction < 0.5f,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_history_24),
                                    contentDescription = "Search History",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                                historyList.forEach { historyQuery ->
                                    InputChip(
                                        selected = false,
                                        onClick = {
                                            searchQuery = historyQuery
                                            viewModel.search(historyQuery)
                                            viewModel.addToHistory(historyQuery, apiName)
                                            focusManager.clearFocus()
                                        },
                                        label = {
                                            Text(
                                                text = historyQuery,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_sharp_clear_24),
                                                contentDescription = "Delete",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable {
                                                        viewModel.removeFromHistory(historyQuery, apiName)
                                                    },
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = InputChipDefaults.inputChipBorder(
                                            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                            enabled = true,
                                            selected = false
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                        }

                        // Collapsing Genre Filter Chips
                        AnimatedVisibility(
                            visible = showFilters,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = 1f - collapseFraction
                                        translationY = -20.dp.toPx() * collapseFraction
                                    }
                                    .layout { measurable, constraints ->
                                        // Animate height to 0 during collapse to seamlessly shift grid items upwards
                                        val height = (measurable.minIntrinsicHeight(constraints.maxWidth) * (1f - collapseFraction)).toInt()
                                        val targetMinHeight = constraints.minHeight.coerceAtMost(height)
                                        val placeable = measurable.measure(
                                            constraints.copy(
                                                minHeight = targetMinHeight,
                                                maxHeight = height
                                            )
                                        )
                                        layout(constraints.maxWidth, height) {
                                            placeable.place(0, 0)
                                        }
                                    },
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Category Filter
                                if (viewModel.api.mainCategories.isNotEmpty()) {
                                    val currentCategoryLabel = currentMainCategory?.let {
                                        viewModel.api.mainCategories.getOrNull(it)?.first
                                    } ?: "All Categories"
                                    
                                    FilterChip(
                                        selected = currentMainCategory != null,
                                        onClick = {
                                            if (collapseFraction < 0.5f) { // Disable clicks when collapsed/invisible
                                                context.showDialog(
                                                    viewModel.api.mainCategories.map { it.first },
                                                    viewModel.currentMainCategory.value ?: -1,
                                                    context.getString(R.string.filter_dialog_general),
                                                    true,
                                                    {}
                                                ) { selection ->
                                                    viewModel.setMainCategory(selection)
                                                }
                                            }
                                        },
                                        label = { Text(currentCategoryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }

                                // Genre Filter
                                if (viewModel.api.tags.isNotEmpty()) {
                                    val currentTagLabel = currentTag?.let {
                                        viewModel.api.tags.getOrNull(it)?.first
                                    } ?: "All Genres"

                                    FilterChip(
                                        selected = currentTag != null,
                                        onClick = {
                                            if (collapseFraction < 0.5f) {
                                                context.showDialog(
                                                    viewModel.api.tags.map { it.first },
                                                    viewModel.currentTag.value ?: -1,
                                                    context.getString(R.string.filter_dialog_genre),
                                                    true,
                                                    {}
                                                ) { selection ->
                                                    viewModel.setTag(selection)
                                                }
                                            }
                                        },
                                        label = { Text(currentTagLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }

                                // Order By Filter
                                if (viewModel.api.orderBys.isNotEmpty()) {
                                    val currentOrderLabel = currentOrderBy?.let {
                                        viewModel.api.orderBys.getOrNull(it)?.first
                                    } ?: "Order By"

                                    FilterChip(
                                        selected = currentOrderBy != null,
                                        onClick = {
                                            if (collapseFraction < 0.5f) {
                                                context.showDialog(
                                                    viewModel.api.orderBys.map { it.first },
                                                    viewModel.currentOrderBy.value ?: -1,
                                                    context.getString(R.string.filter_dialog_order_by),
                                                    true,
                                                    {}
                                                ) { selection ->
                                                    viewModel.setOrderBy(selection)
                                                }
                                            }
                                        },
                                        label = { Text(currentOrderLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isStale,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 180.dp)
                ) {
                    SuggestionChip(
                        onClick = { },
                        label = { Text("Showing cached results", fontSize = 12.sp) },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_warning_24),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProviderNovelGridCard(
    item: SearchResponse,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .springScalePress()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .glassCard(shape = RoundedCornerShape(12.dp), strokeWidth = 0.5.dp)
        ) {
            AsyncImage(
                model = rememberImageRequest(data = item),
                contentDescription = item.name,
                imageLoader = SingletonImageLoader.get(LocalContext.current),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                lineHeight = 16.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun MainPageShimmerSkeleton(isLandscape: Boolean) {
    val brush = rememberShimmerBrush()

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 6 else 3),
        contentPadding = PaddingValues(top = 175.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize()
    ) {
        items(if (isLandscape) 12 else 9) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.66f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
        }
    }
}
