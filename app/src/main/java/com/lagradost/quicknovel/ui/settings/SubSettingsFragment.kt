package com.lagradost.quicknovel.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.quicknovel.API_VERSION
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.BackupUtils.backup
import com.lagradost.quicknovel.util.BackupUtils.restorePrompt
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.quicknovel.util.PluginItem
import com.lagradost.quicknovel.util.PluginManager
import com.lagradost.quicknovel.util.SubtitleHelper
import com.lagradost.quicknovel.util.UIHelper.clipboardHelper
import com.lagradost.quicknovel.util.UIHelper.dismissSafe
import com.lagradost.quicknovel.util.backgroundEffectLabels
import com.lagradost.quicknovel.util.SingleSelectionHelper.showBottomDialog
import com.lagradost.quicknovel.util.SingleSelectionHelper.showDialog
import com.lagradost.quicknovel.ui.txt
import com.lagradost.safefile.SafeFile
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class SubSettingsFragment : Fragment() {

    companion object {
        const val XML_RES_ID = "xml_res"
        const val ICON_RES_ID = "icon_res"
        const val TITLE_RES_ID = "title_res"
    }

    // Path Picker Activity Callback
    private val pathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            val context = context ?: return@registerForActivityResult
            
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                com.lagradost.quicknovel.mvvm.logError(e)
            }

            val file = SafeFile.fromUri(context, uri)
            val filePath = file?.filePath()
            
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit { putString(getString(R.string.download_path_key), uri.toString()) }

            (filePath ?: uri.toString()).let {
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit { putString(getString(R.string.download_path_pref), it) }
            }
        }

    // Auto Backup Path Picker Activity Callback
    private val autoBackupPathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                // If user cancels, reset interval to "never"
                val context = context ?: return@registerForActivityResult
                PreferenceManager.getDefaultSharedPreferences(context).edit {
                    putString("auto_backup_interval", "never")
                }
                return@registerForActivityResult
            }
            val context = context ?: return@registerForActivityResult
            
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                com.lagradost.quicknovel.mvvm.logError(e)
            }

            val file = SafeFile.fromUri(context, uri)
            val filePath = file?.filePath()
            
            PreferenceManager.getDefaultSharedPreferences(context).edit {
                putString("auto_backup_path", uri.toString())
                putString("auto_backup_path_pref", filePath ?: uri.toString())
            }

            com.lagradost.quicknovel.BackupWorkHelper.scheduleBackupWorker(context)
        }

    // Image Background Picker Callback
    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val context = context ?: return@registerForActivityResult
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit { putString(getString(R.string.background_image_key), uri.toString()) }
            // Dynamic theme recreation on image change
            CommonActivity.recreateWithSmoothTransition(activity)
        }

    // Manual Plugin Picker Callback
    private val pluginPicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
             if (uris.isNullOrEmpty()) return@registerForActivityResult
             val context = context ?: return@registerForActivityResult
             ioSafe {
                 try {
                     val pluginsDir = PluginManager.getPluginsDir(context)
                     val apkUris = mutableListOf<Uri>()
                     uris.forEach { uri ->
                         val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                             val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                             cursor.moveToFirst()
                             cursor.getString(nameIndex)
                         } ?: uri.lastPathSegment ?: "unknown"

                         if (name.endsWith(".apk", true) || name.endsWith(".dex", true)) {
                             if (PluginManager.verifyApkSignature(context, uri)) apkUris.add(uri)
                             else activity?.runOnUiThread { showToast("Rejected signature: $name") }
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
                     PluginManager.loadAllPlugins(context)
                     activity?.runOnUiThread { showToast("Plugins updated!") }
                  } catch (e: Exception) {
                     com.lagradost.quicknovel.mvvm.logError(e)
                 }
             }
        }

    // Provider APK Manual Import Picker Callback
    private val providerApkPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val ctx = context ?: return@registerForActivityResult
            ioSafe {
                val result = PluginManager.importProviderApk(ctx, uri)
                activity?.runOnUiThread {
                    result.fold(
                        onSuccess = { count ->
                            showToast(getString(R.string.import_provider_apk_success_format, count))
                        },
                        onFailure = { error ->
                            if (error.message == "No providers found in APK") {
                                showToast(getString(R.string.import_provider_apk_none_found))
                            } else {
                                showToast("Import failed: ${error.message}")
                            }
                        }
                    )
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val xmlRes = arguments?.getInt(XML_RES_ID) ?: R.xml.settings_appearance
        val titleRes = arguments?.getInt(TITLE_RES_ID) ?: R.string.appearance
        val iconRes = arguments?.getInt(ICON_RES_ID) ?: R.drawable.ic_baseline_color_lens_24

        return ComposeView(requireContext()).apply {
            setContent {
                SubSettingsScreen(
                    xmlRes = xmlRes,
                    iconRes = iconRes,
                    titleRes = titleRes,
                    onBack = { findNavController().popBackStack() },
                    onPreferenceClick = { key -> triggerPreferenceClick(key) },
                    onPreferenceChange = { key, value -> triggerPreferenceChange(key, value) }
                )
            }
        }
    }

    // Handles all click preferences dynamically
    private fun triggerPreferenceClick(key: String) {
        val context = context ?: return
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        
        when (key) {
            "background_image" -> {
                imagePicker.launch(arrayOf("image/*"))
            }

            "background_effect_mode" -> {
                val options = backgroundEffectLabels(context)
                val names = options.map { it.first }
                val values = options.map { it.second }
                val current = sharedPrefs.getString(getString(R.string.background_effect_mode_key), "none")
                val index = values.indexOf(com.lagradost.quicknovel.util.BackgroundEffectMode.from(current).value).coerceAtLeast(0)
                activity?.showBottomDialog(names, index, getString(R.string.background_effect_mode), false, {}) {
                    sharedPrefs.edit().putString(getString(R.string.background_effect_mode_key), values[it]).apply()
                    // Dynamic recreate for background refresh
                    CommonActivity.recreateWithSmoothTransition(activity)
                }
            }

            "reset_background" -> {
                com.lagradost.quicknovel.util.ComposeDialogHelper.showResetBackgroundDialog(context) {
                    sharedPrefs.edit {
                        remove(getString(R.string.background_image_key))
                        remove(getString(R.string.background_effect_mode_key))
                        remove(getString(R.string.background_blur_key))
                        remove(getString(R.string.background_dim_key))
                        remove(getString(R.string.background_grain_key))
                        remove(getString(R.string.background_vignette_key))
                    }
                    showToast(R.string.background_reset_confirmed)
                    CommonActivity.recreateWithSmoothTransition(activity)
                }
            }

            "detail_screen_style" -> {
                val names = listOf("Default", "Classic", "Modern")
                val values = listOf("0", "2", "1")
                val currentRaw = sharedPrefs.getString("detail_screen_style", "0")
                val current = when (currentRaw) {
                    "default" -> "0"
                    "classic" -> "2"
                    "modern" -> "1"
                    else -> currentRaw ?: "0"
                }
                val index = values.indexOf(current).coerceAtLeast(0)
                activity?.showBottomDialog(names, index, "Novel Detail Layout", false, {}) {
                    sharedPrefs.edit().putString("detail_screen_style", values[it]).apply()
                }
            }

            "theme_key" -> {
                val names = resources.getStringArray(R.array.themes_names).toList()
                val values = resources.getStringArray(R.array.themes_names_values).toList()
                val current = sharedPrefs.getString(getString(R.string.theme_key), "Amoled")
                activity?.showBottomDialog(names, values.indexOf(current), getString(R.string.theme), false, {}) {
                    sharedPrefs.edit().putString(getString(R.string.theme_key), values[it]).apply()
                    CommonActivity.recreateWithSmoothTransition(activity)
                }
            }

            "primary_color_key" -> {
                val names = listOf("Normal", "Electric Indigo", "Royal Lavender", "Cyber Cyan", "Red", "Sunset Orange", "Golden Hour", "Green", "Green Apple", "Sage Leaf", "Rose Gold", "Ice White", "White", "Coral Rose", "Carnation Pink", "Material You", "Material You (Tertiary)")
                val values = listOf("Normal", "ElectricIndigo", "RoyalLavender", "CyberCyan", "Red", "SolarOrange", "GoldenHour", "Green", "GreenApple", "SageLeaf", "RoseGold", "IceWhite", "White", "Party", "CarnationPink", "Monet", "Monet2")
                val current = sharedPrefs.getString(getString(R.string.primary_color_key), "Banana")
                activity?.showBottomDialog(names, values.indexOf(current), getString(R.string.primary_color_settings), false, {}) {
                    sharedPrefs.edit().putString(getString(R.string.primary_color_key), values[it]).apply()
                    CommonActivity.recreateWithSmoothTransition(activity)
                }
            }

            "app_font_key" -> {
                val names = listOf(
                    "System Default", "Alexandria FLF", "Product Sans", "Comico", "Instrument Serif", 
                    "Manosque", "Nevis", "Nighty Demo", "Orbitron", 
                    "Ostrich Sans Bold", "Ostrich Sans Inline", "Rude", "Shadow Hand",
                    "Skyscapers", "Struggle", "Typefesse Claire-Obscure", "Typefesse Pleine", 
                    "Unique"
                )
                val values = listOf(
                    "default", "alexandriaflf", "productsans", "comico", "instrument_serif", 
                    "manosque", "nevis", "nightydemo", "orbitron", 
                    "ostrich_sans_bold", "ostrich_sans_inline", "rude", "shadowhand",
                    "skyscapers", "struggle", "typefesse_claire_obscure", "typefesse_pleine", 
                    "unique"
                )
                val current = sharedPrefs.getString(getString(R.string.app_font_key), "default")
                activity?.showBottomDialog(names, values.indexOf(current).coerceAtLeast(0), getString(R.string.app_font), false, {}) { selectedIndex ->
                    sharedPrefs.edit().putString(getString(R.string.app_font_key), values[selectedIndex]).apply()
                    CommonActivity.isFontChangeTransition = true
                    CommonActivity.recreateWithSmoothTransition(activity)
                }
            }



            "locale_key" -> {
                val tempLangs = BaseSettingsFragment.appLanguages.toMutableList()
                val current = BaseSettingsFragment.getCurrentLocale(context)
                val languageCodes = tempLangs.map { it.third }
                val languageNames = tempLangs.map { "${it.first.ifBlank { SubtitleHelper.getFlagFromIso(it.third) ?: "" }} ${it.second}" }
                val index = languageCodes.indexOf(current)

                activity?.showDialog(languageNames, index, getString(R.string.provider_lang_settings), true, {}) {
                    val code = languageCodes[it]
                    CommonActivity.setLocale(activity, code)
                    sharedPrefs.edit().putString(getString(R.string.locale_key), code).apply()
                    CommonActivity.recreateWithSmoothTransition(activity)
                }
            }

            "rating_format" -> {
                val names = listOf("Star (1-5)", "Decimal (1-10)")
                val values = listOf("star", "decimal")
                val current = sharedPrefs.getString(getString(R.string.rating_format_key), "star")
                activity?.showBottomDialog(names, values.indexOf(current), getString(R.string.rating_format), false, {}) {
                    sharedPrefs.edit().putString(getString(R.string.rating_format_key), values[it]).apply()
                }
            }



            "library_nav_style" -> {
                val names = listOf("Pill Drawer", "Swipe View")
                val values = listOf("0", "1")
                val current = sharedPrefs.getString("library_nav_style", "1")
                activity?.showBottomDialog(names, values.indexOf(current), getString(R.string.library_nav_style), false, {}) {
                    sharedPrefs.edit().putString("library_nav_style", values[it]).apply()
                }
            }

            "updates_sync_interval" -> {
                val names = listOf("Disabled", "Every hour", "Every 3 hours", "Every 6 hours", "Every 12 hours", "Every 24 hours")
                val values = listOf("0", "1", "3", "6", "12", "24")
                val current = sharedPrefs.getString("updates_sync_interval", "12")
                activity?.showBottomDialog(names, values.indexOf(current), "Updates Check Interval", false, {}) {
                    sharedPrefs.edit().putString("updates_sync_interval", values[it]).apply()
                }
            }

            "manual_check_update" -> {
                ioSafe { if (activity?.runAutoUpdate(false) != true) showToast(R.string.no_update_found) }
            }

            "download_path_key" -> {
                val dirs = getDownloadDirs(context)
                val currentDir = sharedPrefs.getString(getString(R.string.download_path_pref), null)
                    ?: getDefaultDir(context)?.filePath()

                activity?.showBottomDialog(dirs + listOf("Custom"), dirs.indexOf(currentDir), getString(R.string.download_path_pref), true, {}) {
                    if (it == dirs.size) pathPicker.launch(Uri.EMPTY)
                    else sharedPrefs.edit {
                        putString(getString(R.string.download_path_key), dirs[it])
                        putString(getString(R.string.download_path_pref), dirs[it])
                    }
                }
            }

            "auto_backup_interval" -> {
                val names = listOf("Never (Disabled)", "Daily", "Weekly", "Monthly")
                val values = listOf("never", "daily", "weekly", "monthly")
                val current = sharedPrefs.getString("auto_backup_interval", "never") ?: "never"
                
                activity?.showBottomDialog(names, values.indexOf(current), "Automatic Backup", false, {}) { selectedIndex ->
                    val selectedValue = values[selectedIndex]
                    
                    if (selectedValue != "never") {
                        val path = sharedPrefs.getString("auto_backup_path", null)
                        if (path.isNullOrBlank()) {
                            com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.AlertDialogCustom)
                                .setTitle(getString(R.string.auto_backup_disclaimer_title))
                                .setMessage(getString(R.string.auto_backup_disclaimer_msg))
                                .setCancelable(true)
                                .setPositiveButton(getString(R.string.select_folder)) { dialog, _ ->
                                    dialog.dismiss()
                                    sharedPrefs.edit().putString("auto_backup_interval", selectedValue).apply()
                                    autoBackupPathPicker.launch(Uri.EMPTY)
                                }
                                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .show()
                        } else {
                            sharedPrefs.edit().putString("auto_backup_interval", selectedValue).apply()
                            com.lagradost.quicknovel.BackupWorkHelper.scheduleBackupWorker(context)
                        }
                    } else {
                        sharedPrefs.edit().putString("auto_backup_interval", selectedValue).apply()
                        com.lagradost.quicknovel.BackupWorkHelper.scheduleBackupWorker(context)
                    }
                }
            }

            "auto_backup_path" -> {
                autoBackupPathPicker.launch(Uri.EMPTY)
            }

            "backup_key" -> {
                activity?.backup()
            }

            "restore_key" -> {
                activity?.restorePrompt()
            }

            "wifi_sync_key" -> {
                val intent = android.content.Intent(activity, WifiSyncActivity::class.java)
                activity?.startActivity(intent)
            }

            "manage_data_key" -> {
                findNavController().navigate(R.id.navigation_manage_data)
            }

            "show_logcat_key" -> {
                com.lagradost.quicknovel.util.CrashHandler.shareLastCrashLog(context)
            }

            "plugin_sync_key" -> {
                // HOT-reloads plugins check
                ioSafe { PluginManager.loadAllPlugins(context); activity?.runOnUiThread { showToast("Plugins scanned & synced!") } }
            }

            "provider_apk_import_key" -> {
                providerApkPicker.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
            }

            "manage_hidden_providers_key" -> {
                showManageHiddenProviders(context)
            }

            "clear_all_providers_key" -> {
                val ctx = context ?: return
                com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx, R.style.AlertDialogCustom)
                    .setTitle("Clear All Providers")
                    .setMessage("Are you sure you want to delete all imported provider APKs and metadata? This cannot be undone.")
                    .setCancelable(true)
                    .setPositiveButton("Clear") { dialog, _ ->
                        dialog.dismiss()
                        ioSafe {
                            PluginManager.deleteAllPlugins(ctx)
                            activity?.runOnUiThread {
                                showToast("All providers cleared!")
                            }
                        }
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    .show()
            }

            "cloudflare_resolve_manual" -> {
                val apiNames = com.lagradost.quicknovel.util.Apis.apis.map { it.name }
                activity?.showDialog(apiNames, -1, getString(R.string.cloudflare_resolve_manual_title), true, {}) { index ->
                    val api = com.lagradost.quicknovel.util.Apis.apis[index]
                    ioSafe {
                        activity?.runOnUiThread { showToast("Starting Cloudflare WebView check for ${api.name}...") }
                        com.lagradost.quicknovel.network.WebViewResolver(
                            Regex(".^"),
                            userAgent = null,
                            useOkhttp = false,
                            additionalUrls = listOf(Regex("."))
                        ).resolveUsingWebView(api.mainUrl, showDialog = true)
                        activity?.runOnUiThread { showToast("Finished checking ${api.name}") }
                    }
                }
            }

            "clear_cookies_key" -> {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                showToast("Network cookies cleared")
            }

            "card_border_style_picker" -> {
                val names = listOf("None", "Glow — soft accent halo", "Gradient — rotating sweep", "Neon — electric pulse")
                val values = listOf("none", "glow", "gradient", "neon")
                val current = sharedPrefs.getString(com.lagradost.quicknovel.ui.theme.VibePrefs.CARD_BORDER_STYLE, "none")
                activity?.showBottomDialog(names, values.indexOf(current).coerceAtLeast(0), "Card Border Style", false, {}) {
                    sharedPrefs.edit().putString(com.lagradost.quicknovel.ui.theme.VibePrefs.CARD_BORDER_STYLE, values[it]).apply()
                }
            }

            "accent_gradient_end_color" -> {
                val currentArgb = sharedPrefs.getInt(com.lagradost.quicknovel.ui.theme.VibePrefs.ACCENT_GRADIENT_END_COLOR, 0xFF9C27B0.toInt())
                com.jaredrummler.android.colorpicker.ColorPickerDialog.newBuilder()
                    .setColor(currentArgb)
                    .setShowAlphaSlider(false)
                    .setDialogTitle(R.string.primary_color_settings)
                    .create()
                    .also { dialog ->
                        dialog.setColorPickerDialogListener(object : com.jaredrummler.android.colorpicker.ColorPickerDialogListener {
                            override fun onColorSelected(dialogId: Int, color: Int) {
                                sharedPrefs.edit().putInt(com.lagradost.quicknovel.ui.theme.VibePrefs.ACCENT_GRADIENT_END_COLOR, color).apply()
                            }
                            override fun onDialogDismissed(dialogId: Int) {}
                        })
                        dialog.show(parentFragmentManager, "accent_gradient_color_picker")
                    }
            }

            "doh_provider" -> {
                val names = listOf("None (System default)", "Cloudflare", "Google", "AdGuard (Ad-blocking)", "Quad9", "DNSPod", "Mullvad")
                val values = listOf("none", "cloudflare", "google", "adguard", "quad9", "dnspod", "mullvad")
                val current = sharedPrefs.getString(com.lagradost.quicknovel.NetworkPrefs.DOH_PROVIDER, "none")
                activity?.showBottomDialog(names, values.indexOf(current).coerceAtLeast(0), "DNS over HTTPS", false, {}) { selectedIndex ->
                    val selectedValue = values[selectedIndex]
                    sharedPrefs.edit().putString(com.lagradost.quicknovel.NetworkPrefs.DOH_PROVIDER, selectedValue).apply()
                    
                    // Dynamically apply the new DoH settings and sever existing connections immediately
                    com.lagradost.quicknovel.network.DnsHelper.updateDns(context)
                    
                    showToast("DNS settings updated dynamically")
                }
            }
        }
    }

    // Handles live preference slider changes
    private fun triggerPreferenceChange(key: String, value: Any) {
        val act = activity ?: return
        
        when (key) {
            "app_font_scale" -> {
                // Triggers immediate app-wide UI text scaling recompose!
            }
            "background_blur", "background_dim", "background_grain", "background_vignette" -> {
                // Dynamically updates local activities in case we need active canvas updates
            }
            "library_bento_3x3" -> {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(act, R.style.AlertDialogCustom)
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
            }
        }
    }
}
