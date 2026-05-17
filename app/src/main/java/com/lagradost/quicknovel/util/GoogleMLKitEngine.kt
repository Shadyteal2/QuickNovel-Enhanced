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
    override val prefersBatching: Boolean = true
    override val recommendedBatchSize: Int = 10
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

            if (currentTranslator == null || currentFrom != fromLang || currentTo != toLang) {
                currentTranslator?.close()
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(fromLang)
                    .setTargetLanguage(toLang)
                    .build()
                currentTranslator = Translation.getClient(options)
                currentFrom = fromLang
                currentTo = toLang
                
                // Ensure model is downloaded (this handles both source and target)
                currentTranslator?.downloadModelIfNeeded()?.await()
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

