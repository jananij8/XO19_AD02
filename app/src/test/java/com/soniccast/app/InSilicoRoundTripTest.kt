package com.soniccast.app

import com.soniccast.app.data.audio.FSKDecoder
import com.soniccast.app.data.audio.FSKEncoder
import com.soniccast.app.data.protocol.Packet
import com.soniccast.app.data.protocol.PacketType
import com.soniccast.app.data.protocol.Packetizer
import com.soniccast.app.data.transport.RecoveryManager
import org.junit.Assert.*
import org.junit.Test

class InSilicoRoundTripTest {

    @Test
    fun testEndToEndAudioModulationDemodulationRoundTrip() {
        val testPayload = "HelloSonicCast".toByteArray()
        val originalPacket = Packet.createData(
            messageId = 15,
            version = 1,
            packetId = 0,
            totalPackets = 1,
            payload = testPayload
        )

        val encoder = FSKEncoder(symbolDurationMs = 15)
        val pcm = encoder.modulate(originalPacket)

        val decoder = FSKDecoder(symbolDurationMs = 15, squelchThreshold = 0.001)
        val decodedPackets = decoder.decodeFullSignal(pcm)

        assertEquals("Should decode exactly 1 packet", 1, decodedPackets.size)
        val decoded = decodedPackets[0]

        assertEquals(originalPacket.type, decoded.type)
        assertEquals(originalPacket.messageId, decoded.messageId)
        assertEquals(originalPacket.version, decoded.version)
        assertEquals(originalPacket.packetId, decoded.packetId)
        assertEquals(originalPacket.totalPackets, decoded.totalPackets)
        assertArrayEquals("Payload must match original bytes", originalPacket.payload, decoded.payload)
    }

    @Test
    fun testChallenge1PartialLossRecoveryInAudioSimulation() {
        val messageText = "Room 302: Quiz Starts Now"
        val (packets, parity) = Packetizer.packetize(messageId = 7, version = 1, text = messageText, maxPayloadSize = 12)

        val recoveryManager = RecoveryManager()

        // Feed all packets EXCEPT packet 1 to simulate partial reception
        for (pkt in packets) {
            if (pkt.packetId != 1) {
                recoveryManager.onPacketReceived(pkt)
            }
        }
        // Feed parity packet
        val stateAfterDrop = recoveryManager.onPacketReceived(parity)

        // Verify that parity repaired the missing packet 1
        assertTrue("Message must be complete after parity repair", stateAfterDrop.isComplete)
        assertTrue("State must indicate repair via parity", stateAfterDrop.isRepairedViaParity)
        assertEquals("Reconstructed text must match original", messageText, stateAfterDrop.reconstructedText)
    }

    @Test
    fun testChallenge2AnnouncementBeaconAudioRoundTrip() {
        val announcement = Packet.createAnnouncement(messageId = 88, version = 4, totalPackets = 6)
        val encoder = FSKEncoder(symbolDurationMs = 15)
        val pcm = encoder.modulate(announcement)

        val decoder = FSKDecoder(symbolDurationMs = 15, squelchThreshold = 0.001)
        val decoded = decoder.decodeFullSignal(pcm)

        assertEquals(1, decoded.size)
        assertEquals(PacketType.ANNOUNCEMENT, decoded[0].type)
        assertEquals(88, decoded[0].messageId)
        assertEquals(4, decoded[0].version)
        assertEquals(6, decoded[0].totalPackets)
    }
}
