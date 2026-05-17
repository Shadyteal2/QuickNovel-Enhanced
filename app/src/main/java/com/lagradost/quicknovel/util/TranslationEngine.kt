package com.lagradost.quicknovel.util

import android.content.Context
import com.lagradost.quicknovel.mvvm.Resource

enum class TranslationEngineType(val value: Int) {
    None(0),
    GoogleMLKit(1);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: None
    }
}

data class TranslationRequest(
    val text: String,
    val from: String,
    val to: String,
    val systemInstruction: String? = null,
    val bridgeText: String? = "\n---\n",
    val previousContext: String? = null
)


interface TranslationEngine {
    val name: String
    val iconRes: Int
    val type: TranslationEngineType

    /** LLMs perform better with system instructions */
    val supportsSystemInstructions: Boolean
        get() = false

    /** LLMs benefit from batching paragraphs */
    val prefersBatching: Boolean get() = false

    /** Maximum characters allowed in a single request. Default 3000. */
    val recommendedBatchSize: Int get() = 1
    val maxParallelRequests: Int get() = 1
    val maxCharsPerRequest: Int get() = 4000

    suspend fun translate(context: Context, request: TranslationRequest): Resource<String>
}
