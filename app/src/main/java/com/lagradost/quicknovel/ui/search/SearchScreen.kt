package com.lagradost.quicknovel.ui.search

import com.lagradost.quicknovel.ui.theme.LoadingIndicator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.carousel.CarouselDefaults
import com.lagradost.quicknovel.ui.theme.rememberHighQualityRequest
import com.lagradost.quicknovel.ui.theme.disallowParentIntercept
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.HomePageList
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.ui.home.HomeViewModel
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberImageRequest
import com.lagradost.quicknovel.ui.theme.rememberShimmerBrush
private val iconCache = HashMap<String, Int>()


fun resolveIcon(context: android.content.Context, providerName: String): Int {
    return iconCache.getOrPut(providerName) {
        val name = providerName.lowercase().replace(" ", "_")
        if (name.contains("wuxiabox")) {
            val id = context.resources.getIdentifier("icon_wuxiabox", "drawable", context.packageName)
            if (id != 0) return@getOrPut id
        }
        
        var id = context.resources.getIdentifier("icon_$name", "drawable", context.packageName)
        if (id == 0) id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id == 0) id = context.resources.getIdentifier("${name}icon", "drawable", context.packageName)
        id
    }
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    homeViewModel: HomeViewModel,
    onOpenProviders: () -> Unit,
    onBookClick: (SearchResponse) -> Unit,
    onBookLongClick: (SearchResponse) -> Unit,
    onProviderClick: (String) -> Unit,
    onAdvancedProviderMoreClick: (HomePageList) -> Unit,
) {
    val context = LocalContext.current
    val searchResponse by viewModel.searchResponse.observeAsState()
    val currentSearch by viewModel.currentSearch.observeAsState()
    val homeApis by homeViewModel.homeApis.observeAsState(emptyList())
    val focusManager = LocalFocusManager.current
    val historyList by viewModel.searchHistory.observeAsState(initial = emptyList())

    val settingsManager = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val isAdvancedSearch by remember { mutableStateOf(settingsManager.getBoolean("advanced_search", true)) }

    var searchQuery by rememberSaveable { mutableStateOf(viewModel.lastSearchQuery) }
    var isProviderGrid by rememberSaveable { mutableStateOf(false) }

    val hasResults = searchResponse != null || currentSearch != null
    var isSearchFocused by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PremiumSearchBar(
                query = searchQuery,
                onQueryChange = { newQuery ->
                    searchQuery = newQuery
                    if (newQuery.isEmpty()) {
                        viewModel.clearSearch()
                    }
                },
                onSearch = { query ->
                    viewModel.search(query)
                },
                onClear = {
                    searchQuery = ""
                    viewModel.clearSearch()
                },
                onFilterClick = onOpenProviders,
                isLoading = searchResponse is Resource.Loading,
                isProviderGrid = isProviderGrid,
                onToggleProviderGrid = { isProviderGrid = !isProviderGrid },
                showGridToggle = !hasResults,
                onFocusChanged = { isSearchFocused = it }
            )

            AnimatedVisibility(
                visible = isSearchFocused && searchQuery.isEmpty() && historyList.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
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
                                            viewModel.removeFromHistory(historyQuery)
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

            if (!hasResults) {
                HomeProvidersGrid(
                    apis = homeApis,
                    isGrid = isProviderGrid,
                    onProviderClick = onProviderClick
                )
            } else {
                if (searchResponse is Resource.Loading && !isAdvancedSearch) {
                    SearchShimmerSkeleton()
                } else if (isAdvancedSearch) {
                    currentSearch?.let { list ->
                        val validList = list.map {
                            HomePageList(
                                it.apiName,
                                if (it.data is Resource.Success) it.data.value else emptyList()
                            )
                        }
                        if (validList.all { it.list.isEmpty() } && searchResponse is Resource.Loading) {
                            SearchShimmerSkeleton()
                        } else {
                            AdvancedSearchLayout(
                                providers = validList,
                                onBookClick = onBookClick,
                                onBookLongClick = onBookLongClick,
                                onMoreClick = onAdvancedProviderMoreClick
                            )
                        }
                    }
                } else {
                    searchResponse?.let { res ->
                        if (res is Resource.Success) {
                            StandardSearchLayout(
                                results = res.value,
                                onBookClick = onBookClick,
                                onBookLongClick = onBookLongClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onFilterClick: () -> Unit,
    isLoading: Boolean,
    isProviderGrid: Boolean,
    onToggleProviderGrid: () -> Unit,
    showGridToggle: Boolean,
    onFocusChanged: (Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .glassCard(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                strokeWidth = 1.dp
            )
            .height(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                placeholder = { Text(stringResource(id = R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSearch(query)
                    focusManager.clearFocus()
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                trailingIcon = {
                    if (isLoading) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        AnimatedVisibility(
                            visible = query.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            IconButton(onClick = {
                                onClear()
                                focusManager.clearFocus()
                            }) {
                                Icon(androidx.compose.material.icons.Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                }
            )
            
            if (showGridToggle) {
                IconButton(
                    onClick = onToggleProviderGrid,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isProviderGrid) androidx.compose.material.icons.Icons.AutoMirrored.Filled.List else androidx.compose.material.icons.Icons.Default.GridView,
                        contentDescription = "Toggle Grid/List View",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(
                onClick = onFilterClick,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_tune_24),
                    contentDescription = stringResource(id = R.string.change_providers_descript),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun HomeProvidersGrid(
    apis: List<MainAPI>,
    isGrid: Boolean,
    onProviderClick: (String) -> Unit
) {
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apis, key = { it.name }) { api ->
                ProviderCard(api = api, isGrid = true, onClick = { onProviderClick(api.name) })
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apis, key = { it.name }) { api ->
                ProviderCard(api = api, isGrid = false, onClick = { onProviderClick(api.name) })
            }
        }
    }
}

@Composable
fun ProviderCard(
    api: MainAPI,
    isGrid: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val iconData = remember(api) {
        if (api.pluginContext != null && api.iconId != null && api.iconId != 0) {
            try {
                api.pluginContext!!.getDrawable(api.iconId!!) ?: R.drawable.ic_baseline_code_24
            } catch (t: Throwable) {
                R.drawable.ic_baseline_code_24
            }
        } else {
            val resId = resolveIcon(context, api.name)
            if (resId != 0) resId
            else if (api.iconId != null && api.iconId != 0) api.iconId!!
            else R.drawable.ic_baseline_code_24
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGrid) Modifier.aspectRatio(1f) else Modifier.height(64.dp))
            .glassCard(shape = RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isGrid) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = rememberImageRequest(data = iconData),
                    contentDescription = api.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    imageLoader = SingletonImageLoader.get(context)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = api.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = rememberImageRequest(data = iconData),
                    contentDescription = api.name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                    imageLoader = SingletonImageLoader.get(context)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = api.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StandardSearchLayout(
    results: List<SearchResponse>,
    onBookClick: (SearchResponse) -> Unit,
    onBookLongClick: (SearchResponse) -> Unit
) {
    val context = LocalContext.current
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Prefetch cover images of the upcoming 12 novels as the user scrolls (debounced to avoid queuing flood during fast flings)
    LaunchedEffect(gridState.firstVisibleItemIndex, results) {
        kotlinx.coroutines.delay(100)
        val totalItems = results.size
        val startIndex = (gridState.firstVisibleItemIndex + 15).coerceAtMost(totalItems)
        val endIndex = (startIndex + 12).coerceAtMost(totalItems)
        for (i in startIndex until endIndex) {
            val card = results.getOrNull(i) ?: continue
            val req = com.lagradost.quicknovel.ui.theme.buildImageRequest(context, card)
            coil3.SingletonImageLoader.get(context).enqueue(req)
        }
    }

    val uniqueResults = remember(results) { results.distinctBy { it.url } }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(uniqueResults, key = { it.url }) { novel ->
            SearchNovelCard(
                novel = novel,
                onClick = { onBookClick(novel) },
                onLongClick = { onBookLongClick(novel) }
            )
        }
    }
}

@Composable
fun AdvancedSearchLayout(
    providers: List<HomePageList>,
    onBookClick: (SearchResponse) -> Unit,
    onBookLongClick: (SearchResponse) -> Unit,
    onMoreClick: (HomePageList) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        items(providers, key = { it.name }) { provider ->
            if (provider.list.isNotEmpty()) {
                ProviderSearchResultsRow(
                    provider = provider,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                    onMoreClick = { onMoreClick(provider) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSearchResultsRow(
    provider: HomePageList,
    onBookClick: (SearchResponse) -> Unit,
    onBookLongClick: (SearchResponse) -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uniqueProviderList = remember(provider.list) { provider.list.distinctBy { it.url } }
    val carouselState = rememberCarouselState { uniqueProviderList.size }

    // Prefetch cover images of upcoming novels as the carousel scrolls (debounced to avoid queuing flood during fast flings)
    LaunchedEffect(carouselState.currentItem, uniqueProviderList) {
        kotlinx.coroutines.delay(100)
        val totalItems = uniqueProviderList.size
        val startIndex = (carouselState.currentItem + 4).coerceAtMost(totalItems)
        val endIndex = (startIndex + 6).coerceAtMost(totalItems)
        for (i in startIndex until endIndex) {
            val card = uniqueProviderList.getOrNull(i) ?: continue
            val req = com.lagradost.quicknovel.ui.theme.buildImageRequest(context, card)
            coil3.SingletonImageLoader.get(context).enqueue(req)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMoreClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "More",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 120.dp,
            itemSpacing = 8.dp,
            flingBehavior = CarouselDefaults.noSnapFlingBehavior(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .pointerInput(Unit) {
                    disallowParentIntercept(view)
                }
        ) { index ->
            val novel = uniqueProviderList[index]
            key(novel.url) {
                SearchNovelCarouselItem(
                    novel = novel,
                    onClick = { onBookClick(novel) },
                    onLongClick = { onBookLongClick(novel) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarouselItemScope.SearchNovelCarouselItem(
    novel: SearchResponse,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .maskClip(RoundedCornerShape(12.dp))
            .glassCard(shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = rememberHighQualityRequest(data = novel, context = LocalContext.current),
            contentDescription = novel.name,
            imageLoader = SingletonImageLoader.get(LocalContext.current),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient scrim + title overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = novel.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = novel.apiName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SearchNovelCard(
    novel: SearchResponse,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .glassCard(shape = RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = rememberImageRequest(data = novel),
                contentDescription = novel.name,
                imageLoader = SingletonImageLoader.get(LocalContext.current),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            )
        }

        Text(
            text = novel.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )

        Text(
            text = novel.apiName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SearchShimmerSkeleton() {
    val brush = rememberShimmerBrush()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false
    ) {
        items(12) {
            Column(modifier = Modifier.width(110.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.68f)
                        .clip(RoundedCornerShape(8.dp))
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
