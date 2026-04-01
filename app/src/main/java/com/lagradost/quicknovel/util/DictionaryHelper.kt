package com.lagradost.quicknovel.util

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.Requests
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.safeApiCall

@JsonIgnoreProperties(ignoreUnknown = true)
data class DictionaryResponse(
    val word: String,
    val phonetic: String? = null,
    val phonetics: List<DictionaryPhonetic>? = null,
    val meanings: List<DictionaryMeaning>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DictionaryPhonetic(
    val text: String? = null,
    val audio: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DictionaryMeaning(
    val partOfSpeech: String? = null,
    val definitions: List<DictionaryDefinition>? = null,
    val synonyms: List<String>? = null,
    val antonyms: List<String>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DictionaryDefinition(
    val definition: String? = null,
    val example: String? = null,
    val synonyms: List<String>? = null,
    val antonyms: List<String>? = null
)

object DictionaryHelper {
    private const val API_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/"
    private val mapper = jacksonObjectMapper()

    suspend fun fetchDefinition(word: String): Resource<List<DictionaryResponse>> {
        return safeApiCall {
            val response = app.get(API_URL + word)
            val json = response.text
            mapper.readValue<List<DictionaryResponse>>(json)
        }
    }
}
