package com.soniccast.app.data.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ambient acoustic noise estimator.
 *
 * Requirements satisfied:
 * - Samples ambient room noise floor for ~1.0 second at receiver startup.
 * - Dynamically computes baseline RMS and sets adaptive detection threshold.
 * - Prevents false triggers from quiet background chatter or ventilation hum.
 */
class NoiseEstimator(
    private val sampleRate: Int = 44100
) {
    private val _isCalibrated = MutableStateFlow(false)
    val isCalibrated: StateFlow<Boolean> = _isCalibrated.asStateFlow()

    private val _baselineRms = MutableStateFlow(0.0)
    val baselineRms: StateFlow<Double> = _baselineRms.asStateFlow()

    private val _adaptiveThreshold = MutableStateFlow(0.015)
    val adaptiveThreshold: StateFlow<Double> = _adaptiveThreshold.asStateFlow()

    private var accumulatedSamples = 0
    private var sumSquares = 0.0
    private val targetSamples = sampleRate // 1 second of audio at 44.1 kHz

    /**
     * Feeds initial audio samples during the 1.0s calibration phase.
     */
    @Synchronized
    fun addCalibrationSamples(samples: ShortArray, length: Int = samples.size) {
        if (_isCalibrated.value) return

        val n = length.coerceAtMost(samples.size)
        for (i in 0 until n) {
            val norm = samples[i] / 32768.0
            sumSquares += norm * norm
        }
        accumulatedSamples += n

        if (accumulatedSamples >= targetSamples) {
            val meanSquare = sumSquares / accumulatedSamples
            val rms = kotlin.math.sqrt(meanSquare)
            _baselineRms.value = rms

            // Adaptive threshold is 2.5x ambient noise floor, with safe lower bound
            val threshold = (rms * 2.5).coerceIn(0.012, 0.08)
            _adaptiveThreshold.value = threshold
            _isCalibrated.value = true
        }
    }

    /**
     * Resets calibration state.
     */
    @Synchronized
    fun reset() {
        _isCalibrated.value = false
        _baselineRms.value = 0.0
        _adaptiveThreshold.value = 0.015
        accumulatedSamples = 0
        sumSquares = 0.0
    }
}
