package com.soniccast.app.data.transport

import com.soniccast.app.data.protocol.Packet
import com.soniccast.app.data.protocol.PacketType
import com.soniccast.app.data.protocol.Packetizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Status of message reception on a receiver device.
 */
data class ReceptionState(
    val messageId: Int,
    val version: Int,
    val totalPackets: Int,
    val receivedCount: Int,
    val missingIndices: List<Int>,
    val isComplete: Boolean,
    val isRepairedViaParity: Boolean,
    val isRepairedViaNack: Boolean,
    val reconstructedText: String? = null
)

/**
 * Recovery Manager implementing Surprise Challenge 1 (Partial Reception & Self-Healing).
 *
 * Capabilities:
 * - Tracks per-packet CRC status.
 * - Single-packet loss local reconstruction via XOR Parity FEC.
 * - Missing packet diffing and acoustic NACK generation.
 * - Acoustic ACK confirmation generation.
 */
class RecoveryManager {

    // Map of PacketId -> Packet
    private val receivedPackets = ConcurrentHashMap<Int, Packet>()
    private var parityPacket: Packet? = null
    private var messageId: Int = -1
    private var version: Int = -1
    private var totalPackets: Int = 0
    private var repairedViaParity = false
    private var repairedViaNack = false

    /**
     * Records a newly arrived, CRC-verified packet.
     * Returns updated [ReceptionState].
     */
    @Synchronized
    fun onPacketReceived(packet: Packet, isSimulatedLoss: Boolean = false): ReceptionState {
        this.messageId = packet.messageId
        this.version = packet.version
        this.totalPackets = packet.totalPackets

        if (packet.type == PacketType.PARITY_XOR) {
            this.parityPacket = packet
        } else if (packet.type == PacketType.DATA) {
            // Surprise Challenge 1 Demo Hook: If simulated loss requested and packet is #1, skip it once
            if (isSimulatedLoss && packet.packetId == 1 && !receivedPackets.containsKey(1)) {
                // Drop packet intentionally for demonstration
            } else {
                if (receivedPackets.containsKey(packet.packetId) == false && receivedPackets.isNotEmpty()) {
                    // Packet arrived after gap -> repaired via NACK or cyclic retransmission
                    if ((0 until totalPackets).any { !receivedPackets.containsKey(it) && it != packet.packetId }) {
                        repairedViaNack = true
                    }
                }
                receivedPackets[packet.packetId] = packet
            }
        }

        return evaluateState()
    }

    /**
     * Evaluates current reception completeness, attempting XOR Parity FEC if possible.
     */
    @Synchronized
    fun evaluateState(): ReceptionState {
        if (totalPackets <= 0) {
            return ReceptionState(-1, -1, 0, 0, emptyList(), false, false, false)
        }

        val missing = (0 until totalPackets).filter { !receivedPackets.containsKey(it) }

        // Attempt 100% assembly
        var text = Packetizer.depacketize(receivedPackets, totalPackets, parityPacket)

        // Check if single-packet loss was repaired via XOR Parity
        if (text != null && missing.size == 1 && parityPacket != null) {
            repairedViaParity = true
        }

        val complete = text != null
        val receivedCount = if (complete) totalPackets else receivedPackets.size

        return ReceptionState(
            messageId = messageId,
            version = version,
            totalPackets = totalPackets,
            receivedCount = receivedCount,
            missingIndices = if (complete) emptyList() else missing,
            isComplete = complete,
            isRepairedViaParity = repairedViaParity,
            isRepairedViaNack = repairedViaNack,
            reconstructedText = text
        )
    }

    /**
     * Returns list of missing packet indices for NACK or SyncRequest.
     */
    @Synchronized
    fun getMissingPacketIndices(): List<Int> {
        if (totalPackets <= 0) return emptyList()
        return (0 until totalPackets).filter { !receivedPackets.containsKey(it) }
    }

    /**
     * Resets state for a new message session.
     */
    @Synchronized
    fun reset() {
        receivedPackets.clear()
        parityPacket = null
        messageId = -1
        version = -1
        totalPackets = 0
        repairedViaParity = false
        repairedViaNack = false
    }
}
