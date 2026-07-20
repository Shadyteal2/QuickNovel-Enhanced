package com.lagradost.quicknovel.ui.settings

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.WallWidgyResponse
import com.lagradost.quicknovel.util.WallWidgyService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class WallpaperGalleryViewModel : ViewModel() {

    private val _wallpapersState = MutableStateFlow<Resource<WallWidgyResponse>>(Resource.Loading())
    val wallpapersState: StateFlow<Resource<WallWidgyResponse>> = _wallpapersState.asStateFlow()

    private val _wallpapers = MutableStateFlow<List<String>>(emptyList())
    val wallpapers: StateFlow<List<String>> = _wallpapers.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _downloadState = MutableStateFlow<Resource<String>?>(null)
    val downloadState: StateFlow<Resource<String>?> = _downloadState.asStateFlow()

    private val _saveToGalleryState = MutableStateFlow<Resource<String>?>(null)
    val saveToGalleryState: StateFlow<Resource<String>?> = _saveToGalleryState.asStateFlow()

    var selectedCategory = MutableStateFlow("all")
    var selectedColor = MutableStateFlow("all")

    private var currentPage = 1

    fun loadWallpapers() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _wallpapersState.value = Resource.Loading()
            _wallpapers.value = emptyList()
            currentPage = 1
            val category = selectedCategory.value
            val color = selectedColor.value
            
            val response = WallWidgyService.fetchWallpapers(
                category = if (category == "all") null else category,
                color = if (color == "all") null else color,
                count = 10,
                type = "mobile",
                page = 1
            )
            if (response is Resource.Success) {
                _wallpapers.value = response.value.wallpapers ?: emptyList()
            }
            _wallpapersState.value = response
        }
    }

    fun loadMoreWallpapers() {
        if (_isLoadingMore.value) return
        val currentList = _wallpapers.value
        if (_wallpapersState.value !is Resource.Success) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextPage = currentPage + 1
            val category = selectedCategory.value
            val color = selectedColor.value
            
            val response = WallWidgyService.fetchWallpapers(
                category = if (category == "all") null else category,
                color = if (color == "all") null else color,
                count = 10,
                type = "mobile",
                page = nextPage
            )
            if (response is Resource.Success) {
                val newUrls = response.value.wallpapers ?: emptyList()
                if (newUrls.isNotEmpty()) {
                    val updatedList = (currentList + newUrls).distinct()
                    _wallpapers.value = updatedList
                    currentPage = nextPage
                }
            }
            _isLoadingMore.value = false
        }
    }

    fun selectCategory(category: String) {
        selectedCategory.value = category
        loadWallpapers()
    }

    fun selectColor(color: String) {
        selectedColor.value = color
        loadWallpapers()
    }

    fun clearDownloadState() {
        _downloadState.value = null
    }

    fun clearSaveToGalleryState() {
        _saveToGalleryState.value = null
    }

    fun setWallpaper(url: String, context: Context, onComplete: () -> Unit) = ioSafe {
        _downloadState.value = Resource.Loading()
        try {
            val response = app.get(url)
            val bytes = response.okhttpResponse.body.bytes()
            
            val wallpapersDir = File(context.filesDir, "wallpapers")
            if (!wallpapersDir.exists()) wallpapersDir.mkdirs()
            
            // Generate clean filename
            val extension = url.substringAfterLast(".", "jpg").substringBefore("?")
            val sanitisedExtension = if (extension.length in 3..4 && extension.all { it.isLetter() }) extension else "jpg"
            val filename = "wallpaper_${System.currentTimeMillis()}.$sanitisedExtension"
            val targetFile = File(wallpapersDir, filename)
            
            targetFile.writeBytes(bytes)
            
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val currentBgUri = sharedPrefs.getString(context.getString(R.string.background_image_key), null)
            
            // Clean up old wallpapers, exempting the active one
            cleanUpOldWallpapers(context, currentBgUri)
            
            val localUri = Uri.fromFile(targetFile).toString()
            sharedPrefs.edit().putString(context.getString(R.string.background_image_key), localUri).apply()
            
            _downloadState.value = Resource.Success(localUri)
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        } catch (e: Throwable) {
            com.lagradost.quicknovel.mvvm.logError(e)
            _downloadState.value = Resource.Failure(e, "Download failed: ${e.localizedMessage}")
        }
    }

    fun saveToGallery(url: String, context: Context) = ioSafe {
        _saveToGalleryState.value = Resource.Loading()
        try {
            val response = app.get(url)
            val bytes = response.okhttpResponse.body.bytes()
            
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "WallWidgy_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NeoQN")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            
            val resolver = context.contentResolver
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { out ->
                    out.write(bytes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                _saveToGalleryState.value = Resource.Success("Wallpaper saved to Gallery!")
            } else {
                _saveToGalleryState.value = Resource.Failure(null, "Failed to create MediaStore entry")
            }
        } catch (e: Throwable) {
            com.lagradost.quicknovel.mvvm.logError(e)
            _saveToGalleryState.value = Resource.Failure(e, "Save failed: ${e.localizedMessage}")
        }
    }

    private fun cleanUpOldWallpapers(context: Context, currentBgUri: String?) {
        val wallpapersDir = File(context.filesDir, "wallpapers")
        if (!wallpapersDir.exists()) return
        val files = wallpapersDir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= 5) return

        val sorted = files.sortedBy { it.lastModified() }
        val deleteCount = files.size - 5
        var deleted = 0
        for (file in sorted) {
            if (deleted >= deleteCount) break
            
            // Skip deletion if this file is the active wallpaper
            val isActive = currentBgUri != null && (
                currentBgUri == file.toURI().toString() || 
                currentBgUri == Uri.fromFile(file).toString() || 
                currentBgUri.contains(file.name)
            )
            
            if (!isActive) {
                file.delete()
                deleted++
            }
        }
    }
}
