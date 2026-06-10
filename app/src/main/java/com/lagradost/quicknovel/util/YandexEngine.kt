package com.lagradost.quicknovel.util

import android.content.Context
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.DataStore
import com.lagradost.quicknovel.USER_AGENT
import kotlinx.coroutines.delay

class YandexEngine : TranslationEngine {
    override val name: String = "Yandex (Online)"
    override val iconRes: Int = R.drawable.ic_google_ml // Using existing translation icon
    override val type: TranslationEngineType = TranslationEngineType.Yandex
    override val prefersBatching: Boolean = true
    override val recommendedBatchSize: Int = 8
    override val maxParallelRequests: Int = 3
    override val maxCharsPerRequest: Int = 4000

    private var cachedSid: String? = null
    private var sidTimestamp: Long = 0L
    private val SID_EXPIRY_MS = 10 * 60 * 1000 // Cache SID for 10 minutes
    private val SPOOFED_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private suspend fun getSid(): String? {
        val now = System.currentTimeMillis()
        if (cachedSid != null && (now - sidTimestamp < SID_EXPIRY_MS)) {
            return cachedSid
        }

        val responseResult = com.lagradost.quicknovel.mvvm.safeApiCall {
            MainActivity.app.get(
                url = "https://translate.yandex.com/",
                headers = mapOf(
                    "User-Agent" to SPOOFED_USER_AGENT,
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            )
        }

        return when (responseResult) {
            is Resource.Success -> {
                val response = responseResult.value
                if (!response.isSuccessful) return null
                
                val html = response.text
                if (html.contains("<title>Verification</title>") || html.contains("smartcaptcha", ignoreCase = true)) {
                    throw Exception("Yandex blocked the request with a CAPTCHA checkpoint. Please try again later or switch engines.")
                }
                
                // CPU-heavy Regex execution on Dispatchers.Default
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    var sid = Regex("""sid['"\s:=]+([^'"\s,;]+)""").find(html)?.groupValues?.get(1)
                    if (sid == null) {
                        sid = Regex("""["']sid["']\s*:\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1)
                    }
                    
                    if (sid != null) {
                        val cleanSid = sid.trim('\'', '"', ',', ';')
                        cachedSid = cleanSid
                        sidTimestamp = now
                        cleanSid
                    } else {
                        val preview = html.take(300).replace("\n", " ")
                        throw Exception("Failed to retrieve Yandex session ID (SID). HTML Preview: $preview")
                    }
                }
            }
            else -> null
        }
    }

    override suspend fun translate(context: Context, request: TranslationRequest): Resource<String> {
        delay(300) // Cooperative politeness delay
        
        val sid = try {
            getSid()
        } catch (e: Exception) {
            return Resource.Failure(e, e.message ?: "Failed to retrieve Yandex session ID (SID)")
        } ?: return Resource.Failure(null, "Failed to retrieve Yandex session ID (SID)")

        val url = "https://translate.yandex.net/api/v1/tr.json/translate?id=${sid}-0-0&srv=tr-text&lang=${request.from}-${request.to}&reason=auto&format=text"
        val headers = mapOf(
            "User-Agent" to SPOOFED_USER_AGENT,
            "Referer" to "https://translate.yandex.com/",
            "Host" to "translate.yandex.net",
            "Origin" to "https://translate.yandex.com"
        )

        val responseResult = com.lagradost.quicknovel.mvvm.safeApiCall {
            MainActivity.app.post(
                url = url,
                headers = headers,
                data = mapOf("text" to request.text)
            )
        }

        return when (responseResult) {
            is Resource.Failure -> Resource.Failure(responseResult.cause, responseResult.errorString)
            is Resource.Loading -> Resource.Failure(null, "Unexpected loading state")
            is Resource.Success -> {
                val response = responseResult.value
                if (response.code == 429) {
                    return Resource.Failure(null, "Rate limit reached. Please wait or switch translation engines")
                }
                if (!response.isSuccessful) {
                    return Resource.Failure(null, "HTTP error: ${response.code}")
                }

                // CPU-heavy JSON parsing on Dispatchers.Default
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    try {
                        val root = DataStore.mapper.readTree(response.text)
                        val textArray = root.get("text")
                        if (textArray != null && textArray.isArray && textArray.size() > 0) {
                            val translated = textArray.get(0).asText()
                            if (translated != null) {
                                Resource.Success(translated)
                            } else {
                                Resource.Failure(null, "Empty response returned by Yandex")
                            }
                        } else {
                            Resource.Failure(null, "Invalid response structure from Yandex")
                        }
                    } catch (t: Throwable) {
                        Resource.Failure(t, t.message ?: "Failed to parse Yandex JSON response")
                    }
                }
            }
        }
    }
}
