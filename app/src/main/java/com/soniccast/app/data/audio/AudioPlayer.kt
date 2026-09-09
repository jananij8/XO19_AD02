package com.soniccast.app.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages low-latency background PCM audio playback via Android AudioTrack.
 */
class AudioPlayer(
    val sampleRate: Int = 44100
) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Plays 16-bit PCM audio samples synchronously on Dispatchers.IO and suspends until playback finishes.
     */
    suspend fun playSamples(samples: ShortArray) = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext

        _isPlaying.value = true
        _lastError.value = null

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()

            val durationMs = (samples.size * 1000L) / sampleRate
            delay(durationMs + 40) // Allow buffer to drain fully

            audioTrack.stop()
        } catch (e: Exception) {
            _lastError.value = "Playback error: ${e.message}"
        } finally {
            audioTrack.release()
            _isPlaying.value = false
        }
    }
}
