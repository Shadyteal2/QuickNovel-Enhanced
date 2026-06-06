package com.lagradost.quicknovel.ui.foryou

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import androidx.compose.ui.graphics.luminance
import com.lagradost.quicknovel.R
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import com.lagradost.quicknovel.ui.foryou.recommendation.Recommendation
import com.lagradost.quicknovel.ui.foryou.recommendation.RecommendationGroup
import com.lagradost.quicknovel.ui.foryou.recommendation.TagAffinity
import com.lagradost.quicknovel.ui.foryou.recommendation.TagCategory
import com.lagradost.quicknovel.ui.foryou.recommendation.UserTasteProfile
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberImageRequest
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import kotlin.math.absoluteValue


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForYouScreen(
    viewModel: ForYouViewModel,
    onBookClick: (url: String, apiName: String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.profile.observeAsState(UserTasteProfile.EMPTY)
    val recommendations by viewModel.recommendations.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val stats by viewModel.stats.observeAsState(Pair(0, 0))

    val settings = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val imageUri = remember(settings) { settings.getString(context.getString(R.string.background_image_key), null) }
    val hasBackground = !imageUri.isNullOrBlank()
    val containerColor = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background

    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = containerColor
    ) {
        if (!profile.isWizardComplete) {
            WizardScreen(
                profile = profile,
                onTagsSelected = { selected ->
                    val affinities = selected.map { TagAffinity(it, 1.0f, 1.0f) }
                    viewModel.saveProfile(profile.copy(preferredTags = affinities))
                },
                onComplete = { viewModel.markWizardComplete() }
            )
        } else {
            RecommendationsContent(
                groups = recommendations,
                stats = stats,
                isLoading = isLoading,
                onBookClick = onBookClick,
                onRefresh = onRefresh
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardScreen(
    profile: UserTasteProfile,
    onTagsSelected: (Set<TagCategory>) -> Unit,
    onComplete: () -> Unit
) {
    var selectedTags by remember { 
        mutableStateOf(profile.preferredTags.map { it.tag }.toSet()) 
    }

    val view = LocalView.current
    val gridState = rememberLazyGridState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "What do you like to read?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Select at least 3 topics to get personalized recommendations.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(
                items = TagCategory.entries.toList(),
                key = { tag -> tag.name }
            ) { tag ->
                val isSelected = selectedTags.contains(tag)
                val primaryColor = MaterialTheme.colorScheme.primary
                
                // Premium theme-adaptive chip text color
                val isPrimaryLight = remember(primaryColor) {
                    (0.299f * primaryColor.red + 0.587f * primaryColor.green + 0.114f * primaryColor.blue) > 0.5f
                }
                
                GlassTagChip(
                    selected = isSelected,
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        val newTags = if (isSelected) selectedTags - tag else selectedTags + tag
                        selectedTags = newTags
                        onTagsSelected(newTags)
                    },
                    label = tag.displayName,
                    primaryColor = primaryColor,
                    isPrimaryLight = isPrimaryLight,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Dynamically compute luminance to ensure premium text contrast (black vs white text on primary background)
        val primaryColor = MaterialTheme.colorScheme.primary
        val isPrimaryLight = remember(primaryColor) {
            (0.299f * primaryColor.red + 0.587f * primaryColor.green + 0.114f * primaryColor.blue) > 0.5f
        }

        Button(
            onClick = onComplete,
            enabled = selectedTags.size >= 3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = if (isPrimaryLight) Color.Black else MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                "Get Started",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun RecommendationsContent(
    groups: List<RecommendationGroup>,
    stats: Pair<Int, Int>,
    isLoading: Boolean,
    onBookClick: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    val carouselItems = remember(groups) {
        groups.flatMap { it.recommendations }
            .distinctBy { it.novel.url }
            .sortedByDescending { it.score }
            .take(5)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp) // Padding for bottom nav
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "For You",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${stats.first} recommendations • ${stats.second} novels indexed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Refresh Button
                FilledTonalIconButton(onClick = onRefresh) {
                    Text("↻") // Simple text icon for refresh, can be replaced with an actual Icon
                }
            }
        }

        if (carouselItems.isNotEmpty()) {
            item {
                FeaturedCarousel(items = carouselItems, onBookClick = onBookClick)
            }
        }

        if (isLoading && groups.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            items(
                items = groups,
                key = { group -> group.title }
            ) { group ->
                RecommendationGroupSection(group = group, onBookClick = onBookClick)
            }
        }
    }
}

@Composable
fun RecommendationGroupSection(
    group: RecommendationGroup,
    onBookClick: (String, String) -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()

    // Tactile haptic tick feedback as new cards snap or scroll into focus horizontally
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .collect { index ->
                if (index > 0) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
            }
    }

    // Prefetch cover images of the upcoming 8 novels as the user scrolls
    LaunchedEffect(lazyListState.firstVisibleItemIndex, group.recommendations) {
        val totalItems = group.recommendations.size
        val startIndex = (lazyListState.firstVisibleItemIndex + 6).coerceAtMost(totalItems)
        val endIndex = (startIndex + 8).coerceAtMost(totalItems)
        for (i in startIndex until endIndex) {
            val card = group.recommendations.getOrNull(i)?.novel ?: continue
            val req = com.lagradost.quicknovel.ui.theme.buildImageRequest(context, card)
            coil3.SingletonImageLoader.get(context).enqueue(req)
        }
    }

    val uniqueRecommendations = remember(group.recommendations) { group.recommendations.distinctBy { it.novel.url } }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            state = lazyListState,
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
            items(
                items = uniqueRecommendations,
                key = { rec -> rec.novel.url }
            ) { rec ->
                NovelCard(recommendation = rec, onBookClick = onBookClick)
            }
        }
    }
}

