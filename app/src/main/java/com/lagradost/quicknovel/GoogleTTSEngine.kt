package com.lagradost.quicknovel

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import java.net.URLEncoder

class GoogleTTSEngine(
    private val context: Context
) : TTSEngine {
    private var mediaPlayer: MediaPlayer? = null
    private var speed = 1.0f
    private var pitch = 1.0f
    private var currentLanguage = "en"

    override fun isInitialized(): Boolean = true

    override fun setSpeed(speed: Float) { this.speed = speed }
    override fun setPitch(pitch: Float) { this.pitch = pitch }
    override fun setVoice(voice: String?) { /* Google Translate TTS is simpler, usually per language */ }
    override fun setLanguage(language: String?) { 
        this.currentLanguage = language?.take(2) ?: "en"
    }

    override suspend fun speak(text: String, id: Int, isQueueAdd: Boolean): Boolean {
        stop()
        
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_tts?ie=UTF-8&q=$encodedText&tl=$currentLanguage&client=tw-ob"
            
            playAudioFromUrl(url, id)
            true
        } catch (e: Exception) {
            Log.e("GoogleTTS", "Error during synthesis", e)
            false
        }
    }

    private suspend fun playAudioFromUrl(url: String, id: Int) = withContext(Dispatchers.Main) {
        val completer = CompletableDeferred<Unit>()
        
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            setDataSource(url)
            setOnPreparedListener { 
                onStatusUpdate(id, true)
                start() 
            }
            setOnCompletionListener { 
                onStatusUpdate(id, false)
                completer.complete(Unit)
            }
            setOnErrorListener { _, what, extra ->
                Log.e("GoogleTTS", "MediaPlayer error: $what, $extra")
                onStatusUpdate(id, false)
                completer.complete(Unit)
                true
            }
            prepareAsync()
        }
        completer.await()
    }

    override fun getVoices(): List<EngineVoice> {
        return listOf(EngineVoice("Google Neural", java.util.Locale.getDefault(), true))
    }

    override fun getCurrentVoiceName(): String? = "Google Neural"

    override fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun release() {
        stop()
    }

    private var onStatusUpdate: (Int, Boolean) -> Unit = { _, _ -> }
    override fun setStatusUpdateCallback(onStatusUpdate: (Int, Boolean) -> Unit) {
        this.onStatusUpdate = onStatusUpdate
    }
}
