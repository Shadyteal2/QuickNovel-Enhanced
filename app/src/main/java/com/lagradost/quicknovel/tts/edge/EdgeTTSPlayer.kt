package com.lagradost.quicknovel.tts.edge

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class EdgeTTSPlayer(
    private val context: Context,
    private val onStatusUpdate: (Int, Boolean) -> Unit
) {
    private val TAG = "EdgeTTSPlayer"
    private var mediaPlayer: MediaPlayer? = null
    
    // Rotating buffer files to prevent storage leaks
    private val bufferFiles = listOf(
        File(context.cacheDir, "edge_buffer_0.mp3"),
        File(context.cacheDir, "edge_buffer_1.mp3")
    )
    private var nextBufferIndex = 0
    
    // Maps utterance ID to the cached file holding its audio
    private val idToFileMap = ConcurrentHashMap<Int, File>()
    
    private var currentPlayingId: Int? = null
    private var speed = 1.0f
    private var pitch = 1.0f

    @Synchronized
    private fun getNextBufferFile(): File {
        val file = bufferFiles[nextBufferIndex]
        nextBufferIndex = (nextBufferIndex + 1) % bufferFiles.size
        return file
    }

    suspend fun saveAudioBytes(id: Int, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val file = getNextBufferFile()
        try {
            FileOutputStream(file).use { fos ->
                fos.write(bytes)
            }
            idToFileMap[id] = file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audio bytes for ID $id", e)
        }
        file
    }

    fun setSpeed(speed: Float) {
        this.speed = speed
        applyPlaybackParams()
    }

    fun setPitch(pitch: Float) {
        this.pitch = pitch
        applyPlaybackParams()
    }

    private fun applyPlaybackParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { player ->
                    val params = PlaybackParams().apply {
                        speed = this@EdgeTTSPlayer.speed
                        pitch = this@EdgeTTSPlayer.pitch
                    }
                    player.playbackParams = params
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply playback params", e)
            }
        }
    }

    suspend fun play(id: Int): Boolean = withContext(Dispatchers.IO) {
        stop()
        
        val file = idToFileMap[id] ?: return@withContext false
        if (!file.exists() || file.length() == 0L) return@withContext false
        
        currentPlayingId = id
        
        return@withContext suspendPlay(file, id)
    }

    private suspend fun suspendPlay(file: File, id: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
            }
            
            val success = withContext(Dispatchers.Main) {
                mediaPlayer = player
                var prepared = false
                try {
                    player.setOnPreparedListener { mp ->
                        onStatusUpdate(id, true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val params = PlaybackParams().apply {
                                    speed = this@EdgeTTSPlayer.speed
                                    pitch = this@EdgeTTSPlayer.pitch
                                }
                                mp.playbackParams = params
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to set playback params in onPrepared", e)
                            }
                        }
                        mp.start()
                    }
                    player.setOnCompletionListener { mp ->
                        onStatusUpdate(id, false)
                        cleanupId(id)
                        mp.release()
                        if (mediaPlayer == mp) {
                            mediaPlayer = null
                        }
                    }
                    player.setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "MediaPlayer error: $what, $extra")
                        onStatusUpdate(id, false)
                        cleanupId(id)
                        mp.release()
                        if (mediaPlayer == mp) {
                            mediaPlayer = null
                        }
                        true
                    }
                    player.prepare()
                    prepared = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to prepare MediaPlayer", e)
                    onStatusUpdate(id, false)
                }
                prepared
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception in play", e)
            false
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let { player ->
                player.stop()
                player.release()
            }
        } catch (e: Exception) {
            // Ignore if player is not initialized or already released
        }
        mediaPlayer = null
        currentPlayingId?.let { id ->
            onStatusUpdate(id, false)
            cleanupId(id)
        }
        currentPlayingId = null
    }

    fun release() {
        stop()
        idToFileMap.clear()
        try {
            bufferFiles.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting buffer files", e)
        }
    }

    private fun cleanupId(id: Int) {
        idToFileMap.remove(id)
    }

    fun hasPreloaded(id: Int): Boolean {
        return idToFileMap.containsKey(id)
    }
}
