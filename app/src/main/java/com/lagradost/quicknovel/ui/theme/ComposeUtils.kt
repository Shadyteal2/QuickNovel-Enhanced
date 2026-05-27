package com.lagradost.quicknovel.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.ui.img
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.BaseApplication.Companion.getActivity

private val fileExistenceCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
private val fileExistenceTimestamp = java.util.concurrent.ConcurrentHashMap<String, Long>()

fun checkFileExistsCached(file: java.io.File): Boolean {
    val path = file.absolutePath
    val now = System.currentTimeMillis()
    val cachedValue = fileExistenceCache[path]
    if (cachedValue != null) {
        if (cachedValue == true) {
            return true
        } else {
            val timestamp = fileExistenceTimestamp[path] ?: 0L
            if (now - timestamp < 5000L) {
                return false
            }
        }
    }
    val exists = file.exists() && file.length() > 0L
    fileExistenceCache[path] = exists
    fileExistenceTimestamp[path] = now
    return exists
}

@Composable
fun rememberImageRequest(data: Any?): ImageRequest {
    val context = LocalContext.current
    
    val stableKey = remember(data) {
        when (data) {
            is ResultCached -> data.poster to (data.apiName + data.name)
            is DownloadFragment.DownloadDataLoaded -> data.posterUrl to (data.apiName + data.name)
            is com.lagradost.quicknovel.ui.foryou.recommendation.NovelVector -> data.posterUrl to (data.apiName + data.name)
            is com.lagradost.quicknovel.SearchResponse -> data.posterUrl to (data.apiName + data.name)
            else -> data
        }
    }

    // Asynchronously resolve file existence checks on Dispatchers.IO to prevent main-thread lag
    val resolvedDataState = androidx.compose.runtime.produceState<Any?>(initialValue = null as Any?, key1 = stableKey) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val resolved = when (data) {
                is ResultCached -> {
                    val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
                    val filesDir = act?.filesDir?.toString()
                    var file: java.io.File? = null
                    if (filesDir != null) {
                        val filePath = BookDownloader2Helper.getFilenameIMG(
                            BookDownloader2Helper.sanitizeFilename(data.apiName),
                            BookDownloader2Helper.sanitizeFilename(data.author ?: ""),
                            BookDownloader2Helper.sanitizeFilename(data.name)
                        )
                        val f = java.io.File(filesDir + filePath)
                        if (f.exists() && f.length() > 0L) {
                            file = f
                        }
                    }
                    file ?: data.poster
                }
                is DownloadFragment.DownloadDataLoaded -> {
                    val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
                    val filesDir = act?.filesDir?.toString()
                    var file: java.io.File? = null
                    if (filesDir != null) {
                        val filePath = BookDownloader2Helper.getFilenameIMG(
                            BookDownloader2Helper.sanitizeFilename(data.apiName),
                            BookDownloader2Helper.sanitizeFilename(data.author ?: ""),
                            BookDownloader2Helper.sanitizeFilename(data.name)
                        )
                        val f = java.io.File(filesDir + filePath)
                        if (f.exists() && f.length() > 0L) {
                            file = f
                        }
                    }
                    file ?: data.posterUrl
                }
                is com.lagradost.quicknovel.ui.foryou.recommendation.NovelVector -> {
                    val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
                    val filesDir = act?.filesDir?.toString()
                    var file: java.io.File? = null
                    if (filesDir != null) {
                        val filePath = BookDownloader2Helper.getFilenameIMG(
                            BookDownloader2Helper.sanitizeFilename(data.apiName),
                            "",
                            BookDownloader2Helper.sanitizeFilename(data.name)
                        )
                        val f = java.io.File(filesDir + filePath)
                        if (f.exists() && f.length() > 0L) {
                            file = f
                        }
                    }
                    file ?: data.posterUrl
                }
                is com.lagradost.quicknovel.SearchResponse -> {
                    val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
                    val filesDir = act?.filesDir?.toString()
                    var file: java.io.File? = null
                    if (filesDir != null) {
                        val filePath = BookDownloader2Helper.getFilenameIMG(
                            BookDownloader2Helper.sanitizeFilename(data.apiName),
                            "",
                            BookDownloader2Helper.sanitizeFilename(data.name)
                        )
                        val f = java.io.File(filesDir + filePath)
                        if (f.exists() && f.length() > 0L) {
                            file = f
                        }
                    }
                    file ?: if (data.posterHeaders != null) {
                        UiImage.Image(data.posterUrl ?: "", data.posterHeaders)
                    } else {
                        data.posterUrl
                    }
                }
                else -> data
            }
            value = resolved
        }
    }

    val resolvedData = resolvedDataState.value

    return remember(resolvedData) {
        val builder = ImageRequest.Builder(context)
            .crossfade(200)

        when (resolvedData) {
            is java.io.File -> {
                builder.data(resolvedData)
            }
            is UiImage -> {
                when (resolvedData) {
                    is UiImage.Bitmap -> builder.data(resolvedData.bitmap)
                    is UiImage.Drawable -> builder.data(resolvedData.resId)
                    is UiImage.Image -> {
                        builder.data(resolvedData.url)
                        val headersMap = resolvedData.headers
                        if (!headersMap.isNullOrEmpty()) {
                            builder.httpHeaders(coil3.network.NetworkHeaders.Builder().also { headerBuilder ->
                                for (entry in headersMap.entries) {
                                    headerBuilder.set(entry.key, entry.value)
                                }
                            }.build())
                        }
                    }
                }
            }
            is String -> {
                val uiImage = img(resolvedData as String)
                when (uiImage) {
                    is UiImage.Bitmap -> builder.data(uiImage.bitmap)
                    is UiImage.Drawable -> builder.data(uiImage.resId)
                    is UiImage.Image -> {
                        builder.data(uiImage.url)
                        val headersMap = uiImage.headers
                        if (!headersMap.isNullOrEmpty()) {
                            builder.httpHeaders(coil3.network.NetworkHeaders.Builder().also { headerBuilder ->
                                for (entry in headersMap.entries) {
                                    headerBuilder.set(entry.key, entry.value)
                                }
                            }.build())
                        }
                    }
                    else -> builder.data(resolvedData)
                }
            }
            else -> {
                builder.data(resolvedData ?: "")
            }
        }
        builder.build()
    }
}

