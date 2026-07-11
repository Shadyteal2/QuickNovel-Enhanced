package com.lagradost.quicknovel.ui.reader.customization

import android.content.Context
import android.graphics.Color
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.ReadActivityViewModel
import com.lagradost.quicknovel.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

object ReaderCustomizationStore {
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    private val _themes = MutableStateFlow<List<ReaderTheme>>(emptyList())
    val themes: StateFlow<List<ReaderTheme>> = _themes.asStateFlow()

    private val _rules = MutableStateFlow<List<ContentCleanRule>>(emptyList())
    val rules: StateFlow<List<ContentCleanRule>> = _rules.asStateFlow()

    private var compiledRules: List<CompiledContentCleanRule> = emptyList()

    private fun getThemesDir(context: Context): File {
        val dir = File(context.filesDir, "reader_themes")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getRulesFile(context: Context): File {
        return File(context.filesDir, "reader_content_rules.json")
    }

    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        try {
            val themesDir = getThemesDir(context)
            
            // Clean up legacy presets (all previously shipped default theme names)
            val legacyFiles = listOf(
                "Amoled_Midnight.json", "Warm_Sepia.json", "Forest_Moss.json",
                "Amoled_Speed-Read.json", "Warm_Bookworm.json", "Nordic_Frost.json"
            )
            for (legacyName in legacyFiles) {
                val legacyFile = File(themesDir, legacyName)
                if (legacyFile.exists()) {
                    legacyFile.delete()
                }
            }

            // Always recreate/overwrite default presets on init to guarantee updated colors are loaded
            createDefaultPresets(context)
            
            reloadThemes(context)
            reloadRules(context)
        } catch (t: Throwable) {
            logError(t)
        }
    }

    private fun createDefaultPresets(context: Context) {
        val presets = listOf(

            // ─── Theme 1: Midnight Ink ──────────────────────────────────
            // AMOLED hacker aesthetic. Electric-white text on pitch black,
            // heavy film grain (aged print feel), bionic reading for speed,
            // wide breathing margins, amber glow to ease eye strain at night.
            ReaderTheme(
                name = "Midnight Ink",
                textColor = Color.parseColor("#E8E8E8"),
                backgroundColor = Color.parseColor("#000000"),
                textSize = 17,
                lineHeightMultiplier = 1.40f,
                verticalPadding = 12f,
                textFont = "",
                bionicReading = true,
                paddingHorizontal = 28,
                backgroundGrain = 38,
                letterSpacing = 0.04f,
                luminescent = true,
                luminescentIntensity = 0.55f
            ),

            // ─── Theme 2: Gilded Parchment ─────────────────────────────
            // Luxury aged-book experience. Rich sepia ink on warm cream paper,
            // maximum grain for a real tactile paper texture, generous line
            // height and font size for relaxed immersive reading. No glow —
            // pure physical print aesthetic, wide margins like a hardcover novel.
            ReaderTheme(
                name = "Gilded Parchment",
                textColor = Color.parseColor("#3B2510"),
                backgroundColor = Color.parseColor("#F7EDD6"),
                textSize = 22,
                lineHeightMultiplier = 1.65f,
                verticalPadding = 22f,
                textFont = "",
                bionicReading = false,
                paddingHorizontal = 36,
                backgroundGrain = 60,
                letterSpacing = 0.015f,
                luminescent = false,
                luminescentIntensity = 0f
            ),

            // ─── Theme 3: Deep Ocean ───────────────────────────────────
            // Immersive midnight-sea atmosphere. Near-black navy canvas,
            // soft phosphorescent aqua text, subtle grain like light through
            // water. Tightest letter spacing for dense comfortable reading,
            // gentle glow makes text feel like it's self-luminous underwater.
            ReaderTheme(
                name = "Deep Ocean",
                textColor = Color.parseColor("#9ECFCF"),
                backgroundColor = Color.parseColor("#0B1720"),
                textSize = 19,
                lineHeightMultiplier = 1.50f,
                verticalPadding = 16f,
                textFont = "",
                bionicReading = false,
                paddingHorizontal = 30,
                backgroundGrain = 20,
                letterSpacing = 0.01f,
                luminescent = true,
                luminescentIntensity = 0.65f
            ),

            // ─── Theme 4: Nordic Frost ──────────────────────────────────
            // Minimalist day mode / cool gray-blue Scandinavian design.
            // Soft deep slate text on clean glacial slate-gray, low grain.
            ReaderTheme(
                name = "Nordic Frost",
                textColor = Color.parseColor("#2F3E46"),
                backgroundColor = Color.parseColor("#E5E9EC"),
                textSize = 18,
                lineHeightMultiplier = 1.48f,
                verticalPadding = 14f,
                textFont = "",
                bionicReading = false,
                paddingHorizontal = 24,
                backgroundGrain = 12,
                letterSpacing = 0.02f,
                luminescent = false,
                luminescentIntensity = 0f
            ),

            // ─── Theme 5: Nebula Velvet ─────────────────────────────────
            // Mystical deep violet twilight atmosphere. Glowing lavender-pink
            // text on dark amethyst canvas, soft grain, gentle reading glow.
            ReaderTheme(
                name = "Nebula Velvet",
                textColor = Color.parseColor("#E2CFFF"),
                backgroundColor = Color.parseColor("#120A1C"),
                textSize = 19,
                lineHeightMultiplier = 1.52f,
                verticalPadding = 16f,
                textFont = "",
                bionicReading = false,
                paddingHorizontal = 30,
                backgroundGrain = 18,
                letterSpacing = 0.02f,
                luminescent = true,
                luminescentIntensity = 0.70f
            ),

            // ─── Theme 6: Vintage Espresso ──────────────────────────────
            // Warm roasted dark chocolate/coffee cozy nighttime reading.
            // Rich caramel text on dark espresso background, heavy grain.
            ReaderTheme(
                name = "Vintage Espresso",
                textColor = Color.parseColor("#E5C39E"),
                backgroundColor = Color.parseColor("#1F140D"),
                textSize = 20,
                lineHeightMultiplier = 1.58f,
                verticalPadding = 18f,
                textFont = "",
                bionicReading = false,
                paddingHorizontal = 32,
                backgroundGrain = 45,
                letterSpacing = 0.015f,
                luminescent = true,
                luminescentIntensity = 0.35f
            )
        )
        for (theme in presets) {
            saveThemeSync(context, theme)
        }
    }

