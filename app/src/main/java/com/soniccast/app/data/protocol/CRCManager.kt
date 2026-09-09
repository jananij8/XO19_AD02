package com.soniccast.app.data.protocol

/**
 * High-performance pure Kotlin implementation of CRC-8 (Polynomial 0x07: x^8 + x^2 + x + 1).
 *
 * Used for packet-level integrity verification across acoustic transmissions.
 * Employs a precomputed 256-entry lookup table for sub-microsecond calculation.
 */
object CRCManager {
    private val table = IntArray(256)

    init {
        for (i in 0 until 256) {
            var curr = i
            for (j in 0 until 8) {
                curr = if ((curr and 0x80) != 0) {
                    ((curr shl 1) xor 0x07) and 0xFF
                } else {
                    (curr shl 1) and 0xFF
                }
            }
            table[i] = curr
        }
    }

    /**
     * Computes CRC-8 over [bytes] from [offset] for [length] elements.
     * Initial value is 0x00.
     */
    fun compute(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
        var crc = 0x00
        val end = (offset + length).coerceAtMost(bytes.size)
        for (i in offset until end) {
            val data = bytes[i].toInt() and 0xFF
            crc = table[(crc xor data) and 0xFF]
        }
        return crc and 0xFF
    }

    /**
     * Verifies if the computed CRC-8 matches the expected [expectedCrc].
     */
    fun verify(bytes: ByteArray, offset: Int, length: Int, expectedCrc: Int): Boolean {
        val computed = compute(bytes, offset, length)
        return computed == (expectedCrc and 0xFF)
    }
}
