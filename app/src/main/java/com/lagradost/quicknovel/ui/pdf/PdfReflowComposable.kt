package com.lagradost.quicknovel.ui.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.lagradost.quicknovel.ui.theme.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PdfReflowComposable(
    pdfDocument: PdfDocumentKt,
    pageIndex: Int,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 18f
) {
    var reflowText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pdfDocument, pageIndex) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                pdfDocument.openPage(pageIndex).use { page ->
                    page.openTextPage().use { textPage ->
                        val charCount = textPage.textPageCountChars()
                        if (charCount > 0) {
                            val rawText = textPage.textPageGetText(0, charCount) ?: ""
                            
                            // Clean up unicode junk / layout control characters for readable novels
                            val cleanText = rawText
                                .replace("\r\n", "\n")
                                .replace("\r", "\n")
                                .replace("\u0000", "")
                                .trim()
                            
                            withContext(Dispatchers.Main) {
                                reflowText = cleanText
                                isLoading = false
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                reflowText = "(No text found on this page)"
                                isLoading = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                com.lagradost.quicknovel.mvvm.logError(e)
                withContext(Dispatchers.Main) {
                    reflowText = "Failed to extract text from page ${pageIndex + 1}"
                    isLoading = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDarkMode) Color.Black else Color.White)
            .padding(16.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            reflowText?.let { text ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = text,
                        fontSize = textSizeSp.sp,
                        lineHeight = (textSizeSp * 1.5f).sp,
                        color = if (isDarkMode) Color.LightGray else Color.DarkGray,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
