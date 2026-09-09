package com.soniccast.app.data.transport

import com.soniccast.app.data.audio.AudioPlayer
import com.soniccast.app.data.audio.AudioRecorder
import com.soniccast.app.data.audio.FSKDecoder
import com.soniccast.app.data.audio.FSKEncoder
import com.soniccast.app.data.protocol.Packet
import com.soniccast.app.data.protocol.PacketType
import com.soniccast.app.data.protocol.Packetizer
import com.soniccast.app.data.storage.LatestMessageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * High-level state of the Transmitter.
 */
enum class TransmitterState {
    IDLE,
    BROADCASTING_INITIAL,
    WAITING_FEEDBACK,
    REPAIRING_NACK,
    ANNOUNCING_BEACON,         // Surprise Challenge 2: Periodic beacon mode
    DYNAMIC_SYNC_RETRANSMIT,   // Surprise Challenge 2: Retransmitting for late-joining receiver
    COMPLETED
}

/**
 * Coordinates acoustic broadcast, Surprise Challenge 1 ARQ recovery,
 * and Surprise Challenge 2 Dynamic Group periodic announcement beacon & auto-sync.
 */
class TransmissionManager(
    private val audioPlayer: AudioPlayer,
    private val audioRecorder: AudioRecorder,
    private val latestMessageStore: LatestMessageStore,
    private val encoder: FSKEncoder = FSKEncoder()
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _state = MutableStateFlow(TransmitterState.IDLE)
    val state: StateFlow<TransmitterState> = _state.asStateFlow()

    private val _acksHeard = MutableStateFlow(0)
    val acksHeard: StateFlow<Int> = _acksHeard.asStateFlow()

    private val _syncRequestsHeard = MutableStateFlow(0)
    val syncRequestsHeard: StateFlow<Int> = _syncRequestsHeard.asStateFlow()

    private val _recoveredReceivers = MutableStateFlow(0)
    val recoveredReceivers: StateFlow<Int> = _recoveredReceivers.asStateFlow()

    private val _currentMessageText = MutableStateFlow("")
    val currentMessageText: StateFlow<String> = _currentMessageText.asStateFlow()

    private val _currentVersion = MutableStateFlow(1)
    val currentVersion: StateFlow<Int> = _currentVersion.asStateFlow()

    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    private var cachedDataPackets: List<Packet> = emptyList()
    private var cachedParityPacket: Packet? = null
    private var activeMessageId = 101

    private var broadcastJob: Job? = null
    private var beaconJob: Job? = null

    /**
     * Initiates a complete acoustic broadcast sequence for [text].
     */
    fun startBroadcast(text: String, versionNumber: Int = 1) {
        broadcastJob?.cancel()
        beaconJob?.cancel()

        broadcastJob = scope.launch {
            _state.value = TransmitterState.BROADCASTING_INITIAL
            _currentMessageText.value = text
            _currentVersion.value = versionNumber
            _acksHeard.value = 0
            _logs.emit("Transmitter: Initiating acoustic broadcast for Version #V$versionNumber")

            // Packetize and generate XOR parity FEC packet
            val (dataPackets, parityPacket) = Packetizer.packetize(
                messageId = activeMessageId,
                version = versionNumber,
                text = text
            )
            cachedDataPackets = dataPackets
            cachedParityPacket = parityPacket

            // Persist to local storage for Challenge 2 late-joiners
            latestMessageStore.saveLatestMessage(
                messageId = activeMessageId,
                version = versionNumber,
                totalPackets = dataPackets.size,
                text = text,
                packets = dataPackets
            )

            // --- Tier 1: Initial Broadcast Pass + Parity FEC ---
            _logs.emit("Transmitter: Transmitting ${dataPackets.size} data packets + 1 XOR parity packet")
            for (packet in dataPackets) {
                if (!isActive) return@launch
                val pcm = encoder.modulate(packet)
                audioPlayer.playSamples(pcm)
                delay(60) // Inter-packet acoustic spacing
            }

            // Transmit XOR Parity packet
            val parityPcm = encoder.modulate(parityPacket)
            audioPlayer.playSamples(parityPcm)
            delay(100)

            // --- Tier 2/3: Listen for ACKs / NACKs ---
            _state.value = TransmitterState.WAITING_FEEDBACK
            _logs.emit("Transmitter: Listening for acoustic ACKs / NACKs (2.0s window)...")
            listenForFeedback(listenDurationMs = 2000)

            // --- Challenge 2: Transition into ANNOUNCEMENT / BEACON Mode ---
            // "The Sender UI must NOT require the sender to press Broadcast again."
            // "The Sender can remain in BROADCAST READY / ACTIVE GROUP while periodically advertising the latest message"
            _state.value = TransmitterState.ANNOUNCING_BEACON
            _logs.emit("Transmitter: Entering Dynamic Group Announcement Beacon mode (Challenge 2)")
            startAnnouncementBeacon()
        }
    }

    /**
     * Starts the periodic acoustic announcement beacon for Surprise Challenge 2 (Dynamic Group).
     */
    private fun startAnnouncementBeacon() {
        beaconJob?.cancel()
        beaconJob = scope.launch {
            while (isActive) {
                if (_state.value == TransmitterState.ANNOUNCING_BEACON) {
                    val latest = latestMessageStore.getLatestMessage()
                    if (latest != null) {
                        _logs.emit("Beacon: Broadcasting acoustic announcement (V#${latest.version}, ${latest.totalPackets} pkts)")
                        val announcementPacket = Packet.createAnnouncement(
                            messageId = latest.messageId,
                            version = latest.version,
                            totalPackets = latest.totalPackets
                        )
                        val pcm = encoder.modulate(announcementPacket)
                        audioPlayer.playSamples(pcm)
                    }

                    // Listen for incoming SYNC_REQUEST or NACK between beacons
                    listenForFeedback(listenDurationMs = 3500)
                }
                delay(1500)
            }
        }
    }

    /**
     * Listens for acoustic feedback bursts (ACK, NACK, or SYNC_REQUEST).
     */
    private suspend fun listenForFeedback(listenDurationMs: Long) {
        val decoder = FSKDecoder(
            onPacketDecoded = { packet ->
                handleIncomingFeedbackPacket(packet)
            }
        )

        audioRecorder.start { chunk, count ->
            decoder.processSamples(chunk, 0, count)
        }

        delay(listenDurationMs)
        audioRecorder.stop()
    }

    /**
     * Evaluates feedback packet decoded during listening window.
     */
    private fun handleIncomingFeedbackPacket(packet: Packet) {
        scope.launch {
            when (packet.type) {
                PacketType.ACK_CONFIRM -> {
                    _acksHeard.value += 1
                    _logs.emit("Transmitter: Acoustic ACK detected ✓ (Total ACKs: ${_acksHeard.value})")
                }

                PacketType.NACK_REPAIR -> {
                    // Surprise Challenge 1: Selective Retransmission
                    val missing = packet.parseMissingIndices()
                    _logs.emit("Transmitter: Acoustic NACK detected requesting missing packets: $missing")
                    retransmitPackets(missing, reason = "NACK Repair (Challenge 1)")
                }

                PacketType.SYNC_REQUEST -> {
                    // Surprise Challenge 2: Dynamic Group Late-Join Synchronization
                    _syncRequestsHeard.value += 1
                    val missing = packet.parseMissingIndices()
                    _logs.emit("Transmitter: Acoustic SYNC_REQUEST detected from new late-joining receiver! (Reqs: ${_syncRequestsHeard.value})")

                    // Re-broadcast required packets for the new receiver
                    val targets = if (missing.isNotEmpty()) missing else cachedDataPackets.indices.toList()
                    retransmitPackets(targets, reason = "Dynamic Group Sync (Challenge 2)")
                    _recoveredReceivers.value += 1
                }

                else -> {}
            }
        }
    }

    /**
     * Retransmits a specific subset of packets out-of-turn.
     */
    private suspend fun retransmitPackets(packetIndices: List<Int>, reason: String) {
        val prevState = _state.value
        _state.value = TransmitterState.REPAIRING_NACK
        _logs.emit("Transmitter: Retransmitting [${packetIndices.joinToString(",") { "P$it" }}] for $reason")

        for (idx in packetIndices) {
            val targetPacket = cachedDataPackets.getOrNull(idx) ?: continue
            val pcm = encoder.modulate(targetPacket)
            audioPlayer.playSamples(pcm)
            delay(60)
        }

        _state.value = prevState
    }

    /**
     * Manually triggers retransmission of a single specific packet (for demo control).
     */
    fun retransmitSpecificPacket(packetId: Int) {
        scope.launch {
            retransmitPackets(listOf(packetId), reason = "Manual UI Retransmit")
        }
    }

    /**
     * Stops all transmitter operations and releases hardware.
     */
    fun stop() {
        broadcastJob?.cancel()
        beaconJob?.cancel()
        audioRecorder.stop()
        _state.value = TransmitterState.IDLE
    }
}
