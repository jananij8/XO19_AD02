package com.soniccast.app

import com.soniccast.app.data.protocol.CRCManager
import org.junit.Assert.*
import org.junit.Test

class CRCManagerTest {

    @Test
    fun testCrcCalculationMatches() {
        val data = "SonicCast Acoustic Protocol".toByteArray(Charsets.UTF_8)
        val crc = CRCManager.compute(data)

        assertTrue("CRC must be valid 8-bit integer (0..255)", crc in 0..255)
        assertTrue("Verification must pass for original data", CRCManager.verify(data, 0, data.size, crc))
    }

    @Test
    fun testBitFlipDetected() {
        val data = "Zero Network Broadcast".toByteArray(Charsets.UTF_8)
        val originalCrc = CRCManager.compute(data)

        // Corrupt a single bit in the 3rd byte
        val corrupted = data.copyOf()
        corrupted[2] = (corrupted[2].toInt() xor 0x01).toByte()

        val corruptedCrc = CRCManager.compute(corrupted)
        assertNotEquals("Corrupted data must produce different CRC", originalCrc, corruptedCrc)
        assertFalse("Verification must fail on corrupted data", CRCManager.verify(corrupted, 0, corrupted.size, originalCrc))
    }

    @Test
    fun testEmptyDataCrc() {
        val empty = ByteArray(0)
        val crc = CRCManager.compute(empty)
        assertEquals("CRC of empty array must be 0", 0, crc)
    }
}
