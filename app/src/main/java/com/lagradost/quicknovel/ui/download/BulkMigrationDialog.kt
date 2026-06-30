package com.lagradost.quicknovel.ui.download

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.db.NovelEntity
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberHighQualityRequest
import com.lagradost.quicknovel.ui.theme.rememberShimmerBrush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkMigrationDialog(
    novels: List<NovelEntity>,
    viewModel: BulkMigrationViewModel,
    onDismiss: () -> Unit,
    onMigrationComplete: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) } // Step 1: Provider selection, Step 2: Matcher screen

    val isSearching by viewModel.isSearching
    val isMigrating by viewModel.isMigrating
    val progressText by viewModel.migrationProgress

    // Initialize list of novels and providers when dialog opens
    LaunchedEffect(novels) {
        viewModel.setNovelsToMigrate(novels)
        viewModel.initializeProviders()
    }

    if (isMigrating) {
        // Step 3 UI: Non-dismissible Loading Dialog during destructive DB migration
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(28.dp), backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Migrating Database records...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isMigrating) {
                viewModel.cancelSearch()
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            if (currentStep == 1) {
                ProviderSelectionScreen(
                    viewModel = viewModel,
                    onCancel = onDismiss,
                    onContinue = {
                        if (viewModel.selectedProviders.isEmpty()) {
                            Toast.makeText(context, "Select at least one provider", Toast.LENGTH_SHORT).show()
                        } else {
                            currentStep = 2
                            viewModel.startSearch()
                        }
                    }
                )
            } else {
                MigrationMatcherScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.cancelSearch()
                        currentStep = 1
                    },
                    onMigrateAll = {
                        viewModel.startMigration(context) {
                            Toast.makeText(context, "Migration completed successfully!", Toast.LENGTH_LONG).show()
                            onMigrationComplete()
                            onDismiss()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectionScreen(
    viewModel: BulkMigrationViewModel,
    onCancel: () -> Unit,
    onContinue: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val providers = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            viewModel.allProviders
        } else {
            viewModel.allProviders.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Target Sources", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.selectAllProviders() }) {
                        Text("All")
                    }
                    TextButton(onClick = { viewModel.selectNoneProviders() }) {
                        Text("None")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search sources...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Providers Checklist
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(providers, key = { it.name }) { provider ->
                    val isChecked = viewModel.selectedProviders.contains(provider.name)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(shape = RoundedCornerShape(12.dp))
                            .clickable { viewModel.toggleProvider(provider.name) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { viewModel.toggleProvider(provider.name) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationMatcherScreen(
    viewModel: BulkMigrationViewModel,
    onBack: () -> Unit,
    onMigrateAll: () -> Unit
) {
    val items = viewModel.migrationItems
    val context = LocalContext.current
    var selectedItemForManualSearch by remember { mutableStateOf<BulkMigrationItem?>(null) }
    
    val totalMatches = items.count { it.targetMatch != null }
    val totalItems = items.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Migration ($totalMatches/$totalItems)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onMigrateAll,
                        enabled = totalMatches > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Migrate All",
                            tint = if (totalMatches > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stable keys using unique novel entity ID to prevent recompositions loops
                items(items, key = { it.sourceNovel.id }) { item ->
                    MigrationRow(
                        item = item,
                        onRowClick = { selectedItemForManualSearch = item },
                        onUpdateItem = viewModel::updateItem
                    )
                }
            }
        }
    }

    // Manual override dialog popup
    selectedItemForManualSearch?.let { item ->
        ManualSearchOverrideDialog(
            item = item,
            onDismiss = { selectedItemForManualSearch = null },
            onSelectMatch = { chosenMatch ->
                viewModel.updateItem(item.copy(targetMatch = chosenMatch))
                selectedItemForManualSearch = null
            }
        )
    }
}

@Composable
fun MigrationRow(
    item: BulkMigrationItem,
    onRowClick: () -> Unit,
    onUpdateItem: (BulkMigrationItem) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(16.dp))
            .clickable { onRowClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Original source novel
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            AsyncImage(
                model = rememberHighQualityRequest(item.sourceNovel.posterUrl ?: "", LocalContext.current),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 65.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.sourceNovel.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.sourceNovel.apiName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Arrow transition
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(20.dp)
        )

        // Target match result
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            when (item.searchState) {
                is MigrationSearchState.Pending, is MigrationSearchState.Loading -> {
                    val brush = rememberShimmerBrush()
                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 65.dp, height = 90.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(brush)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(65.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(45.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush)
                        )
                    }
                }
                is MigrationSearchState.Success -> {
                    val match = item.targetMatch
                    if (match != null) {
                        AsyncImage(
                            model = rememberHighQualityRequest(match.posterUrl ?: "", LocalContext.current),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 65.dp, height = 90.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = match.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = match.apiName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 65.dp, height = 90.dp)
                                .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("No match", style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = Color.Gray)
                    }
                }
                is MigrationSearchState.Error -> {
                    Box(
                        modifier = Modifier
                            .size(width = 65.dp, height = 90.dp)
                            .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Failed", style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Row menu actions
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Search Manually") },
                    onClick = {
                        showMenu = false
                        onRowClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Don't Migrate") },
                    onClick = {
                        showMenu = false
                        onUpdateItem(item.copy(targetMatch = null, searchState = MigrationSearchState.Success))
                    }
                )
            }
        }
    }
}

@Composable
fun ManualSearchOverrideDialog(
    item: BulkMigrationItem,
    onDismiss: () -> Unit,
    onSelectMatch: (SearchResponse) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .glassCard(shape = RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Select match for:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.sourceNovel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (item.allResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matches found", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(item.allResults) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassCard(shape = RoundedCornerShape(12.dp))
                                    .clickable { onSelectMatch(result) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = rememberHighQualityRequest(result.posterUrl ?: "", LocalContext.current),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 45.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = result.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = result.apiName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
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
