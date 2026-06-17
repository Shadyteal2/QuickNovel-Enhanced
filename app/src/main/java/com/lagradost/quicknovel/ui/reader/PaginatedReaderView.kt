package com.lagradost.quicknovel.ui.reader

import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.lagradost.quicknovel.ui.theme.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.quicknovel.ReadActivityViewModel
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.ui.TextVisualLine
import com.lagradost.quicknovel.ui.ScrollVisibilityIndex
import com.lagradost.quicknovel.hasNonLatinAlpha
import kotlinx.coroutines.launch
import java.io.File
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.compose.material3.LocalTextStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaginatedReaderView(
    viewModel: ReadActivityViewModel,
    onToggleMenu: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chapterState by viewModel.chapter.observeAsState()
    val loadingStatus by viewModel.loadingStatus.observeAsState()
    
    val currentIndex = viewModel.currentIndex
    val spannedText = remember(chapterState, currentIndex) {
        viewModel.getLoadedChapterSpanned(currentIndex)
    }
    val spans = remember(chapterState, currentIndex) {
        viewModel.getLoadedChapterSpans(currentIndex)
    }

    val isContrastCompromised by viewModel.isContrastCompromisedLive.observeAsState(false)
    val isCustomBgEnabled = remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "reader_background" || key == "background_image") {
                isCustomBgEnabled.value = prefs.getBoolean("reader_background", false) &&
                        !prefs.getString("background_image", null).isNullOrBlank()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        isCustomBgEnabled.value = prefs.getBoolean("reader_background", false) &&
                !prefs.getString("background_image", null).isNullOrBlank()
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val bgModifier = if (isCustomBgEnabled.value) {
        if (isContrastCompromised) {
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
        } else {
            Modifier.fillMaxSize()
        }
    } else {
        // Force full opaque ARGB — avoids bluish tint from premultiplied-alpha
        // interpretation of the stored Int on AMOLED/Black themes.
        Modifier.fillMaxSize().background(Color(viewModel.backgroundColor).copy(alpha = 1f))
    }

    BoxWithConstraints(
        modifier = bgModifier
    ) {
        // Capture density in composable scope — never inside LaunchedEffect
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        val showTime by viewModel.showTimeLive.observeAsState(true)
        val showBattery by viewModel.showBatteryLive.observeAsState(true)

        val systemBars = WindowInsets.systemBars
        val systemBarsTopPx = systemBars.getTop(density)
        val systemBarsBottomPx = systemBars.getBottom(density)
        val systemBarsLeftPx = systemBars.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
        val systemBarsRightPx = systemBars.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)

        // ─── Stable Inset Capture (Bug 2 Fix) ──────────────────────────────────
        // Capture maximum seen insets to maintain a stable layout height/width
        // when system bars hide in immersive mode. This prevents re-pagination.
        val stableInsets = remember(widthPx, heightPx) { intArrayOf(0, 0, 0, 0) }
        if (systemBarsTopPx > stableInsets[0]) stableInsets[0] = systemBarsTopPx
        if (systemBarsBottomPx > stableInsets[1]) stableInsets[1] = systemBarsBottomPx
        if (systemBarsLeftPx > stableInsets[2]) stableInsets[2] = systemBarsLeftPx
        if (systemBarsRightPx > stableInsets[3]) stableInsets[3] = systemBarsRightPx

        val topInset = stableInsets[0]
        val bottomInset = stableInsets[1]
        val leftInset = stableInsets[2]
        val rightInset = stableInsets[3]

        val showOverlay = showTime || showBattery
        val overlayHeightPx = if (showOverlay) with(density) { 25.dp.toPx() } else 0f

        val topPaddingPx = topInset + with(density) { viewModel.paddingVertical.dp.toPx() }
        val bottomPaddingPx = bottomInset + with(density) { viewModel.paddingVertical.dp.toPx() } + overlayHeightPx
        val leftPaddingPx = leftInset + with(density) { viewModel.paddingHorizontal.dp.toPx() }
        val rightPaddingPx = rightInset + with(density) { viewModel.paddingHorizontal.dp.toPx() }

        // ─── Custom Font Resolution (Bug 1 & 4 Fix) ─────────────────────────────
        // Match TextAdapter's custom/system font loading logic exactly so that
        // StaticLayout and Compose use the identical font metrics.
        val fontFile = remember(viewModel.textFont) {
            if (viewModel.textFont.isBlank()) null else {
                val found = com.lagradost.quicknovel.util.UIHelper.systemFonts.firstOrNull { it.name == viewModel.textFont }
                if (found != null) found else {
                    val file = File(File(context.filesDir, "fonts"), viewModel.textFont)
                    if (file.exists()) file else null
                }
            }
        }

        val customFontFamily = remember(viewModel.textFont, fontFile) {
            if (viewModel.textFont.isNotEmpty()) {
                val preloaded = com.lagradost.quicknovel.util.PreloadedFontsCache.get(viewModel.textFont)
                if (preloaded != null) {
                    try {
                        FontFamily(preloaded)
                    } catch (e: Exception) {
                        FontFamily.Default
                    }
                } else if (fontFile != null) {
                    try {
                        FontFamily(Typeface.createFromFile(fontFile))
                    } catch (e: Exception) {
                        FontFamily.Default
                    }
                } else {
                    FontFamily.Serif
                }
            } else {
                FontFamily.Serif
            }
        }

        val paginationResult = remember { mutableStateOf<PaginationResult?>(null) }
        var isPaginating by remember { mutableStateOf(false) }

        // Recalculate pages when spannedText, layout constraints, insets, or settings change
        LaunchedEffect(
            spannedText,
            spans,
            widthPx,
            heightPx,
            viewModel.textSize,
            viewModel.textFont,
            viewModel.paddingHorizontal,
            viewModel.paddingVertical,
            viewModel.textVerticalPadding,
            showTime,
            showBattery,
            topInset,
            bottomInset,
            leftInset,
            rightInset
        ) {
            if (spannedText == null || spans.isEmpty() || widthPx <= 0 || heightPx <= 0) {
                paginationResult.value = null
                return@LaunchedEffect
            }
            isPaginating = true

            val textSizePx = with(density) { viewModel.textSize.sp.toPx() }

            val innerWidth  = (widthPx  - leftPaddingPx - rightPaddingPx).toInt().coerceAtLeast(100)
            val innerHeight = (heightPx - topPaddingPx - bottomPaddingPx).toInt().coerceAtLeast(100)

            val paint = TextPaint().apply {
                textSize = textSizePx
                val hasNonLatin = spannedText.hasNonLatinAlpha()
                val tf = if (viewModel.textFont.isNotEmpty() && !hasNonLatin) {
                    com.lagradost.quicknovel.util.PreloadedFontsCache.get(viewModel.textFont)
                        ?: fontFile?.let {
                            try {
                                Typeface.createFromFile(it)
                            } catch (e: Exception) {
                                null
                            }
                        } ?: Typeface.DEFAULT
                } else {
                    Typeface.DEFAULT
                }
                typeface = tf
                color = viewModel.textColor
            }

            val result = TextPaginator.paginate(
                text = spannedText,
                spans = spans,
                paint = paint,
                width = innerWidth,
                height = innerHeight,
                spacingMult = 1.0f,
                spacingAdd = 0f
            )

            paginationResult.value = result
            isPaginating = false
        }

        val currentResult = paginationResult.value

        if (loadingStatus is Resource.Loading || isPaginating || currentResult == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(color = Color(viewModel.textColor))
            }
        } else if (currentResult.pages.isNotEmpty()) {
            val prevPageOffset = if (currentIndex > 0) 1 else 0
            val totalPagesCount = currentResult.pages.size + prevPageOffset + 1
            
            val pagerState = rememberPagerState(
                pageCount = { totalPagesCount }
            )

            // Adjust starting page index when chapter index changes
            var lastChapterIndex by remember { mutableStateOf(currentIndex) }
            LaunchedEffect(currentIndex, currentResult.pages.size) {
                if (currentResult.pages.isNotEmpty()) {
                    if (currentIndex > lastChapterIndex) {
                        pagerState.animateScrollToPage(prevPageOffset)
                    } else if (currentIndex < lastChapterIndex) {
                        pagerState.animateScrollToPage(currentResult.pages.size - 1 + prevPageOffset)
                    }
                    lastChapterIndex = currentIndex
                }
            }

            // Sync scroll position initially when page result gets calculated
            var hasLoadedInitialPage by remember(currentIndex) { mutableStateOf(false) }
            LaunchedEffect(currentResult, hasLoadedInitialPage) {
                if (!hasLoadedInitialPage && currentResult.pages.isNotEmpty()) {
                    val targetParagraph = viewModel.desiredIndex?.innerIndex ?: 0
                    val targetPage = currentResult.paragraphToPage[targetParagraph] ?: 0
                    pagerState.scrollToPage(targetPage + prevPageOffset)
                    hasLoadedInitialPage = true
                }
            }

            // Sync scroll position from Pager to ViewModel on page swipe
            LaunchedEffect(pagerState.currentPage, currentResult) {
                val actualPage = pagerState.currentPage - prevPageOffset
                if (actualPage in currentResult.pages.indices) {
                    val paragraphIndex = currentResult.pageToParagraph[actualPage] ?: 0
                    val span = spans.find { it.innerIndex == paragraphIndex }
                    val charOffset = span?.start ?: 0

                    // Build a synthetic TextVisualLine to satisfy ScrollVisibilityIndex's type contract
                    val visualLine = TextVisualLine(
                        startChar = charOffset,
                        endChar = charOffset,
                        index = currentIndex,
                        innerIndex = paragraphIndex,
                        top = 0,
                        bottom = 0
                    )
                    viewModel.onScroll(
                        ScrollVisibilityIndex(
                            firstInMemory = visualLine,
                            lastInMemory = visualLine,
                            firstFullyVisible = visualLine,
                            lastHalfVisible = visualLine,
                            firstFullyVisibleUnderLine = visualLine
                        )
                    )
                } else if (pagerState.currentPage == 0 && currentIndex > 0) {
                    viewModel.seekToChapter(currentIndex - 1)
                } else if (pagerState.currentPage == totalPagesCount - 1) {
                    viewModel.seekToChapter(currentIndex + 1)
                }
            }

            HorizontalPager(
                state = pagerState,
                // Pre-render 1 page ahead for smooth swipe — native alternative to removed LazyLayoutCacheWindow
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(totalPagesCount) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val tapX = offset.x
                            if (tapX < width * 0.2f) {
                                // Left 20% edge tap: go to previous page
                                if (pagerState.currentPage > 0) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            } else if (tapX > width * 0.8f) {
                                // Right 20% edge tap: go to next page
                                if (pagerState.currentPage < totalPagesCount - 1) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            } else {
                                // Middle tap: toggle overlay menus
                                onToggleMenu()
                            }
                        }
                    }
            ) { page ->
                val actualPage = page - prevPageOffset
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = with(density) { leftPaddingPx.toDp() },
                            top = with(density) { topPaddingPx.toDp() },
                            end = with(density) { rightPaddingPx.toDp() },
                            bottom = with(density) { bottomPaddingPx.toDp() }
                        ),
                    // TopStart: text begins at top-left corner.
                    contentAlignment = Alignment.TopStart
                ) {
                    if (page == 0 && currentIndex > 0) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Loading previous chapter...",
                                color = Color(viewModel.textColor).copy(alpha = 0.6f),
                                fontSize = 16.sp
                            )
                        }
                    } else if (page == totalPagesCount - 1) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Loading next chapter...",
                                color = Color(viewModel.textColor).copy(alpha = 0.6f),
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        val pageText = currentResult.pages.getOrNull(actualPage)?.text ?: ""
                        val hasNonLatin = pageText.hasNonLatinAlpha()
                        Text(
                            text = pageText,
                            color = Color(viewModel.textColor),
                            fontSize = viewModel.textSize.sp,
                            fontFamily = if (hasNonLatin) FontFamily.Default else customFontFamily,
                            style = LocalTextStyle.current.copy(
                                lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                ),
                                shadow = if (isContrastCompromised) {
                                    androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black,
                                        blurRadius = 4f
                                    )
                                } else {
                                    null
                                }
                            )
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No pages found", color = Color(viewModel.textColor))
            }
        }
    }
}
