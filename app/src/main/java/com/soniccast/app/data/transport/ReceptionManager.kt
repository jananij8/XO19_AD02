package com.soniccast.app.data.transport

import com.soniccast.app.data.audio.AudioPlayer
import com.soniccast.app.data.audio.AudioRecorder
import com.soniccast.app.data.audio.FSKDecoder
import com.soniccast.app.data.audio.FSKEncoder
import com.soniccast.app.data.audio.NoiseEstimator
import com.soniccast.app.data.protocol.Packet
import com.soniccast.app.data.protocol.PacketType
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
 * State of Dynamic Group Late-Join Synchronization (Surprise Challenge 2).
 */
data class DynamicGroupState(
    val groupDiscovery: String = "● Listening for Beacon",
    val latestMessageStatus: String = "Searching...",
    val version: Int = 0,
    val syncStatus: String = "IDLE", // IDLE, SYNCING..., SYNC COMPLETE ✓
    val packetsReceived: Int = 0,
    val totalPackets: Int = 0,
    val isCrcVerified: Boolean = false,
    val latestMessageText: String = ""
)

/**
 * High-level state of the Receiver.
 */
enum class ReceiverState {
    IDLE,
    CALIBRATING_NOISE,
    LISTENING,
    RECEIVING_PACKETS,
    RECOVERING_NACK,
    SYNCING_DYNAMIC_GROUP,
    COMPLETE
}

/**
 * Coordinates acoustic reception, ambient noise calibration,
 * Surprise Challenge 1 ARQ partial loss recovery,
 * and Surprise Challenge 2 Dynamic Group late-join synchronization.
 */
