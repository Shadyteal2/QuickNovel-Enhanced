package com.lagradost.quicknovel.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import coil3.request.crossfade
import com.lagradost.quicknovel.HomePageList
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.OnGoingSearch
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.ui.home.BrowseAdapter
import com.lagradost.quicknovel.ui.home.HomeViewModel
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberImageRequest
import com.lagradost.quicknovel.util.KineticTiltHelper

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

    val settingsManager = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val isAdvancedSearch by remember { mutableStateOf(settingsManager.getBoolean("advanced_search", true)) }

    var searchQuery by rememberSaveable { mutableStateOf(viewModel.lastSearchQuery) }
    var isProviderGrid by rememberSaveable { mutableStateOf(false) }

    val hasResults = searchResponse != null || currentSearch != null

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
                showGridToggle = !hasResults
            )

            if (!hasResults) {
                HomeProvidersGrid(
                    apis = homeApis,
                    isGrid = isProviderGrid,
                    onProviderClick = onProviderClick
                )
            } else {
                if (isAdvancedSearch) {
                    currentSearch?.let { list ->
                        val validList = list.map {
                            HomePageList(
                                it.apiName,
                                if (it.data is Resource.Success) it.data.value else emptyList()
                            )
                        }
                        AdvancedSearchLayout(
                            providers = validList,
                            onBookClick = onBookClick,
                            onBookLongClick = onBookLongClick,
                            onMoreClick = onAdvancedProviderMoreClick
                        )
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
    showGridToggle: Boolean
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
                    .focusRequester(focusRequester),
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
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
            api.pluginContext!!.getDrawable(api.iconId!!) ?: R.drawable.ic_baseline_code_24
        } else {
            val resId = BrowseAdapter.resolveIcon(context, api.name)
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

    // Prefetch cover images of the upcoming 12 novels as the user scrolls
    LaunchedEffect(gridState.firstVisibleItemIndex, results) {
        val totalItems = results.size
        val startIndex = (gridState.firstVisibleItemIndex + 15).coerceAtMost(totalItems)
        val endIndex = (startIndex + 12).coerceAtMost(totalItems)
        for (i in startIndex until endIndex) {
            val card = results.getOrNull(i) ?: continue
            val req = com.lagradost.quicknovel.ui.theme.buildImageRequest(context, card)
            coil3.SingletonImageLoader.get(context).enqueue(req)
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(results, key = { it.url }) { novel ->
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

@Composable
fun ProviderSearchResultsRow(
    provider: HomePageList,
    onBookClick: (SearchResponse) -> Unit,
    onBookLongClick: (SearchResponse) -> Unit,
    onMoreClick: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Prefetch cover images of the upcoming 8 novels as the user scrolls
    LaunchedEffect(listState.firstVisibleItemIndex, provider.list) {
        val totalItems = provider.list.size
        val startIndex = (listState.firstVisibleItemIndex + 6).coerceAtMost(totalItems)
        val endIndex = (startIndex + 8).coerceAtMost(totalItems)
        for (i in startIndex until endIndex) {
            val card = provider.list.getOrNull(i) ?: continue
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

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                },
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(provider.list, key = { it.url }) { novel ->
                SearchNovelCard(
                    novel = novel,
                    onClick = { onBookClick(novel) },
                    onLongClick = { onBookLongClick(novel) }
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
