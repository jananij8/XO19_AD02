package com.soniccast.app.data.audio

import com.soniccast.app.data.protocol.Packet

/**
 * Pure Kotlin Frequency Shift Keying (FSK) Demodulator.
 *
 * Implements Bell 202 acoustic demodulation:
 * - Dual Goertzel filters: 1200 Hz (Space / 0) and 2200 Hz (Mark / 1).
 * - Squelch RMS energy gate to reject ambient silence and low-level noise.
 * - Clock recovery: Matched correlation over alternating preamble symbols to find optimal symbol phase.
 * - Bitstream Frame Marker: Locks on 0xD5 (11010101) to establish packet boundary.
 * - Byte assembling (8 bits MSB-first) and strict CRC-8 validation on candidate packets.
 */
class FSKDecoder(
    val sampleRate: Int = 44100,
    val spaceFreq: Double = 1200.0,
    val markFreq: Double = 2200.0,
    val symbolDurationMs: Int = 25,
    var squelchThreshold: Double = 0.015,
    private val onPacketDecoded: ((Packet) -> Unit)? = null,
    private val onLogMessage: ((String) -> Unit)? = null
) {
    val samplesPerSymbol: Int = (sampleRate * symbolDurationMs) / 1000

    private val markDetector = GoertzelDetector(sampleRate, markFreq, samplesPerSymbol)
    private val spaceDetector = GoertzelDetector(sampleRate, spaceFreq, samplesPerSymbol)

    private enum class State {
        SEARCHING_PREAMBLE,
        DECODING_STREAM
    }

    private var state = State.SEARCHING_PREAMBLE
    private val sampleBuffer = ArrayList<Short>()

    private var markerShifter = 0
    private var markerFound = false
    private var symbolsSincePreamble = 0

    // Packet byte accumulator
    private val packetBytes = ArrayList<Byte>()
    private var currentByteVal = 0
    private var bitCountInByte = 0
    private var expectedTotalPacketBytes = Packet.MIN_PACKET_SIZE

    /**
     * Feeds incoming PCM audio samples into the demodulator.
     */
    @Synchronized
    fun processSamples(samples: ShortArray, offset: Int = 0, length: Int = samples.size) {
        for (i in offset until (offset + length)) {
            sampleBuffer.add(samples[i])
        }

        // Bound memory: discard ancient samples if buffer exceeds 4 seconds while searching preamble
        val maxBufferSize = sampleRate * 4
        if (state == State.SEARCHING_PREAMBLE && sampleBuffer.size > maxBufferSize) {
            val toRemove = sampleBuffer.size - (sampleRate * 2)
            sampleBuffer.subList(0, toRemove).clear()
        }

        processBuffer()
    }

    /**
     * Resets internal state to search for a new preamble.
     */
    @Synchronized
    fun reset() {
        state = State.SEARCHING_PREAMBLE
        sampleBuffer.clear()
        markerShifter = 0
        markerFound = false
        symbolsSincePreamble = 0
        packetBytes.clear()
        currentByteVal = 0
        bitCountInByte = 0
        expectedTotalPacketBytes = Packet.MIN_PACKET_SIZE
    }

    private fun processBuffer() {
        while (true) {
            when (state) {
                State.SEARCHING_PREAMBLE -> {
                    // Need at least 9 symbols to evaluate preamble correlation
                    val requiredSamples = samplesPerSymbol * 9
                    if (sampleBuffer.size < requiredSamples) return

                    // Check energy of first symbol
                    val firstWindow = sampleBuffer.take(samplesPerSymbol).toShortArray()
                    val rms = GoertzelDetector.computeRms(firstWindow)
                    if (rms < squelchThreshold) {
                        // Skip silence in chunks of half symbol
                        val step = (samplesPerSymbol / 2).coerceAtLeast(1)
                        if (sampleBuffer.size >= step) {
                            sampleBuffer.subList(0, step).clear()
                        }
                        continue
                    }

                    // Preamble Matched Filter Search:
                    // Test 8 candidate phase offsets across one symbol period
                    val numCandidates = 8
                    val phaseStep = (samplesPerSymbol / numCandidates).coerceAtLeast(1)
                    var bestScore = -1.0
                    var bestOffset = 0

                    val tempArray = sampleBuffer.take(requiredSamples).toShortArray()

                    for (c in 0 until numCandidates) {
                        val offset = c * phaseStep
                        var score = 0.0
                        // Preamble pattern is 1, 0, 1, 0, 1, 0, 1, 0 (Mark, Space, Mark, Space...)
                        for (m in 0 until 8) {
                            val start = offset + m * samplesPerSymbol
                            if (start + samplesPerSymbol <= tempArray.size) {
                                val pMark = markDetector.computePowerFromShorts(tempArray, start, samplesPerSymbol)
                                val pSpace = spaceDetector.computePowerFromShorts(tempArray, start, samplesPerSymbol)
                                val diff = pMark - pSpace
                                val expectedSign = if (m % 2 == 0) 1.0 else -1.0
                                score += expectedSign * diff
                            }
                        }

                        if (score > bestScore) {
                            bestScore = score
                            bestOffset = offset
                        }
                    }

                    // If correlation score is positive and confident:
                    if (bestScore > 0.002) {
                        // Lock symbol clock! Align buffer to bestOffset
                        if (bestOffset > 0 && sampleBuffer.size >= bestOffset) {
                            sampleBuffer.subList(0, bestOffset).clear()
                        }

                        state = State.DECODING_STREAM
                        markerShifter = 0
                        markerFound = false
                        symbolsSincePreamble = 0
                        packetBytes.clear()
                        currentByteVal = 0
                        bitCountInByte = 0
                        expectedTotalPacketBytes = Packet.MIN_PACKET_SIZE
                        onLogMessage?.invoke("FSK: Symbol clock locked (score=${String.format("%.4f", bestScore)}). Scanning for Frame Marker...")
                    } else {
                        // Slide by 1 symbol and continue searching
                        sampleBuffer.subList(0, samplesPerSymbol).clear()
                    }
                }

                State.DECODING_STREAM -> {
                    if (sampleBuffer.size < samplesPerSymbol) return

                    val window = sampleBuffer.take(samplesPerSymbol).toShortArray()
                    val pMark = markDetector.computePowerFromShorts(window)
                    val pSpace = spaceDetector.computePowerFromShorts(window)

                    val bit = if (pMark >= pSpace) 1 else 0
                    sampleBuffer.subList(0, samplesPerSymbol).clear()
                    symbolsSincePreamble++

                    if (!markerFound) {
                        markerShifter = ((markerShifter shl 1) or bit) and 0xFF
                        val targetMarker = Packet.FRAME_MARKER_BYTE.toInt() and 0xFF

                        if (markerShifter == targetMarker) {
                            markerFound = true
                            packetBytes.clear()
                            currentByteVal = 0
                            bitCountInByte = 0
                            expectedTotalPacketBytes = Packet.MIN_PACKET_SIZE
                            onLogMessage?.invoke("FSK: Frame Marker (0xD5) verified ✓. Reading packet bytes...")
                        } else if (symbolsSincePreamble > 36) {
                            // Preamble ended without finding marker; return to preamble search
                            state = State.SEARCHING_PREAMBLE
                            return
                        }
                    } else {
                        // Reading packet bytes: 8 bits per byte (MSB-first)
                        currentByteVal = (currentByteVal shl 1) or bit
                        bitCountInByte++

                        if (bitCountInByte == 8) {
                            val decodedByte = (currentByteVal and 0xFF).toByte()
                            packetBytes.add(decodedByte)
                            currentByteVal = 0
                            bitCountInByte = 0

                            // Once header (6 bytes) is read, determine exact total packet size
                            if (packetBytes.size == Packet.HEADER_SIZE) {
                                val payloadLen = packetBytes[5].toInt() and 0xFF
                                if (payloadLen > Packet.MAX_PAYLOAD_SIZE) {
                                    // Corrupted header, abort packet
                                    state = State.SEARCHING_PREAMBLE
                                    return
                                }
                                expectedTotalPacketBytes = Packet.HEADER_SIZE + payloadLen + Packet.CRC_SIZE
                            }

                            // When all bytes are gathered, parse and verify CRC-8
                            if (packetBytes.size >= expectedTotalPacketBytes && expectedTotalPacketBytes >= Packet.MIN_PACKET_SIZE) {
                                val candidate = packetBytes.toByteArray()
                                val packet = Packet.parse(candidate)

                                if (packet != null) {
                                    onLogMessage?.invoke("FSK: Packet verified ✓ [${packet.type}, msg=${packet.messageId}, ver=${packet.version}, id=${packet.packetId}/${packet.totalPackets}]")
                                    onPacketDecoded?.invoke(packet)
                                } else {
                                    onLogMessage?.invoke("FSK: Packet rejected (CRC-8 mismatch)")
                                }

                                // Reset to search for next packet
                                state = State.SEARCHING_PREAMBLE
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Synchronous batch decoder for tests and diagnostic simulations.
     */
    fun decodeFullSignal(signal: ShortArray): List<Packet> {
        val decoded = mutableListOf<Packet>()
        val testDecoder = FSKDecoder(
            sampleRate = sampleRate,
            spaceFreq = spaceFreq,
            markFreq = markFreq,
            symbolDurationMs = symbolDurationMs,
            squelchThreshold = squelchThreshold,
            onPacketDecoded = { decoded.add(it) },
            onLogMessage = onLogMessage
        )
        testDecoder.processSamples(signal)
        return decoded
    }
}