fun buildImageRequest(context: android.content.Context, data: Any?): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .crossfade(200)

    val resolvedData = when (data) {
        is ResultCached -> {
            val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
            val filesDir = act?.filesDir?.toString()
            var file: java.io.File? = null
            if (filesDir != null) {
                val filePath = BookDownloader2Helper.getFilenameIMG(
                    BookDownloader2Helper.sanitizeFilename(data.apiName),
                    BookDownloader2Helper.sanitizeFilename(data.author ?: ""),
                    BookDownloader2Helper.sanitizeFilename(data.name)
                )
                val f = java.io.File(filesDir + filePath)
                if (checkFileExistsCached(f)) {
                    file = f
                }
            }
            file ?: data.poster
        }
        is DownloadFragment.DownloadDataLoaded -> {
            val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
            val filesDir = act?.filesDir?.toString()
            var file: java.io.File? = null
            if (filesDir != null) {
                val filePath = BookDownloader2Helper.getFilenameIMG(
                    BookDownloader2Helper.sanitizeFilename(data.apiName),
                    BookDownloader2Helper.sanitizeFilename(data.author ?: ""),
                    BookDownloader2Helper.sanitizeFilename(data.name)
                )
                val f = java.io.File(filesDir + filePath)
                if (checkFileExistsCached(f)) {
                    file = f
                }
            }
            file ?: data.posterUrl
        }
        is com.lagradost.quicknovel.ui.foryou.recommendation.NovelVector -> {
            val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
            val filesDir = act?.filesDir?.toString()
            var file: java.io.File? = null
            if (filesDir != null) {
                val filePath = BookDownloader2Helper.getFilenameIMG(
                    BookDownloader2Helper.sanitizeFilename(data.apiName),
                    "",
                    BookDownloader2Helper.sanitizeFilename(data.name)
                )
                val f = java.io.File(filesDir + filePath)
                if (checkFileExistsCached(f)) {
                    file = f
                }
            }
            file ?: data.posterUrl
        }
        is com.lagradost.quicknovel.SearchResponse -> {
            val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
            val filesDir = act?.filesDir?.toString()
            var file: java.io.File? = null
            if (filesDir != null) {
                val filePath = BookDownloader2Helper.getFilenameIMG(
                    BookDownloader2Helper.sanitizeFilename(data.apiName),
                    "",
                    BookDownloader2Helper.sanitizeFilename(data.name)
                )
                val f = java.io.File(filesDir + filePath)
                if (checkFileExistsCached(f)) {
                    file = f
                }
            }
            
            if (file != null) file else {
                if (data.posterHeaders != null) {
                    UiImage.Image(data.posterUrl ?: "", data.posterHeaders)
                } else {
                    data.posterUrl
                }
            }
        }
        else -> data
    }

    when (resolvedData) {
        is java.io.File -> {
            builder.data(resolvedData)
        }
        is UiImage -> {
            when (resolvedData) {
                is UiImage.Bitmap -> builder.data(resolvedData.bitmap)
                is UiImage.Drawable -> builder.data(resolvedData.resId)
                is UiImage.Image -> {
                    builder.data(resolvedData.url)
                    val headersMap = resolvedData.headers
                    if (!headersMap.isNullOrEmpty()) {
                        builder.httpHeaders(coil3.network.NetworkHeaders.Builder().also { headerBuilder ->
                            for (entry in headersMap.entries) {
                                headerBuilder.set(entry.key, entry.value)
                            }
                        }.build())
                    }
                }
            }
        }
        is String -> {
            val uiImage = img(resolvedData as String)
            when (uiImage) {
                is UiImage.Bitmap -> builder.data(uiImage.bitmap)
                is UiImage.Drawable -> builder.data(uiImage.resId)
                is UiImage.Image -> {
                    builder.data(uiImage.url)
                    val headersMap = uiImage.headers
                    if (!headersMap.isNullOrEmpty()) {
                        builder.httpHeaders(coil3.network.NetworkHeaders.Builder().also { headerBuilder ->
                            for (entry in headersMap.entries) {
                                headerBuilder.set(entry.key, entry.value)
                            }
                        }.build())
                    }
                }
                else -> builder.data(resolvedData)
            }
        }
        else -> {
            builder.data(resolvedData ?: "")
        }
    }
    return builder.build()
}
