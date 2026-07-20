package com.lagradost.quicknovel.util

import android.content.Context
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.DataStore
import com.lagradost.quicknovel.USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Locale

class AzureEngine : TranslationEngine {
    override val name: String = "Azure Translator (Online)"
    override val iconRes: Int = R.drawable.ic_google_ml
    override val type: TranslationEngineType = TranslationEngineType.Azure
    override val prefersBatching: Boolean = true
    override val recommendedBatchSize: Int = 8
    override val maxParallelRequests: Int = 3
    override val maxCharsPerRequest: Int = 4000

    private var tokenCache: String? = null
    private var tokenExpiresAt: Long = 0L
    private val TOKEN_EXPIRY_MS = 8 * 60 * 1000L // 8 minutes

    private fun normalizeLang(lang: String): String {
        return when (val lower = lang.lowercase(Locale.ROOT)) {
            "zh", "zh-cn" -> "zh-Hans"
            "zh-tw", "zh-hk" -> "zh-Hant"
            else -> lower
        }
    }

    private suspend fun getAuthToken(): String {
        val now = System.currentTimeMillis()
        if (tokenCache != null && tokenExpiresAt > now) {
            return tokenCache!!
        }

        val responseResult = com.lagradost.quicknovel.mvvm.safeApiCall {
            MainActivity.app.get(
                url = "https://edge.microsoft.com/translate/auth",
                headers = mapOf(
                    "User-Agent" to USER_AGENT
                )
            )
        }

        return when (responseResult) {
            is Resource.Success -> {
                val response = responseResult.value
                if (!response.isSuccessful) {
                    throw Exception("Failed to retrieve Microsoft Auth Token: HTTP ${response.code}")
                }
                val token = response.text
                if (token.isNullOrBlank()) {
                    throw Exception("Auth token response is empty")
                }
                tokenCache = token
                tokenExpiresAt = now + TOKEN_EXPIRY_MS
                token
            }
            is Resource.Failure -> {
                throw Exception(responseResult.errorString, responseResult.cause)
            }
            else -> throw Exception("Unexpected loading state during auth")
        }
    }

    override suspend fun translate(context: Context, request: TranslationRequest): Resource<String> {
        delay(300) // Cooperative politeness delay

        val token = try {
            getAuthToken()
        } catch (e: Exception) {
            return Resource.Failure(e, e.message ?: "Failed to retrieve Microsoft Translator Auth Token")
        }

        val fromLang = normalizeLang(request.from)
        val toLang = normalizeLang(request.to)

        val urlBuilder = StringBuilder("https://api-edge.cognitive.microsofttranslator.com/translate?api-version=3.0&to=")
        urlBuilder.append(URLEncoder.encode(toLang, "UTF-8"))
        if (fromLang.isNotEmpty() && !fromLang.equals("auto", ignoreCase = true)) {
            urlBuilder.append("&from=").append(URLEncoder.encode(fromLang, "UTF-8"))
        }
        val url = urlBuilder.toString()

        val jsonString = try {
            DataStore.mapper.writeValueAsString(listOf(mapOf("Text" to request.text)))
        } catch (t: Throwable) {
            return Resource.Failure(t, "Failed to serialize translation request payload")
        }

        val responseResult = try {
            withContext(Dispatchers.IO) {
                val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())
                val response = MainActivity.app.post(
                    url = url,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Authorization" to "Bearer $token",
                        "User-Agent" to USER_AGENT
                    ),
                    requestBody = requestBody
                )
                Resource.Success(response)
            }
        } catch (t: Throwable) {
            Resource.Failure(t, t.message ?: "Connection error during Azure translation")
        }

        return when (responseResult) {
            is Resource.Failure -> Resource.Failure(responseResult.cause, responseResult.errorString)
            is Resource.Loading -> Resource.Failure(null, "Unexpected loading state")
            is Resource.Success -> {
                val response = responseResult.value
                if (response.code == 429) {
                    return Resource.Failure(null, "Rate limit reached on Azure Translator. Please wait or switch engines.")
                }
                if (!response.isSuccessful) {
                    return Resource.Failure(null, "HTTP error ${response.code}: ${response.text?.take(200)}")
                }

                withContext(Dispatchers.Default) {
                    try {
                        val responseBody = response.text
                        if (responseBody.isNullOrBlank()) {
                            Resource.Failure(null, "Azure Parse Error: Empty response body")
                        } else {
                            val root = DataStore.mapper.readTree(responseBody)
                            if (root != null && root.isArray && root.size() > 0) {
                                val translations = root.get(0)?.get("translations")
                                if (translations != null && translations.isArray && translations.size() > 0) {
                                    val translatedText = translations.get(0)?.get("text")?.asText()
                                    if (translatedText != null) {
                                        Resource.Success(translatedText)
                                    } else {
                                        Resource.Failure(null, "Azure Parse Error: Text field missing")
                                    }
                                } else {
                                    Resource.Failure(null, "Azure Parse Error: Translations array empty")
                                }
                            } else {
                                Resource.Failure(null, "Azure Parse Error: Invalid JSON structure")
                            }
                        }
                    } catch (t: Throwable) {
                        Resource.Failure(t, t.message ?: "Failed to parse Azure response")
                    }
                }
            }
        }
    }
}
