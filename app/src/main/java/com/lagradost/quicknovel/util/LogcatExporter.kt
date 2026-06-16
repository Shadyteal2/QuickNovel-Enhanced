package com.lagradost.quicknovel.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lagradost.quicknovel.CommonActivity.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object LogcatExporter {

    // Regexes to identify and sanitize tokens, auth headers, passwords, and API keys
    private val SENSITIVE_PATTERNS = listOf(
        // Match headers like Authorization: Bearer xxxx or Authorization: Basic xxxx
        Regex("(?i)(authorization\\s*:\\s*)([a-zA-Z0-9+=/\\-_]+)"),
        // Match bearer tokens/keys in query params or headers
        Regex("(?i)(bearer\\s+)([a-zA-Z0-9+=/\\-_.]+)"),
        // Match query params or JSON keys like token=xxx, api_key=xxx, password=xxx, etc.
        Regex("(?i)(token|password|key|secret|pass|api_key|auth|credential)(\\s*[:=]\\s*[\"']?)([^\\s&\"';]+)")
    )

    private fun scrubLine(line: String): String {
        var scrubbed = line
        for (regex in SENSITIVE_PATTERNS) {
            scrubbed = regex.replace(scrubbed) { matchResult ->
                val groups = matchResult.groupValues
                if (groups.size >= 4) {
                    "${groups[1]}${groups[2]}[REDACTED]"
                } else if (groups.size >= 3) {
                    "${groups[1]}[REDACTED]"
                } else {
                    "[REDACTED]"
                }
            }
        }
        return scrubbed
    }

    suspend fun exportAndShareLogs(context: Context) {
        val file = File(context.cacheDir, "NeoQN_app_log.txt")

        // 1. Run process and read/write log lines securely in Dispatchers.IO
        withContext(Dispatchers.IO) {
            try {
                val command = arrayOf(
                    "logcat",
                    "-d",
                    "-v",
                    "threadtime",
                    "-b",
                    "main",
                    "-b",
                    "crash",
                    "-t",
                    "2000",
                    "--pid=${android.os.Process.myPid()}"
                )
                val process = Runtime.getRuntime().exec(command)
                
                val deviceDetails = CrashHandler.getDeviceDetails(context)
                
                process.inputStream.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        file.outputStream().use { fileOutputStream ->
                            fileOutputStream.bufferedWriter().use { writer ->
                                writer.write(deviceDetails)
                                writer.newLine()
                                writer.newLine()
                                
                                var line: String? = reader.readLine()
                                while (line != null) {
                                    val sanitized = scrubLine(line)
                                    writer.write(sanitized)
                                    writer.newLine()
                                    line = reader.readLine()
                                }
                            }
                        }
                    }
                }
                process.destroy()
            } catch (t: Throwable) {
                com.lagradost.quicknovel.mvvm.logError(t)
            }
        }

        // 2. Share the file from Dispatchers.Main
        withContext(Dispatchers.Main) {
            if (!file.exists() || file.length() == 0L) {
                showToast("Failed to generate logs or logs are empty")
                return@withContext
            }

            try {
                val authority = "${context.packageName}.provider"
                val uri = FileProvider.getUriForFile(context, authority, file)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "NeoQN App Logs")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(intent, "Share App Logs").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (t: Throwable) {
                showToast("Failed to share logs: ${t.localizedMessage}")
            }
        }
    }
}
