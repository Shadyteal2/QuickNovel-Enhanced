package com.lagradost.quicknovel.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.ReadActivityViewModel
import android.util.Log
import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.DataStore.getKey

object TranslationEnginesManager {
    val tones = listOf("Neutral", "Formal", "Casual", "Professional", "Humorous", "Friendly", "Informal")
    val contentTypes = listOf("General", "Literary", "Technical", "Conversation", "Poetry", "Academic", "Business", "Creative")
    
    private val engines = mutableMapOf<TranslationEngineType, TranslationEngine>()

    init {
        registerEngine(GoogleMLKitEngine())
    }

    fun getEngine(type: TranslationEngineType): TranslationEngine? {
        return engines[type]
    }

    fun registerEngine(engine: TranslationEngine) {
        engines[engine.type] = engine
    }

    fun getActiveEngine(context: Context): TranslationEngine? {
        val engineKey = context.getString(R.string.translation_engine_key)
        val engineTypeStr = BaseApplication.getKey<String>(engineKey, "0") ?: "0"
        
        val engineType = try {
            TranslationEngineType.fromInt(engineTypeStr.toInt())
        } catch (e: Exception) {
            TranslationEngineType.values().find { it.name.equals(engineTypeStr, ignoreCase = true) } ?: TranslationEngineType.None
        }
        
        return getEngine(engineType)
    }

    fun getEngineModel(context: Context, type: TranslationEngineType): String {
        return ""
    }

    fun getTargetLanguage(context: Context): String {
        return BaseApplication.getKey<String>(PrefKeys.TRANSLATION_TO_LANG, "en") ?: "en"
    }

    fun getOriginLanguage(context: Context): String {
        return BaseApplication.getKey<String>(PrefKeys.TRANSLATION_FROM_LANG, "auto") ?: "auto"
    }

    fun getTranslationTone(context: Context): String {
        return BaseApplication.getKey<String>(PrefKeys.TRANSLATION_TONE, "Neutral") ?: "Neutral"
    }

    fun getTranslationContentType(context: Context): String {
        return BaseApplication.getKey<String>(PrefKeys.TRANSLATION_CONTENT_TYPE, "General") ?: "General"
    }

    suspend fun translate(context: Context, text: String): Resource<String> {
        val engine = getActiveEngine(context) ?: return Resource.Failure(null, "No engine selected")
        val from = getOriginLanguage(context)
        val to = getTargetLanguage(context)
        
        val systemInstruction = if (engine.supportsSystemInstructions) {
            "Translate the following novel text from $from to $to. Maintain the paragraph structure. " +
            "Tone: ${getTranslationTone(context)}, Type: ${getTranslationContentType(context)}."
        } else null
        
        val request = TranslationRequest(
            text = text,
            from = from,
            to = to,
            systemInstruction = systemInstruction
        )
        
        return engine.translate(context, request)
    }
}

