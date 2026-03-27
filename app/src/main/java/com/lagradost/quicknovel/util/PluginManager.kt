package com.lagradost.quicknovel.util

import android.content.Context
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.API_VERSION
import com.lagradost.quicknovel.mvvm.logError
import dalvik.system.DexClassLoader
import java.io.File
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.*

object PluginManager {
    private const val TAG = "PluginManager"
    private val contextCache = mutableMapOf<String, Context>()
    private val loaderCache = mutableMapOf<String, DexClassLoader>()
    private val classCache = mutableMapOf<String, Class<*>>()

    private fun getCachedContext(base: Context, file: File): Context {
        return contextCache.getOrPut(file.absolutePath) {
            PluginContextWrapper(base, file)
        }
    }
    
    // Trusted signature hash (same cert for debug & release, stored only in memory)
    private val TRUSTED_SIGNATURE = arrayOf(
        "8C:27:D9:66:64:8B:ED:16:3A:B3:5D:C4:BF:8E:BC:3E:52:1B:CE:28:C0:7E:FF:6D:2D:31:41:65:64:0D:EF:E2"
    )

    // Track class loaders to avoid leaks and facilitate manual unloading
    private val classLoaders = mutableMapOf<String, DexClassLoader>()

    /**
     * Gets the directory where plugins are stored.
     */
    fun getPluginsDir(context: Context): File {
        val dir = File(context.filesDir, "plugins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Checks if the given signature hash is trusted.
     * Only logs a generic rejection — never logs the real hash.
     */
    fun isSignatureTrusted(signatureHash: String): Boolean {
        val match = TRUSTED_SIGNATURE.any { it.equals(signatureHash, ignoreCase = true) }
        if (!match) {
            Log.w(TAG, "Signature verification failed: the APK is not signed by a trusted key.")
        }
        return match
    }

    /**
     * Creates a context for the plugin to access its own resources (Phase 4/5)
     */
    fun getPluginContext(context: Context, pluginFile: File): Context? {
        return try {
            context.createPackageContext(
                "com.lagradost.quicknovel", // This might need to be dynamic if plugins have their own pkg names
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    /**
     * Extracts the SHA-256 signature hash from an APK file.
     */
    fun getSignatureHash(context: Context, file: File): String? {
        return getSignatureHash(context, file.absolutePath)
    }

    /**
     * Extracts the SHA-256 signature hash from an APK file URI.
     */
    fun getSignatureHash(context: Context, uri: android.net.Uri): String? {
        return try {
            val tempFile = File(context.cacheDir, "temp_plugin_verify.apk")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            val hash = getSignatureHash(context, tempFile.absolutePath)
            tempFile.delete()
            hash
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    private fun getSignatureHash(context: Context, path: String): String? {
        return try {
            val packageManager = context.packageManager
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageArchiveInfo(path, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageArchiveInfo(path, android.content.pm.PackageManager.GET_SIGNATURES)
            }

            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo?.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.signatures
            }

            if (signatures.isNullOrEmpty()) return null

            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(signatures[0].toByteArray())
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    /**
     * Verifies if an APK from a URI has a trusted signature.
     */
    fun verifyApkSignature(context: Context, uri: android.net.Uri): Boolean {
        val hash = getSignatureHash(context, uri)
        return hash != null && isSignatureTrusted(hash)
    }

    /**
     * Loads a single plugin from a file.
     * @param file The APK or DEX file.
     * @param mainClass The full class name of the MainAPI implementation.
     */
    fun loadPlugin(context: Context, file: File, mainClass: String): MainAPI? {
        Log.d(TAG, "Attempting to load $mainClass from ${file.name}")
        return try {
            // STEP 1: Verify Signature
            val hash = getSignatureHash(context, file)
            if (hash == null || !isSignatureTrusted(hash)) {
                Log.w(TAG, "Plugin $mainClass rejected: Trusted signature mismatch! Hash: $hash")
                return null
            }

            // STEP 2: Load Class
            // Android 13+ requirement: Executable files in internal storage must be read-only
            if (file.canWrite()) {
                file.setReadOnly()
            }

            val classLoader = loaderCache.getOrPut(file.absolutePath) {
                DexClassLoader(
                    file.absolutePath,
                    context.codeCacheDir.absolutePath,
                    null,
                    context.classLoader
                )
            }
            
            val loadedClass = classCache.getOrPut("${file.absolutePath}:$mainClass") {
                classLoader.loadClass(mainClass)
            }
            val instance = loadedClass.getDeclaredConstructor().newInstance() as MainAPI
            
            // Pre-warm the instance to trigger JIT and resource mapping
            val providerName = instance.name
            
            // Set plugin context for resource loading (e.g. icons)
            try {
                instance.pluginContext = getCachedContext(context, file)
            } catch (e: Exception) {
                logError(e)
            }

            classLoaders[mainClass] = classLoader
            
            // Extreme warm-up: Trigger JIT and resolve all primary lazy properties
            try { 
                instance.name 
                instance.mainUrl
                instance.lang
                if (instance.hasMainPage) instance.mainCategories
            } catch (e: Exception) {
                Log.e(TAG, "Warm-up error for ${instance.name}", e)
            }
            
            return instance
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $mainClass", e)
            null
        }
    }

    /**
     * Unloads a plugin and attempts to release the ClassLoader.
     */
    fun unloadPlugin(mainClass: String) {
        Apis.removePlugin(mainClass)
        classLoaders.remove(mainClass)
        // Note: Java/Kotlin doesn't allow explicit ClassLoader unloading, 
        // but removing all references allows GC to reclaim it.
    }

    /**
     * Synchronous entry point for application startup to ensure all providers are ready.
     * Uses internal parallelism to stay significantly faster than the original sequential loader.
     */
    fun loadAllPluginsBlocking(context: Context): Int = runBlocking {
        val start = System.currentTimeMillis()
        val count = loadAllPlugins(context)
        Log.i(TAG, "Startup Sync Load finished in ${System.currentTimeMillis() - start}ms. Plugins: $count")
        count
    }

    /**
     * Scans the plugins directory and loads all valid candidates asynchronously.
     */
    suspend fun loadAllPlugins(context: Context): Int = supervisorScope {
        try {
            val dir = getPluginsDir(context)
            val apkFiles = dir.listFiles { _, name -> 
                (name.endsWith(".apk") || name.endsWith(".dex")) && !name.endsWith(".part")
            }
            
            Log.d(TAG, "Plugin Directory Scan: ${dir.absolutePath} - Found ${apkFiles?.size ?: 0} files")
            
            if (apkFiles == null || apkFiles.isEmpty()) {
                Log.w(TAG, "No plugin files found in ${dir.absolutePath}")
                return@supervisorScope 0
            }
            
            val results = apkFiles.map { file ->
                async(Dispatchers.IO) {
                    val metaFile = File(dir, file.nameWithoutExtension + ".json")
                    if (!metaFile.exists()) {
                        Log.w(TAG, "Missing metadata for ${file.name}")
                        return@async emptyList<MainAPI>()
                    }
                    
                    try {
                        val json = metaFile.readText()
                        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                        val item = mapper.readValue(json, PluginItem::class.java)
                        
                        if (item.minApiVersion > API_VERSION) {
                            Log.w(TAG, "${item.name} requires version ${item.minApiVersion}, current is $API_VERSION")
                            return@async emptyList<MainAPI>()
                        }

                        val classesToLoad = mutableListOf<String>()
                        item.mainClass?.let { classesToLoad.add(it) }
                        item.mainClasses?.let { classesToLoad.addAll(it) }
                        
                        if (classesToLoad.isEmpty()) return@async emptyList<MainAPI>()
                        
                        val instances = classesToLoad.distinct().map { className ->
                            loadPlugin(context, file, className)
                        }.filterNotNull()
                        
                        Log.d(TAG, "Bundled ${instances.size} provider(s) from ${file.name}")
                        instances
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in async load for ${file.name}", e)
                        logError(e)
                        emptyList<MainAPI>()
                    }
                }
            }
            
            val allResults = results.awaitAll()
            val allInstances = allResults.flatten()
            if (allInstances.isNotEmpty()) {
                Apis.addPlugins(allInstances)
                Log.i(TAG, "Batch registration successful: ${allInstances.size} plugins total.")
            }
            
            val totalBundles = allResults.count { it.isNotEmpty() }
            Log.i(TAG, "Optimized warmup finished. Total bundles loaded: $totalBundles")
            allInstances.size
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in loadAllPlugins", e)
            logError(e)
            0
        }
    }
}

class PluginContextWrapper(base: Context, apkFile: File) : ContextWrapper(base) {
    private val pluginResources: android.content.res.Resources by lazy {
        try {
            val assetManager = AssetManager::class.java.getConstructor().newInstance()
            val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            addAssetPath.invoke(assetManager, apkFile.absolutePath)
            android.content.res.Resources(assetManager, base.resources.displayMetrics, base.resources.configuration)
        } catch (e: Exception) {
            Log.e("PluginManager", "Failed to load resources for ${apkFile.name}", e)
            base.resources
        }
    }

    override fun getResources(): android.content.res.Resources {
        return pluginResources
    }
}
