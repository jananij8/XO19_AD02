package com.soniccast.app

import com.soniccast.app.data.protocol.Packet
import com.soniccast.app.data.protocol.PacketType
import com.soniccast.app.data.protocol.Packetizer
import org.junit.Assert.*
import org.junit.Test

class PacketizerTest {

    @Test
    fun testPacketizationChunksProperly() {
        val text = "Exam Hall A: Section 2 begins in 10 minutes. Code: 7892"
        val (packets, parity) = Packetizer.packetize(messageId = 1, version = 1, text = text, maxPayloadSize = 16)

        assertTrue("Should produce multiple packets for long message", packets.size >= 3)
        assertEquals("Parity packet must have type PARITY_XOR", PacketType.PARITY_XOR, parity.type)
        assertEquals("Parity packet totalPackets must match data packets count", packets.size, parity.totalPackets)

        for ((index, pkt) in packets.withIndex()) {
            assertEquals("Packet index should match sequential ID", index, pkt.packetId)
            assertTrue("Payload must be <= 16 bytes", pkt.payloadLength <= 16)
        }
    }

    @Test
    fun testFullMessageReassembly() {
        val originalText = "SonicCast Offline Acoustic Broadcast Test String!"
        val (packets, parity) = Packetizer.packetize(messageId = 5, version = 1, text = originalText)

        val packetMap = packets.associateBy { it.packetId }
        val reconstructed = Packetizer.depacketize(packetMap, packets.size, parity)

        assertEquals("Reconstructed message must match original", originalText, reconstructed)
    }

    @Test
    fun testSurpriseChallenge1ParityFecRecovery() {
        val originalText = "Classroom Alert: Wi-Fi is down, please listen for sound broadcast."
        val (packets, parity) = Packetizer.packetize(messageId = 12, version = 1, text = originalText, maxPayloadSize = 16)

        val total = packets.size
        assertTrue("Test needs at least 3 packets", total >= 3)

        // Drop packet #1 (simulate echo corruption / noise drop)
        val incompleteMap = packets.filter { it.packetId != 1 }.associateBy { it.packetId }
        assertEquals("Map should be missing packet 1", total - 1, incompleteMap.size)
        assertFalse("Packet 1 must not be in map", incompleteMap.containsKey(1))

        // Depacketize with Parity FEC
        val recoveredText = Packetizer.depacketize(incompleteMap, total, parity)

        assertNotNull("Recovery via XOR parity must succeed for single packet drop", recoveredText)
        assertEquals("Recovered text must match original exactly", originalText, recoveredText)
    }

    @Test
    fun testOutOfOrderReassembly() {
        val text = "Multi-device acoustic mesh reassembly out of order!"
        val (packets, parity) = Packetizer.packetize(messageId = 99, version = 1, text = text)

        // Reverse packet arrival order
        val reversedMap = packets.reversed().associateBy { it.packetId }
        val reconstructed = Packetizer.depacketize(reversedMap, packets.size, parity)

        assertEquals("Out-of-order packets must assemble in correct order", text, reconstructed)
    }
}
