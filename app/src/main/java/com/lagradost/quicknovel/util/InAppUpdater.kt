package com.lagradost.quicknovel.util

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.BuildConfig
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import io.noties.markwon.Markwon
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

const val UPDATE_TIME = 1000

sealed class DownloadState {
    object NotStarted : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Finished : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class InAppUpdater {
    companion object {
        // === IN APP UPDATER ===
        data class GithubAsset(
            @JsonProperty("name") val name: String,
            @JsonProperty("size") val size: Int, // Size bytes
            @JsonProperty("browser_download_url") val browser_download_url: String, // download link
            @JsonProperty("content_type") val content_type: String, // application/vnd.android.package-archive
        )

        data class GithubRelease(
            @JsonProperty("tag_name") val tag_name: String, // Version code
            @JsonProperty("body") val body: String, // Desc
            @JsonProperty("assets") val assets: List<GithubAsset>,
            @JsonProperty("target_commitish") val target_commitish: String, // branch
        )

        data class Update(
            @JsonProperty("shouldUpdate") val shouldUpdate: Boolean,
            @JsonProperty("updateURL") val updateURL: String?,
            @JsonProperty("updateVersion") val updateVersion: String?,
            @JsonProperty("changelog") val changelog: String?,
        )

        private val mapper = JsonMapper.builder().addModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

        private suspend fun Activity.getAppUpdate(): Update = withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/Shadyteal2/QuickNovel-Enhanced/releases/latest"
                val headers = mapOf("Accept" to "application/vnd.github.v3+json")
                val response = mapper.readValue<GithubRelease>(app.get(url, headers = headers).text)

                // Match version pattern like 2.1.5 inside tag_name (e.g. "v2.2.0")
                val versionRegex = Regex("""((\d+)\.(\d+)\.(\d+))""")
                val foundVersion = versionRegex.find(response.tag_name)

                val foundAsset = response.assets.find { it.content_type == "application/vnd.android.package-archive" }
                    ?: response.assets.find { it.name.endsWith(".apk") }
                    ?: response.assets.getOrNull(0)

                val currentVersion = packageName?.let {
                    packageManager.getPackageInfo(it, 0)
                }

                val shouldUpdate = if (foundAsset != null && foundAsset.browser_download_url != "" && foundVersion != null) {
                    val current = currentVersion?.versionName?.split(".")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                    val found = foundVersion.groupValues[1].split(".").mapNotNull { it.toIntOrNull() }

                    if (current.isNotEmpty() && found.isNotEmpty()) {
                        var updateNeeded = false
                        for (i in 0 until maxOf(current.size, found.size)) {
                            val curr = current.getOrNull(i) ?: 0
                            val fnd = found.getOrNull(i) ?: 0
                            if (fnd > curr) {
                                updateNeeded = true
                                break
                            } else if (fnd < curr) {
                                break
                            }
                        }
                        updateNeeded
                    } else false
                } else false

                if (foundVersion != null && foundAsset != null) {
                    Update(
                        shouldUpdate,
                        foundAsset.browser_download_url,
                        foundVersion.groupValues[1],
                        response.body
                    )
                } else {
                    Update(false, null, null, null)
                }

            } catch (t: Throwable) {
                logError(t)
                Update(false, null, null, null)
            }
        }

