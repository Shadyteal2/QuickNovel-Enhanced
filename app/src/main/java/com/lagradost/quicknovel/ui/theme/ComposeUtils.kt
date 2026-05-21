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

@Composable
fun rememberImageRequest(data: Any?): ImageRequest {
    val context = LocalContext.current
    return remember(data) {
        val builder = ImageRequest.Builder(context)
            .crossfade(200)

        val uiImage = when (data) {
            is UiImage -> data
            is ResultCached -> {
                val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
                val bitmap = BookDownloader2Helper.getCachedBitmap(act, data.apiName, data.author, data.name)
                if (bitmap != null) {
                    UiImage.Bitmap(bitmap)
                } else {
                    img(data.poster)
                }
            }
            is DownloadFragment.DownloadDataLoaded -> {
                val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
                val bitmap = BookDownloader2Helper.getCachedBitmap(act, data.apiName, data.author, data.name)
                if (bitmap != null) {
                    UiImage.Bitmap(bitmap)
                } else {
                    img(data.posterUrl)
                }
            }
            is com.lagradost.quicknovel.ui.foryou.recommendation.NovelVector -> {
                val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
                val bitmap = BookDownloader2Helper.getCachedBitmap(act, data.apiName, null, data.name)
                if (bitmap != null) {
                    UiImage.Bitmap(bitmap)
                } else {
                    img(data.posterUrl)
                }
            }
            is String -> img(data)
            else -> null
        }

        when (uiImage) {
            is UiImage.Bitmap -> {
                builder.data(uiImage.bitmap)
            }
            is UiImage.Drawable -> {
                builder.data(uiImage.resId)
            }
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
            else -> {
                builder.data("")
            }
        }
        builder.build()
    }
}
