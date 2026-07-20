package com.lagradost.quicknovel.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.util.StorageCacheHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val _imageCacheSize = MutableStateFlow("Calculating...")
    val imageCacheSize: StateFlow<String> = _imageCacheSize.asStateFlow()

    private val _networkCacheSize = MutableStateFlow("Calculating...")
    val networkCacheSize: StateFlow<String> = _networkCacheSize.asStateFlow()

    private val _codeCacheSize = MutableStateFlow("Calculating...")
    val codeCacheSize: StateFlow<String> = _codeCacheSize.asStateFlow()

    private val _crashLogSize = MutableStateFlow("Calculating...")
    val crashLogSize: StateFlow<String> = _crashLogSize.asStateFlow()

    private val _chapterCacheSize = MutableStateFlow("Calculating...")
    val chapterCacheSize: StateFlow<String> = _chapterCacheSize.asStateFlow()

    private val _webViewCacheSize = MutableStateFlow("Calculating...")
    val webViewCacheSize: StateFlow<String> = _webViewCacheSize.asStateFlow()

    private val _wallpaperCacheSize = MutableStateFlow("Calculating...")
    val wallpaperCacheSize: StateFlow<String> = _wallpaperCacheSize.asStateFlow()

    fun loadSizes(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            _imageCacheSize.value = "Calculating..."
            _imageCacheSize.value = StorageCacheHelper.getImageCacheSize(appContext)
        }
        viewModelScope.launch(Dispatchers.IO) {
            _networkCacheSize.value = "Calculating..."
            _networkCacheSize.value = StorageCacheHelper.getNetworkCacheSize(appContext)
        }
        viewModelScope.launch(Dispatchers.IO) {
            _codeCacheSize.value = "Calculating..."
            _codeCacheSize.value = StorageCacheHelper.getCodeCacheSize(appContext)
        }
        viewModelScope.launch(Dispatchers.IO) {
            _crashLogSize.value = "Calculating..."
            _crashLogSize.value = StorageCacheHelper.getCrashLogSize(appContext)
        }
        viewModelScope.launch(Dispatchers.IO) {
            _chapterCacheSize.value = "Calculating..."
            _chapterCacheSize.value = StorageCacheHelper.getChapterCacheSize(appContext)
        }
        viewModelScope.launch(Dispatchers.IO) {
            _webViewCacheSize.value = "Calculating..."
            _webViewCacheSize.value = StorageCacheHelper.getWebViewCacheSize(appContext)
        }
        viewModelScope.launch(Dispatchers.IO) {
            _wallpaperCacheSize.value = "Calculating..."
            _wallpaperCacheSize.value = StorageCacheHelper.getWallpaperCacheSize(appContext)
        }
    }
}
