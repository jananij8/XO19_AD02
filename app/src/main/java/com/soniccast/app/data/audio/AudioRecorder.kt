package com.soniccast.app.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages low-latency background PCM audio recording via Android AudioRecord.
 */
class AudioRecorder(
    val sampleRate: Int = 44100
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _rmsEnergy = MutableStateFlow(0f)
    val rmsEnergy: StateFlow<Float> = _rmsEnergy.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Starts audio recording and feeds read audio samples to [onAudioChunk].
     */
    @SuppressLint("MissingPermission")
    fun start(onAudioChunk: (ShortArray, Int) -> Unit) {
        if (_isRecording.value) return

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to standard MIC
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _lastError.value = "AudioRecord initialization failed: Hardware mic busy or unavailable."
                return
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            _lastError.value = null

            recordingJob = scope.launch {
                val readBuffer = ShortArray(1024)
                while (isActive && _isRecording.value) {
                    val count = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                    if (count > 0) {
                        val rms = GoertzelDetector.computeRms(readBuffer, 0, count)
                        _rmsEnergy.value = (rms * 10f).coerceIn(0.0, 1.0).toFloat()
                        onAudioChunk(readBuffer, count)
                    }
                }
            }
        } catch (e: Exception) {
            _lastError.value = "Recording error: ${e.message}"
            _isRecording.value = false
        }
    }

    /**
     * Stops audio recording and releases hardware resources.
     */
    fun stop() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            // Ignore during cleanup
        }
        _rmsEnergy.value = 0f
    }
}
