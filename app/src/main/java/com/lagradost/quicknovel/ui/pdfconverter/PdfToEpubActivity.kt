package com.lagradost.quicknovel.ui.pdfconverter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.pdfconverter.PdfToEpubWorker
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PdfToEpubActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(androidx.compose.ui.platform.ComposeView(this).apply {
            setContent {
                QuickNovelTheme {
                    PdfToEpubScreen(onNavigateBack = { finish() })
                }
            }
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfToEpubScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workManager = remember(context) { WorkManager.getInstance(context) }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("Unknown Author") }
    var autoFixParagraphs by rememberSaveable { mutableStateOf(true) }
    var exportToDownloads by rememberSaveable { mutableStateOf(false) }
    var isCopying by rememberSaveable { mutableStateOf(false) }

    // Reconnect to active background workers
    val workInfosState = workManager.getWorkInfosByTagLiveData("pdf_to_epub_tag").observeAsState()
    val activeWorkInfo = remember(workInfosState.value) {
        workInfosState.value?.find { !it.state.isFinished }
    }
    val isRunning = activeWorkInfo != null
    val page = activeWorkInfo?.progress?.getInt(PdfToEpubWorker.PROGRESS_PAGE, 0) ?: 0
    val totalPages = activeWorkInfo?.progress?.getInt(PdfToEpubWorker.PROGRESS_TOTAL, 0) ?: 0
    val progress = if (totalPages > 0) page.toFloat() / totalPages.toFloat() else 0f

    var lastActiveWorkId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(activeWorkInfo?.id) {
        if (activeWorkInfo != null) {
            lastActiveWorkId = activeWorkInfo.id.toString()
        }
    }

    LaunchedEffect(workInfosState.value) {
        val lastId = lastActiveWorkId ?: return@LaunchedEffect
        val info = workInfosState.value?.find { it.id.toString() == lastId } ?: return@LaunchedEffect
        if (info.state.isFinished) {
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val isDuplicate = info.outputData.getBoolean(PdfToEpubWorker.KEY_IS_DUPLICATE, false)
                    if (isDuplicate) {
                        Toast.makeText(context, "Book already in Library", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "EPUB added to Library", Toast.LENGTH_SHORT).show()
                    }
                }
                WorkInfo.State.FAILED -> {
                    Toast.makeText(context, "PDF conversion failed", Toast.LENGTH_SHORT).show()
                }
                else -> Unit
            }
            lastActiveWorkId = null
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val fileName = context.displayName(uri) ?: "Selected PDF"
        selectedUri = uri.toString()
        selectedName = fileName
        title = fileName.substringBeforeLast(".").replace("_", " ").replace("-", " ")
    }

    // Adapt background to app background settings (AMOLED/Monet/Custom BG image)
    val settings = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val imageUri = remember(settings) { settings.getString(context.getString(R.string.background_image_key), null) }
    val hasBackground = !imageUri.isNullOrBlank()
    val containerColor = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = containerColor,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Text(
                        text = "PDF to EPUB",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ConverterHeroCard(
                selectedName = selectedName,
                isRunning = isRunning || isCopying,
                onSelectPdf = {
                    pdfPicker.launch(arrayOf("application/pdf"))
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Metadata & Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isRunning && !isCopying,
                    label = { Text("Title") }
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isRunning && !isCopying,
                    label = { Text("Author") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text("Auto-fix paragraphs", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Merge PDF line breaks into reader-friendly text",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }
                    Switch(
                        checked = autoFixParagraphs,
                        onCheckedChange = { autoFixParagraphs = it },
                        enabled = !isRunning && !isCopying
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text("Export copy to Downloads", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Save EPUB copy to your custom downloads folder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }
                    Switch(
                        checked = exportToDownloads,
                        onCheckedChange = { exportToDownloads = it },
                        enabled = !isRunning && !isCopying
                    )
                }
            }

            AnimatedVisibility(visible = isRunning || isCopying) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val statusText = when {
                        isCopying -> "Preparing PDF file..."
                        totalPages > 0 -> "Converting page $page of $totalPages"
                        else -> "Preparing conversion..."
                    }
                    Text(
                        text = statusText,
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(
                        progress = { if (isCopying) 0f else progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = {
                    val uriStr = selectedUri ?: return@Button
                    val uri = Uri.parse(uriStr)
                    isCopying = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val tempFile = File(context.cacheDir, "pdf_to_epub_${UUID.randomUUID()}.pdf")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            withContext(Dispatchers.Main) {
                                isCopying = false
                                val request = OneTimeWorkRequestBuilder<PdfToEpubWorker>()
                                    .addTag("pdf_to_epub_tag")
                                    .setInputData(
                                        Data.Builder()
                                            .putString(PdfToEpubWorker.KEY_FILE_PATH, tempFile.absolutePath)
                                            .putString(PdfToEpubWorker.KEY_TITLE, title)
                                            .putString(PdfToEpubWorker.KEY_AUTHOR, author)
                                            .putBoolean(PdfToEpubWorker.KEY_AUTO_FIX, autoFixParagraphs)
                                            .putBoolean(PdfToEpubWorker.KEY_EXPORT_DOWNLOADS, exportToDownloads)
                                            .build()
                                    )
                                    .build()
                                
                                lastActiveWorkId = request.id.toString()
                                workManager.enqueueUniqueWork(
                                    "pdf_to_epub_${request.id}",
                                    ExistingWorkPolicy.REPLACE,
                                    request
                                )
                            }
                        } catch (e: Exception) {
                            com.lagradost.quicknovel.mvvm.logError(e)
                            withContext(Dispatchers.Main) {
                                isCopying = false
                                Toast.makeText(context, "Failed to prepare PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = selectedUri != null && title.isNotBlank() && !isRunning && !isCopying,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.size(10.dp))
                Text("Convert and add to Library", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConverterHeroCard(
    selectedName: String?,
    isRunning: Boolean,
    onSelectPdf: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isRunning) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "converterHeroScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .glassCard(
                shape = RoundedCornerShape(28.dp),
                strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                strokeWidth = 1.dp
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = selectedName ?: "Choose a PDF",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Creates a local EPUB copy with reflowable text for the built-in reader.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 20.sp
            )
            Button(
                onClick = onSelectPdf,
                enabled = !isRunning,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (selectedName == null) "Select PDF" else "Change PDF")
            }
        }
    }
}

private fun Context.displayName(uri: Uri): String? {
    return runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }.getOrNull()
}