        private fun Context.canInstallApk(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                packageManager.canRequestPackageInstalls()
            } else {
                true
            }
        }

        private fun Activity.requestInstallPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                    Toast.makeText(this, "Please enable permission to install unknown apps for NeoQN", Toast.LENGTH_LONG).show()
                } catch (t: Throwable) {
                    logError(t)
                }
            }
        }

        private fun Context.installApk(file: File) {
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(intent)
            } catch (t: Throwable) {
                logError(t)
                Toast.makeText(this, "Failed to start installation: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }

        private fun setupViewTreeOwners(dialog: Dialog, context: Context, composeView: ComposeView) {
            var currentContext = context
            while (currentContext is android.content.ContextWrapper) {
                if (currentContext is androidx.fragment.app.FragmentActivity) {
                    break
                }
                currentContext = currentContext.baseContext
            }
            val activity = currentContext as? androidx.fragment.app.FragmentActivity
                ?: CommonActivity.activity as? androidx.fragment.app.FragmentActivity

            val lifecycleOwner = activity
            val viewModelStoreOwner = activity
            val savedStateRegistryOwner = activity

            if (lifecycleOwner != null) {
                composeView.setViewTreeLifecycleOwner(lifecycleOwner)
                dialog.window?.decorView?.setViewTreeLifecycleOwner(lifecycleOwner)
            }
            if (viewModelStoreOwner != null) {
                composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
                dialog.window?.decorView?.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            }
            if (savedStateRegistryOwner != null) {
                composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
                dialog.window?.decorView?.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            }
        }

        @Composable
        private fun UpdateDialogContent(
            currentVersionName: String,
            newVersionName: String,
            changelog: String,
            downloadUrl: String,
            activity: Activity,
            onDismiss: () -> Unit,
            onDontShowAgain: (() -> Unit)? = null
        ) {
            var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.NotStarted) }
            var downloadJob by remember { mutableStateOf<Job?>(null) }
            val coroutineScope = rememberCoroutineScope()
            val apkFile = remember { File(activity.cacheDir, "NeoQN-update.apk") }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .glassCard(shape = RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Header
                    Text(
                        text = "New Update Available",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "v$currentVersionName  ➔  v$newVersionName",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrollable Changelog
                    Text(
                        text = "Changelog",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            val textColor = MaterialTheme.colorScheme.onSurface
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { ctx ->
                                    TextView(ctx).apply {
                                        textSize = 14f
                                        setTextColor(textColor.toArgb())
                                    }
                                },
                                update = { textView ->
                                    try {
                                        val markwon = Markwon.create(textView.context)
                                        markwon.setMarkdown(textView, changelog)
                                    } catch (t: Throwable) {
                                        logError(t)
                                        textView.text = changelog
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Dynamic Footer based on download status
                    when (val state = downloadState) {
                        is DownloadState.NotStarted -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (onDontShowAgain != null) {
                                    TextButton(onClick = {
                                        onDontShowAgain()
                                        onDismiss()
                                    }) {
                                        Text(
                                            text = "Don't ask again",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                TextButton(onClick = onDismiss) {
                                    Text(
                                        text = "Later",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        downloadJob = coroutineScope.launch {
                                            startApkDownload(
                                                urlString = downloadUrl,
                                                outputFile = apkFile,
                                                onProgress = { progress ->
                                                    downloadState = DownloadState.Downloading(progress)
                                                },
                                                onSuccess = {
                                                    downloadState = DownloadState.Finished
                                                    activity.installApk(apkFile)
                                                },
                                                onError = { errorMsg ->
                                                    downloadState = DownloadState.Error(errorMsg)
                                                }
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Update Now",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        is DownloadState.Downloading -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Downloading update...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${(state.progress * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = state.progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            downloadJob?.cancel()
                                            downloadJob = null
                                            downloadState = DownloadState.NotStarted
                                        }
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        is DownloadState.Finished -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Download finished. Ready to install.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = onDismiss) {
                                        Text(text = "Close")
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            if (activity.canInstallApk()) {
                                                activity.installApk(apkFile)
                                            } else {
                                                activity.requestInstallPermission()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Install Update",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        is DownloadState.Error -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Download failed: ${state.message}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            downloadState = DownloadState.NotStarted
                                        }
                                    ) {
                                        Text(text = "Cancel")
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            downloadJob = coroutineScope.launch {
                                                startApkDownload(
                                                    urlString = downloadUrl,
                                                    outputFile = apkFile,
                                                    onProgress = { progress ->
                                                        downloadState = DownloadState.Downloading(progress)
                                                    },
                                                    onSuccess = {
                                                        downloadState = DownloadState.Finished
                                                        activity.installApk(apkFile)
                                                    },
                                                    onError = { errorMsg ->
                                                        downloadState = DownloadState.Error(errorMsg)
                                                    }
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Retry",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        private suspend fun startApkDownload(
            urlString: String,
            outputFile: File,
            onProgress: (progress: Float) -> Unit,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) {
            withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL(urlString)
                    connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.instanceFollowRedirects = true
                    connection.connect()

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        onError("Server returned HTTP ${connection.responseCode}: ${connection.responseMessage}")
                        return@withContext
                    }

                    val fileLength = connection.contentLength

                    var lastUpdateTime = 0L
                    var lastPublishedProgress = -1f

                    connection.inputStream.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val data = ByteArray(4096)
                            var total: Long = 0
                            var count: Int
                            while (isActive) {
                                count = input.read(data)
                                if (count == -1) break
                                total += count
                                if (fileLength > 0) {
                                    val currentProgress = total.toFloat() / fileLength.toFloat()
                                    val currentTime = System.currentTimeMillis()
                                    // Throttle progress updates to avoid high-frequency recompositions (minimum 1% change or 100ms interval)
                                    if (currentProgress - lastPublishedProgress >= 0.01f || (currentTime - lastUpdateTime) >= 100L) {
                                        onProgress(currentProgress)
                                        lastPublishedProgress = currentProgress
                                        lastUpdateTime = currentTime
                                    }
                                }
                                output.write(data, 0, count)
                            }
                            output.flush()
                        }
                    }

                    if (isActive) {
                        onSuccess()
                    }
                } catch (t: Throwable) {
                    logError(t)
                    onError(t.localizedMessage ?: "Unknown connection error")
                } finally {
                    connection?.disconnect()
                }
            }
        }

        suspend fun Activity.runAutoUpdate(checkAutoUpdate: Boolean = true): Boolean {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            if (!checkAutoUpdate || settingsManager.getBoolean(
                    getString(R.string.auto_update_key),
                    true
                )
            ) {
                val update = getAppUpdate()
                if (update.shouldUpdate && update.updateURL != null) {
                    runOnUiThread {
                        val currentVersion = packageName?.let {
                            packageManager.getPackageInfo(it, 0)
                        }

                        val dialog = Dialog(this)
                        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

                        val composeView = ComposeView(this).apply {
                            setContent {
                                QuickNovelTheme {
                                    UpdateDialogContent(
                                        currentVersionName = currentVersion?.versionName ?: "2.2.0",
                                        newVersionName = update.updateVersion ?: "2.2.0",
                                        changelog = update.changelog ?: "",
                                        downloadUrl = update.updateURL,
                                        activity = this@runAutoUpdate,
                                        onDismiss = { dialog.dismiss() },
                                        onDontShowAgain = if (checkAutoUpdate) {
                                            {
                                                settingsManager.edit().putBoolean(getString(R.string.auto_update_key), false).apply()
                                            }
                                        } else null
                                    )
                                }
                            }
                        }

                        dialog.setContentView(composeView)
                        setupViewTreeOwners(dialog, this, composeView)
                        dialog.window?.apply {
                            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                            setLayout(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.WRAP_CONTENT
                            )
                            setDimAmount(0.6f)
                        }
                        dialog.show()
                    }
                    return true
                }
                return false
            }
            return false
        }
    }
}