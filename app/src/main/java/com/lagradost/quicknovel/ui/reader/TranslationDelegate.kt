package com.lagradost.quicknovel.ui.reader

import android.content.Context
import android.text.Spanned
import android.util.Log
import androidx.core.text.toSpanned
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.lagradost.quicknovel.TextSpan
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Delegate responsible for translation logic.
 * Decouples translation dependencies from the main ReadActivityViewModel.
 */
class TranslationDelegate(
    private val context: Context,
    private val state: StateFlow<ReaderState>,
    private val onAction: (ReaderAction) -> Unit,
    private val scope: CoroutineScope
) : Closeable {

    private var mlTranslator: Translator? = null
    private var sessionTranslationCancelled = false

    fun stopTranslation() {
        sessionTranslationCancelled = true
        close()
    }

    override fun close() {
        mlTranslator?.close()
        mlTranslator = null
    }

    /**
     * Checks if translation models need to be downloaded.
     */
    suspend fun requireMLDownload(from: String, to: String): Boolean {
        if (from == to || from.isBlank() || to.isBlank()) return false

        val modelManager = RemoteModelManager.getInstance()
        val modelsToCheck = mutableListOf<String>()
        if (from != "en") modelsToCheck.add(from)
        if (to != "en") modelsToCheck.add(to)

        for (model in modelsToCheck) {
            val isDownloaded = try {
                Tasks.await(modelManager.isModelDownloaded(TranslateRemoteModel.Builder(model).build()))
            } catch (e: Exception) {
                false
            }
            if (!isDownloaded) return true
        }
        return false
    }

    /**
     * Entry point for translation.
     * Logic migrated from ReadActivityViewModel for Phase 1.
     */
    suspend fun translate(
        text: Spanned,
        spans: List<TextSpan>,
        from: String,
        to: String,
        loading: (Triple<Int, Int, Int>) -> Unit
    ): Pair<Spanned, ArrayList<TextSpan>> {
        if (from == to) return text to ArrayList(spans)

        val builder = StringBuilder()
        val out = ArrayList<TextSpan>()
        val translator = getTranslator(from, to, true) ?: return text to ArrayList(spans)

        sessionTranslationCancelled = false

        try {
            for (i in spans.indices) {
                currentCoroutineContext().ensureActive()
                if (sessionTranslationCancelled) break

                loading.invoke(Triple(spans[i].index, i + 1, spans.size))

                val translated = try {
                    withContext(Dispatchers.IO) {
                        Tasks.await(translator.translate(spans[i].text.toString()))
                    }
                } catch (t: Throwable) {
                    spans[i].text.toString()
                }

                val start = builder.length
                builder.append(translated).append("\n\n")
                out.add(
                    TextSpan(
                        translated.toSpanned(),
                        start,
                        builder.length - 2,
                        spans[i].index,
                        spans[i].innerIndex
                    )
                )
            }
            return builder.toString().toSpanned() to out
        } catch (t: Throwable) {
            Log.e("TranslationDelegate", "Translation error", t)
            return text to ArrayList(spans)
        }
    }

    suspend fun getTranslator(from: String, to: String, allowDownload: Boolean): Translator? {
        if (from == to) return null
        if (mlTranslator != null) return mlTranslator

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(from)
            .setTargetLanguage(to)
            .build()

        val client = Translation.getClient(options)
        if (allowDownload) {
            withContext(Dispatchers.IO) {
                try {
                    Tasks.await(client.downloadModelIfNeeded(), 30, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.e("TranslationDelegate", "Model download failed", e)
                }
            }
        }
        mlTranslator = client
        return client
    }
}
