package com.lagradost.quicknovel.ui.neolists

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.allowHardware
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.db.NeoListEntity
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.rememberImageRequest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NeoListsHubScreen(
    viewModel: NeoListsViewModel,
    onFolderClick: (String) -> Unit,
    isSwipeMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folders by viewModel.folders.collectAsState()
    val versionConflict by viewModel.versionConflict.collectAsState()
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current



    // FAB menu sheet state
    var showAddFolderSheet by remember { mutableStateOf(false) }
    val addFolderSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var createFolderTitle by remember { mutableStateOf("") }
    var createFolderDesc by remember { mutableStateOf("") }

    // Edit folder dialog states
    var editFolderTarget by remember { mutableStateOf<NeoListEntity?>(null) }
    var editFolderTitle by remember { mutableStateOf("") }
    var editFolderAuthor by remember { mutableStateOf("") }
    var editFolderDesc by remember { mutableStateOf("") }

    // Document Picker for .neolist / JSON files
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                pendingImportUri = uri
                viewModel.importFromUri(uri)
            }
        }
    )

    // Consume import results as single-time events
    LaunchedEffect(Unit) {
        viewModel.importResultEvent.collectLatest { result ->
            when (result) {
                is ImportResult.Success -> {
                    Toast.makeText(context, "Imported: ${result.title}", Toast.LENGTH_SHORT).show()
                }
                is ImportResult.AlreadySaved -> {
                    Toast.makeText(context, "'${result.title}' is already saved", Toast.LENGTH_SHORT).show()
                }
                is ImportResult.Failure -> {
                    Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    // Version conflict prompt
    versionConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissVersionConflict() },
            title = { Text("Version Conflict") },
            text = { Text("You already have an older version of '${conflict.existingTitle}'. Replace it with the newer one?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri?.let { uri ->
                        viewModel.resolveVersionConflictReplace(conflict.incoming, uri)
                    }
                }) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissVersionConflict() }) {
                    Text("Keep Existing")
                }
            }
        )
    }

    // Create New Folder Dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateFolderDialog = false
                createFolderTitle = ""
                createFolderDesc = ""
            },
            title = { Text("Create New Folder") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = createFolderTitle,
                        onValueChange = { createFolderTitle = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = createFolderDesc,
                        onValueChange = { createFolderDesc = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createNewFolder(createFolderTitle, createFolderDesc)
                        showCreateFolderDialog = false
                        createFolderTitle = ""
                        createFolderDesc = ""
                    },
                    enabled = createFolderTitle.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateFolderDialog = false
                    createFolderTitle = ""
                    createFolderDesc = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editFolderTarget != null) {
        AlertDialog(
            onDismissRequest = { editFolderTarget = null },
            title = { Text("Edit Folder Details") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editFolderTitle,
                        onValueChange = { editFolderTitle = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editFolderAuthor,
                        onValueChange = { editFolderAuthor = it },
                        label = { Text("Author Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editFolderDesc,
                        onValueChange = { editFolderDesc = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editFolderTarget?.let { target ->
                            viewModel.updateFolderMetadata(
                                listId = target.id,
                                title = editFolderTitle,
                                authorHandle = editFolderAuthor,
                                description = editFolderDesc
                            )
                        }
                        editFolderTarget = null
                    },
                    enabled = editFolderTitle.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editFolderTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // FAB menu sheet (Create vs Import choice)
    if (showAddFolderSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddFolderSheet = false },
            sheetState = addFolderSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = if (!isSwipeMode) 100.dp else 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "New Folder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Option 1: Create
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            scope.launch {
                                addFolderSheetState.hide()
                                showAddFolderSheet = false
                                showCreateFolderDialog = true
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Create New Folder",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Option 2: Import
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            scope.launch {
                                addFolderSheetState.hide()
                                showAddFolderSheet = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                filePickerLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DriveFolderUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Import Folder from File",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddFolderSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = if (!isSwipeMode) 156.dp else 84.dp) // Clears bottom navigation/settings button
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add options")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    bottom = innerPadding.calculateBottomPadding()
                )
        ) {
            if (folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No lists found",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create or import a .neolist file to get started.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alpha(0.8f)
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(folders, key = { it.id }) { folder ->
                        NeoFolderCard(
                            folder = folder,
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onFolderClick(folder.id)
                            },
                            onToggleLock = {
                                scope.launch {
                                    viewModel.toggleLock(folder.id)
                                }
                            },
                            onShare = {
                                viewModel.exportList(folder.id, context)
                            },
                            onSaveToDevice = {
                                viewModel.saveList(folder.id, context)
                            },
                            onEditFolder = {
                                editFolderTarget = folder
                                editFolderTitle = folder.title
                                editFolderAuthor = folder.authorHandle ?: "Me"
                                editFolderDesc = folder.description ?: ""
                            },
                            onDelete = {
                                viewModel.deleteList(folder.id)
                            }
                        )
                    }

                    // Spacer at the bottom to clear floating buttons
                    item(span = { GridItemSpan(2) }) {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NeoFolderCard(
    folder: NeoListEntity,
    onTap: () -> Unit,
    onToggleLock: () -> Unit,
    onShare: () -> Unit,
    onSaveToDevice: () -> Unit,
    onEditFolder: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .glassCard(RoundedCornerShape(20.dp))
            .clickable(onClick = onTap)
    ) {
        // Folder Cover (Mosaic or Image)
        FolderCover(
            folder = folder,
            modifier = Modifier.fillMaxSize()
        )

        // Scrim gradient for text visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 180f
                    )
                )
        )

        // Card options dropdown menu (anchored to three-dots icon button)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            var showMenu by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { showMenu = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text(if (folder.isLocked) "Unlock for Bookmarks" else "Lock Category") },
                    onClick = {
                        showMenu = false
                        onToggleLock()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (folder.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share NeoList") },
                    onClick = {
                        showMenu = false
                        onShare()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Save on Device") },
                    onClick = {
                        showMenu = false
                        onSaveToDevice()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null
                        )
                    }
                )
                if (!folder.isImported) {
                    DropdownMenuItem(
                        text = { Text("Edit Details") },
                        onClick = {
                            showMenu = false
                            onEditFolder()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null
                            )
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete List") },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }

        // Icons overlay (top right)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (folder.isLocked) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Novel Count badge (bottom right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${folder.novels.size} books",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Title and description
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .fillMaxWidth(0.75f)
        ) {
            Text(
                text = folder.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!folder.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = folder.description,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FolderCover(
    folder: NeoListEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (!folder.coverUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(folder.coverUrl)
                .crossfade(200)
                .allowHardware(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else if (folder.novels.isNotEmpty()) {
        // Auto-Cover Mosaic (2x2 grid of first 4 novels)
        val firstFour = folder.novels.take(4)
        Column(modifier = modifier) {
            Row(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    firstFour.getOrNull(0)?.let { NovelItemCover(it) }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    firstFour.getOrNull(1)?.let { NovelItemCover(it) }
                }
            }
            Row(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    firstFour.getOrNull(2)?.let { NovelItemCover(it) }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    firstFour.getOrNull(3)?.let { NovelItemCover(it) }
                }
            }
        }
    } else {
        // Default gradient fallback based on title hashcode
        val hash = folder.title.hashCode()
        val brush = remember(hash) {
            val color1 = Color.hsl((hash.absoluteValue % 360f), 0.65f, 0.45f)
            val color2 = Color.hsl(((hash.absoluteValue + 120) % 360f), 0.7f, 0.35f)
            Brush.verticalGradient(listOf(color1, color2))
        }
        Box(modifier = modifier.background(brush))
    }
}

@Composable
fun NovelItemCover(
    entry: NeoListNovelEntry,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = rememberImageRequest(data = entry.posterUrl)
    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}
