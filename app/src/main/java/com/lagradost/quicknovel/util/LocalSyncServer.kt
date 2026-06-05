package com.lagradost.quicknovel.util

import android.content.Context
import com.lagradost.quicknovel.mvvm.logError
import java.io.File
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class LocalSyncServer(
    private val context: Context,
    private val onSyncSuccess: () -> Unit,
    private val onError: (Exception) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    var activePort: Int = -1
        private set

    /**
     * Starts the TCP Server Socket on an available port on a background thread.
     */
    fun start(): Int {
        isRunning = true
        val socket = ServerSocket(0) // Bind to any available port
        serverSocket = socket
        activePort = socket.localPort

        thread(name = "WifiSyncServerThread") {
            try {
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    thread(name = "WifiSyncClientHandlerThread") {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    onError(e)
                }
            }
        }
        return activePort
    }

    /**
     * Stops the TCP Server Socket.
     */
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Suppress close exceptions
        }
        serverSocket = null
        activePort = -1
    }

    private fun handleClient(socket: Socket) {
        val cacheFile = File(context.cacheDir, "incoming_sync.json")
        try {
            val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val output = PrintWriter(socket.getOutputStream())

            // Parse HTTP Headers
            var line: String? = input.readLine()
            var contentLength = 0
            var isPost = false

            while (!line.isNullOrBlank()) {
                if (line.startsWith("POST", ignoreCase = true)) {
                    isPost = true
                }
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                line = input.readLine()
            }

            if (isPost && contentLength > 0) {
                // Memory-safe streaming: stream the payload directly to a cache file
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }

                cacheFile.outputStream().use { fos ->
                    val buffer = CharArray(4096)
                    var totalReadBytes = 0
                    
                    // We read character blocks and convert them to raw bytes to stream into the file
                    while (totalReadBytes < contentLength) {
                        val toRead = minOf(buffer.size, contentLength - totalReadBytes)
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        
                        val chunkStr = String(buffer, 0, read)
                        val bytes = chunkStr.toByteArray(Charsets.UTF_8)
                        fos.write(bytes)
                        totalReadBytes += bytes.size
                    }
                }

                // Deserialization and Restoration
                val success = BackupUtils.restoreFromFile(context, cacheFile)

                if (success) {
                    // Send HTTP 200 OK Response
                    output.print("HTTP/1.1 200 OK\r\n")
                    output.print("Content-Length: 0\r\n")
                    output.print("Connection: close\r\n\r\n")
                    output.flush()
                    
                    // Trigger sync success callback on Main thread
                    onSyncSuccess()
                } else {
                    sendErrorResponse(output, 400, "Bad Request: Failed to restore backup payload.")
                }
            } else {
                sendErrorResponse(output, 404, "Not Found")
            }
        } catch (e: Exception) {
            logError(e)
            try {
                val output = PrintWriter(socket.getOutputStream())
                sendErrorResponse(output, 500, "Internal Server Error: ${e.message}")
            } catch (e2: Exception) {
                // Ignore secondary write failures
            }
        } finally {
            try {
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
            } catch (e: Exception) {
                // Ignore cache cleanup failures
            }
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }

    private fun sendErrorResponse(output: PrintWriter, code: Int, msg: String) {
        output.print("HTTP/1.1 $code $msg\r\n")
        output.print("Content-Length: ${msg.length}\r\n")
        output.print("Connection: close\r\n\r\n")
        output.print(msg)
        output.flush()
    }
}
