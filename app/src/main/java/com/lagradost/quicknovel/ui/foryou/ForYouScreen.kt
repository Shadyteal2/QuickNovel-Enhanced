package com.lagradost.quicknovel.ui.foryou

import com.lagradost.quicknovel.ui.theme.LoadingIndicator
import com.lagradost.quicknovel.ui.theme.rememberHighQualityRequest
import com.lagradost.quicknovel.ui.theme.disallowParentIntercept
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.runtime.key

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.foryou.recommendation.Recommendation
import com.lagradost.quicknovel.ui.foryou.recommendation.RecommendationGroup
import com.lagradost.quicknovel.ui.foryou.recommendation.TagAffinity
import com.lagradost.quicknovel.ui.foryou.recommendation.TagCategory
import com.lagradost.quicknovel.ui.foryou.recommendation.UserTasteProfile
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberImageRequest

// ─── For You Screen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForYouScreen(
    viewModel: ForYouViewModel,
    onBookClick: (url: String, apiName: String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val baseProfile by viewModel.baseProfile.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val carouselItems by viewModel.carouselItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    val settings = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val imageUri = remember(settings) { settings.getString(context.getString(R.string.background_image_key), null) }
    val hasBackground = !imageUri.isNullOrBlank()
    val containerColor = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = containerColor
    ) {
        if (!baseProfile.isWizardComplete) {
            WizardScreen(
                profile = baseProfile,
                onTagsSelected = { selected ->
                    val affinities = selected.map { TagAffinity(it, 1.0f, 1.0f) }
                    viewModel.saveProfile(baseProfile.copy(preferredTags = affinities))
                },
                onComplete = { diversity -> viewModel.markWizardComplete(diversity) }
            )
        } else {
            RecommendationsContent(
                groups = recommendations,
                carouselItems = carouselItems,
                stats = stats,
                isLoading = isLoading,
                onBookClick = onBookClick,
                onRefresh = onRefresh,
                onEditTags = {
                    viewModel.saveProfile(baseProfile.copy(isWizardComplete = false))
                },
                onDismissClick = { url -> viewModel.recordInteraction(url, "DISMISS") }
            )
        }
    }
}

// ─── Wizard Screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardScreen(
    profile: UserTasteProfile,
    onTagsSelected: (Set<TagCategory>) -> Unit,
    onComplete: (Float) -> Unit
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
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )

        Text(
            text = "Harem vs Solo MC Preference",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        var sliderValue by remember { mutableStateOf(profile.diversityScore) }

        GlassmorphicSlider(
            valueState = { sliderValue },
            onValueChange = { sliderValue = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
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
            onClick = { onComplete(sliderValue) },
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

// ─── Recommendations Content ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsContent(
    groups: List<RecommendationGroup>,
    carouselItems: List<Recommendation>,
    stats: Pair<Int, Int>,
    isLoading: Boolean,
    onBookClick: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onEditTags: () -> Unit,
    onDismissClick: (String) -> Unit
) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Edit Tags Button
                        FilledTonalIconButton(onClick = onEditTags) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Edit Tags",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Refresh Button
                        FilledTonalIconButton(onClick = onRefresh) {
                            Text("↻")
                        }
                    }
                }

                if (isLoading && groups.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (carouselItems.isNotEmpty()) {
            item {
                FeaturedCarousel(items = carouselItems, onBookClick = onBookClick, onDismissClick = onDismissClick)
            }
        }

        if (isLoading && groups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            items(
                count = groups.size,
                key = { index -> groups[index].title }
            ) { index ->
                RecommendationGroupSection(group = groups[index], onBookClick = onBookClick, onDismissClick = onDismissClick)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeaturedCarousel(
    items: List<Recommendation>,
    onBookClick: (String, String) -> Unit,
    onDismissClick: (String) -> Unit
) {
    val carouselState = rememberCarouselState { items.size }
    val context = LocalContext.current
    val view = LocalView.current
 
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = 300.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(Unit) {
                    disallowParentIntercept(view)
                }
        ) { index ->
            val rec = items[index]
            val novel = rec.novel
            var showMenu by remember { mutableStateOf(false) }
 
            key(novel.url) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .maskClip(RoundedCornerShape(16.dp))
                        .glassCard(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                        .combinedClickable(
                            onClick = { onBookClick(novel.url, novel.apiName) },
                            onLongClick = { showMenu = true }
                        )
                ) {
                    // Dropdown menu styled with glassCard
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.glassCard(shape = RoundedCornerShape(12.dp)),
                        containerColor = Color.Transparent
                    ) {
                        DropdownMenuItem(
                            text = { Text("Not Interested") },
                            onClick = {
                                showMenu = false
                                onDismissClick(novel.url)
                            }
                        )
                    }

                    // Overlay of the trigger book (if present)
                    rec.triggerNovel?.let { trigger ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(36.dp)
                                .glassCard(shape = RoundedCornerShape(6.dp), strokeWidth = 0.5.dp)
                        ) {
                            AsyncImage(
                                model = rememberHighQualityRequest(data = trigger, context = context),
                                contentDescription = trigger.name,
                                imageLoader = SingletonImageLoader.get(context),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                            )
                        }
                    }

                    // Blurred background ambiance
                    AsyncImage(
                        model = rememberHighQualityRequest(data = novel, context = context),
                        contentDescription = null,
                        imageLoader = SingletonImageLoader.get(context),
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
                            model = rememberHighQualityRequest(data = novel, context = context),
                            contentDescription = novel.name,
                            imageLoader = SingletonImageLoader.get(context),
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
        }
    }
}

// ─── Recommendation Group Section (M3 HorizontalMultiBrowseCarousel) ─────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationGroupSection(
    group: RecommendationGroup,
    onBookClick: (String, String) -> Unit,
    onDismissClick: (String) -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
 
    val uniqueRecommendations = remember(group.recommendations) {
        group.recommendations.distinctBy { it.novel.url }
    }
 
    val carouselState = rememberCarouselState { uniqueRecommendations.size }
 
    // Prefetch cover images of upcoming novels as user scrolls (debounced to avoid queuing flood during fast flings)
    LaunchedEffect(carouselState.currentItem, uniqueRecommendations) {
        kotlinx.coroutines.delay(100)
        val totalItems = uniqueRecommendations.size
        val startIndex = (carouselState.currentItem + 4).coerceAtMost(totalItems)
        val endIndex = (startIndex + 6).coerceAtMost(totalItems)
        for (i in startIndex until endIndex) {
            val card = uniqueRecommendations.getOrNull(i)?.novel ?: continue
            val req = com.lagradost.quicknovel.ui.theme.buildImageRequest(context, card)
            coil3.SingletonImageLoader.get(context).enqueue(req)
        }
    }
 
    // Tactile haptic feedback as new items snap into focus
    LaunchedEffect(carouselState.currentItem) {
        if (carouselState.currentItem > 0) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
    }
 
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
 
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 130.dp,
            itemSpacing = 8.dp,
            flingBehavior = CarouselDefaults.noSnapFlingBehavior(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(215.dp)
                .pointerInput(Unit) {
                    disallowParentIntercept(view)
                }
        ) { index ->
            val rec = uniqueRecommendations[index]
            key(rec.novel.url) {
                NovelCarouselItem(recommendation = rec, onBookClick = onBookClick, onDismissClick = onDismissClick)
            }
        }
    }
}

