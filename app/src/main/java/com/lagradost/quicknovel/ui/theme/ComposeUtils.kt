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
    return remember(data) {
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
                        if (!resolvedData.headers.isNullOrEmpty()) {
                            builder.httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
                                resolvedData.headers.forEach { (key, value) ->
                                    headerBuilder[key] = value
                                }
                            }.build())
                        }
                    }
                }
            }
            is String -> {
                val uiImage = img(resolvedData)
                when (uiImage) {
                    is UiImage.Bitmap -> builder.data(uiImage.bitmap)
                    is UiImage.Drawable -> builder.data(uiImage.resId)
                    is UiImage.Image -> {
                        builder.data(uiImage.url)
                        if (!uiImage.headers.isNullOrEmpty()) {
                            builder.httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
                                uiImage.headers.forEach { (key, value) ->
                                    headerBuilder[key] = value
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
                    if (!resolvedData.headers.isNullOrEmpty()) {
                        builder.httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
                            resolvedData.headers.forEach { (key, value) ->
                                headerBuilder[key] = value
                            }
                        }.build())
                    }
                }
            }
        }
        is String -> {
            val uiImage = img(resolvedData)
            when (uiImage) {
                is UiImage.Bitmap -> builder.data(uiImage.bitmap)
                is UiImage.Drawable -> builder.data(uiImage.resId)
                is UiImage.Image -> {
                    builder.data(uiImage.url)
                    if (!uiImage.headers.isNullOrEmpty()) {
                        builder.httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
                            uiImage.headers.forEach { (key, value) ->
                                headerBuilder[key] = value
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
