package com.lagradost.quicknovel.ui.reader.customization

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.mvvm.logError

data class TextReplacement(
    @JsonProperty("find") val find: String,
    @JsonProperty("replace") val replace: String,
    @JsonProperty("isRegex") val isRegex: Boolean = false
)

data class ContentCleanRule(
    @JsonProperty("id") val id: String,
    @JsonProperty("providerApiName") val providerApiName: String, // "*" for all providers
    @JsonProperty("enabled") val enabled: Boolean = true,
    @JsonProperty("selectorsToRemove") val selectorsToRemove: List<String> = emptyList(),
    @JsonProperty("replacements") val replacements: List<TextReplacement> = emptyList()
)

data class CompiledTextReplacement(
    val find: String,
    val replace: String,
    val isRegex: Boolean,
    val regex: Regex?
)

data class CompiledContentCleanRule(
    val id: String,
    val providerApiName: String,
    val enabled: Boolean,
    val selectorsToRemove: List<String>,
    val replacements: List<CompiledTextReplacement>
)

fun TextReplacement.compile(): CompiledTextReplacement {
    val regex = if (isRegex) {
        try {
            Regex(find)
        } catch (t: Throwable) {
            logError(t)
            null
        }
    } else {
        null
    }
    return CompiledTextReplacement(find, replace, isRegex, regex)
}

fun ContentCleanRule.compile(): CompiledContentCleanRule {
    return CompiledContentCleanRule(
        id = id,
        providerApiName = providerApiName,
        enabled = enabled,
        selectorsToRemove = selectorsToRemove,
        replacements = replacements.map { it.compile() }
    )
}