@Composable
fun NovelCard(
    recommendation: Recommendation,
    onBookClick: (String, String) -> Unit
) {
    val novel = recommendation.novel
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onBookClick(novel.url, novel.apiName) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .glassCard(shape = RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = rememberImageRequest(data = novel),
                contentDescription = novel.name,
                imageLoader = SingletonImageLoader.get(LocalContext.current),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            )
            
            // Score Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .glassCard(shape = RoundedCornerShape(4.dp), strokeWidth = 0.5.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${(recommendation.score * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Text(
            text = novel.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
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
fun GlassTagChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    primaryColor: Color,
    isPrimaryLight: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        primaryColor
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)
    }

    val strokeColor = if (selected) {
        primaryColor.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    }

    val textColor = if (selected) {
        if (isPrimaryLight) Color.Black else MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    }

    Box(
        modifier = modifier
            .glassCard(
                shape = RoundedCornerShape(16.dp),
                backgroundColor = backgroundColor,
                strokeColor = strokeColor
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FeaturedCarousel(
    items: List<Recommendation>,
    onBookClick: (String, String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) { page ->
            val rec = items[page]
            val novel = rec.novel
            
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            
            val scale = 0.9f + (1f - pageOffset.coerceIn(0f, 1f)) * 0.1f
            val alpha = 0.6f + (1f - pageOffset.coerceIn(0f, 1f)) * 0.4f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .glassCard(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                    .clickable { onBookClick(novel.url, novel.apiName) }
            ) {
                // Blurred background
                AsyncImage(
                    model = rememberImageRequest(data = novel),
                    contentDescription = null,
                    imageLoader = SingletonImageLoader.get(LocalContext.current),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(12.dp)
                        .alpha(0.15f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = rememberImageRequest(data = novel),
                        contentDescription = novel.name,
                        imageLoader = SingletonImageLoader.get(LocalContext.current),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(0.66f)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = novel.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = novel.apiName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .glassCard(shape = RoundedCornerShape(6.dp), strokeWidth = 0.5.dp)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Match ${(rec.score * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dots Indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(items.size) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (isSelected) 18.dp else 6.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                    label = "width"
                )
                val color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                }
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

