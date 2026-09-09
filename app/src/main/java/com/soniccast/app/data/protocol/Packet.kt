package com.soniccast.app.data.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Types of acoustic frames in the SonicCast protocol.
 */
enum class PacketType(val code: Byte) {
    DATA(0x01),
    PARITY_XOR(0x02),       // Surprise Challenge 1: FEC Parity packet
    NACK_REPAIR(0x03),      // Surprise Challenge 1: Acoustic NACK requesting missing packets
    ACK_CONFIRM(0x04),      // Acoustic confirmation chirp
    ANNOUNCEMENT(0x05),     // Surprise Challenge 2: Periodic beacon advertising latest message
    SYNC_REQUEST(0x06);     // Surprise Challenge 2: Late-joining receiver sync request

    companion object {
        fun fromCode(code: Byte): PacketType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Fixed-structure Binary Packet for SonicCast Acoustic One-to-Many Protocol.
 *
 * Binary Layout (Header + Payload + CRC):
 * [0]    Packet Type (1 Byte: DATA, PARITY_XOR, NACK_REPAIR, ACK_CONFIRM, ANNOUNCEMENT, SYNC_REQUEST)
 * [1]    Message ID (1 Byte: 0..255)
 * [2]    Message Version (1 Byte: 0..255 for Challenge 2 Dynamic Group sync)
 * [3]    Packet ID (1 Byte: 0..totalPackets-1, or special IDs)
 * [4]    Total Packets (1 Byte: 1..255)
 * [5]    Payload Length (1 Byte: 0..16)
 * [6..6+N-1] Payload (N Bytes, max 16)
 * [6+N]  CRC-8 (1 Byte over bytes 0 .. 5+N)
 */
data class Packet(
    val type: PacketType,
    val messageId: Int,       // 0..255
    val version: Int,         // 0..255
    val packetId: Int,        // 0..255
    val totalPackets: Int,    // 1..255
    val payload: ByteArray = ByteArray(0)
) {
    val payloadLength: Int = payload.size.coerceAtMost(MAX_PAYLOAD_SIZE)

    companion object {
        // Preamble: 16 alternating bits (10101010 10101010)
        val PREAMBLE_BITS = intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0)
        // Unique frame marker (0xD5 = 0b11010101)
        const val FRAME_MARKER_BYTE: Byte = 0xD5.toByte()
        const val FRAME_MARKER: Byte = FRAME_MARKER_BYTE

        const val HEADER_SIZE = 6 // Type(1) + MsgId(1) + Version(1) + PacketId(1) + Total(1) + Len(1)
        const val CRC_SIZE = 1
        const val MAX_PAYLOAD_SIZE = 16
        const val MIN_PACKET_SIZE = HEADER_SIZE + CRC_SIZE

        // Special Packet IDs
        const val PACKET_ID_PARITY = 0xFE
        const val PACKET_ID_NACK = 0xFD
        const val PACKET_ID_ACK = 0xFC
        const val PACKET_ID_ANNOUNCEMENT = 0xFB
        const val PACKET_ID_SYNC_REQ = 0xFA

        fun createData(messageId: Int, version: Int, packetId: Int, totalPackets: Int, payload: ByteArray): Packet {
            return Packet(
                type = PacketType.DATA,
                messageId = messageId and 0xFF,
                version = version and 0xFF,
                packetId = packetId and 0xFF,
                totalPackets = totalPackets and 0xFF,
                payload = payload.take(MAX_PAYLOAD_SIZE).toByteArray()
            )
        }

        fun createParity(messageId: Int, version: Int, totalPackets: Int, parityPayload: ByteArray): Packet {
            return Packet(
                type = PacketType.PARITY_XOR,
                messageId = messageId and 0xFF,
                version = version and 0xFF,
                packetId = PACKET_ID_PARITY,
                totalPackets = totalPackets and 0xFF,
                payload = parityPayload.take(MAX_PAYLOAD_SIZE).toByteArray()
            )
        }

        fun createNack(messageId: Int, version: Int, missingIndices: List<Int>): Packet {
            val payload = missingIndices.take(MAX_PAYLOAD_SIZE).map { it.toByte() }.toByteArray()
            return Packet(
                type = PacketType.NACK_REPAIR,
                messageId = messageId and 0xFF,
                version = version and 0xFF,
                packetId = PACKET_ID_NACK,
                totalPackets = 1,
                payload = payload
            )
        }

        fun createAck(messageId: Int, version: Int): Packet {
            return Packet(
                type = PacketType.ACK_CONFIRM,
                messageId = messageId and 0xFF,
                version = version and 0xFF,
                packetId = PACKET_ID_ACK,
                totalPackets = 1,
                payload = ByteArray(0)
            )
        }

        fun createAnnouncement(messageId: Int, version: Int, totalPackets: Int): Packet {
            return Packet(
                type = PacketType.ANNOUNCEMENT,
                messageId = messageId and 0xFF,
                version = version and 0xFF,
                packetId = PACKET_ID_ANNOUNCEMENT,
                totalPackets = totalPackets and 0xFF,
                payload = ByteArray(0)
            )
        }

        fun createSyncRequest(messageId: Int, version: Int, missingIndices: List<Int> = emptyList()): Packet {
            val payload = missingIndices.take(MAX_PAYLOAD_SIZE).map { it.toByte() }.toByteArray()
            return Packet(
                type = PacketType.SYNC_REQUEST,
                messageId = messageId and 0xFF,
                version = version and 0xFF,
                packetId = PACKET_ID_SYNC_REQ,
                totalPackets = 1,
                payload = payload
            )
        }

        /**
         * Deserializes and validates a packet from raw bytes (Header + Payload + CRC-8).
         * Returns null if length is insufficient or CRC-8 verification fails.
         */
        fun parse(raw: ByteArray): Packet? {
            if (raw.size < MIN_PACKET_SIZE) return null

            val typeCode = raw[0]
            val type = PacketType.fromCode(typeCode) ?: return null
            val messageId = raw[1].toInt() and 0xFF
            val version = raw[2].toInt() and 0xFF
            val packetId = raw[3].toInt() and 0xFF
            val totalPackets = raw[4].toInt() and 0xFF
            val payloadLen = raw[5].toInt() and 0xFF

            if (payloadLen > MAX_PAYLOAD_SIZE) return null
            val expectedTotal = HEADER_SIZE + payloadLen + CRC_SIZE
            if (raw.size < expectedTotal) return null

            val payload = ByteArray(payloadLen)
            System.arraycopy(raw, HEADER_SIZE, payload, 0, payloadLen)

            val receivedCrc = raw[HEADER_SIZE + payloadLen].toInt() and 0xFF
            val computedCrc = CRCManager.compute(raw, offset = 0, length = HEADER_SIZE + payloadLen)

            if (computedCrc != receivedCrc) {
                return null // CRC mismatch: reject corrupted packet
            }

            return Packet(
                type = type,
                messageId = messageId,
                version = version,
                packetId = packetId,
                totalPackets = totalPackets,
                payload = payload
            )
        }
    }

    /**
     * Serializes this packet into a binary byte array: Header (6B) + Payload (NB) + CRC-8 (1B).
     */
    fun toByteArray(): ByteArray {
        val totalSize = HEADER_SIZE + payloadLength + CRC_SIZE
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        buffer.put(type.code)
        buffer.put(messageId.toByte())
        buffer.put(version.toByte())
        buffer.put(packetId.toByte())
        buffer.put(totalPackets.toByte())
        buffer.put(payloadLength.toByte())

        if (payloadLength > 0) {
            buffer.put(payload, 0, payloadLength)
        }

        val rawArray = buffer.array()
        val crc = CRCManager.compute(rawArray, offset = 0, length = HEADER_SIZE + payloadLength)
        buffer.put(crc.toByte())

        return buffer.array()
    }

    fun parseMissingIndices(): List<Int> {
        if (type != PacketType.NACK_REPAIR && type != PacketType.SYNC_REQUEST) return emptyList()
        return payload.map { it.toInt() and 0xFF }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Packet
        if (type != other.type) return false
        if (messageId != other.messageId) return false
        if (version != other.version) return false
        if (packetId != other.packetId) return false
        if (totalPackets != other.totalPackets) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + messageId
        result = 31 * result + version
        result = 31 * result + packetId
        result = 31 * result + totalPackets
        result = 31 * result + payload.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "Packet(type=$type, msg=$messageId, ver=$version, id=$packetId/$totalPackets, len=$payloadLength)"
    }
}
