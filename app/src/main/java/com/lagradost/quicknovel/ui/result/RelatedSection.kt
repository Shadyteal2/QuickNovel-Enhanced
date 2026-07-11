package com.lagradost.quicknovel.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.ui.theme.disallowParentIntercept
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberImageRequest
import com.lagradost.quicknovel.ui.theme.rememberShimmerBrush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartRelatedSection(
    state: RelatedState,
    onNovelClick: (SearchResponse) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state !is RelatedState.Hidden,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val title = when (state) {
                is RelatedState.Shown -> state.label
                else -> "You May Also Like"
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            when (state) {
                is RelatedState.Loading -> {
                    RelatedShimmerRow()
                }
                is RelatedState.Shown -> {
                    val view = LocalView.current
                    val carouselState = rememberCarouselState { state.items.size }

                    // Tactile haptic feedback as new items snap into focus
                    LaunchedEffect(carouselState.currentItem) {
                        if (carouselState.currentItem > 0) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }

                    HorizontalMultiBrowseCarousel(
                        state = carouselState,
                        preferredItemWidth = 130.dp,
                        itemSpacing = 8.dp,
                        flingBehavior = CarouselDefaults.noSnapFlingBehavior(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .pointerInput(Unit) {
                                disallowParentIntercept(view)
                            }
                    ) { index ->
                        val item = state.items[index]
                        RelatedNovelCarouselCard(
                            item = item,
                            onClick = { onNovelClick(item) }
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarouselItemScope.RelatedNovelCarouselCard(
    item: SearchResponse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .maskClip(RoundedCornerShape(12.dp))
            .glassCard(shape = RoundedCornerShape(12.dp), strokeWidth = 0.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = rememberImageRequest(data = item.posterUrl),
            contentDescription = item.name,
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
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.apiName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun RelatedShimmerRow(
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberShimmerBrush()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmerBrush)
            )
        }
    }
}
