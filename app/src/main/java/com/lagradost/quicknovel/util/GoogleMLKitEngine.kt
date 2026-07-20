package com.lagradost.quicknovel.util

import android.content.Context
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.lagradost.quicknovel.mvvm.Resource
import kotlinx.coroutines.tasks.await
import android.util.Log
import com.lagradost.quicknovel.R

class GoogleMLKitEngine : TranslationEngine {
    override val name: String = "Google ML"
    override val iconRes: Int = R.drawable.ic_google_ml
    override val type: TranslationEngineType = TranslationEngineType.GoogleMLKit
    override val prefersBatching: Boolean = false
    override val recommendedBatchSize: Int = 1
    override val maxParallelRequests: Int = 5 

    private var currentTranslator: Translator? = null
    private var currentFrom: String? = null
    private var currentTo: String? = null
    
    private val TAG = "GoogleMLKitEngine"

    suspend fun isModelDownloaded(lang: String): Boolean {
        val model = TranslateRemoteModel.Builder(lang).build()
        return RemoteModelManager.getInstance().isModelDownloaded(model).await()
    }

    suspend fun downloadModel(lang: String): Resource<Unit> {
        val model = TranslateRemoteModel.Builder(lang).build()
        val conditions = com.google.mlkit.common.model.DownloadConditions.Builder()
            .build()
        return try {
            RemoteModelManager.getInstance().download(model, conditions).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e, e.localizedMessage ?: "Network or Storage Error")
        }
    }

    suspend fun deleteModel(lang: String): Resource<Unit> {
        val model = TranslateRemoteModel.Builder(lang).build()
        return try {
            RemoteModelManager.getInstance().deleteDownloadedModel(model).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e, e.localizedMessage ?: "Unknown Error")
        }
    }

    override suspend fun translate(context: Context, request: TranslationRequest): Resource<String> {
        return try {
            var fromLang = request.from
            val toLang = request.to

            // Correctly handle "Auto" source language using local Identification
            if (fromLang == "auto") {
                val languageIdentifier = LanguageIdentification.getClient()
                // Identify from first 200 chars for efficiency
                val sampleText = request.text.take(200)
                fromLang = try {
                    val result = languageIdentifier.identifyLanguage(sampleText).await()
                    if (result == "und") {
                        Log.w(TAG, "Language Undetermined, defaulting to English.")
                        TranslateLanguage.ENGLISH
                    } else {
                        Log.d(TAG, "Identified language: $result")
                        result
                    }
                } catch (e: Exception) {
                    TranslateLanguage.ENGLISH
                }
            }

            val modelsToCheck = mutableListOf<String>()
            if (fromLang != "en") modelsToCheck.add(fromLang)
            if (toLang != "en") modelsToCheck.add(toLang)

            for (lang in modelsToCheck) {
                if (!isModelDownloaded(lang)) {
                    val msg = "ML Kit model not ready: Download required for language '$lang'. " +
                        "Open Translation settings and tap Apply to download the model."
                    Log.e(TAG, msg)
                    return Resource.Failure(null, msg)
                }
            }

            if (currentTranslator == null || currentFrom != fromLang || currentTo != toLang) {
                currentTranslator?.close()
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(fromLang)
                    .setTargetLanguage(toLang)
                    .build()
                currentTranslator = Translation.getClient(options)
                currentFrom = fromLang
                currentTo = toLang

                // ── Ensure model is downloaded before translating. ──
                // If the model hasn't been downloaded yet (e.g., first-time use or
                // after a cache clear), surface a clear, user-friendly failure rather
                // than propagating a cryptic MLKitException through the call chain.
                try {
                    currentTranslator?.downloadModelIfNeeded()?.await()
                } catch (e: Exception) {
                    val msg = "ML Kit model not ready: ${e.message ?: "Download required"}. " +
                        "Open Translation settings and tap Apply to download the model."
                    Log.e(TAG, msg, e)
                    return Resource.Failure(e, msg)
                }
            }

            val result = currentTranslator?.translate(request.text)?.await()
            if (result != null) {
                Resource.Success(result)
            } else {
                Resource.Failure(null, "Translation failed")
            }
        } catch (e: Exception) {
            Resource.Failure(e, e.message ?: "Unknown Error")
        }
    }
}

