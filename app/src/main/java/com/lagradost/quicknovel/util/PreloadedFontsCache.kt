package com.lagradost.quicknovel.util

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.ReaderPrefs
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object PreloadedFontsCache {
    private const val TAG = "PreloadedFontsCache"
    private val cache = ConcurrentHashMap<String, Typeface>()

    /**
     * Preloads the active reader font on a background thread to prevent FOUT.
     */
    fun preload(context: Context) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val fontName = sp.getString(ReaderPrefs.FONT, "") ?: ""
            if (fontName.isNotEmpty()) {
                preloadFont(context, fontName)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to preload active font", t)
        }
    }

    /**
     * Attempts to load a typeface from system fonts, custom fonts, or assets.
     */
    fun preloadFont(context: Context, fontName: String) {
        if (fontName.isEmpty() || cache.containsKey(fontName)) return
        try {
            // 1. Try as a system font or custom font file
            val found = UIHelper.systemFonts.firstOrNull { it.name == fontName }
            val fontFile = if (found != null) {
                found
            } else {
                val file = File(File(context.filesDir, "fonts"), fontName)
                if (file.exists()) file else null
            }

            if (fontFile != null) {
                val typeface = Typeface.createFromFile(fontFile)
                if (typeface != null) {
                    cache[fontName] = typeface
                    Log.d(TAG, "Preloaded font from file: $fontName")
                    return
                }
            }

            // 2. Fallback: Try loading from assets/fonts/
            try {
                val typeface = Typeface.createFromAsset(context.assets, "fonts/$fontName")
                if (typeface != null) {
                    cache[fontName] = typeface
                    Log.d(TAG, "Preloaded font from assets: $fontName")
                }
            } catch (_: Exception) {
                // Ignore if not present in assets
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error preloading font: $fontName", t)
        }
    }

    /**
     * Gets a preloaded Typeface or null if not loaded.
     */
    fun get(fontName: String): Typeface? {
        if (fontName.isEmpty()) return null
        return cache[fontName]
    }
}
