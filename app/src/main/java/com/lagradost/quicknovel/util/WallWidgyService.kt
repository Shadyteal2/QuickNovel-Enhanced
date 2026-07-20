package com.lagradost.quicknovel.util

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.safeApiCall

@JsonIgnoreProperties(ignoreUnknown = true)
data class WallWidgyResponse(
    val wallpapers: List<String>? = null,
    val count: Int? = null,
    val category: String? = null,
    val type: String? = null,
    val color: String? = null
)

object WallWidgyService {
    private const val API_URL = "https://wallwidgy.vercel.app/api/wallpapers"
    private val mapper = AppUtils.mapper

    suspend fun fetchWallpapers(
        category: String? = null,
        color: String? = null,
        count: Int = 10,
        type: String = "mobile",
        page: Int = 1
    ): Resource<WallWidgyResponse> {
        return safeApiCall {
            val params = mutableMapOf<String, String>()
            params["count"] = count.toString()
            params["type"] = type
            params["page"] = page.toString()
            if (!category.isNullOrBlank() && category != "all") {
                params["category"] = category
            }
            if (!color.isNullOrBlank() && color != "all") {
                params["color"] = color
            }

            val response = app.get(API_URL, params = params)
            val json = response.text
            mapper.readValue<WallWidgyResponse>(json)
        }
    }
}
