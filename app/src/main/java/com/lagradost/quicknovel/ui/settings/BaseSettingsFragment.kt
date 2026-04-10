package com.lagradost.quicknovel.ui.settings

import com.lagradost.quicknovel.MainActivity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.lagradost.quicknovel.APIRepository.Companion.providersActive
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.LogcatBinding
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.ui.clear
import com.lagradost.quicknovel.ui.download.AnyAdapter
import com.lagradost.quicknovel.ui.history.HistoryAdapter
import com.lagradost.quicknovel.ui.txt
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.Apis.Companion.getApiProviderLangSettings
import com.lagradost.quicknovel.util.Apis.Companion.getApiSettings
import com.lagradost.quicknovel.util.BackupUtils.backup
import com.lagradost.quicknovel.util.BackupUtils.restorePrompt
import com.lagradost.quicknovel.util.BackupUtils.setupStream
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.quicknovel.util.SingleSelectionHelper.showBottomDialog
import com.lagradost.quicknovel.util.SingleSelectionHelper.showDialog
import com.lagradost.quicknovel.util.SingleSelectionHelper.showMultiDialog
import com.lagradost.quicknovel.util.PluginManager
import com.lagradost.quicknovel.util.SubtitleHelper
import com.lagradost.quicknovel.util.UIHelper.clipboardHelper
import com.lagradost.quicknovel.util.UIHelper.dismissSafe
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.util.backgroundEffectDisplayLabel
import com.lagradost.quicknovel.util.backgroundEffectLabels
import com.lagradost.safefile.MediaFileContentType
import com.lagradost.safefile.SafeFile
import dalvik.system.DexClassLoader
import dalvik.system.DexFile
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.lagradost.quicknovel.util.TranslationEngineType
import androidx.preference.ListPreference
import androidx.preference.EditTextPreference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.quicknovel.API_VERSION
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.util.PluginItem

/**
 * Universal SafeFile Extensions for NeoQN storage management.
 */
fun SafeFile.findFileOrThrow(name: String): SafeFile {
    return findFile(name) ?: throw java.io.IOException("File not found: $name")
}

fun SafeFile.createFileOrThrow(name: String, mime: String = "application/epub+zip"): SafeFile {
    return createFile(name) ?: throw java.io.IOException("Could not create file: $name")
}

fun showSearchProviders(context: Context?) {
    if (context == null) return
    val apiNames = apis.map { it.name }

    context.apply {
        val active = getApiSettings()
        showMultiDialog(
            apiNames,
            apiNames.mapIndexed { index, s -> index to active.contains(s) }
                .filter { it.second }
                .map { it.first }.toList(),
            getString(R.string.search_providers),
            {}) { list ->
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            settingsManager.edit {
                putStringSet(
                    getString(R.string.search_providers_list_key),
                    list.map { apiNames[it] }.toSet()
                )
            }
            val settings = getApiSettings()
            providersActive.clear()
            providersActive.addAll(settings)
        }
    }
}

fun getDefaultDir(context: Context): SafeFile? {
    val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    if (externalDir != null) {
         return SafeFile.fromUri(context, File(externalDir, "Epub").apply { mkdirs() }.toUri())
    }
    
    return SafeFile.fromMedia(
        context, MediaFileContentType.Downloads
    )?.gotoDirectory("Epub")
}

private fun basePathToFile(context: Context, path: String?): SafeFile? {
    return when {
        path.isNullOrBlank() -> getDefaultDir(context)
        path.startsWith("content://") -> SafeFile.fromUri(context, path.toUri())
        else -> SafeFile.fromFilePath(
            context,
            path.removePrefix(Environment.getExternalStorageDirectory().path).removePrefix(
                File.separator
            ).removeSuffix(File.separator) + File.separator
        )
    }
}

fun Context.getBasePath(): Pair<SafeFile?, String?> {
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
    val basePathSetting =
        settingsManager.getString(getString(R.string.download_path_key), null)
    return basePathToFile(this, basePathSetting) to basePathSetting
}

fun getDownloadDirs(context: Context?): List<String> {
    return safe {
        context?.let { ctx ->
            val defaultDir = getDefaultDir(ctx)?.filePath()
            val first = listOf(defaultDir)
            (try {
                val currentDir = ctx.getBasePath().let { it.first?.filePath() ?: it.second }
                (first +
                        ctx.getExternalFilesDirs("").mapNotNull { it.path } +
                        currentDir)
            } catch (e: Exception) {
                first
            }).filterNotNull().distinct()
        }
    } ?: emptyList()
}

abstract class BaseSettingsFragment : PreferenceFragmentCompat() {
    
