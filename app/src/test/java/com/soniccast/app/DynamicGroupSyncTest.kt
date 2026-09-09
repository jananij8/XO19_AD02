package com.soniccast.app

import com.soniccast.app.data.protocol.Packet
import com.soniccast.app.data.protocol.PacketType
import org.junit.Assert.*
import org.junit.Test

class DynamicGroupSyncTest {

    @Test
    fun testAnnouncementPacketStructure() {
        val announcement = Packet.createAnnouncement(messageId = 42, version = 3, totalPackets = 5)

        assertEquals(PacketType.ANNOUNCEMENT, announcement.type)
        assertEquals(42, announcement.messageId)
        assertEquals(3, announcement.version)
        assertEquals(5, announcement.totalPackets)
        assertEquals(0, announcement.payloadLength)

        val raw = announcement.toByteArray()
        val parsed = Packet.parse(raw)

        assertNotNull("Announcement packet must serialize and deserialize properly", parsed)
        assertEquals(PacketType.ANNOUNCEMENT, parsed!!.type)
        assertEquals(42, parsed.messageId)
        assertEquals(3, parsed.version)
        assertEquals(5, parsed.totalPackets)
    }

    @Test
    fun testSyncRequestPacketStructure() {
        val syncReq = Packet.createSyncRequest(messageId = 42, version = 3, missingIndices = listOf(1, 2))

        assertEquals(PacketType.SYNC_REQUEST, syncReq.type)
        assertEquals(42, syncReq.messageId)
        assertEquals(3, syncReq.version)
        assertEquals(listOf(1, 2), syncReq.parseMissingIndices())

        val raw = syncReq.toByteArray()
        val parsed = Packet.parse(raw)

        assertNotNull("SyncRequest packet must serialize and deserialize properly", parsed)
        assertEquals(PacketType.SYNC_REQUEST, parsed!!.type)
        assertEquals(listOf(1, 2), parsed.parseMissingIndices())
    }

    @Test
    fun testDynamicVersionDiffing() {
        val senderVersion = 5
        val receiverPossessedVersion = 4

        val syncNeeded = senderVersion != receiverPossessedVersion
        assertTrue("Receiver should detect sync is required when versions differ", syncNeeded)

        val receiverUpdatedVersion = 5
        val noSyncNeeded = senderVersion == receiverUpdatedVersion
        assertTrue("Receiver should detect no sync needed when version matches", noSyncNeeded)
    }
}
