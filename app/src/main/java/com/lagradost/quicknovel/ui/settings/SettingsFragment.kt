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
import com.lagradost.safefile.MediaFileContentType
import com.lagradost.safefile.SafeFile
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

class SettingsFragment : PreferenceFragmentCompat() {
    private fun PreferenceFragmentCompat?.getPref(id: Int): Preference? {
        if (this == null) return null

        return try {
            findPreference(getString(id))
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    private fun PreferenceFragmentCompat?.getPref(key: String): Preference? {
        if (this == null) return null

        return findPreference(key)
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

        // idk, if you find a way of automating this it would be great
        // https://www.iemoji.com/view/emoji/1794/flags/antarctica
        // Emoji Character Encoding Data --> C/C++/Java Src
        // https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes leave blank for auto
        val appLanguages = arrayListOf(
            /* begin language list */
            Triple("", "English", "en"),
            Triple("", "Türkçe", "tr"),
            Triple("", "Español", "es"),
            /* end language list */
        ).sortedBy { it.second.lowercase() } //ye, we go alphabetical, so ppl don't put their lang on top

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
            // Priority 1: App's public external files directory (guaranteed accessible on Scoped Storage)
            val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (externalDir != null) {
                 return SafeFile.fromUri(context, File(externalDir, "Epub").apply { mkdirs() }.toUri())
            }
            
            // Priority 2: Traditional Downloads folder via MediaStore/SAF (Legacy fallback)
            return SafeFile.fromMedia(
                context, MediaFileContentType.Downloads
            )?.gotoDirectory("Epub")
        }

        /**
         * Turns a string to an UniFile. Used for stored string paths such as settings.
         * Should only be used to get a download path.
         * */
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


        /**
         * Base path where downloaded things should be stored, changes depending on settings.
         * Returns the file and a string to be stored for future file retrieval.
         * UniFile.filePath is not sufficient for storage.
         * */
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
    }

    // Open file picker
    private val pathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            // It lies, it can be null if file manager quits.
            if (uri == null) return@registerForActivityResult
            val context = context ?: return@registerForActivityResult
            val file = SafeFile.fromUri(context, uri)
            val filePath = file?.filePath()
            println("Selected URI path: $uri - Full path: $filePath")

            // Stores the real URI using download_path_key
            // Important that the URI is stored instead of filepath due to permissions.
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit { putString(getString(R.string.download_path_key), uri.toString()) }

            // From URI -> File path
            // File path here is purely for cosmetic purposes in settings
            (filePath ?: uri.toString()).let {
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit { putString(getString(R.string.download_path_pref), it) }
            }
        }

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val context = context ?: return@registerForActivityResult
            
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)

            PreferenceManager.getDefaultSharedPreferences(context)
                .edit { putString(getString(R.string.background_image_key), uri.toString()) }
        }
    private val pluginPicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult
            val context = context ?: return@registerForActivityResult

            ioSafe {
                try {
                    val pluginsDir = com.lagradost.quicknovel.util.PluginManager.getPluginsDir(context)

                    val apkUris = mutableListOf<android.net.Uri>()
                    val jsonUris = mutableListOf<android.net.Uri>()
                    var rejectedCount = 0

                    uris.forEach { uri ->
                        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            cursor.moveToFirst()
                            cursor.getString(nameIndex)
                        } ?: uri.lastPathSegment ?: "unknown"

                        when {
                            name.endsWith(".apk", true) || name.endsWith(".dex", true) -> {
                                if (com.lagradost.quicknovel.util.PluginManager.verifyApkSignature(context, uri)) {
                                    apkUris.add(uri)
                                } else {
                                    rejectedCount++
                                    activity?.runOnUiThread {
                                        showToast("Rejected: \"$name\" is not signed by a trusted key.")
                                    }
                                }
                            }
                            name.endsWith(".json", true) -> jsonUris.add(uri)
                        }
                    }

                    // Nothing valid to import
                    if (apkUris.isEmpty()) {
                        if (rejectedCount == 0) {
                            activity?.runOnUiThread { showToast(getString(R.string.plugin_import_invalid_apk)) }
                        }
                        return@ioSafe
                    }

                    // Special case: 1 APK + 1 JSON -> Rename JSON to match APK
                    if (apkUris.size == 1 && jsonUris.size == 1) {
                        val apkUri = apkUris[0]
                        val jsonUri = jsonUris[0]

                        val apkName = context.contentResolver.query(apkUri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            cursor.moveToFirst()
                            cursor.getString(nameIndex)
                        } ?: "plugin.apk"

                        val baseName = apkName.substringBeforeLast(".")
                        val jsonTarget = File(pluginsDir, "$baseName.json")
                        
                        // Clean up old metadata before overwriting
                        if (jsonTarget.exists()) {
                            try {
                                val oldMetaStr = jsonTarget.readText()
                                val oldMeta = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readValue(oldMetaStr, com.lagradost.quicknovel.util.PluginItem::class.java)
                                oldMeta.mainClass?.let { com.lagradost.quicknovel.util.PluginManager.unloadPlugin(it) }
                                oldMeta.mainClasses?.forEach { com.lagradost.quicknovel.util.PluginManager.unloadPlugin(it) }
                            } catch (e: Exception) { }
                        }

                        context.contentResolver.openInputStream(apkUri)?.use { input ->
                            val target = File(pluginsDir, apkName)
                            com.lagradost.quicknovel.util.PluginManager.removeCachesForPath(target.absolutePath)
                            if (target.exists()) target.delete() // Clear existing to avoid EACCES
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        context.contentResolver.openInputStream(jsonUri)?.use { input ->
                            val target = File(pluginsDir, "$baseName.json")
                            if (target.exists()) target.delete()
                            
                            // Load the JSON and set isManualImport to true before saving
                            try {
                                val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                                val meta = mapper.readValue(input.readBytes(), com.lagradost.quicknovel.util.PluginItem::class.java)
                                val updatedMeta = meta.copy(isManualImport = true)
                                target.writeText(mapper.writeValueAsString(updatedMeta))
                            } catch (e: Exception) {
                                // Fallback to raw copy if parsing fails
                                context.contentResolver.openInputStream(jsonUri)?.use { freshInput ->
                                    target.outputStream().use { output -> freshInput.copyTo(output) }
                                }
                            }
                        }
                    } else {
                        // Multiple files: copy all verified APKs and any JSONs
                        apkUris.forEach { uri ->
                            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                cursor.moveToFirst()
                                cursor.getString(nameIndex)
                            } ?: uri.lastPathSegment ?: "unknown"

                            val baseName = fileName.substringBeforeLast(".")
                            val jsonTarget = File(pluginsDir, "$baseName.json")
                            
                            // Unload any old plugin classes associated with this APK using its JSON
                            if (jsonTarget.exists()) {
                                try {
                                    val oldMetaStr = jsonTarget.readText()
                                    val oldMeta = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readValue(oldMetaStr, com.lagradost.quicknovel.util.PluginItem::class.java)
                                    oldMeta.mainClass?.let { com.lagradost.quicknovel.util.PluginManager.unloadPlugin(it) }
                                    oldMeta.mainClasses?.forEach { com.lagradost.quicknovel.util.PluginManager.unloadPlugin(it) }
                                } catch (e: Exception) { }
                            }

                            context.contentResolver.openInputStream(uri)?.use { input ->
                                val target = File(pluginsDir, fileName)
                                com.lagradost.quicknovel.util.PluginManager.removeCachesForPath(target.absolutePath)
                                if (target.exists()) target.delete()
                                target.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        jsonUris.forEach { uri ->
                            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                cursor.moveToFirst()
                                cursor.getString(nameIndex)
                            } ?: uri.lastPathSegment ?: "unknown"

                            context.contentResolver.openInputStream(uri)?.use { input ->
                                val target = File(pluginsDir, fileName)
                                if (target.exists()) target.delete()
                                
                                // Load the JSON and set isManualImport to true before saving
                                try {
                                    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                                    val meta = mapper.readValue(input.readBytes(), com.lagradost.quicknovel.util.PluginItem::class.java)
                                    val updatedMeta = meta.copy(isManualImport = true)
                                    target.writeText(mapper.writeValueAsString(updatedMeta))
                                } catch (e: Exception) {
                                    // Fallback to raw copy
                                    context.contentResolver.openInputStream(uri)?.use { freshInput ->
                                        target.outputStream().use { output -> freshInput.copyTo(output) }
                                    }
                                }
                            }
                        }
                    }

                    // Reload plugins from disk and report the count of freshly loaded providers
                    val loadedCount = com.lagradost.quicknovel.util.PluginManager.loadAllPlugins(context)
                    activity?.runOnUiThread {
                        val msg = if (loadedCount > 0) {
                            "Plugin installed! $loadedCount provider(s) now active."
                        } else {
                            "Plugin copied but no providers loaded. Check the JSON metadata."
                        }
                        showToast(msg)
                    }
                } catch (e: Exception) {
                    logError(e)
                    activity?.runOnUiThread { showToast("Failed to import: ${e.message}") }
                }
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val multiPreference = getPref(R.string.search_providers_list_key)

        findPreference<Preference>("reading_stats_header")?.setOnPreferenceClickListener {
            try {
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    .navigate(R.id.navigation_reading_stats)
            } catch (e: Exception) {
                logError(e)
            }
            true
        }
        
        findPreference<Preference>(getString(R.string.manage_data_key))?.setOnPreferenceClickListener {
            try {
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    .navigate(R.id.navigation_manage_data)
            } catch (e: Exception) {
                logError(e)
            }
            true
        }

        val updatePrefrence =
            findPreference<Preference>(getString(R.string.manual_check_update_key))!!
        val providerLangPreference =
            findPreference<Preference>(getString(R.string.provider_lang_key))!!

        multiPreference?.setOnPreferenceClickListener {
            showSearchProviders(activity)
            return@setOnPreferenceClickListener true
        }

        /*multiPreference.entries = apiNames.toTypedArray()
        multiPreference.entryValues = apiNames.toTypedArray()

        multiPreference.setOnPreferenceChangeListener { _, newValue ->
            (newValue as HashSet<String>?)?.let {
                providersActive = it
            }
            return@setOnPreferenceChangeListener true
        }*/

        getPref(R.string.locale_key)?.setOnPreferenceClickListener { pref ->
            val tempLangs = appLanguages.toMutableList()
            val current = getCurrentLocale(pref.context)
            val languageCodes = tempLangs.map { (_, _, iso) -> iso }
            val languageNames = tempLangs.map { (emoji, name, iso) ->
                val flag = emoji.ifBlank { SubtitleHelper.getFlagFromIso(iso) ?: "ERROR" }
                "$flag $name"
            }
            val index = languageCodes.indexOf(current)

            activity?.showDialog(
                languageNames, index, getString(R.string.provider_lang_settings), true, { }
            ) { languageIndex ->
                try {
                    val code = languageCodes[languageIndex]
                    CommonActivity.setLocale(activity, code)
                    settingsManager.edit { putString(getString(R.string.locale_key), code) }
                    CommonActivity.recreateWithSmoothTransition(activity)
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.backup_key)?.setOnPreferenceClickListener {
            activity?.backup()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.restore_key)?.setOnPreferenceClickListener {
            activity?.restorePrompt()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.download_path_key)?.setOnPreferenceClickListener {
            val dirs = getDownloadDirs(context)

            val currentDir =
                settingsManager.getString(getString(R.string.download_path_pref), null)
                    ?: context?.let { ctx -> getDefaultDir(ctx)?.filePath() }

            activity?.showBottomDialog(
                dirs + listOf("Custom"),
                dirs.indexOf(currentDir),
                getString(R.string.download_path_pref),
                true,
                {}) {
                // Last = custom
                if (it == dirs.size) {
                    try {
                        pathPicker.launch(Uri.EMPTY)
                    } catch (e: Exception) {
                        logError(e)
                    }
                } else {
                    // Sets both visual and actual paths.
                    // key = used path
                    // pref = visual path
                    settingsManager.edit {
                        putString(getString(R.string.download_path_key), dirs[it])
                        putString(getString(R.string.download_path_pref), dirs[it])
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }

        updatePrefrence.setOnPreferenceClickListener {
            ioSafe {
                if (true != activity?.runAutoUpdate(false)) {
                    showToast(R.string.no_update_found, Toast.LENGTH_SHORT)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.background_image_key)?.setOnPreferenceClickListener {
            try {
                imagePicker.launch(arrayOf("image/*"))
            } catch (e: Exception) {
                logError(e)
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.reset_background_key)?.setOnPreferenceClickListener {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it.context)
            settingsManager.edit().remove(getString(R.string.background_image_key)).apply()
            showToast(R.string.background_reset_confirmed) // Or a dedicated string if available, but this works
            return@setOnPreferenceClickListener true
        }

        providerLangPreference.setOnPreferenceClickListener {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it.context)

            activity?.getApiProviderLangSettings()?.let { current ->
                val allLangs = HashSet<String>()
                for (api in apis) {
                    allLangs.add(api.lang)
                }

                val currentList = ArrayList<Int>()
                for (i in current) {
                    currentList.add(allLangs.indexOf(i))
                }

                val names = allLangs.mapNotNull {
                    val fullName = SubtitleHelper.fromTwoLettersToLanguage(it)
                    if (fullName.isNullOrEmpty()) {
                        return@mapNotNull null
                    }

                    it to fullName
                }

                context?.showMultiDialog(
                    names.map { it.second },
                    currentList,
                    getString(R.string.provider_lang_settings),
                    {}) { selectedList ->
                    settingsManager.edit {
                        putStringSet(
                            getString(R.string.provider_lang_key),
                            selectedList.map { names[it].first }.toMutableSet()
                        )
                    }

                    val settings = it.context.getApiSettings()
                    providersActive.clear()
                    providersActive.addAll(settings)
                }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.show_logcat_key)?.setOnPreferenceClickListener { pref ->
            val builder = AlertDialog.Builder(pref.context, R.style.AlertDialogCustom)

            val binding = LogcatBinding.inflate(layoutInflater, null, false)
            builder.setView(binding.root)

            val dialog = builder.create()
            dialog.show()

            val logList = mutableListOf<String>()
            try {
                // https://developer.android.com/studio/command-line/logcat
                val process = Runtime.getRuntime().exec("logcat -d")
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                bufferedReader.lineSequence().forEach { logList.add(it) }
            } catch (e: Exception) {
                logError(e) // kinda ironic
            }

            val adapter = LogcatAdapter().apply { submitList(logList) }
            binding.logcatRecyclerView.layoutManager = LinearLayoutManager(pref.context)
            binding.logcatRecyclerView.adapter = adapter

            binding.copyBtt.setOnClickListener {
                clipboardHelper(txt("Logcat"), logList.joinToString("\n"))
                dialog.dismissSafe(activity)
            }

            binding.clearBtt.setOnClickListener {
                Runtime.getRuntime().exec("logcat -c")
                dialog.dismissSafe(activity)
            }

            binding.saveBtt.setOnClickListener {
                val date = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.getDefault()).format(
                    Date(currentTimeMillis())
                )
                var fileStream: OutputStream?
                try {
                    val (stream, _) = setupStream(
                        it.context,
                        "logcat_${date}",
                        "txt",
                        getDefaultDir(context = it.context)
                    )
                    fileStream = stream
                    if (fileStream == null) throw ErrorLoadingException("No stream")

                    fileStream.writer().use { writer -> writer.write(logList.joinToString("\n")) }
                    dialog.dismissSafe(activity)
                } catch (t: Throwable) {
                    logError(t)
                    showToast(t.message)
                }
            }

            binding.closeBtt.setOnClickListener {
                dialog.dismissSafe(activity)
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.plugin_sync_key)?.setOnPreferenceClickListener {
            val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.lagradost.quicknovel.sync.PluginSyncWorker>()
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .build()
            androidx.work.WorkManager.getInstance(it.context).enqueueUniqueWork(
                "PluginSyncManual",
                androidx.work.ExistingWorkPolicy.REPLACE,
                syncRequest
            )
            showToast("Checking for plugin updates...")
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.plugin_import_key)?.setOnPreferenceClickListener {
            try {
                pluginPicker.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                logError(e)
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.cloudflare_resolve_manual_key)?.setOnPreferenceClickListener {
            val builder = AlertDialog.Builder(it.context, R.style.AlertDialogCustom)
            builder.setTitle(R.string.cloudflare_resolve_manual_title)
            
            val input = android.widget.EditText(it.context)
            input.hint = "https://example.com"
            builder.setView(input)
            
            builder.setPositiveButton(R.string.ok) { _, _ ->
                val url = input.text.toString()
                if (url.isNotBlank()) {
                    ioSafe {
                        com.lagradost.quicknovel.network.WebViewResolver(
                            Regex(".^"),
                            userAgent = null,
                            useOkhttp = false,
                            additionalUrls = listOf(Regex("."))
                        ).resolveUsingWebView(url, showDialog = true) {
                            // Just open it to get cookies and save them to CookieManager
                            false
                        }
                    }
                }
            }
            builder.setNegativeButton(R.string.cancel, null)
            builder.show()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.clear_cookies_key)?.setOnPreferenceClickListener {
            try {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                // Also clear the internal map in our interceptor if possible
                // Since it's a singleton-like static in MainActivity for now (or instantiated once)
                // Actually, we can just clear it here if we had access, but CookieManager is the most important.
                showToast("Network cookies cleared successfully")
            } catch (e: Exception) {
                logError(e)
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.theme_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_names).toMutableList()
            val prefValues = resources.getStringArray(R.array.themes_names_values).toMutableList()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith("Monet")) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.theme_key), "Amoled")

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.theme),
                false,
                {}) {
                try {
                    //AnyAdapter.sharedPool.clear()
                    //HistoryAdapter.sharedPool.clear()

                    settingsManager.edit {
                        putString(getString(R.string.theme_key), prefValues[it])
                    }
                    CommonActivity.recreateWithSmoothTransition(activity)
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.primary_color_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_overlay_names).toMutableList()
            val prefValues =
                resources.getStringArray(R.array.themes_overlay_names_values).toMutableList()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith("Monet")) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.primary_color_key), "Banana")

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.primary_color_settings),
                true,
                {}) {
                try {
                    settingsManager.edit {
                        putString(getString(R.string.primary_color_key), prefValues[it])
                    }
                    CommonActivity.recreateWithSmoothTransition(activity)
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.rating_format_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.RatingFormat)
            val prefValues = resources.getStringArray(R.array.RatingFormatData)

            val current =
                settingsManager.getString(getString(R.string.rating_format_key), prefValues.first())

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.rating_format),
                false,
                {}) {
                try {
                    settingsManager.edit {
                        putString(getString(R.string.rating_format_key), prefValues[it])
                    }
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.download_format_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.DownloadGridFormat)
            val prefValues = resources.getStringArray(R.array.DownloadGridFormatData)

            val current =
                settingsManager.getString(
                    getString(R.string.download_format_key),
                    prefValues[1]//As soon as you install the app, everything is displayed as a list even though it is set to grid. This is because it was previously set to .first()
                )

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.rating_format),
                false,
                {}) {
                safe {
                    settingsManager.edit {
                        AnyAdapter.sharedPool.clear()
                        putString(getString(R.string.download_format_key), prefValues[it])
                    }
                    showRestartDialog()
                }
            }
            return@setOnPreferenceClickListener true
        }

        /*getPref(R.string.theme_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_names)
            val prefValues = resources.getStringArray(R.array.themes_names_values)

            val currentPref =
                settingsManager.getString(getString(R.string.theme_key), "Blue")

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPref),
                getString(R.string.theme),
                true,
                {}) { index ->
                settingsManager.edit()
                    .putString(getString(R.string.theme_key), prefValues[index])
                    .apply()
            }
            return@setOnPreferenceClickListener true
        }*/


        /*
        val listPreference = findPreference<ListPreference>("provider_list")!!

        val apiNames = MainActivity.apis.map { it.name }

        listPreference.entries = apiNames.toTypedArray()
        listPreference.entryValues = apiNames.toTypedArray()
        listPreference.setOnPreferenceChangeListener { preference, newValue ->
            MainActivity.activeAPI = MainActivity.getApiFromName(newValue.toString())
            return@setOnPreferenceChangeListener true
        }*/
        getPref(R.string.library_pinterest_bento_key)?.setOnPreferenceChangeListener { _, newValue ->
             val enabled = newValue as? Boolean ?: false
             updateBentoSettings(enabled)
             true
         }

        getPref("library_bento_3x3")?.setOnPreferenceChangeListener { _, _ ->
            showRestartDialog()
            true
        }

        getPref(R.string.download_format_key)?.setOnPreferenceChangeListener { _, _ ->
            showRestartDialog()
            true
        }
        updateBentoSettings()
    }

    private fun updateBentoSettings(forceValue: Boolean? = null) {
        val context = context ?: return
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val pinterestBento = forceValue ?: settingsManager.getBoolean(getString(R.string.library_pinterest_bento_key), false)
        getPref("library_bento_3x3")?.let { bento3x3 ->
            bento3x3.isEnabled = pinterestBento
            // If Pinterest bento is disabled, we MUST ensure 3x3 is also disabled to avoid phantom state
            if (!pinterestBento && settingsManager.getBoolean("library_bento_3x3", false)) {
                settingsManager.edit { putBoolean("library_bento_3x3", false) }
                // Update the UI switch state if it's currently showing
                (bento3x3 as? androidx.preference.TwoStatePreference)?.isChecked = false
            }
        }
    }

    private fun restartApp() {
        val ctx = activity?.applicationContext ?: context ?: return
        val intent = Intent(ctx, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)
        // Force exit to ensure clean relaunch
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun showRestartDialog() {
        val ctx = context ?: return
        val builder = AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
        val dialogBinding = com.lagradost.quicknovel.databinding.DialogRestartAppBinding.inflate(layoutInflater)
        builder.setView(dialogBinding.root)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.restartButton.setOnClickListener {
            dialog.dismissSafe(activity)
            restartApp()
        }

        dialogBinding.laterButton.setOnClickListener {
            dialog.dismissSafe(activity)
        }

        dialog.show()
        dialog.window?.let { window ->
            window.setDimAmount(0.8f) // Darker dim for better readability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // System-level blur for a premium glass feel on supported devices
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes.blurBehindRadius = 45 
                window.attributes = window.attributes // Apply changes
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Optimization for smooth scrolling
        listView.apply {
            recycledViewPool.setMaxRecycledViews(0, 20)
            setItemViewCacheSize(20)
            isDrawingCacheEnabled = false
            setHasFixedSize(true)
        }

        // Fix status bar overlap
        activity?.fixPaddingStatusbar(view)

        // Fix bottom overlap with floating nav bar
        listView?.let { recycler ->
            recycler.clipToPadding = false
            // 60dp (nav bar) + 30dp (margin) + some extra breathing room
            val extraPadding = 100.toPx
            
            ViewCompat.setOnApplyWindowInsetsListener(recycler) { v, insets ->
                val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                v.updatePadding(bottom = navigationBars.bottom + extraPadding)
                insets
            }
        }
    }
}