package com.lagradost.quicknovel.util

import com.fasterxml.jackson.annotation.JsonProperty

data class PluginManifest(
    @JsonProperty("plugins") val plugins: List<PluginItem>
)

data class PluginItem(
    @JsonProperty("pluginId") val pluginId: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("version") val version: Int,
    @JsonProperty("minApiVersion") val minApiVersion: Int,
    @JsonProperty("author") val author: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("mainClass") val mainClass: String? = null,
    @JsonProperty("mainClasses") val mainClasses: List<String>? = null,
    @JsonProperty("url") val url: String,
    @JsonProperty("iconUrl") val iconUrl: String? = null
)
