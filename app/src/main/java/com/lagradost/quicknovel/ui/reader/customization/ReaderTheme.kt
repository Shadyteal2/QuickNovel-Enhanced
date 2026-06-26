package com.lagradost.quicknovel.ui.reader.customization

import com.fasterxml.jackson.annotation.JsonProperty

data class ReaderTheme(
    @JsonProperty("name") val name: String,
    @JsonProperty("textColor") val textColor: Int? = null,
    @JsonProperty("backgroundColor") val backgroundColor: Int? = null,
    @JsonProperty("textSize") val textSize: Int? = null,
    @JsonProperty("lineHeightMultiplier") val lineHeightMultiplier: Float? = null,
    @JsonProperty("verticalPadding") val verticalPadding: Float? = null,
    @JsonProperty("textFont") val textFont: String? = null,
    @JsonProperty("bionicReading") val bionicReading: Boolean? = null,
    @JsonProperty("paddingHorizontal") val paddingHorizontal: Int? = null,
    @JsonProperty("backgroundGrain") val backgroundGrain: Int? = null,
    @JsonProperty("letterSpacing") val letterSpacing: Float? = null,
    @JsonProperty("luminescent") val luminescent: Boolean? = null,
    @JsonProperty("luminescentIntensity") val luminescentIntensity: Float? = null
)
