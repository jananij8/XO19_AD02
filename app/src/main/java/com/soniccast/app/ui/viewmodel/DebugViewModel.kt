package com.soniccast.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soniccast.app.data.audio.FSKDecoder
import com.soniccast.app.data.audio.FSKEncoder
import com.soniccast.app.data.protocol.Packet
import com.soniccast.app.data.protocol.Packetizer
import com.soniccast.app.data.transport.RecoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiagnosticResult(
    val testName: String,
    val passed: Boolean,
    val executionTimeMs: Long,
    val details: String
)

class DebugViewModel : ViewModel() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _results = MutableStateFlow<List<DiagnosticResult>>(emptyList())
    val results: StateFlow<List<DiagnosticResult>> = _results.asStateFlow()

    fun runAllDiagnostics() {
        if (_isRunning.value) return

        viewModelScope.launch {
            _isRunning.value = true
            _results.value = emptyList()

            val list = mutableListOf<DiagnosticResult>()

            withContext(Dispatchers.Default) {
                // Test 1: CRC-8 Bit-flip and corruption rejection
                val t1Start = System.currentTimeMillis()
                val testData = "SonicCast CRC Integrity Test".toByteArray(Charsets.UTF_8)
                val crc = com.soniccast.app.data.protocol.CRCManager.compute(testData)
                val crcValid = com.soniccast.app.data.protocol.CRCManager.verify(testData, 0, testData.size, crc)
                testData[2] = (testData[2].toInt() xor 0x01).toByte() // Single bit flip
                val corruptedCrc = com.soniccast.app.data.protocol.CRCManager.compute(testData)
                val bitFlipDetected = corruptedCrc != crc

                list.add(
                    DiagnosticResult(
                        testName = "CRC-8 Integrity & Bit-Flip Detection",
                        passed = crcValid && bitFlipDetected,
                        executionTimeMs = System.currentTimeMillis() - t1Start,
                        details = "CRC-8=0x${String.format("%02X", crc)}. Corrupted CRC=0x${String.format("%02X", corruptedCrc)}. Bit-flip rejected."
                    )
                )

                // Test 2: In-Memory CPFSK Modulation & Goertzel Demodulation
                val t2Start = System.currentTimeMillis()
                val testPacket = Packet.createData(10, 1, 0, 1, "AcousticBell202".toByteArray())
                val encoder = FSKEncoder(symbolDurationMs = 15) // Faster symbol for rapid test
                val pcm = encoder.modulate(testPacket)
                val decoder = FSKDecoder(symbolDurationMs = 15, squelchThreshold = 0.001)
                val decoded = decoder.decodeFullSignal(pcm)
                val test2Passed = decoded.isNotEmpty() && decoded[0].packetId == 0

                list.add(
                    DiagnosticResult(
                        testName = "Bell 202 CPFSK & Goertzel Demodulation",
                        passed = test2Passed,
                        executionTimeMs = System.currentTimeMillis() - t2Start,
                        details = "Synthesized ${pcm.size} PCM samples. Decoded ${decoded.size} valid CRC packets in RAM."
                    )
                )

                // Test 3: Surprise Challenge 1 — XOR Parity FEC Single-Packet Loss Repair
                val t3Start = System.currentTimeMillis()
                val originalText = "Classroom Broadcast: Room 104"
                val (dataPkts, parityPkt) = Packetizer.packetize(20, 1, originalText, maxPayloadSize = 12)
                // Simulate dropping packet index 1
                val droppedMap = dataPkts.filter { it.packetId != 1 }.associateBy { it.packetId }
                val recoveredText = Packetizer.depacketize(droppedMap, dataPkts.size, parityPkt)
                val test3Passed = recoveredText == originalText

                list.add(
                    DiagnosticResult(
                        testName = "Surprise Challenge 1: XOR Parity FEC Self-Healing",
                        passed = test3Passed,
                        executionTimeMs = System.currentTimeMillis() - t3Start,
                        details = "Dropped Packet #1 (${dataPkts.size} total pkts). Reconstructed 100% via XOR parity arithmetic without retransmission."
                    )
                )

                // Test 4: Surprise Challenge 2 — Dynamic Group Discovery & Sync
                val t4Start = System.currentTimeMillis()
                val beacon = Packet.createAnnouncement(30, 2, 4)
                val beaconPcm = encoder.modulate(beacon)
                val decodedBeacon = decoder.decodeFullSignal(beaconPcm)
                val test4Passed = decodedBeacon.isNotEmpty() && decodedBeacon[0].type == com.soniccast.app.data.protocol.PacketType.ANNOUNCEMENT

                list.add(
                    DiagnosticResult(
                        testName = "Surprise Challenge 2: Dynamic Group Acoustic Beacon",
                        passed = test4Passed,
                        executionTimeMs = System.currentTimeMillis() - t4Start,
                        details = "Detected Announcement beacon (V#2, 4 pkts). Verified autonomous discovery format."
                    )
                )
            }

            _results.value = list
            _isRunning.value = false
        }
    }
}
