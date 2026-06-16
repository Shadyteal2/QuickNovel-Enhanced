package com.lagradost.quicknovel.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    private val appContext: Context = context.applicationContext

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val logFile = File(appContext.cacheDir, "NeoQN_crash_log.txt")
            
            // Collect memory details
            val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            val totalRam = String.format(Locale.US, "%.2f GB", memInfo.totalMem.toDouble() / (1024 * 1024 * 1024))
            val availRam = String.format(Locale.US, "%.2f GB", memInfo.availMem.toDouble() / (1024 * 1024 * 1024))
            
            // Collect CPU architectures
            val supportedAbis = Build.SUPPORTED_ABIS.joinToString(", ")

            val writer = StringWriter()
            val printWriter = PrintWriter(writer)
            e.printStackTrace(printWriter)
            val stackTrace = writer.toString()

            val deviceDetails = getDeviceDetails(appContext)
            val report = """
                Crash Report
                ------------
                $deviceDetails
                System RAM: $totalRam ($availRam available)
                CPU Architecture (ABIs): $supportedAbis
                
                Stack Trace:
                $stackTrace
            """.trimIndent()

            logFile.writeText(report)
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            defaultHandler?.uncaughtException(t, e)
        }
    }

    companion object {
        @JvmStatic
        fun getDeviceDetails(context: Context): String {
            val packageInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Throwable) {
                null
            }
            val appId = context.packageName
            val appVersionName = packageInfo?.versionName ?: "Unknown"
            val appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode ?: 0L
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong() ?: 0L
            }
            
            val androidVersion = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY})"
            val deviceBrand = Build.BRAND
            val deviceManufacturer = Build.MANUFACTURER
            val deviceName = try {
                val name = android.provider.Settings.Global.getString(context.contentResolver, "device_name")
                if (name.isNullOrBlank()) "${Build.DEVICE} (${Build.PRODUCT})" else name
            } catch (e: Throwable) {
                "${Build.DEVICE} (${Build.PRODUCT})"
            }
            val deviceModel = Build.MODEL
            
            val webview = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.webkit.WebView.getCurrentWebViewPackage()?.let {
                        "${it.packageName} ${it.versionName}"
                    } ?: "Unknown"
                } else {
                    val webviewPackages = arrayOf(
                        "com.google.android.webview",
                        "com.android.webview",
                        "com.android.chrome"
                    )
                    var found = "Unknown"
                    for (pkg in webviewPackages) {
                        try {
                            val info = context.packageManager.getPackageInfo(pkg, 0)
                            found = "${info.packageName} ${info.versionName}"
                            break
                        } catch (e: Throwable) {}
                    }
                    found
                }
            } catch (e: Throwable) {
                "Unknown"
            }
            
            val currentTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                } catch (e: Throwable) {
                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", java.util.Locale.US).format(java.util.Date())
                }
            } else {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", java.util.Locale.US).format(java.util.Date())
            }

            return """
                App ID: $appId
                App version: $appVersionName
                Build version: $appVersionCode
                Android version: $androidVersion
                Device brand: $deviceBrand
                Device manufacturer: $deviceManufacturer
                Device name: $deviceName
                Device model: $deviceModel
                WebView: $webview
                Current time: $currentTime
            """.trimIndent()
        }

        @JvmStatic
        fun shareLastCrashLog(context: Context) {
            val logFile = File(context.cacheDir, "NeoQN_crash_log.txt")
            if (!logFile.exists() || logFile.length() == 0L) {
                Toast.makeText(context, "No recent crashes found!", Toast.LENGTH_SHORT).show()
                return
            }

            try {
                val authority = "${context.packageName}.provider"
                val uri = FileProvider.getUriForFile(context, authority, logFile)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "QuickNovel Crash Log")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(intent, "Share Crash Log"))
            } catch (ex: Exception) {
                Toast.makeText(context, "Failed to share crash log: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
