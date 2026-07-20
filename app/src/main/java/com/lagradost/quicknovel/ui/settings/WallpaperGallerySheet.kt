package com.lagradost.quicknovel.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.BaseApplication.Companion.getActivity
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberShimmerBrush
import kotlinx.coroutines.launch

@Composable
fun WallpaperGalleryScreen(
    onBack: () -> Unit,
    viewModel: WallpaperGalleryViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val wallpapersState by viewModel.wallpapersState.collectAsState()
    val wallpapers by viewModel.wallpapers.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val saveToGalleryState by viewModel.saveToGalleryState.collectAsState()
    
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    var previewUrl by remember { mutableStateOf<String?>(null) }

    val categories = listOf(
        "all" to "All",
        "abstract" to "Abstract",
        "anime" to "Anime",
        "architecture" to "Architecture",
        "art" to "Art",
        "cars" to "Cars",
        "minimal" to "Minimal",
        "nature" to "Nature",
        "tech" to "Tech"
    )

    // Load initial wallpapers
    LaunchedEffect(Unit) {
        viewModel.loadWallpapers()
    }

    // Handle wallpaper download state
    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is Resource.Success -> {
                Toast.makeText(context, "Background Image Set!", Toast.LENGTH_SHORT).show()
                viewModel.clearDownloadState()
                previewUrl = null
                onBack()
                val act = context.getActivity() ?: CommonActivity.activity
                CommonActivity.recreateWithSmoothTransition(act)
            }
            is Resource.Failure -> {
                Toast.makeText(context, state.errorString, Toast.LENGTH_LONG).show()
                viewModel.clearDownloadState()
            }
            else -> {}
        }
    }

    // Handle MediaStore saving state
    LaunchedEffect(saveToGalleryState) {
        when (val state = saveToGalleryState) {
            is Resource.Success -> {
                Toast.makeText(context, state.value, Toast.LENGTH_SHORT).show()
                viewModel.clearSaveToGalleryState()
            }
            is Resource.Failure -> {
                Toast.makeText(context, state.errorString, Toast.LENGTH_LONG).show()
                viewModel.clearSaveToGalleryState()
            }
            else -> {}
        }
    }

    val gridState = rememberLazyGridState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoadingMore && wallpapersState is Resource.Success) {
            viewModel.loadMoreWallpapers()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Full screen Header Toolbar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Browse Wallpapers",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            // Category Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(categories) { (key, name) ->
                    val isSelected = selectedCategory == key
                    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(containerColor)
                            .clickable { viewModel.selectCategory(key) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(text = name, color = contentColor, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Wallpapers Grid
            when (val state = wallpapersState) {
                is Resource.Loading -> {
                    // Shimmer Skeletons
                    val shimmerBrush = rememberShimmerBrush()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(6) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.625f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(shimmerBrush)
                            )
                        }
                    }
                }
                is Resource.Success -> {
                    val list = wallpapers
                    if (list.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "No wallpapers found.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        val cardShimmerBrush = rememberShimmerBrush()
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(list) { url ->
                                // Thumbnail optimization: downsample to avoid OutOfMemoryError!
                                val thumbnailRequest = ImageRequest.Builder(context)
                                    .data(url)
                                    .size(360, 640)
                                    .crossfade(true)
                                    .build()

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.625f)
                                        .clickable { previewUrl = url }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        var isThumbnailLoading by remember { mutableStateOf(true) }

                                        if (isThumbnailLoading) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(cardShimmerBrush)
                                            )
                                        }

                                        AsyncImage(
                                            model = thumbnailRequest,
                                            contentDescription = "Wallpaper Thumbnail",
                                            contentScale = ContentScale.Crop,
                                            onState = { imgState ->
                                                isThumbnailLoading = imgState is AsyncImagePainter.State.Loading
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            if (isLoadingMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
                is Resource.Failure -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Failed to load wallpapers",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadWallpapers() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-Screen Preview Dialog
    previewUrl?.let { url ->
        Dialog(
            onDismissRequest = { previewUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // High quality image preview
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Wallpaper Preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Back Button
                IconButton(
                    onClick = { previewUrl = null },
                    modifier = Modifier
                        .padding(top = 16.dp, start = 16.dp)
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Preview",
                        tint = Color.White
                    )
                }

                // Control Actions Overlay at the bottom
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                ) {
                    val isDownloading = downloadState is Resource.Loading
                    val isSaving = saveToGalleryState is Resource.Loading

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Set Background Action
                        Button(
                            onClick = {
                                viewModel.setWallpaper(url, context) {
                                    previewUrl = null
                                }
                            },
                            enabled = !isDownloading && !isSaving,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                                    Text("Set as Background", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Save to Gallery Action
                        IconButton(
                            onClick = { viewModel.saveToGallery(url, context) },
                            enabled = !isDownloading && !isSaving,
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save to Gallery",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
