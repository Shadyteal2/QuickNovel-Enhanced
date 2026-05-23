package com.lagradost.quicknovel.ui.mainpage

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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

// Spring scaling press effect for high-fidelity micro-animations
fun Modifier.springScalePress(): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "spring_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
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
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Observe settings and backgrounds
    val settings = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val imageUri = remember(settings) { settings.getString(context.getString(R.string.background_image_key), null) }
    val hasBackground = !imageUri.isNullOrBlank()
    val containerColor = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background

    // Observe ViewModel LiveData
    val currentCardsState = viewModel.currentCards.observeAsState(initial = Resource.Loading())
    val currentMainCategory by viewModel.currentMainCategory.observeAsState(initial = null)
    val currentOrderBy by viewModel.currentOrderBy.observeAsState(initial = null)
    val currentTag by viewModel.currentTag.observeAsState(initial = null)
    val loadingMoreItems by viewModel.loadingMoreItems.observeAsState(initial = false)
    val isInSearch by viewModel.isInSearch.observeAsState(initial = false)

    var searchQuery by remember { mutableStateOf("") }

    QuickNovelTheme {
        Scaffold(
            topBar = {
                // Header with fully custom Search Input & navigation actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isInSearch) {
                                    searchQuery = ""
                                    viewModel.switchToMain()
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Rich Glassmorphism search input
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search $apiName…",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        focusManager.clearFocus()
                                        viewModel.search(searchQuery)
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.02f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            trailingIcon = {
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
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Open in browser button
                        IconButton(
                            onClick = { viewModel.openInBrowser() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_open_in_new_24),
                                contentDescription = "Open in Browser",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Filters Chips (Invisible in search mode)
                    if (!isInSearch) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
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
                                        context.showDialog(
                                            viewModel.api.mainCategories.map { it.first },
                                            viewModel.currentMainCategory.value ?: -1,
                                            context.getString(R.string.filter_dialog_general),
                                            true,
                                            {}
                                        ) { selection ->
                                            viewModel.setMainCategory(selection)
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
                                        context.showDialog(
                                            viewModel.api.tags.map { it.first },
                                            viewModel.currentTag.value ?: -1,
                                            context.getString(R.string.filter_dialog_genre),
                                            true,
                                            {}
                                        ) { selection ->
                                            viewModel.setTag(selection)
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
                                        context.showDialog(
                                            viewModel.api.orderBys.map { it.first },
                                            viewModel.currentOrderBy.value ?: -1,
                                            context.getString(R.string.filter_dialog_order_by),
                                            true,
                                            {}
                                        ) { selection ->
                                            viewModel.setOrderBy(selection)
                                        }
                                    },
                                    label = { Text(currentOrderLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = containerColor,
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Render list contents depending on active Resource State
                when (val data = currentCardsState.value) {
                    is Resource.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    is Resource.Failure -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .glassCard(RoundedCornerShape(24.dp))
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_warning_24),
                                    contentDescription = "Error Icon",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = data.errorString,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { viewModel.load(0, currentMainCategory, currentOrderBy, currentTag) }
                                ) {
                                    Text(stringResource(id = R.string.reload_error))
                                }
                            }
                        }
                    }

                    is Resource.Success -> {
                        val response = data.value
                        val items = response.items

                        if (items.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.no_data),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            val gridState = rememberLazyGridState()
                            
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
                            LaunchedEffect(gridState.firstVisibleItemIndex, items) {
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
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = items,
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
                                            CircularProgressIndicator(
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
