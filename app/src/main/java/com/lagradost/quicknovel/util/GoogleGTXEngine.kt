package com.lagradost.quicknovel.util

import android.content.Context
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.DataStore
import kotlinx.coroutines.delay
import android.util.Log
import java.net.URLEncoder

class GoogleGTXEngine : TranslationEngine {
    override val name: String = "Google GTX (Online)"
    override val iconRes: Int = R.drawable.ic_google_ml
    override val type: TranslationEngineType = TranslationEngineType.GoogleGTX
    override val prefersBatching: Boolean = true
    override val recommendedBatchSize: Int = 8
    override val maxParallelRequests: Int = 3
    override val maxCharsPerRequest: Int = 2000

    override suspend fun translate(context: Context, request: TranslationRequest): Resource<String> {
        Log.d("TranslationEngine", "GoogleGTXEngine.translate() started for text length: ${request.text.length}")
        delay(300) // Cooperative politeness delay
        
        val encodedText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            java.net.URLEncoder.encode(request.text, "UTF-8")
        }
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=${request.from}&tl=${request.to}&dt=t&q=$encodedText"

        Log.d("TranslationEngine", "GoogleGTXEngine sending network request to URL: $url")
        val responseResult = com.lagradost.quicknovel.mvvm.safeApiCall {
            MainActivity.app.get(url)
        }
        Log.d("TranslationEngine", "GoogleGTXEngine network request completed: ${responseResult.javaClass.simpleName}")

        return when (responseResult) {
            is Resource.Failure -> {
                Log.d("TranslationEngine", "GoogleGTXEngine request failed: ${responseResult.errorString}")
                Resource.Failure(responseResult.cause, responseResult.errorString)
            }
            is Resource.Loading -> Resource.Failure(null, "Unexpected loading state")
            is Resource.Success -> {
                val response = responseResult.value
                Log.d("TranslationEngine", "GoogleGTXEngine response code: ${response.code}")
                if (response.code == 429) {
                    return Resource.Failure(null, "Rate limit reached. Please wait or switch translation engines")
                }
                if (!response.isSuccessful) {
                    return Resource.Failure(null, "HTTP error: ${response.code}")
                }

                // CPU-heavy JSON parsing on Dispatchers.Default
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    try {
                        val text = response.text
                        Log.d("TranslationEngine", "GoogleGTXEngine response body: ${text?.take(200)}")
                        if (text.isNullOrBlank()) {
                            Log.d("TranslationEngine", "GoogleGTXEngine body is empty")
                            Resource.Failure(null, "GTX Parse Error: Empty response body")
                        } else {
                            val root = DataStore.mapper.readTree(text)
                            val segments = root?.get(0)
                            if (segments != null && segments.isArray) {
                                val sb = StringBuilder()
                                for (segment in segments) {
                                    val translatedText = segment?.get(0)?.asText()
                                    if (translatedText != null) {
                                        sb.append(translatedText)
                                    }
                                }
                                val resultString = sb.toString()
                                Log.d("TranslationEngine", "GoogleGTXEngine parsed translation: ${resultString.take(100)}")
                                Resource.Success(resultString)
                            } else {
                                Log.d("TranslationEngine", "GoogleGTXEngine root or segments not an array")
                                Resource.Failure(null, "GTX Parse Error: Invalid translation structure returned by Google GTX")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TranslationEngine", "GoogleGTXEngine parsing failed", e)
                        Resource.Failure(e, "GTX Parse Error: ${e.message}")
                    }
                }
            }
        }
    }
}
