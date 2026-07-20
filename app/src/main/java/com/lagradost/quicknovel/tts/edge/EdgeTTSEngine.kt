package com.lagradost.quicknovel.tts.edge

import android.content.Context
import android.util.Log
import com.lagradost.quicknovel.EngineVoice
import com.lagradost.quicknovel.TTSEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class EdgeTTSEngine(
    private val context: Context
) : TTSEngine {
    private val TAG = "EdgeTTSEngine"
    
    private var onStatusUpdate: (Int, Boolean) -> Unit = { _, _ -> }
    private val player = EdgeTTSPlayer(context) { id, isStarted ->
        onStatusUpdate(id, isStarted)
    }

    private var speed = 1.0f
    private var pitch = 1.0f
    private var currentVoice: String? = null
    private var currentLanguage: String = "en-US"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun isInitialized(): Boolean = true

    override fun setSpeed(speed: Float) {
        this.speed = speed
        player.setSpeed(speed)
    }

    override fun setPitch(pitch: Float) {
        this.pitch = pitch
        player.setPitch(pitch)
    }

    override fun setVoice(voice: String?) {
        this.currentVoice = voice
    }

    override fun setLanguage(language: String?) {
        this.currentLanguage = language ?: "en-US"
    }

    override suspend fun speak(text: String, id: Int, isQueueAdd: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (isQueueAdd) {
            // This is a preload request. Fetch and cache the audio, but do not play it.
            val bytes = downloadAudio(text)
            if (bytes != null && bytes.isNotEmpty()) {
                player.saveAudioBytes(id, bytes)
                return@withContext true
            }
            return@withContext false
        }

        // Standard play request
        stop()

        // Check if already preloaded
        if (player.hasPreloaded(id)) {
            return@withContext player.play(id)
        }

        // Fetch on-demand
        val bytes = downloadAudio(text)
        if (bytes != null && bytes.isNotEmpty()) {
            player.saveAudioBytes(id, bytes)
            return@withContext player.play(id)
        }
        
        return@withContext false
    }

    override fun stop() {
        player.stop()
    }

    override fun release() {
        player.release()
    }

    override fun setStatusUpdateCallback(onStatusUpdate: (Int, Boolean) -> Unit) {
        this.onStatusUpdate = onStatusUpdate
    }

    override fun getVoices(): List<EngineVoice> {
        return EdgeTTSVoices.allVoices.map { it.toEngineVoice() }
    }

    override fun getCurrentVoiceName(): String? {
        return currentVoice ?: getVoiceId()
    }

    private fun getVoiceId(): String {
        val voice = currentVoice
        if (voice != null && EdgeTTSVoices.allVoices.any { it.id == voice }) {
            return voice
        }
        
        // Match language tag base (e.g. "en" from "en-US")
        val cleanLang = currentLanguage.split("-")[0].lowercase()
        val langVoice = EdgeTTSVoices.allVoices.firstOrNull { 
            it.lang.split("-")[0].lowercase() == cleanLang 
        }
        if (langVoice != null) return langVoice.id
        
        return "en-US-AriaNeural"
    }

    private suspend fun downloadAudio(text: String): ByteArray? = withContext(Dispatchers.IO) {
        val voiceId = getVoiceId()
        val voiceObj = EdgeTTSVoices.allVoices.firstOrNull { it.id == voiceId }
        val lang = voiceObj?.lang ?: "en-US"

        val deferredBytes = CompletableDeferred<ByteArray>()
        val audioAccumulator = ByteArrayOutputStream()

        val request = Request.Builder()
            .url(EdgeTTSProtocol.buildConnectionUrl())
            .apply {
                EdgeTTSProtocol.buildHeaders().forEach { (k, v) -> header(k, v) }
            }
            .build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                val date = java.util.Date().toString()
                webSocket.send(EdgeTTSProtocol.buildSpeechConfigFrame(date))
                webSocket.send(EdgeTTSProtocol.buildSSMLFrame(text, voiceId, lang, speed, pitch, date))
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                val parsed = EdgeTTSProtocol.parseTextFrame(text)
                if (parsed.headers["Path"] == "turn.end") {
                    webSocket.close(1000, "Done")
                    deferredBytes.complete(audioAccumulator.toByteArray())
                }
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (data.size >= 2) {
                    val headerLength = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    if (data.size > 2 + headerLength) {
                        audioAccumulator.write(data, 2 + headerLength, data.size - (2 + headerLength))
                    }
                }
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                deferredBytes.completeExceptionally(t)
            }

            override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
        })

        val result = try {
            // Apply 10 seconds timeout for WebSocket synthesis
            withTimeoutOrNull(10000) {
                deferredBytes.await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during WebSocket audio synthesis download", e)
            null
        } finally {
            try {
                ws.close(1000, "Cleanup")
            } catch (e: Exception) {
                // Ignore
            }
        }
        result
    }
}