class ReceptionManager(
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    private val latestMessageStore: LatestMessageStore,
    private val encoder: FSKEncoder = FSKEncoder()
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _receiverState = MutableStateFlow(ReceiverState.IDLE)
    val receiverState: StateFlow<ReceiverState> = _receiverState.asStateFlow()

    private val _receptionState = MutableStateFlow<ReceptionState?>(null)
    val receptionState: StateFlow<ReceptionState?> = _receptionState.asStateFlow()

    private val _dynamicGroupState = MutableStateFlow(DynamicGroupState())
    val dynamicGroupState: StateFlow<DynamicGroupState> = _dynamicGroupState.asStateFlow()

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    // Surprise Challenge 1 Demo Switch: Drop Packet #1 intentionally to show recovery
    var simulatePacketLoss: Boolean = false

    private val recoveryManager = RecoveryManager()
    private val noiseEstimator = NoiseEstimator()
    private var decoder: FSKDecoder? = null

    private var listeningJob: Job? = null

    /**
     * Starts continuous passive acoustic listening with automated 1.0s noise calibration.
     */
    fun startListening() {
        listeningJob?.cancel()
        recoveryManager.reset()

        listeningJob = scope.launch {
            _receiverState.value = ReceiverState.CALIBRATING_NOISE
            _isCalibrating.value = true
            _logs.emit("Receiver: Calibrating ambient acoustic noise floor (1.0s)...")

            noiseEstimator.reset()

            // Initialize decoder with adaptive squelch threshold
            decoder = FSKDecoder(
                squelchThreshold = noiseEstimator.adaptiveThreshold.value,
                onPacketDecoded = { packet ->
                    handleDecodedPacket(packet)
                },
                onLogMessage = { log ->
                    scope.launch { _logs.emit(log) }
                }
            )

            audioRecorder.start { chunk, count ->
                if (!noiseEstimator.isCalibrated.value) {
                    noiseEstimator.addCalibrationSamples(chunk, count)
                    if (noiseEstimator.isCalibrated.value) {
                        _isCalibrating.value = false
                        decoder?.squelchThreshold = noiseEstimator.adaptiveThreshold.value
                        _receiverState.value = ReceiverState.LISTENING
                        scope.launch {
                            _logs.emit("Receiver: Calibrated! Baseline RMS=${String.format("%.4f", noiseEstimator.baselineRms.value)}, Squelch=${String.format("%.4f", noiseEstimator.adaptiveThreshold.value)}")
                        }
                    }
                } else {
                    decoder?.processSamples(chunk, 0, count)
                }

                val rms = com.soniccast.app.data.audio.GoertzelDetector.computeRms(chunk, 0, count)
                _audioRms.value = (rms * 10f).coerceIn(0.0, 1.0).toFloat()
            }
        }
    }

    /**
     * Handles incoming verified packet from FSKDecoder.
     */
    private fun handleDecodedPacket(packet: Packet) {
        scope.launch {
            when (packet.type) {
                PacketType.DATA, PacketType.PARITY_XOR -> {
                    handleDataOrParityPacket(packet)
                }

                PacketType.ANNOUNCEMENT -> {
                    // Surprise Challenge 2: Dynamic Group Late-Join Discovery
                    handleAnnouncementBeacon(packet)
                }

                else -> {}
            }
        }
    }

    private suspend fun handleDataOrParityPacket(packet: Packet) {
        _receiverState.value = ReceiverState.RECEIVING_PACKETS

        val updated = recoveryManager.onPacketReceived(packet, isSimulatedLoss = simulatePacketLoss)
        _receptionState.value = updated

        // Check if dynamic group sync is in progress
        if (_dynamicGroupState.value.syncStatus == "SYNCING...") {
            _dynamicGroupState.value = _dynamicGroupState.value.copy(
                packetsReceived = updated.receivedCount,
                totalPackets = updated.totalPackets,
                isCrcVerified = true
            )
        }

        if (updated.isComplete) {
            _receiverState.value = ReceiverState.COMPLETE
            _logs.emit("Receiver: 100% Message reconstructed and CRC-8 verified ✓")
            if (updated.isRepairedViaParity) {
                _logs.emit("Receiver: Self-Healed via XOR Parity FEC ⚡ (Surprise Challenge 1)")
            }

            // Save to local store
            if (updated.reconstructedText != null) {
                latestMessageStore.saveLatestMessage(
                    messageId = updated.messageId,
                    version = updated.version,
                    totalPackets = updated.totalPackets,
                    text = updated.reconstructedText,
                    packets = emptyList()
                )

                // Update Challenge 2 status
                _dynamicGroupState.value = _dynamicGroupState.value.copy(
                    syncStatus = "SYNC COMPLETE ✓",
                    latestMessageStatus = "Received ✓",
                    isCrcVerified = true,
                    latestMessageText = updated.reconstructedText
                )
            }

            // Emit acoustic ACK confirmation chirp
            emitAcousticAck()
        } else if (updated.missingIndices.isNotEmpty() && !updated.isRepairedViaParity) {
            // Gaps remain -> Emit acoustic NACK
            requestNackRepair(updated.messageId, updated.version, updated.missingIndices)
        }
    }

    /**
     * Handles periodic acoustic announcement beacon (Surprise Challenge 2).
     */
    private suspend fun handleAnnouncementBeacon(beacon: Packet) {
        val version = beacon.version
        val total = beacon.totalPackets

        _logs.emit("Receiver: Detected acoustic Announcement Beacon (Version #V$version, $total pkts)")

        val hasCurrent = latestMessageStore.hasVersion(version)
        if (hasCurrent) {
            _dynamicGroupState.value = _dynamicGroupState.value.copy(
                groupDiscovery = "● Synchronized",
                latestMessageStatus = "Up to Date (V#$version)",
                version = version,
                totalPackets = total,
                isCrcVerified = true
            )
            return
        }

        // Newly joined receiver! Automatic synchronization flow:
        // ANNOUNCEMENT_DETECTED -> CHECK_LATEST_VERSION -> SYNC_REQUIRED -> REQUESTING_PACKETS -> SYNCING
        _receiverState.value = ReceiverState.SYNCING_DYNAMIC_GROUP
        _dynamicGroupState.value = DynamicGroupState(
            groupDiscovery = "● Beacon Detected",
            latestMessageStatus = "New Version Detected (#V$version)",
            version = version,
            syncStatus = "SYNCING...",
            packetsReceived = 0,
            totalPackets = total,
            isCrcVerified = false
        )

        _logs.emit("Receiver: Late-join detected! Automatically initiating acoustic sync for #V$version (Challenge 2)")

        // Emit acoustic SYNC_REQUEST
        emitAcousticSyncRequest(beacon.messageId, version)
    }

    /**
     * Emits acoustic SYNC_REQUEST burst to notify sender of late join.
     */
    private suspend fun emitAcousticSyncRequest(messageId: Int, version: Int) {
        // Acoustic CSMA jitter backoff (100ms - 300ms)
        delay((100L..300L).random())

        audioRecorder.stop()
        val syncReq = Packet.createSyncRequest(messageId, version)
        val pcm = encoder.modulate(syncReq)
        audioPlayer.playSamples(pcm)

        // Resume listening immediately to capture retransmitted packets
        restartListeningInternal()
    }

    /**
     * Emits acoustic NACK burst specifying lost packet indices (Surprise Challenge 1).
     */
    private suspend fun requestNackRepair(messageId: Int, version: Int, missing: List<Int>) {
        _receiverState.value = ReceiverState.RECOVERING_NACK
        _logs.emit("Receiver: Emitting acoustic NACK for missing packets: $missing")

        delay((100L..300L).random())
        audioRecorder.stop()

        val nackPcm = encoder.modulateNackBurst(messageId, version, missing)
        audioPlayer.playSamples(nackPcm)

        restartListeningInternal()
    }

    /**
     * Emits acoustic ACK confirmation chirp.
     */
    private suspend fun emitAcousticAck() {
        delay((100L..250L).random())
        audioRecorder.stop()

        val ackPcm = encoder.modulateAckChirp()
        audioPlayer.playSamples(ackPcm)

        restartListeningInternal()
    }

    private fun restartListeningInternal() {
        audioRecorder.start { chunk, count ->
            decoder?.processSamples(chunk, 0, count)
            val rms = com.soniccast.app.data.audio.GoertzelDetector.computeRms(chunk, 0, count)
            _audioRms.value = (rms * 10f).coerceIn(0.0, 1.0).toFloat()
        }
    }

    /**
     * Stops receiver and releases hardware resources.
     */
    fun stop() {
        listeningJob?.cancel()
        audioRecorder.stop()
        _receiverState.value = ReceiverState.IDLE
    }
}
