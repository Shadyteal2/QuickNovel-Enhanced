package com.lagradost.quicknovel.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.BuildConfig
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.logError
import java.io.*
import java.net.URL
import java.net.URLConnection
import kotlin.concurrent.thread

const val UPDATE_TIME = 1000

class InAppUpdater {
    companion object {
        // === IN APP UPDATER ===
        data class GithubAsset(
            @JsonProperty("name") val name: String,
            @JsonProperty("size") val size: Int, // Size bytes
            @JsonProperty("browser_download_url") val browser_download_url: String, // download link
            @JsonProperty("content_type") val content_type: String, // application/vnd.android.package-archive
        )

        data class GithubRelease(
            @JsonProperty("tag_name") val tag_name: String, // Version code
            @JsonProperty("body") val body: String, // Desc
            @JsonProperty("assets") val assets: List<GithubAsset>,
            @JsonProperty("target_commitish") val target_commitish: String, // branch
        )

        data class Update(
            @JsonProperty("shouldUpdate") val shouldUpdate: Boolean,
            @JsonProperty("updateURL") val updateURL: String?,
            @JsonProperty("updateVersion") val updateVersion: String?,
            @JsonProperty("changelog") val changelog: String?,
        )

        private val mapper = JsonMapper.builder().addModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

        private suspend fun Activity.getAppUpdate(): Update {
            try {
                val url = "https://api.github.com/repos/Shadyteal2/QuickNovel-Enhanced/releases/latest"
                val headers = mapOf("Accept" to "application/vnd.github.v3+json")
                val response =
                    mapper.readValue<GithubRelease>(app.get(url, headers = headers).text)

                val versionRegex = Regex("""(.*?((\d)\.(\d)\.(\d)).*\.apk)""")

                val foundAsset = response.assets.find { it.content_type == "application/vnd.android.package-archive" }
                    ?: response.assets.find { it.name.endsWith(".apk") }
                    ?: response.assets.getOrNull(0)
                val currentVersion = packageName?.let {
                    packageManager.getPackageInfo(
                        it,
                        0
                    )
                }

                val foundVersion = foundAsset?.name?.let { versionRegex.find(it) }
                val shouldUpdate = if (foundAsset?.browser_download_url != "" && foundVersion != null) {
                    val current = currentVersion?.versionName?.split(".")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                    val found = foundVersion.groupValues[2].split(".").mapNotNull { it.toIntOrNull() }
                    
                    if (current.isNotEmpty() && found.isNotEmpty()) {
                        var updateNeeded = false
                        for (i in 0 until maxOf(current.size, found.size)) {
                            val curr = current.getOrNull(i) ?: 0
                            val fnd = found.getOrNull(i) ?: 0
                            if (fnd > curr) {
                                updateNeeded = true
                                break
                            } else if (fnd < curr) {
                                break
                            }
                        }
                        updateNeeded
                    } else false
                } else false
                return if (foundVersion != null) {
                    Update(
                        shouldUpdate,
                        foundAsset.browser_download_url,
                        foundVersion.groupValues[2],
                        response.body
                    )
                } else {
                    Update(false, null, null, null)
                }

            } catch (e: Exception) {
                println(e)
                return Update(false, null, null, null)
            }
        }

        suspend fun Activity.runAutoUpdate(checkAutoUpdate: Boolean = true): Boolean {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            if (!checkAutoUpdate || settingsManager.getBoolean(
                    getString(R.string.auto_update_key),
                    true
                )
            ) {
                val update = getAppUpdate()
                if (update.shouldUpdate && update.updateURL != null) {
                    runOnUiThread {
                        val currentVersion = packageName?.let {
                            packageManager.getPackageInfo(
                                it,
                                0
                            )
                        }

                        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                        builder.setTitle(
                            getString(R.string.new_update_found_format).format(
                                currentVersion?.versionName,
                                update.updateVersion
                            )
                        )
                        builder.setMessage(update.changelog)

                        val context = this
                        builder.apply {
                            setPositiveButton(R.string.download_open_action) { _, _ ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Shadyteal2/QuickNovel-Enhanced/releases"))
                                startActivity(intent)
                            }

                            setNegativeButton(R.string.cancel) { _, _ -> }

                            if (checkAutoUpdate) {
                                setNeutralButton(R.string.dont_show_again) { _, _ ->
                                    settingsManager.edit().putBoolean("auto_update", false).apply()
                                }
                            }
                        }
                        builder.show()
                    }
                    return true
                }
                return false
            }
            return false
        }
    }
}