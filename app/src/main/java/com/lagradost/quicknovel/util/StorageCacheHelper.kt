package com.lagradost.quicknovel.util

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import coil3.SingletonImageLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StorageCacheHelper {

    fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val files = file.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) getFolderSize(f) else f.length()
        }
        return size
    }

    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.US, "%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    suspend fun getNetworkCacheSize(context: Context): String = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "okhttp_response_cache")
        formatSize(getFolderSize(dir))
    }

    suspend fun getImageCacheSize(context: Context): String = withContext(Dispatchers.IO) {
        val dir = context.cacheDir.resolve("qn_image_cache")
        formatSize(getFolderSize(dir))
    }

    suspend fun getCodeCacheSize(context: Context): String = withContext(Dispatchers.IO) {
        val dir = context.codeCacheDir
        formatSize(getFolderSize(dir))
    }

    suspend fun getCrashLogSize(context: Context): String = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "NeoQN_crash_log.txt")
        formatSize(if (file.exists()) file.length() else 0L)
    }

    suspend fun getChapterCacheSize(context: Context): String = withContext(Dispatchers.IO) {
        var totalSize = 0L
        context.filesDir.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name != "plugins") {
                totalSize += getFolderSize(file)
            }
        }
        formatSize(totalSize)
    }

    suspend fun getWebViewCacheSize(context: Context): String = withContext(Dispatchers.IO) {
        var totalSize = 0L
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.contains("webview", ignoreCase = true) || file.name.contains("chromium", ignoreCase = true)) {
                totalSize += getFolderSize(file)
            }
        }
        formatSize(totalSize)
    }

    suspend fun clearNetworkCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "okhttp_response_cache")
            dir.deleteRecursively()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearImageCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                SingletonImageLoader.get(context).memoryCache?.clear()
            }
            SingletonImageLoader.get(context).diskCache?.clear()
            val dir = context.cacheDir.resolve("qn_image_cache")
            dir.deleteRecursively()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearCodeCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = context.codeCacheDir
            dir.listFiles()?.forEach { it.deleteRecursively() }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearCrashLog(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "NeoQN_crash_log.txt")
            if (file.exists()) file.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearChapterCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            context.filesDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name != "plugins") {
                    file.deleteRecursively()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearWebViewCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                WebView(context).clearCache(true)
                CookieManager.getInstance().removeAllCookies(null)
            }
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.contains("webview", ignoreCase = true) || file.name.contains("chromium", ignoreCase = true)) {
                    file.deleteRecursively()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
