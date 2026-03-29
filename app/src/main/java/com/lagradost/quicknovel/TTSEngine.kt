package com.lagradost.quicknovel

import android.content.Context
import android.speech.tts.TextToSpeech
import java.io.File

/**
 * Interface for different TTS providers (System Native, Microsoft Edge, etc.)
 */
interface TTSEngine {
    fun isInitialized(): Boolean
    fun setSpeed(speed: Float)
    fun setPitch(pitch: Float)
    fun setVoice(voice: String?)
    fun setLanguage(language: String?)
    
    /**
     * Start speaking a line. 
     * @return an ID for the utterance.
     */
    suspend fun speak(text: String, id: Int, isQueueAdd: Boolean): Boolean
    
    fun stop()
    fun release()
    
    /**
     * Optional: pre-load the next sentence to avoid gaps (important for Edge TTS)
     */
    suspend fun preload(text: String) {}

    /**
     * Set a callback for utterance status updates (start/done/error).
     * @param onStatusUpdate A lambda that takes utterance ID (Int) and isStarted (Boolean).
     */
    fun setStatusUpdateCallback(onStatusUpdate: (Int, Boolean) -> Unit)

    /**
     * Get available voices for the current engine
     */
    fun getVoices(): List<EngineVoice>

    /**
     * Get the current selected voice name
     */
    fun getCurrentVoiceName(): String?
}

data class EngineVoice(
    val name: String,
    val locale: java.util.Locale,
    val isNetworkRequired: Boolean
)

/**
 * Standard Android TextToSpeech Engine
 */
class NativeTTSEngine(
    private val context: Context,
    private var onStatusUpdate: (Int, Boolean) -> Unit // id, isStarted
) : TTSEngine {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        utteranceId.toIntOrNull()?.let { onStatusUpdate(it, true) }
                    }
                    override fun onDone(utteranceId: String) {
                        utteranceId.toIntOrNull()?.let { onStatusUpdate(it, false) }
                    }
                    override fun onError(utteranceId: String) {
                        utteranceId.toIntOrNull()?.let { onStatusUpdate(it, false) }
                    }
                })
            }
        }
    }

    override fun isInitialized() = isReady
    
    override fun setSpeed(speed: Float) { tts?.setSpeechRate(speed) }
    override fun setPitch(pitch: Float) { tts?.setPitch(pitch) }
    
    override fun setVoice(voice: String?) {
        val v = tts?.voices?.firstOrNull { it.name == voice } ?: tts?.defaultVoice
        tts?.voice = v
    }
    
    override fun setLanguage(language: String?) {
        if (language == null) return
        tts?.language = java.util.Locale.forLanguageTag(language)
    }

    override suspend fun speak(text: String, id: Int, isQueueAdd: Boolean): Boolean {
        if (!isReady) return false
        val mode = if (isQueueAdd) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        return tts?.speak(text, mode, null, id.toString()) == TextToSpeech.SUCCESS
    }

    override fun stop() { tts?.stop() }
    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun setStatusUpdateCallback(onStatusUpdate: (Int, Boolean) -> Unit) {
        this.onStatusUpdate = onStatusUpdate
    }

    override fun getVoices(): List<EngineVoice> {
        return tts?.voices?.filterNotNull()?.map {
            EngineVoice(it.name, it.locale, it.isNetworkConnectionRequired)
        } ?: emptyList()
    }

    override fun getCurrentVoiceName(): String? {
        return tts?.voice?.name
    }
}
