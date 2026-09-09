package com.soniccast.app

import com.soniccast.app.data.audio.FSKEncoder
import com.soniccast.app.data.audio.GoertzelDetector
import com.soniccast.app.data.protocol.Packet
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FSKModulationTest {

    @Test
    fun testGoertzelFrequencySelectivityBell202() {
        val sampleRate = 44100
        val spaceFreq = 1200.0
        val markFreq = 2200.0
        val blockSize = 1102 // 25ms at 44.1 kHz

        val spaceDetector = GoertzelDetector(sampleRate, spaceFreq, blockSize)
        val markDetector = GoertzelDetector(sampleRate, markFreq, blockSize)

        // Synthesize pure Space tone (1200 Hz)
        val spaceSignal = ShortArray(blockSize) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2.0 * PI * spaceFreq * t) * 25000.0).toInt().toShort()
        }

        // Synthesize pure Mark tone (2200 Hz)
        val markSignal = ShortArray(blockSize) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2.0 * PI * markFreq * t) * 25000.0).toInt().toShort()
        }

        val pSpaceInSpace = spaceDetector.computePowerFromShorts(spaceSignal)
        val pMarkInSpace = markDetector.computePowerFromShorts(spaceSignal)
        assertTrue("Space detector must measure high power on Space tone", pSpaceInSpace > 0.05)
        assertTrue("Space selectivity ratio must exceed 10x rejection of Mark", pSpaceInSpace > pMarkInSpace * 10.0)

        val pMarkInMark = markDetector.computePowerFromShorts(markSignal)
        val pSpaceInMark = spaceDetector.computePowerFromShorts(markSignal)
        assertTrue("Mark detector must measure high power on Mark tone", pMarkInMark > 0.05)
        assertTrue("Mark selectivity ratio must exceed 10x rejection of Space", pMarkInMark > pSpaceInMark * 10.0)
    }

    @Test
    fun testEncoderProducesValidPcm() {
        val encoder = FSKEncoder()
        val testPacket = Packet.createData(1, 1, 0, 1, "AcousticData".toByteArray())
        val pcm = encoder.modulate(testPacket)

        assertTrue("PCM audio buffer must not be empty", pcm.isNotEmpty())
        val rms = GoertzelDetector.computeRms(pcm)
        assertTrue("Modulated signal RMS must be audible/detectable (> 0.1)", rms > 0.1)
    }

    @Test
    fun testAckChirpGeneration() {
        val encoder = FSKEncoder()
        val ackChirp = encoder.modulateAckChirp()

        assertTrue("ACK chirp must have audio samples", ackChirp.isNotEmpty())
        val rms = GoertzelDetector.computeRms(ackChirp)
        assertTrue("ACK chirp RMS must be significant", rms > 0.1)
    }
}
