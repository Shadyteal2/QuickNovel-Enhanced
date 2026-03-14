package com.lagradost.quicknovel

import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.lagradost.quicknovel.util.Apis
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass

class ProviderEvaluationTest {

    @Before
    fun setup() {
        MainActivity.app = Requests(
            OkHttpClient()
                .newBuilder()
                .ignoreAllSSLErrors()
                .readTimeout(30L, TimeUnit.SECONDS)
                .build(),
            responseParser = object : ResponseParser {
                val mapper: ObjectMapper = jacksonObjectMapper().configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false
                )

                override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
                    return mapper.readValue(text, kClass.java)
                }

                override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
                    return try {
                        mapper.readValue(text, kClass.java)
                    } catch (e: Exception) {
                        null
                    }
                }

                override fun writeValueAsString(obj: Any): String {
                    return mapper.writeValueAsString(obj)
                }
            }
        ).apply {
            defaultHeaders = mapOf("user-agent" to USER_AGENT)
        }
    }

    @Test
    fun evaluateProviders() = runBlocking {
        println("Starting provider evaluation...")
        val working = mutableListOf<String>()
        val broken = mutableListOf<String>()

        for (api in Apis.apis) {
            try {
                println("Testing: ${api.name}")
                var searchResult: List<SearchResponse>? = null
                val searchTerms = listOf("magic", "system", "reincarnation", "blood", "sword", "moon", "the")
                for (term in searchTerms) {
                    try {
                        searchResult = api.search(term)
                        if (!searchResult.isNullOrEmpty()) {
                            break
                        }
                    } catch (e: Exception) {
                       // ignore search error for single term, try next
                    }
                }

                if (searchResult.isNullOrEmpty()) {
                    println("❌ ${api.name} FAILED: Empty search results")
                    broken.add(api.name)
                    continue
                }

                val targetNovel = searchResult.first()
                println("   Found novel: ${targetNovel.name} at ${targetNovel.url}")
                
                val loadResult = api.load(targetNovel.url)
                if (loadResult == null) {
                    println("❌ ${api.name} FAILED: Load returned null")
                    broken.add(api.name)
                    continue
                }

                if (loadResult is StreamResponse && loadResult.data.isEmpty()) {
                    println("❌ ${api.name} FAILED: Load returned 0 chapters")
                    broken.add(api.name)
                    continue
                }
                
                if (loadResult !is StreamResponse && loadResult !is SearchResponse) {
                     println("❌ ${api.name} FAILED: Load returned unknown type")
                     broken.add(api.name)
                     continue
                }

                println("✅ ${api.name} WORKING")
                working.add(api.name)

            } catch (t: Throwable) {
                println("❌ ${api.name} FAILED with exception: ${t.message}")
                broken.add(api.name)
            }
        }

        println("==================================================")
        println("WORKING PROVIDERS (${working.size}):")
        working.forEach { println(" - $it") }
        println("==================================================")
        println("BROKEN PROVIDERS (${broken.size}):")
        broken.forEach { println(" - $it") }
        println("==================================================")
    }
}
