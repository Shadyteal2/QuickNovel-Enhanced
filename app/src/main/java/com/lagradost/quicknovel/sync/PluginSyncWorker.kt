package com.lagradost.quicknovel.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.util.PluginItem
import com.lagradost.quicknovel.util.PluginManifest
import com.lagradost.quicknovel.util.PluginManager
import com.lagradost.quicknovel.API_VERSION
import com.lagradost.quicknovel.mvvm.logError
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.nicehttp.Requests
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PluginSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val syncApp = Requests(
        OkHttpClient().newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build(),
        defaultHeaders = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val MANIFEST_URL = "https://raw.githubusercontent.com/Shadyteal2/NeoQN-Extensions/main/manifest.json"
        try {
            android.util.Log.i("PluginSync", "Starting manifest fetch from $MANIFEST_URL")
            val response = syncApp.get(MANIFEST_URL)
            if (!response.isSuccessful) {
                android.util.Log.e("PluginSync", "Manifest fetch failed: ${response.code}")
                return@withContext Result.retry()
            }

            val mapper = jacksonObjectMapper()
            val manifest = mapper.readValue(response.text, PluginManifest::class.java)
            android.util.Log.i("PluginSync", "Manifest loaded: ${manifest.plugins.size} plugins")

            val pluginsDir = PluginManager.getPluginsDir(applicationContext)

            // Group by APK URL to avoid redundant downloads
            val groups = manifest.plugins.groupBy { it.url }
            
            groups.forEach { (url, bundlePlugins) ->
                syncBundle(url, bundlePlugins, pluginsDir)
            }

            // CRITICAL: Reload all plugins from disk so newly downloaded ones appear immediately
            android.util.Log.i("PluginSync", "Sync complete. Reloading all plugins from disk...")
            val count = PluginManager.loadAllPlugins(applicationContext)
            android.util.Log.i("PluginSync", "Post-sync reload complete. Total providers: $count")

            Result.success()
        } catch (e: Exception) {
            logError(e)
            Result.failure()
        }
    }

    private suspend fun syncBundle(url: String, plugins: List<PluginItem>, pluginsDir: File) {
        if (plugins.isEmpty()) return
        
        // Use a stable filename based on the URL or the first plugin's ID
        val bundleId = plugins.first().pluginId 
        val apkFile = File(pluginsDir, "$bundleId.apk")
        val jsonFile = File(pluginsDir, "$bundleId.json")

        // 1. Determine if any plugin in this bundle needs an update
        var currentVersion = -1
        if (jsonFile.exists()) {
            try {
                val currentMeta = jacksonObjectMapper()
                    .readValue(jsonFile.readText(), PluginItem::class.java)
                currentVersion = currentMeta.version
            } catch (e: Exception) {}
        }

        val latestVersion = plugins.maxOf { it.version }
        android.util.Log.d("PluginSync", "Syncing bundle for $url - Current version: $currentVersion, Latest version: $latestVersion")
        
        if (latestVersion >= currentVersion) {
            // Check compatibility for all plugins in bundle (optional: if any fails, skip bundle or just that plugin?)
            // We'll proceed if at least one is compatible
            val compatiblePlugins = plugins.filter { it.minApiVersion <= API_VERSION }
            if (compatiblePlugins.isEmpty()) {
                android.util.Log.w("PluginSync", "No compatible plugins in bundle for $url (Required: <= $API_VERSION)")
                return
            }

            val partFile = File(pluginsDir, "$bundleId.apk.part")
            android.util.Log.i("PluginSync", "Downloading bundle from $url")
            val downloadResponse = syncApp.get(url)
            if (downloadResponse.isSuccessful) {
                try {
                    partFile.writeBytes(downloadResponse.body.bytes())
                    partFile.setReadOnly() // Security requirement for DexClassLoader

                    // Unload ALL classes from previous version of this APK
                    if (jsonFile.exists()) {
                         try {
                            val oldMeta = jacksonObjectMapper().readValue(jsonFile.readText(), PluginItem::class.java)
                            oldMeta.mainClass?.let { PluginManager.unloadPlugin(it) }
                            oldMeta.mainClasses?.forEach { PluginManager.unloadPlugin(it) }
                        } catch (e: Exception) {}
                    }

                    if (apkFile.exists()) apkFile.delete()
                    partFile.renameTo(apkFile)

                    // Create a "Bundle" PluginItem for local tracking
                    val bundleItem = PluginItem(
                        pluginId = bundleId,
                        name = "Bundle (${compatiblePlugins.size} providers)",
                        version = latestVersion,
                        minApiVersion = compatiblePlugins.minOf { it.minApiVersion },
                        mainClasses = compatiblePlugins.flatMap { p -> 
                            val list = mutableListOf<String>()
                            p.mainClass?.let { list.add(it) }
                            p.mainClasses?.let { list.addAll(it) }
                            list
                        }.distinct(),
                        url = url
                    )

                    jsonFile.writeText(jacksonObjectMapper().writeValueAsString(bundleItem))
                    android.util.Log.i("PluginSync", "Successfully saved bundle metadata to $jsonFile")

                    // Load all classes
                    val instances = bundleItem.mainClasses?.mapNotNull { className ->
                        val instance = PluginManager.loadPlugin(applicationContext, apkFile, className)
                        android.util.Log.i("PluginSync", "Sync Load $className: ${if (instance != null) "SUCCESS" else "FAILED"}")
                        instance
                    } ?: emptyList()

                    if (instances.isNotEmpty()) {
                        com.lagradost.quicknovel.util.Apis.addPlugins(instances)
                    }
                    android.util.Log.i("PluginSync", "Successfully updated bundle with ${compatiblePlugins.size} providers from $url")
                } catch (e: Exception) {
                    android.util.Log.e("PluginSync", "Error during bundle sync: ${e.message}", e)
                    logError(e)
                } finally {
                    if (partFile.exists()) partFile.delete()
                }
            } else {
                android.util.Log.e("PluginSync", "Failed to download APK from $url - Status: ${downloadResponse.code}")
            }
        } else {
            android.util.Log.d("PluginSync", "Bundle is already up to date ($currentVersion)")
        }
    }
}