// ─── Novel Carousel Item ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CarouselItemScope.NovelCarouselItem(
    recommendation: Recommendation,
    onBookClick: (String, String) -> Unit,
    onDismissClick: (String) -> Unit
) {
    val novel = recommendation.novel
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
 
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .maskClip(RoundedCornerShape(12.dp))
            .glassCard(shape = RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onBookClick(novel.url, novel.apiName) },
                onLongClick = { showMenu = true }
            )
    ) {
        // Dropdown menu styled with glassCard
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.glassCard(shape = RoundedCornerShape(12.dp)),
            containerColor = Color.Transparent
        ) {
            DropdownMenuItem(
                text = { Text("Not Interested") },
                onClick = {
                    showMenu = false
                    onDismissClick(novel.url)
                }
            )
        }

        // Overlay of the trigger book (if present)
        recommendation.triggerNovel?.let { trigger ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(36.dp)
                    .glassCard(shape = RoundedCornerShape(6.dp), strokeWidth = 0.5.dp)
            ) {
                AsyncImage(
                    model = rememberHighQualityRequest(data = trigger, context = context),
                    contentDescription = trigger.name,
                    imageLoader = SingletonImageLoader.get(context),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                )
            }
        }

        AsyncImage(
            model = rememberHighQualityRequest(data = novel, context = context),
            contentDescription = novel.name,
            imageLoader = SingletonImageLoader.get(context),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient scrim + title at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 10.dp)
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

                // Score badge
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .glassCard(
                            shape = RoundedCornerShape(4.dp),
                            backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            strokeWidth = 0.dp
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "${(recommendation.score * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── Glass Tag Chip ───────────────────────────────────────────────────────────

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

// Legacy NovelCard kept for compatibility if referenced elsewhere
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

// ─── Deferred State Slider & Gesture Utils ───────────────────────────────────

@Composable
fun GlassmorphicSlider(
    valueState: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableStateOf(1f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .glassCard(shape = RoundedCornerShape(24.dp))
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    val delta = dragAmount.x / width
                    val newValue = (valueState() + delta).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Deferred state read triggers redraw only, avoiding composition
                    shadowElevation = 0f
                }
        ) {
            val v = valueState()
            val trackHeight = 6.dp.toPx()
            val centerY = size.height / 2
            
            // Draw background track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.2f),
                topLeft = androidx.compose.ui.geometry.Offset(24.dp.toPx(), centerY - trackHeight / 2),
                size = androidx.compose.ui.geometry.Size(size.width - 48.dp.toPx(), trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
            )
            // Draw active track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.6f),
                topLeft = androidx.compose.ui.geometry.Offset(24.dp.toPx(), centerY - trackHeight / 2),
                size = androidx.compose.ui.geometry.Size((size.width - 48.dp.toPx()) * v, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
            )
            // Draw thumb
            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(24.dp.toPx() + (size.width - 48.dp.toPx()) * v, centerY)
            )
        }
    }
}

