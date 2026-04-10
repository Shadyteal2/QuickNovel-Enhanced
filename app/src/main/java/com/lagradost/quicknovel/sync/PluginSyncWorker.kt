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
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.nicehttp.Requests
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PluginSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private val syncMutex = Mutex()
    }

    private val syncApp = Requests(
        OkHttpClient().newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build(),
        defaultHeaders = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    )

    private fun getManifestHash(text: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(text.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            text.hashCode().toString()
        }
    }

    private fun showToast(message: String) {
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) { }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val force = inputData.getBoolean("force", false)
        syncMutex.withLock {
            val MANIFEST_URL = "https://raw.githubusercontent.com/Shadyteal2/NeoQN-Extensions/main/manifest.json"
            try {
                com.lagradost.quicknovel.util.Apis.setSyncing(true)
                android.util.Log.i("PluginSync", "Starting manifest fetch from $MANIFEST_URL")

                // ── Early dismiss: if providers already exist (manually imported or
                //    previously synced), clear the "syncing" indicator right away so
                //    the UI never hangs waiting for GitHub. The actual network sync
                //    continues running silently in the background.
                val pluginsDir = PluginManager.getPluginsDir(applicationContext)
                val hasPluginsLocally = pluginsDir.listFiles()?.any { it.name.endsWith(".apk") } == true
                if (hasPluginsLocally && !force) {
                    android.util.Log.i("PluginSync", "Providers already installed — dismissing syncing indicator. Will sync silently.")
                    com.lagradost.quicknovel.util.Apis.setSyncing(false)
                }

            val response = syncApp.get(MANIFEST_URL)
            if (!response.isSuccessful) {
                android.util.Log.e("PluginSync", "Manifest fetch failed: ${response.code}")
                com.lagradost.quicknovel.util.Apis.setSyncing(false)
                return@withContext Result.retry()
            }

            val manifestText = response.text
            val currentHash = getManifestHash(manifestText)
            val lastHash = com.lagradost.quicknovel.BaseApplication.getKey<String>("LAST_MANIFEST_HASH")

            // If hash matches and we have plugins, skip everything
            if (!force && currentHash == lastHash && hasPluginsLocally) {
                android.util.Log.i("PluginSync", "Manifest hash matches ($currentHash). Skipping sync.")
                com.lagradost.quicknovel.util.Apis.setSyncing(false)
                return@withLock Result.success()
            }

            showToast("Syncing plugins...")
            val mapper = jacksonObjectMapper()
            val manifest = mapper.readValue(manifestText, PluginManifest::class.java)
            android.util.Log.i("PluginSync", "Manifest loaded: ${manifest.plugins.size} plugins. New hash: $currentHash")

            // FILTER: Skip test/experimental bundles during auto-sync to keep it fast for regular users
            val groups = manifest.plugins
                .filter { p -> 
                    val isTest = p.pluginId.contains("test", ignoreCase = true) || 
                                p.pluginId.contains("experimental", ignoreCase = true) ||
                                (p.description?.contains("experimental", ignoreCase = true) == true)
                    !isTest 
                }
                .groupBy { it.url }
            
            // Limit parallelism to avoid overwhelming the device/network
            val semaphore = kotlinx.coroutines.sync.Semaphore(2) // Reduced for better stability
            
            kotlinx.coroutines.coroutineScope {
                groups.map { (url, bundlePlugins) ->
                    async {
                        semaphore.withPermit {
                            // Individual bundle timeout (1 min) so one broken link doesn't hang the whole sync
                            withTimeoutOrNull(60_000) {
                                syncBundle(url, bundlePlugins, pluginsDir)
                            }
                        }
                    }
                }.awaitAll()
            }

            // CRITICAL: Reload all plugins from disk so newly downloaded ones appear immediately
            android.util.Log.i("PluginSync", "Sync complete. Reloading all plugins from disk...")
            val count = PluginManager.loadAllPlugins(applicationContext)
            android.util.Log.i("PluginSync", "Post-sync reload complete. Total providers: $count")

            com.lagradost.quicknovel.util.Apis.setSyncing(false)
            showToast("Sync Complete: $count providers loaded")
            com.lagradost.quicknovel.BaseApplication.setKey("PLUGINS_INITIAL_SYNC_DONE", true)
            com.lagradost.quicknovel.BaseApplication.setKey("LAST_MANIFEST_HASH", currentHash)
            Result.success()
        } catch (e: CancellationException) {
            com.lagradost.quicknovel.util.Apis.setSyncing(false)
            throw e
        } catch (e: Exception) {
            com.lagradost.quicknovel.util.Apis.setSyncing(false)
            logError(e)
            Result.failure()
        }
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
        var isManual = false
        if (jsonFile.exists()) {
            try {
                val currentMeta = jacksonObjectMapper()
                    .readValue(jsonFile.readText(), PluginItem::class.java)
                currentVersion = currentMeta.version
                isManual = currentMeta.isManualImport
            } catch (e: Exception) {}
        }

        // If this bundle was manually side-loaded, don't let auto-sync overwrite it
        if (isManual) {
            android.util.Log.i("PluginSync", "Skipping sync for $bundleId - Manually imported.")
            return
        }

        val latestVersion = plugins.maxOf { it.version }
        android.util.Log.d("PluginSync", "Syncing bundle for $url - Current version: $currentVersion, Latest version: $latestVersion")
        
        if (latestVersion > currentVersion) {
            val compatiblePlugins = plugins.filter { it.minApiVersion <= API_VERSION }
            if (compatiblePlugins.isEmpty()) {
                android.util.Log.w("PluginSync", "No compatible plugins in bundle for $url (Required: <= $API_VERSION)")
                return
            }

            val partFile = File(pluginsDir, "$bundleId.part")
            android.util.Log.i("PluginSync", "Downloading bundle from $url")
            
            // USE STREAMING DOWNLOAD to avoid OOM on large APKs
            val downloadResponse = syncApp.get(url)
            if (downloadResponse.isSuccessful) {
                try {
                    val body = downloadResponse.okhttpResponse.body
                    if (body != null) {
                        body.source().use { source ->
                            partFile.outputStream().use { output ->
                                source.readAll(output.sink())
                            }
                        }
                        partFile.setReadOnly() // Security requirement for DexClassLoader

                        // Clean up old metadata classes before renaming
                        if (jsonFile.exists()) {
                            try {
                                val oldMeta = jacksonObjectMapper().readValue(jsonFile.readText(), PluginItem::class.java)
                                oldMeta.mainClass?.let { PluginManager.unloadPlugin(it) }
                                oldMeta.mainClasses?.forEach { PluginManager.unloadPlugin(it) }
                            } catch (e: Exception) { }
                        }

                        PluginManager.removeCachesForPath(apkFile.absolutePath)

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
                        android.util.Log.i("PluginSync", "Successfully saved bundle metadata for $bundleId")
                        // Note: PluginManager.loadAllPlugins() will be called at the end of doWork()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PluginSync", "Error during bundle sync for $bundleId: ${e.message}", e)
                    logError(e)
                } finally {
                    if (partFile.exists()) partFile.delete()
                }
            } else {
                android.util.Log.w("PluginSync", "Failed to download APK from $url - Status: ${downloadResponse.code}. This may be intentional if the bundle is experimental.")
            }
        } else {
            android.util.Log.d("PluginSync", "Bundle $bundleId is already up to date ($currentVersion)")
        }
    }
}
