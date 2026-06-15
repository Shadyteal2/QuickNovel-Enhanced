package com.lagradost.quicknovel.ui.pdf

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfPath: String,
    title: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pdfDocument by remember { mutableStateOf<PdfDocumentKt?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var totalPages by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Table of contents / Bookmarks
    var outlines by remember { mutableStateOf<List<io.legere.pdfiumandroid.PdfDocument.Bookmark>>(emptyList()) }
    var showOutlineSheet by remember { mutableStateOf(false) }

    // Reading Settings & Modes
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var isReflowMode by remember { mutableStateOf(prefs.getBoolean("pdf_reflow_mode", false)) }
    var isHorizontalLayout by remember { mutableStateOf(prefs.getBoolean("pdf_horizontal_layout", false)) }
    var isDarkMode by remember { mutableStateOf(prefs.getBoolean("pdf_dark_mode", true)) }
    var textSizeSp by remember { mutableStateOf(prefs.getFloat("pdf_reflow_text_size", 18f)) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Page state tracker
    var currentPageIndex by remember { mutableIntStateOf(0) }

    // Outlines & PDFium initialization
    LaunchedEffect(pdfPath) {
        isLoading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                val file = File(pdfPath)
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "File does not exist: $pdfPath"
                        isLoading = false
                    }
                    return@withContext
                }

                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pfd = descriptor
                val core = PdfiumCoreKt(Dispatchers.Default)
                val doc = core.newDocument(descriptor)
                pdfDocument = doc
                totalPages = doc.getPageCount()
                outlines = doc.getTableOfContents()

                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            } catch (e: Exception) {
                com.lagradost.quicknovel.mvvm.logError(e)
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to load PDF: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    // Keep screen on
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    pdfDocument?.close()
                    pfd?.close()
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
    }

    val scaffoldBgColor = if (isDarkMode) Color.Black else Color.White
    val controlColor = if (isDarkMode) Color.DarkGray else Color.LightGray
    val contentTextColor = if (isDarkMode) Color.White else Color.Black

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        color = contentTextColor,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = contentTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isReflowMode = !isReflowMode
                        prefs.edit().putBoolean("pdf_reflow_mode", isReflowMode).apply()
                    }) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Text Reflow Mode",
                            tint = if (isReflowMode) MaterialTheme.colorScheme.primary else contentTextColor
                        )
                    }
                    IconButton(onClick = { showOutlineSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Chapters Table of Contents",
                            tint = contentTextColor
                        )
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = contentTextColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scaffoldBgColor
                )
            )
        },
        bottomBar = {
            if (totalPages > 0 && !isLoading && errorMessage == null) {
                Surface(
                    color = scaffoldBgColor,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = currentPageIndex.toFloat(),
                                onValueChange = { currentPageIndex = it.toInt() },
                                valueRange = 0f..(totalPages - 1).coerceAtLeast(0).toFloat(),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${currentPageIndex + 1} / $totalPages",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentTextColor
                            )
                        }
                    }
                }
            }
        },
        containerColor = scaffoldBgColor
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "Unknown error",
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            } else {
                pdfDocument?.let { doc ->
                    if (isReflowMode) {
                        PdfReflowComposable(
                            pdfDocument = doc,
                            pageIndex = currentPageIndex,
                            isDarkMode = isDarkMode,
                            textSizeSp = textSizeSp,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (isHorizontalLayout) {
                        val pagerState = rememberPagerState(initialPage = currentPageIndex, pageCount = { totalPages })
                        
                        LaunchedEffect(currentPageIndex) {
                            if (pagerState.currentPage != currentPageIndex) {
                                pagerState.scrollToPage(currentPageIndex)
                            }
                        }
                        
                        LaunchedEffect(pagerState.currentPage) {
                            currentPageIndex = pagerState.currentPage
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            key = { it }
                        ) { page ->
                            PdfPageComposable(
                                pdfDocument = doc,
                                pageIndex = page,
                                isDarkMode = isDarkMode,
                                isActive = (currentPageIndex == page),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPageIndex)
                        
                        LaunchedEffect(currentPageIndex) {
                            if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != currentPageIndex) {
                                listState.scrollToItem(currentPageIndex)
                            }
                        }
                        
                        LaunchedEffect(listState.firstVisibleItemIndex) {
                            currentPageIndex = listState.firstVisibleItemIndex
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(totalPages, key = { it }) { page ->
                                PdfPageComposable(
                                    pdfDocument = doc,
                                    pageIndex = page,
                                    isDarkMode = isDarkMode,
                                    isActive = (currentPageIndex == page),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp).background(controlColor))
                            }
                        }
                    }
                }
            }
        }
    }

    // Chapters Table of Contents Sheet
    if (showOutlineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOutlineSheet = false },
            containerColor = if (isDarkMode) Color(0xFF161616) else Color.White,
            contentColor = contentTextColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Table of Contents",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentTextColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                HorizontalDivider(color = controlColor)

                if (outlines.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No Table of Contents available in this file", color = contentTextColor.copy(alpha = 0.6f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(outlines.size) { index ->
                            val outline = outlines[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentPageIndex = outline.pageIdx.toInt()
                                        showOutlineSheet = false
                                    }
                                    .padding(vertical = 14.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = outline.title ?: "Chapter ${index + 1}",
                                    color = contentTextColor,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Page ${outline.pageIdx + 1}",
                                    color = contentTextColor.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            }
                            HorizontalDivider(color = controlColor.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    // Reader Settings Bottom Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = if (isDarkMode) Color(0xFF161616) else Color.White,
            contentColor = contentTextColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentTextColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                HorizontalDivider(color = controlColor)

                // Layout style selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Layout Direction",
                        color = contentTextColor,
                        fontWeight = FontWeight.Medium
                    )
                    Row {
                        Button(
                            onClick = {
                                isHorizontalLayout = false
                                prefs.edit().putBoolean("pdf_horizontal_layout", false).apply()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isHorizontalLayout) MaterialTheme.colorScheme.primary else controlColor,
                                contentColor = if (!isHorizontalLayout) Color.White else contentTextColor
                            ),
                            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                        ) {
                            Text("Vertical")
                        }
                        Button(
                            onClick = {
                                isHorizontalLayout = true
                                prefs.edit().putBoolean("pdf_horizontal_layout", true).apply()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isHorizontalLayout) MaterialTheme.colorScheme.primary else controlColor,
                                contentColor = if (isHorizontalLayout) Color.White else contentTextColor
                            ),
                            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                        ) {
                            Text("Page Flip")
                        }
                    }
                }

                // Theme Inversion Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reader Dark Mode Inversion",
                        color = contentTextColor,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = {
                            isDarkMode = it
                            prefs.edit().putBoolean("pdf_dark_mode", it).apply()
                        }
                    )
                }

                // Text Size Slider (for Reflow mode)
                if (isReflowMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Reflow Text Size: ${textSizeSp.toInt()} sp",
                            color = contentTextColor,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = textSizeSp,
                            onValueChange = {
                                textSizeSp = it
                                prefs.edit().putFloat("pdf_reflow_text_size", it).apply()
                            },
                            valueRange = 12f..36f
                        )
                    }
                }
            }
        }
    }
}
