package com.soniccast.app.data.audio

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure Kotlin implementation of Goertzel's Algorithm for rapid, lightweight
 * spectral energy detection at targeted frequency bins (1200 Hz Space, 2200 Hz Mark).
 *
 * Characteristics:
 * - O(N) complexity per frequency bin without computing full FFT.
 * - Negligible RAM footprint (only 2 state variables per bin).
 * - Ideal for mobile DSP and zero-external-library constraint.
 */
class GoertzelDetector(
    val sampleRate: Int,
    val targetFreq: Double,
    val blockSize: Int
) {
    private val k: Int = ((blockSize * targetFreq) / sampleRate).roundToInt()
    private val omega: Double = (2.0 * Math.PI * k) / blockSize
    private val coeff: Double = 2.0 * cos(omega)

    /**
     * Computes the signal power at [targetFreq] from 16-bit PCM ShortArray.
     */
    fun computePowerFromShorts(samples: ShortArray, offset: Int = 0, length: Int = blockSize): Double {
        val n = length.coerceAtMost(samples.size - offset)
        if (n <= 0) return 0.0

        var s1 = 0.0
        var s2 = 0.0

        for (i in offset until (offset + n)) {
            val x = samples[i] / 32768.0
            val s0 = x + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }

        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        return (power / (n * n)).coerceAtLeast(0.0)
    }

    companion object {
        /**
         * Computes Root Mean Square (RMS) energy of a block of audio samples.
         * Used for squelch filtering and ambient noise rejection.
         */
        fun computeRms(samples: ShortArray, offset: Int = 0, length: Int = samples.size): Double {
            val n = length.coerceAtMost(samples.size - offset)
            if (n <= 0) return 0.0

            var sumSquares = 0.0
            for (i in offset until (offset + n)) {
                val norm = samples[i] / 32768.0
                sumSquares += norm * norm
            }
            return sqrt(sumSquares / n)
        }
    }
}
