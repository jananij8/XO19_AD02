package com.soniccast.app.data.audio

import com.soniccast.app.data.protocol.Packet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Continuous Phase Frequency Shift Keying (CPFSK) Audio Synthesizer.
 *
 * Implements Bell 202 acoustic specification:
 * - Space (Bit 0): 1200 Hz
 * - Mark (Bit 1): 2200 Hz
 * - Sample Rate: 44,100 Hz
 * - Symbol Rate: 40 Baud (25 ms per bit)
 * - Preamble: 16 alternating bits for bit clock recovery
 * - Frame Marker: 0xD5 (11010101) for packet alignment
 * - Edge Windowing: Raised-cosine windowing to suppress clicks and out-of-band splatter.
 */
class FSKEncoder(
    val sampleRate: Int = 44100,
    val spaceFreq: Double = 1200.0,
    val markFreq: Double = 2200.0,
    val symbolDurationMs: Int = 25,
    val interPacketSilenceMs: Int = 60
) {
    val samplesPerSymbol: Int = (sampleRate * symbolDurationMs) / 1000
    val samplesPerSilence: Int = (sampleRate * interPacketSilenceMs) / 1000

    /**
     * Modulates a [Packet] into a 16-bit PCM [ShortArray].
     */
    fun modulate(packet: Packet): ShortArray {
        val rawBytes = packet.toByteArray()
        return modulateBytes(rawBytes)
    }

    /**
     * Modulates arbitrary raw bytes into CPFSK audio with Preamble (16 bits) and Frame Marker (0xD5).
     */
    fun modulateBytes(data: ByteArray): ShortArray {
        val bits = mutableListOf<Int>()

        // 1. Preamble: 16 alternating bits (1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0)
        for (bit in Packet.PREAMBLE_BITS) {
            bits.add(bit)
        }

        // 2. Frame Marker: 0xD5 (11010101, MSB-first)
        val marker = Packet.FRAME_MARKER_BYTE.toInt() and 0xFF
        for (i in 7 downTo 0) {
            bits.add((marker ushr i) and 0x01)
        }

        // 3. Packet Bytes (8 bits per byte, MSB-first)
        for (b in data) {
            val byteVal = b.toInt() and 0xFF
            for (i in 7 downTo 0) {
                bits.add((byteVal ushr i) and 0x01)
            }
        }

        // Synthesize continuous-phase PCM samples for all bits
        val totalSamples = (bits.size * samplesPerSymbol) + samplesPerSilence
        val output = ShortArray(totalSamples)

        var phase = 0.0
        var sampleOffset = 0

        for (bit in bits) {
            val freq = if (bit == 1) markFreq else spaceFreq
            phase = emitSymbol(freq, samplesPerSymbol, phase, output, sampleOffset)
            sampleOffset += samplesPerSymbol
        }

        // Silence tail (samples are initialized to 0)
        return output
    }

    /**
     * Synthesizes a compact acoustic ACK tone chirp (~2400 Hz -> 2800 Hz, 80ms).
     */
    fun modulateAckChirp(): ShortArray {
        val numSamples = (sampleRate * 80) / 1000
        val samples = ShortArray(numSamples)
        val f0 = 2400.0
        val f1 = 2800.0
        val durationSec = 0.08
        val k = (f1 - f0) / durationSec

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val phase = 2.0 * PI * (f0 * t + 0.5 * k * t * t)
            var s = sin(phase)

            // Raised cosine edge window (10%)
            val edge = (numSamples * 0.1).toInt()
            if (i < edge) s *= 0.5 * (1.0 - cos(PI * i / edge))
            else if (i > numSamples - edge) s *= 0.5 * (1.0 - cos(PI * (numSamples - i) / edge))

            samples[i] = (s * 26000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return samples
    }

    /**
     * Synthesizes a compact acoustic NACK burst specifying a missing packet index.
     */
    fun modulateNackBurst(messageId: Int, version: Int, missingIndices: List<Int>): ShortArray {
        val nackPacket = Packet.createNack(messageId, version, missingIndices)
        return modulate(nackPacket)
    }

    private fun emitSymbol(
        freq: Double,
        numSamples: Int,
        initialPhase: Double,
        buffer: ShortArray,
        offset: Int
    ): Double {
        var phase = initialPhase
        val phaseInc = 2.0 * PI * freq / sampleRate

        for (i in 0 until numSamples) {
            var sample = sin(phase)
            phase += phaseInc
            if (phase >= 2.0 * PI) phase -= 2.0 * PI

            // 5% edge window on symbol boundary to prevent clicks
            val edge = (numSamples * 0.05).toInt().coerceAtLeast(2)
            if (i < edge) {
                sample *= 0.5 * (1.0 - cos(PI * i / edge))
            } else if (i > numSamples - edge) {
                sample *= 0.5 * (1.0 - cos(PI * (numSamples - i) / edge))
            }

            buffer[offset + i] = (sample * 28000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return phase
    }
}
