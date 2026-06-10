package com.lagradost.quicknovel.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class CloudAITranslator : TranslationEngine {
    override val name: String = "Cloud AI (API Key)"
    override val iconRes: Int = R.drawable.translate_24px
    override val type: TranslationEngineType = TranslationEngineType.CloudAI
    override val prefersBatching: Boolean = true
    override val supportsSystemInstructions: Boolean = false

    // ─── Static caps — actual runtime values are read from prefs in translate() ──────
    // These interface properties must stay val, so we keep safe defaults here.
    // The orchestrator in ReadActivityViewModel reads engine.recommendedBatchSize /
    // engine.maxParallelRequests once before the loop, so we expose context-aware
    // helpers that the orchestrator should call instead (see companion object below).
    override val recommendedBatchSize: Int get() = DEFAULT_BATCH_SIZE
    override val maxParallelRequests: Int get() = DEFAULT_MAX_PARALLEL
    override val maxCharsPerRequest: Int = 8000

    companion object {
        const val DEFAULT_BATCH_SIZE   = 5   // safe for free-tier LLMs
        const val DEFAULT_MAX_PARALLEL = 1   // no burst — sequential by default
        const val DEFAULT_DELAY_MS     = 2000L // 2 s gap between sequential requests

        /** Returns the user-configured batch size (falls back to default). */
        fun getBatchSize(context: Context): Int {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getInt(PrefKeys.CLOUD_AI_BATCH_SIZE, DEFAULT_BATCH_SIZE)
                .coerceIn(1, 20)
        }

        /** Returns the user-configured max parallel requests (falls back to default). */
        fun getMaxParallel(context: Context): Int {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getInt(PrefKeys.CLOUD_AI_MAX_PARALLEL, DEFAULT_MAX_PARALLEL)
                .coerceIn(1, 5)
        }

        /** Returns the user-configured inter-request delay in milliseconds. */
        fun getDelayMs(context: Context): Long {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getInt(PrefKeys.CLOUD_AI_DELAY_MS, DEFAULT_DELAY_MS.toInt())
                .toLong()
                .coerceIn(0L, 30_000L)
        }
    }

    override suspend fun translate(context: Context, request: TranslationRequest): Resource<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val apiUrl   = prefs.getString("pref_translation_api_url", "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")?.trim() ?: ""
        val apiKey   = prefs.getString("pref_translation_api_key", "")?.trim() ?: ""
        val apiModel = prefs.getString("pref_translation_api_model", "")?.trim() ?: ""

        if (apiUrl.isEmpty() || apiKey.isEmpty()) {
            return Resource.Failure(null, "Cloud AI Settings incomplete. Please verify API URL and Key in Settings.")
        }

        // ─── Cache lookup ─────────────────────────────────────────────────────────
        val hash      = getMd5Hash(request.text)
        val cacheDir  = File(context.filesDir, "cloud_ai_cache")
        val cacheFile = File(cacheDir, "${hash}_${request.from}_to_${request.to}.txt")

        if (cacheFile.exists()) {
            try {
                val cachedText = cacheFile.readText()
                if (cachedText.isNotEmpty()) return Resource.Success(cachedText)
            } catch (_: Throwable) { /* fail silently, fall through to API */ }
        }

        // ─── URL & model resolution ───────────────────────────────────────────────
        val resolvedModel = apiModel.ifEmpty { "gemini-2.0-flash" }
        val resolvedUrl   = if (apiUrl.contains("generativelanguage.googleapis.com")) {
            apiUrl.replace(Regex("""/models/[^:]+"""), "/models/$resolvedModel")
        } else {
            apiUrl
        }

        val isGemini = resolvedUrl.contains("googleapis.com")

        val finalUrl = if (isGemini) {
            if (!resolvedUrl.contains("key=", ignoreCase = true)) {
                val sep = if (resolvedUrl.contains("?")) "&" else "?"
                "$resolvedUrl${sep}key=$apiKey"
            } else resolvedUrl
        } else resolvedUrl

        // ─── Headers ──────────────────────────────────────────────────────────────
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "application/json"
        if (isGemini) {
            headers["x-goog-api-key"] = apiKey
        } else {
            headers["Authorization"] = "Bearer $apiKey"
            headers["HTTP-Referer"] = "https://github.com/Shadyteal2/QuickNovel-Enhanced"
            headers["X-Title"] = "QuickNovel-Enhanced"
        }

        // ─── JSON payload (serialised on Default dispatcher — CPU-bound) ──────────
        val jsonString = withContext(Dispatchers.Default) {
            val payload = if (isGemini) {
                val systemInstructionText = "You are a professional novel translator. Translate the text from '${request.from}' to '${request.to}' fluently and naturally. Output ONLY the translated text — no greetings, commentary, or explanations. Maintain the paragraph structure exactly."
                GeminiRequest(
                    systemInstruction = GeminiContent(
                        parts = listOf(GeminiPart(text = systemInstructionText))
                    ),
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = request.text)))),
                    generationConfig = GeminiGenerationConfig(temperature = 0.1)
                )
            } else {
                val prompt = buildString {
                    append("You are a professional novel translator. Translate the following text from '${request.from}' to '${request.to}' fluently and naturally. ")
                    append("Strict rules: ")
                    append("1. Preserve the exact paragraph structure and line breaks of the source. ")
                    append("2. Do NOT skip or summarise any sentence. ")
                    append("3. Output ONLY the translated text — no greetings, commentary, or explanations. ")
                    if (request.bridgeText?.isNotEmpty() == true) {
                        append("4. Keep the separator token \"${request.bridgeText.trim()}\" exactly as-is between paragraphs. ")
                    }
                    append("Translate the text enclosed in the XML tags below:\n")
                    append("<text_to_translate>\n")
                    append(request.text)
                    append("\n</text_to_translate>")
                }
                val model = apiModel.ifEmpty { "google/gemini-2.5-flash" }
                OpenRouterRequest(
                    model    = model,
                    messages  = listOf(OpenRouterMessage(role = "user", content = prompt)),
                    temperature = 0.1
                )
            }
            DataStore.mapper.writeValueAsString(payload)
        }

        // ─── HTTP POST (IO dispatcher) ────────────────────────────────────────────
        val sanitizedUrl = finalUrl.replace(apiKey, "REDACTED_API_KEY")
        val sanitizedHeaders = headers.mapValues { (k, v) ->
            if (k.equals("x-goog-api-key", ignoreCase = true) || k.equals("Authorization", ignoreCase = true)) "REDACTED_API_KEY" else v
        }
        android.util.Log.d("CloudAITranslator", "Request details: url=$sanitizedUrl, model=$resolvedModel, headers=$sanitizedHeaders")

        val postResult = try {
            withContext(Dispatchers.IO) {
                val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())
                val response    = MainActivity.app.post(
                    url         = finalUrl,
                    headers     = headers,
                    requestBody = requestBody
                )
                Resource.Success(response)
            }
        } catch (t: Throwable) {
            Resource.Failure(t, t.message ?: "Connection error")
        }

        // ─── User-configured inter-request delay (rate-limit guard) ───────────────
        // Applied AFTER the network call so it only paces consecutive requests,
        // not the first request in a cold start. 0 delay = no throttle.
        val delayMs = getDelayMs(context)
        if (delayMs > 0L) {
            delay(delayMs)
        }

        // ─── Response handling ────────────────────────────────────────────────────
        return when (postResult) {
            is Resource.Failure -> Resource.Failure(postResult.cause, postResult.errorString)
            is Resource.Loading -> Resource.Failure(null, "Unexpected loading state")
            is Resource.Success -> {
                val response = postResult.value
                val bodyText = response.text ?: ""

                if (response.code == 429) {
                    android.util.Log.e("CloudAITranslator", "429 Rate Limit Response body: $bodyText")
                    // Surface actionable guidance instead of a bare status code
                    return Resource.Failure(
                        null,
                        "API rate limit hit (429): $bodyText. Try increasing Request Delay in Translation Settings, or reduce Parallel Requests to 1."
                    )
                }
                if (!response.isSuccessful) {
                    android.util.Log.e("CloudAITranslator", "Error Response (HTTP ${response.code}): $bodyText")
                    return Resource.Failure(null, "API Error: HTTP ${response.code} — $bodyText")
                }

                // Parse response on Default dispatcher (CPU-bound JSON work)
                withContext(Dispatchers.Default) {
                    try {
                        val root           = DataStore.mapper.readTree(response.text)
                        val translatedText = if (isGemini) {
                            root.get("candidates")
                                ?.get(0)
                                ?.get("content")
                                ?.get("parts")
                                ?.get(0)
                                ?.get("text")
                                ?.asText()
                        } else {
                            root.get("choices")
                                ?.get(0)
                                ?.get("message")
                                ?.get("content")
                                ?.asText()
                        }

                        if (!translatedText.isNullOrBlank()) {
                            val cleanText = translatedText.trim()
                            try {
                                cacheDir.mkdirs()
                                cacheFile.writeText(cleanText)
                            } catch (_: Exception) { /* fail silently */ }
                            Resource.Success(cleanText)
                        } else {
                            Resource.Failure(null, "Cloud AI translation output was empty or invalid.")
                        }
                    } catch (t: Throwable) {
                        Resource.Failure(t, "Failed to parse Cloud AI response: ${t.localizedMessage}")
                    }
                }
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────
    private fun getMd5Hash(text: String): String {
        val bytes  = text.toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("MD5").digest(bytes)
        return buildString { digest.forEach { b -> append(String.format("%02x", b)) } }
    }
}

// ─── Payload data classes ──────────────────────────────────────────────────────

private data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig
)

private data class GeminiContent(val parts: List<GeminiPart>)

private data class GeminiPart(val text: String)

private data class GeminiGenerationConfig(val temperature: Double)

private data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val temperature: Double
)

private data class OpenRouterMessage(val role: String, val content: String)