    private fun reloadThemes(context: Context) {
        try {
            val themesDir = getThemesDir(context)
            val files = themesDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
            val list = files.mapNotNull { file ->
                try {
                    mapper.readValue<ReaderTheme>(file)
                } catch (t: Throwable) {
                    logError(t)
                    null
                }
            }
            _themes.value = list
        } catch (t: Throwable) {
            logError(t)
        }
    }

    private fun reloadRules(context: Context) {
        try {
            val file = getRulesFile(context)
            val list = if (file.exists()) {
                try {
                    mapper.readValue<List<ContentCleanRule>>(file)
                } catch (t: Throwable) {
                    logError(t)
                    emptyList()
                }
            } else {
                emptyList()
            }
            _rules.value = list
            compiledRules = list.map { it.compile() }
        } catch (t: Throwable) {
            logError(t)
        }
    }

    private fun saveThemeSync(context: Context, theme: ReaderTheme) {
        try {
            val themesDir = getThemesDir(context)
            val safeName = theme.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(themesDir, "$safeName.json")
            mapper.writeValue(file, theme)
        } catch (t: Throwable) {
            logError(t)
        }
    }

    suspend fun saveTheme(context: Context, theme: ReaderTheme) = withContext(Dispatchers.IO) {
        saveThemeSync(context, theme)
        reloadThemes(context)
    }

    suspend fun deleteTheme(context: Context, name: String) = withContext(Dispatchers.IO) {
        try {
            val themesDir = getThemesDir(context)
            val safeName = name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(themesDir, "$safeName.json")
            if (file.exists()) {
                file.delete()
            }
        } catch (t: Throwable) {
            logError(t)
        }
        reloadThemes(context)
    }

    suspend fun saveRules(context: Context, newRules: List<ContentCleanRule>) = withContext(Dispatchers.IO) {
        try {
            val file = getRulesFile(context)
            mapper.writeValue(file, newRules)
        } catch (t: Throwable) {
            logError(t)
        }
        reloadRules(context)
    }

    fun getRulesFor(providerApiName: String?, rulesEnabled: Boolean): List<CompiledContentCleanRule> {
        if (!rulesEnabled) return emptyList()
        val provider = providerApiName ?: ""
        return compiledRules.filter {
            it.enabled && (it.providerApiName == "*" || it.providerApiName.equals(provider, ignoreCase = true))
        }
    }

    fun applyTheme(theme: ReaderTheme, viewModel: ReadActivityViewModel) {
        theme.textColor?.let { viewModel.textColor = it }
        theme.backgroundColor?.let { viewModel.backgroundColor = it }
        theme.textSize?.let { viewModel.textSize = it }
        theme.lineHeightMultiplier?.let { viewModel.lineHeightMultiplier = it }
        theme.verticalPadding?.let { viewModel.textVerticalPadding = it }
        theme.textFont?.let { viewModel.textFont = it }
        theme.bionicReading?.let { viewModel.bionicReading = it }
        theme.paddingHorizontal?.let { viewModel.paddingHorizontal = it }
        theme.backgroundGrain?.let { viewModel.backgroundGrain = it }
        theme.letterSpacing?.let { viewModel.letterSpacing = it }
        theme.luminescent?.let { viewModel.luminescentReader = it }
        theme.luminescentIntensity?.let { viewModel.luminescentIntensity = it }
    }

    fun exportThemeToString(theme: ReaderTheme): String? {
        return try {
            mapper.writeValueAsString(theme)
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    fun importThemeFromString(json: String): ReaderTheme? {
        return try {
            mapper.readValue<ReaderTheme>(json)
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }
}
