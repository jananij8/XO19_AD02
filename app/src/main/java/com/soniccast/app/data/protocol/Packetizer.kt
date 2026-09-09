package com.soniccast.app.data.protocol

/**
 * Handles message packetization, XOR-parity forward-error-correction (FEC),
 * out-of-order reassembly, and single-packet loss reconstruction.
 */
object Packetizer {

    /**
     * Splits a text message into fixed-size [Packet]s and generates an XOR Parity FEC packet.
     *
     * @return Pair of (DataPackets, ParityPacket)
     */
    fun packetize(
        messageId: Int,
        version: Int,
        text: String,
        maxPayloadSize: Int = Packet.MAX_PAYLOAD_SIZE
    ): Pair<List<Packet>, Packet> {
        val rawBytes = text.toByteArray(Charsets.UTF_8)
        val chunks = rawBytes.toList().chunked(maxPayloadSize).map { it.toByteArray() }
        val totalPackets = chunks.size.coerceAtLeast(1)

        val dataPackets = chunks.mapIndexed { index, payload ->
            Packet.createData(
                messageId = messageId,
                version = version,
                packetId = index,
                totalPackets = totalPackets,
                payload = payload
            )
        }

        // Compute XOR Parity FEC across all data chunks
        val maxChunkLen = chunks.maxOfOrNull { it.size } ?: 0
        val parityBytes = ByteArray(maxChunkLen)

        for (i in 0 until maxChunkLen) {
            var xorVal = 0
            for (chunk in chunks) {
                if (i < chunk.size) {
                    xorVal = xorVal xor (chunk[i].toInt() and 0xFF)
                }
            }
            parityBytes[i] = xorVal.toByte()
        }

        val parityPacket = Packet.createParity(
            messageId = messageId,
            version = version,
            totalPackets = totalPackets,
            parityPayload = parityBytes
        )

        return Pair(dataPackets, parityPacket)
    }

    /**
     * Reconstructs UTF-8 message from received packets.
     * Supports single-packet loss reconstruction via XOR parity FEC.
     *
     * @param packets Map of PacketId -> Packet
     * @param totalPackets Expected total payload packets
     * @param parityPacket Optional XOR parity packet for FEC recovery
     * @return Reconstructed text string, or null if unrecoverable
     */
    fun depacketize(
        packets: Map<Int, Packet>,
        totalPackets: Int,
        parityPacket: Packet? = null
    ): String? {
        if (totalPackets <= 0) return null

        val missing = (0 until totalPackets).filter { !packets.containsKey(it) }

        // Case 1: 100% of data packets are present
        if (missing.isEmpty()) {
            return assembleString(packets, totalPackets)
        }

        // Case 2: Exactly 1 packet is missing, and Parity packet is present!
        // Surprise Challenge 1: Instant local repair without waiting for retransmission!
        if (missing.size == 1 && parityPacket != null) {
            val missingIndex = missing[0]
            val recoveredPayload = reconstructMissingPayload(packets, totalPackets, missingIndex, parityPacket)
            if (recoveredPayload != null) {
                val fullMap = packets.toMutableMap()
                fullMap[missingIndex] = Packet.createData(
                    messageId = parityPacket.messageId,
                    version = parityPacket.version,
                    packetId = missingIndex,
                    totalPackets = totalPackets,
                    payload = recoveredPayload
                )
                return assembleString(fullMap, totalPackets)
            }
        }

        return null // Cannot reconstruct yet (requires cyclic retransmit or NACK repair)
    }

    /**
     * Reconstructs payload for [missingIndex] using XOR parity.
     * P_missing[j] = Parity[j] XOR (XOR of all present P_i[j])
     */
    fun reconstructMissingPayload(
        packets: Map<Int, Packet>,
        totalPackets: Int,
        missingIndex: Int,
        parityPacket: Packet
    ): ByteArray? {
        val parityBytes = parityPacket.payload
        val parityLen = parityBytes.size
        val recovered = ByteArray(parityLen)

        for (j in 0 until parityLen) {
            var xorSum = parityBytes[j].toInt() and 0xFF
            for (i in 0 until totalPackets) {
                if (i == missingIndex) continue
                val p = packets[i] ?: return null
                if (j < p.payload.size) {
                    xorSum = xorSum xor (p.payload[j].toInt() and 0xFF)
                }
            }
            recovered[j] = xorSum.toByte()
        }

        return recovered
    }

    private fun assembleString(packets: Map<Int, Packet>, totalPackets: Int): String {
        val byteList = mutableListOf<Byte>()
        for (i in 0 until totalPackets) {
            val pkt = packets[i] ?: return ""
            pkt.payload.forEach { byteList.add(it) }
        }
        return String(byteList.toByteArray(), Charsets.UTF_8)
    }
}