    // Pickers moved from SettingsFragment to Base
    protected val pathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            val context = context ?: return@registerForActivityResult
            val file = SafeFile.fromUri(context, uri)
            val filePath = file?.filePath()
            
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit { putString(getString(R.string.download_path_key), uri.toString()) }

            (filePath ?: uri.toString()).let {
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit { putString(getString(R.string.download_path_pref), it) }
            }
        }

    protected val imagePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val context = context ?: return@registerForActivityResult
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit { putString(getString(R.string.background_image_key), uri.toString()) }
        }

    protected val pluginPicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
             if (uris.isNullOrEmpty()) return@registerForActivityResult
             val context = context ?: return@registerForActivityResult
             ioSafe {
                 try {
                     val pluginsDir = PluginManager.getPluginsDir(context)
                     val apkUris = mutableListOf<Uri>()
                     val jsonUris = mutableListOf<Uri>()
                     
                     uris.forEach { uri ->
                         val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                             val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                             cursor.moveToFirst()
                             cursor.getString(nameIndex)
                         } ?: uri.lastPathSegment ?: "unknown"

                         if (name.endsWith(".apk", true) || name.endsWith(".dex", true)) {
                             if (PluginManager.verifyApkSignature(context, uri)) apkUris.add(uri)
                             else activity?.runOnUiThread { showToast("Rejected signature: $name") }
                         } else if (name.endsWith(".json", true)) {
                             jsonUris.add(uri)
                         }
                     }
                     
                     apkUris.forEach { uri ->
                         val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                             val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                             cursor.moveToFirst()
                             cursor.getString(nameIndex)
                         } ?: "plugin.apk"
                         context.contentResolver.openInputStream(uri)?.use { input ->
                             val target = File(pluginsDir, fileName)
                             if (target.exists()) target.delete()
                             target.outputStream().use { output -> input.copyTo(output) }
                         }
                     }
                     // Simplification: set isManualImport in JSON if provided or just load all
                     PluginManager.loadAllPlugins(context)
                     activity?.runOnUiThread { showToast("Plugins updated!") }
                  } catch (e: Exception) {
                     logError(e)
                 }
             }
        }

    /**
     * User-facing picker: select a single provider APK from storage.
     * No JSON required — metadata is auto-synthesised by scanning the DEX.
     * Works identically to the sync system under-the-hood; providers load right away.
     */
    protected val providerApkPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val ctx = context ?: return@registerForActivityResult
            ioSafe {
                try {
                    // ── 1. Verify signature ─────────────────────────────────────────
                    if (!PluginManager.verifyApkSignature(ctx, uri)) {
                        activity?.runOnUiThread { showToast(getString(R.string.import_provider_apk_bad_sig)) }
                        return@ioSafe
                    }

                    // ── 2. Resolve display name → stable bundle id ────────────────
                    val displayName = ctx.contentResolver
                        .query(uri, null, null, null, null)?.use { cursor ->
                            val col = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            cursor.moveToFirst()
                            cursor.getString(col)
                        } ?: "provider.apk"

                    val bundleId = displayName
                        .removeSuffix(".apk").removeSuffix(".dex")
                        .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

                    val pluginsDir = PluginManager.getPluginsDir(ctx)
                    val destApk   = File(pluginsDir, "$bundleId.apk")
                    val destJson  = File(pluginsDir, "$bundleId.json")

                    // ── 3. Notify if this is already installed (update path) ──────
                    if (destJson.exists()) {
                        try {
                            val old = jacksonObjectMapper().readValue(destJson.readText(), PluginItem::class.java)
                            activity?.runOnUiThread {
                                showToast(getString(R.string.import_provider_apk_duplicate_format, old.version))
                            }
                        } catch (_: Exception) {}
                    }

                    // ── 4. Copy APK bytes to plugins dir ─────────────────────────
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        destApk.outputStream().use { out -> input.copyTo(out) }
                    }
                    destApk.setReadOnly() // DexClassLoader requires read-only on API 26+

                    // Clear old classloader caches so the new file loads fresh
                    PluginManager.removeCachesForPath(destApk.absolutePath)

                    // ── 5. Scan DEX for all concrete MainAPI subclasses ───────────
                    val foundClasses = mutableListOf<String>()
                    try {
                        @Suppress("DEPRECATION")
                        val dex = DexFile(destApk.absolutePath)
                        val loader = DexClassLoader(
                            destApk.absolutePath,
                            ctx.codeCacheDir.absolutePath,
                            null,
                            ctx.classLoader
                        )
                        val entries = dex.entries()
                        while (entries.hasMoreElements()) {
                            val className = entries.nextElement()
                            // Fast-skip framework packages to keep scan time low
                            if (className.startsWith("android.") ||
                                className.startsWith("kotlin.") ||
                                className.startsWith("kotlinx.") ||
                                className.startsWith("java.")) continue
                            try {
                                val clazz = loader.loadClass(className)
                                if (MainAPI::class.java.isAssignableFrom(clazz) &&
                                    !java.lang.reflect.Modifier.isAbstract(clazz.modifiers) &&
                                    !clazz.isInterface) {
                                    foundClasses.add(className)
                                    android.util.Log.d("ProviderApkImport", "Found provider: $className")
                                }
                            } catch (_: Throwable) { /* skip unloadable / abstract classes */ }
                        }
                        dex.close()
                    } catch (e: Exception) {
                        logError(e)
                    }

                    // ── 6. Abort if nothing was found ─────────────────────────────
                    if (foundClasses.isEmpty()) {
                        destApk.delete()
                        activity?.runOnUiThread { showToast(getString(R.string.import_provider_apk_none_found)) }
                        return@ioSafe
                    }

                    // ── 7. Write companion JSON  (isManualImport = true) ──────────
                    //      Sync worker checks this flag and will SKIP auto-update for
                    //      manually imported APKs, so the two paths never conflict.
                    val meta = PluginItem(
                        pluginId      = bundleId,
                        name          = bundleId,
                        version       = 1,
                        minApiVersion = API_VERSION,
                        mainClasses   = foundClasses,
                        url           = "local://$bundleId",  // placeholder; sync skips isManualImport
                        isManualImport = true
                    )
                    destJson.writeText(jacksonObjectMapper().writeValueAsString(meta))

                    // ── 8. Hot-reload so providers appear immediately ──────────────
                    PluginManager.loadAllPlugins(ctx)
                    activity?.runOnUiThread {
                        showToast(getString(R.string.import_provider_apk_success_format, foundClasses.size))
                    }
                } catch (e: Exception) {
                    logError(e)
                    activity?.runOnUiThread { showToast("Import failed: ${e.message}") }
                }
            }
        }

    fun PreferenceFragmentCompat?.getPref(id: Int): Preference? {
        if (this == null) return null
        return try {
            findPreference(getString(id))
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    fun PreferenceFragmentCompat?.getPref(key: String): Preference? {
        if (this == null) return null
        return findPreference(key)
    }

    /**
     * Centralized logic for all preference listeners.
     */
    protected fun setupPreferenceListeners() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Search Providers
        getPref(R.string.search_providers_list_key)?.setOnPreferenceClickListener {
            showSearchProviders(activity)
            true
        }

        // Locale
        getPref(R.string.locale_key)?.setOnPreferenceClickListener { pref ->
            val tempLangs = appLanguages.toMutableList()
            val current = getCurrentLocale(pref.context)
            val languageCodes = tempLangs.map { it.third }
            val languageNames = tempLangs.map { "${it.first.ifBlank { SubtitleHelper.getFlagFromIso(it.third) ?: "" }} ${it.second}" }
            val index = languageCodes.indexOf(current)

            activity?.showDialog(languageNames, index, getString(R.string.provider_lang_settings), true, {}) {
                val code = languageCodes[it]
                CommonActivity.setLocale(activity, code)
                settingsManager.edit { putString(getString(R.string.locale_key), code) }
                CommonActivity.recreateWithSmoothTransition(activity)
            }
            true
        }

        // Backup & Restore
        getPref(R.string.backup_key)?.setOnPreferenceClickListener {
            activity?.backup()
            true
        }
        getPref(R.string.restore_key)?.setOnPreferenceClickListener {
            activity?.restorePrompt()
            true
        }

        // Download Path
        getPref(R.string.download_path_key)?.setOnPreferenceClickListener {
            val dirs = getDownloadDirs(context)
            val currentDir = settingsManager.getString(getString(R.string.download_path_pref), null)
                ?: context?.let { getDefaultDir(it)?.filePath() }

            activity?.showBottomDialog(dirs + listOf("Custom"), dirs.indexOf(currentDir), getString(R.string.download_path_pref), true, {}) {
                if (it == dirs.size) pathPicker.launch(Uri.EMPTY)
                else settingsManager.edit {
                    putString(getString(R.string.download_path_key), dirs[it])
                    putString(getString(R.string.download_path_pref), dirs[it])
                }
            }
            true
        }

        // Updates
        getPref(R.string.manual_check_update_key)?.setOnPreferenceClickListener {
            ioSafe { if (activity?.runAutoUpdate(false) != true) showToast(R.string.no_update_found) }
            true
        }

        // Background Image
        getPref(R.string.background_image_key)?.setOnPreferenceClickListener {
            imagePicker.launch(arrayOf("image/*"))
            true
        }
        getPref(R.string.background_effect_mode_key)?.let { pref ->
            fun updateSummary() {
                val current = settingsManager.getString(getString(R.string.background_effect_mode_key), "none")
                pref.summary = backgroundEffectDisplayLabel(requireContext(), current)
            }

            updateSummary()

            pref.setOnPreferenceClickListener {
                val current = settingsManager.getString(getString(R.string.background_effect_mode_key), "none")
                val options = backgroundEffectLabels(requireContext())
                val names = options.map { it.first }
                val values = options.map { it.second }
                val index = values.indexOf(com.lagradost.quicknovel.util.BackgroundEffectMode.from(current).value).coerceAtLeast(0)
                activity?.showBottomDialog(names, index, getString(R.string.background_effect_mode), false, {}) {
                    settingsManager.edit { putString(getString(R.string.background_effect_mode_key), values[it]) }
                    updateSummary()
                }
                true
            }
        }
        getPref(R.string.reset_background_key)?.setOnPreferenceClickListener {
            val ctx = context ?: return@setOnPreferenceClickListener true
            AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                .setTitle(R.string.reset_background)
                .setMessage(R.string.reset_background_summary)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reset_background) { dialog, _ ->
                    settingsManager.edit {
                        remove(getString(R.string.background_image_key))
                        remove(getString(R.string.background_effect_mode_key))
                        remove(getString(R.string.background_blur_key))
                        remove(getString(R.string.background_dim_key))
                        remove(getString(R.string.background_grain_key))
                        remove(getString(R.string.background_vignette_key))
                    }
                    showToast(R.string.background_reset_confirmed)
                    dialog.dismiss()
                }
                .show()
            true
        }

        // Logcat
        getPref(R.string.show_logcat_key)?.setOnPreferenceClickListener { pref ->
            // Re-using the logic from SettingsFragment (simplified here)
            val builder = AlertDialog.Builder(pref.context, R.style.AlertDialogCustom)
            val binding = LogcatBinding.inflate(layoutInflater, null, false)
            builder.setView(binding.root)
            val dialog = builder.create()
            dialog.show()
            
            val logList = mutableListOf<String>()
            try {
                val process = Runtime.getRuntime().exec("logcat -d")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                reader.lineSequence().forEach { logList.add(it) }
            } catch (e: Exception) { logError(e) }
            
            binding.logcatRecyclerView.layoutManager = LinearLayoutManager(pref.context)
            binding.logcatRecyclerView.adapter = com.lagradost.quicknovel.ui.settings.LogcatAdapter().apply { submitList(logList) }
            binding.copyBtt.setOnClickListener { clipboardHelper(txt("Logcat"), logList.joinToString("\n")); dialog.dismissSafe(activity) }
            binding.clearBtt.setOnClickListener { Runtime.getRuntime().exec("logcat -c"); dialog.dismissSafe(activity) }
            binding.closeBtt.setOnClickListener { dialog.dismissSafe(activity) }
            true
        }

        // Plugins
        getPref(R.string.plugin_import_key)?.setOnPreferenceClickListener {
            pluginPicker.launch(arrayOf("*/*"))
            true
        }
        
        // Themes
        getPref(R.string.theme_key)?.appThemeListener(settingsManager)

        // Accent Color
        getPref(R.string.primary_color_key)?.setOnPreferenceClickListener {
            val names = listOf("Normal", "Pink", "Dark Green", "Maroon", "Navy Blue", "Grey", "White", "Brown", "Purple", "Green", "Green Apple", "Red", "Banana", "Party", "Carnation Pink", "Monet", "Monet 2")
            val values = listOf("Normal", "Pink", "DarkGreen", "Maroon", "NavyBlue", "Grey", "White", "Brown", "Purple", "Green", "GreenApple", "Red", "Banana", "Party", "CarnationPink", "Monet", "Monet2")
            val current = settingsManager.getString(getString(R.string.primary_color_key), "Banana")
            
            activity?.showBottomDialog(names, values.indexOf(current), getString(R.string.primary_color_settings), false, {}) {
                settingsManager.edit { putString(getString(R.string.primary_color_key), values[it]) }
                CommonActivity.recreateWithSmoothTransition(activity)
            }
            true
        }

        // Manage Data
        getPref(R.string.manage_data_key)?.setOnPreferenceClickListener {
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                .navigate(R.id.navigation_manage_data)
            true
        }

        // Library Display Mode
        getPref(R.string.download_format_key)?.let { pref ->
            val names = listOf("List View", "Grid View")
            val values = listOf("list", "grid")
            val current = settingsManager.getString(getString(R.string.download_format_key), "list")
            pref.summary = if (current == "list") names[0] else names[1]
            
            pref.setOnPreferenceClickListener {
                activity?.showBottomDialog(names, values.indexOf(current), getString(R.string.library_display_mode), false, {}) {
                    settingsManager.edit { putString(getString(R.string.download_format_key), values[it]) }
                    pref.summary = names[it]
                }
                true
            }
        }

        // Book Rating Format
        getPref(R.string.rating_format_key)?.let { pref ->
            val names = listOf("Star (1-5)", "Decimal (1-10)")
            val values = listOf("star", "decimal")
            val current = settingsManager.getString(getString(R.string.rating_format_key), "star")
            pref.summary = if (current == "star") names[0] else names[1]
            
            pref.setOnPreferenceClickListener {
                activity?.showBottomDialog(names, values.indexOf(current), getString(R.string.rating_format), false, {}) {
                    settingsManager.edit { putString(getString(R.string.rating_format_key), values[it]) }
                    pref.summary = names[it]
                }
                true
            }
        }

        // Cloudflare Resolver
        getPref(R.string.cloudflare_resolve_manual_key)?.setOnPreferenceClickListener {
            val apiNames = apis.map { it.name }
            activity?.showDialog(apiNames, -1, getString(R.string.cloudflare_resolve_manual_title), true, {}) { index ->
                val api = apis[index]
                ioSafe {
                    activity?.runOnUiThread { showToast("Starting Cloudflare check for ${api.name}...") }
                    com.lagradost.quicknovel.network.WebViewResolver(
                        Regex(".^"),
                        userAgent = null,
                        useOkhttp = false,
                        additionalUrls = listOf(Regex("."))
                    ).resolveUsingWebView(api.mainUrl, showDialog = true)
                    activity?.runOnUiThread { showToast("Finished checking ${api.name}") }
                }
            }
            true
        }

        // Clear Cookies
        getPref(R.string.clear_cookies_key)?.setOnPreferenceClickListener {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            showToast("Network cookies cleared")
            true
        }

        // Manual provider APK import
        getPref(R.string.provider_apk_import_key)?.setOnPreferenceClickListener {
            providerApkPicker.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
            true
        }

        // 3×3 Bento Grid — requires restart to take effect
        getPref("library_bento_3x3")?.setOnPreferenceChangeListener { _, _ ->
            val ctx = context ?: return@setOnPreferenceChangeListener true
            val act = activity ?: return@setOnPreferenceChangeListener true
            com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx, R.style.AlertDialogCustom)
                .setTitle("Restart Required")
                .setMessage("The 3×3 Bento Grid layout change takes effect after restarting the app.")
                .setCancelable(true)
                .setPositiveButton("Restart Now") { dialog, _ ->
                    dialog.dismiss()
                    val intent = act.packageManager.getLaunchIntentForPackage(act.packageName)
                        ?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    act.finishAffinity()
                    if (intent != null) act.startActivity(intent)
                }
                .setNegativeButton("Later") { dialog, _ -> dialog.dismiss() }
                .show()
            true // Allow the pref to be saved regardless
        }
    }

    private fun Preference.appThemeListener(settingsManager: android.content.SharedPreferences) {
        this.setOnPreferenceClickListener {
            val names = resources.getStringArray(R.array.themes_names).toList()
            val values = resources.getStringArray(R.array.themes_names_values).toList()
            val current = settingsManager.getString(getString(R.string.theme_key), "Amoled")
            activity?.showBottomDialog(names, values.indexOf(current), getString(R.string.theme), false, {}) {
                settingsManager.edit { putString(getString(R.string.theme_key), values[it]) }
                CommonActivity.recreateWithSmoothTransition(activity)
            }
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.fixPaddingStatusbar(view)
        listView.layoutManager = LinearLayoutManager(context)
        listView.clipChildren = false
    }

    companion object {
        fun getCurrentLocale(context: Context): String {
            val res = context.resources
            val conf = res.configuration
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                conf?.locales?.get(0)?.toString() ?: "en"
            } else {
                @Suppress("DEPRECATION")
                conf?.locale?.toString() ?: "en"
            }
        }

        val appLanguages = arrayListOf(
            Triple("", "English", "en"),
            Triple("", "Türkçe", "tr"),
            Triple("", "Español", "es"),
        ).sortedBy { it.second.lowercase() }
    }
}
