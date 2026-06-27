package com.lagradost.quicknovel.ui.neolists

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.allowHardware
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.navigate
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.db.NeoListEntity
import com.lagradost.quicknovel.ui.search.SearchViewModel
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberImageRequest
import com.lagradost.quicknovel.ui.theme.rememberShimmerBrush
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeoListDetailScreen(
    listId: String,
    viewModel: NeoListDetailViewModel,
    searchViewModel: SearchViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val folder by viewModel.folder.collectAsState()
    val resolutionStates by viewModel.resolutionStates.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val batchMigrateIndex by viewModel.batchMigrateIndex.collectAsState()

    val gridState = rememberLazyGridState()

    // Trigger load when screen starts
    LaunchedEffect(listId) {
        viewModel.load(listId)
    }

    val currentFolder = folder

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(currentFolder?.title ?: "Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (stats.deadCount > 0) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.startBatchMigrate()
                            }
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "Migrate All Dead Providers")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        if (currentFolder == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.lagradost.quicknovel.ui.theme.LoadingIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Hero Folder Header
                    item(span = { GridItemSpan(2) }) {
                        NeoListHeroHeader(folder = currentFolder, stats = stats)
                    }

                    // Novel List entries
                    itemsIndexed(currentFolder.novels, key = { index, item -> "${item.title}::${item.apiName}::$index" }) { index, entry ->
                        val resState = resolutionStates[index] ?: NovelResolutionState.Pending
                        NeoListNovelCard(
                            entry = entry,
                            resolutionState = resState,
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (resState is NovelResolutionState.Resolved) {
                                    MainActivity.loadResult(entry.sourceUrl, entry.apiName)
                                }
                            },
                            onMigrateClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                performMigrationSearch(context, searchViewModel, entry.title, entry.author)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

                // Batch Migration Bottom Sheet flow
                batchMigrateIndex?.let { index ->
                    val deadEntry = currentFolder.novels.getOrNull(index)
                    if (deadEntry != null) {
                        ModalBottomSheet(
                            onDismissRequest = { viewModel.cancelBatchMigrate() },
                            containerColor = Color.Transparent,
                            scrimColor = Color.Black.copy(alpha = 0.32f),
                            dragHandle = null
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassCard(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                                color = Color.Transparent
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp)
                                        .navigationBarsPadding()
                                ) {
                                    Text(
                                        text = "Batch Migration",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Find working provider for:\n'${deadEntry.title}'?",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.advanceBatchMigrate() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Skip")
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.advanceBatchMigrate()
                                                performMigrationSearch(context, searchViewModel, deadEntry.title, deadEntry.author)
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Search")
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

/**
 * Triggers search in SearchViewModel and navigates to the search fragment.
 */
private fun performMigrationSearch(
    context: android.content.Context,
    searchViewModel: SearchViewModel,
    title: String,
    author: String?
) {
    val query = if (!author.isNullOrBlank()) "$title $author" else title
    searchViewModel.clearSearch()
    searchViewModel.lastSearchQuery = query
    searchViewModel.search(query)
    (context as? Activity)?.navigate(R.id.navigation_search)
}

@Composable
fun NeoListHeroHeader(
    folder: NeoListEntity,
    stats: NeoListStats,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        // Collapsing Hero cover (Large Image)
        if (!folder.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(folder.coverUrl)
                    .crossfade(200)
                    .allowHardware(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp))
                    .blur(20.dp)
            )
        } else {
            // Mosaic fallback or gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp))
                    .blur(20.dp)
            ) {
                FolderCover(folder = folder, modifier = Modifier.fillMaxSize())
            }
        }

        // Deep Dark Gradient Scrim overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Overlay metadata card (Glassmorphic)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .glassCard(RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = folder.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!folder.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = folder.description,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))

                // Stats and Dates row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${stats.totalNovels} books · ${stats.resolvedCount} active",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (stats.deadCount > 0) {
                            Text(
                                text = "${stats.deadCount} unavailable",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        val dateText = if (folder.isImported && folder.importedAt != null) {
                            "Imported: ${dateFormatter.format(Date(folder.importedAt))}"
                        } else {
                            "Created: ${dateFormatter.format(Date(folder.createdAt))}"
                        }
                        Text(
                            text = dateText,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                        if (!folder.authorHandle.isNullOrBlank()) {
                            Text(
                                text = "by ${folder.authorHandle}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NeoListNovelCard(
    entry: NeoListNovelEntry,
    resolutionState: NovelResolutionState,
    onTap: () -> Unit,
    onMigrateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = rememberImageRequest(data = entry.posterUrl)

    val isPending = resolutionState is NovelResolutionState.Pending
    val isDead = resolutionState is NovelResolutionState.DeadProvider

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .glassCard(RoundedCornerShape(16.dp))
            .clickable(enabled = !isPending && !isDead, onClick = onTap),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Novel Cover
            AsyncImage(
                model = imageRequest,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isDead) Modifier
                            .blur(8.dp)
                            .alpha(0.48f) else Modifier
                    )
            )

            // Shimmer brush overlay if resolution is pending
            if (isPending) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(rememberShimmerBrush())
                )
            }

            // Scrim overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = 120f
                        )
                    )
            )

            // Metadata & Badges
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Provider label tag
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.64f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = entry.apiName.replace("Provider", "").replace("API", ""),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom title & actions
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = entry.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isDead) TextDecoration.LineThrough else TextDecoration.None
                    )

                    if (isDead) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Provider Unavailable",
                                color = MaterialTheme.colorScheme.onError,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onMigrateClick,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Migrate", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
