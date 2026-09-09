package com.soniccast.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccast.app.ui.theme.*

@Composable
fun HowItWorksScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Pitch Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Executive Pitch (Read Aloud to Judges)",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "\"In crowded spaces like exam halls or emergency zones, broadcasting critical instructions often fails because Wi-Fi is down, cell towers are jammed, and pairing 30 phones via Bluetooth is impossible.\n\nSonicCast turns smartphone speakers and microphones into an autonomous acoustic network operating over Bell 202 FSK sound waves with ZERO internet, Wi-Fi, or Bluetooth. We solve Surprise Challenge 1 (Partial Reception) via per-packet CRC-8 and XOR Parity self-healing, and Surprise Challenge 2 (Dynamic Group) via periodic acoustic announcement beacons that let late-joining phones synchronize automatically without the sender restarting.\"",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = LightText
                    )
                }
            }
        }

        // Hard Constraints Matrix
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GridBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Zero-Network Hard Constraints",
                        style = MaterialTheme.typography.titleSmall,
                        color = LightText
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ConstraintRow("Internet / Mobile Data", "OMITTED (0 matches in Manifest)")
                    ConstraintRow("Wi-Fi & Wi-Fi Direct", "OMITTED (Zero Wi-Fi APIs used)")
                    ConstraintRow("Bluetooth / BLE", "OMITTED (No pairing required)")
                    ConstraintRow("Location Services / GPS", "OMITTED (100% immune to throttles)")
                    ConstraintRow("Only Permission Used", "android.permission.RECORD_AUDIO")
                }
            }
        }

        // Acoustic Layer Specs
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GridBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "2. Bell 202 Binary FSK Physical Layer",
                        style = MaterialTheme.typography.titleSmall,
                        color = CyanPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ConstraintRow("Modulation", "Continuous-Phase FSK (CPFSK)")
                    ConstraintRow("Bit 0 (Space)", "1200 Hz Tone")
                    ConstraintRow("Bit 1 (Mark)", "2200 Hz Tone (Bell 202 Split)")
                    ConstraintRow("Sample Rate", "44,100 Hz (100% Android hardware support)")
                    ConstraintRow("Symbol Rate", "40 Baud (25 ms per bit)")
                    ConstraintRow("Spectral Filter", "O(N) Goertzel power evaluation at 1200 & 2200 Hz")
                    ConstraintRow("Noise Robustness", "1.0s ambient noise calibration + adaptive squelch")
                }
            }
        }

        // Surprise Challenge 1 & 2 Explanations
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentOrange.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "3. Surprise Challenges Solved",
                        style = MaterialTheme.typography.titleSmall,
                        color = AccentOrange
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Challenge 1: Partial Reception Recovery\n" +
                                "Packets are verified individually with CRC-8. Every transmission block includes an XOR Parity FEC packet. A single dropped packet is reconstructed instantly on-device without retransmission. For multiple drops, an acoustic NACK burst requests only the lost packet IDs.\n\n" +
                                "• Challenge 2: Dynamic Group Late-Join Sync\n" +
                                "After broadcast, the sender enters background Announcement Beacon mode (every 5s). When a new phone enters the room later, it detects the beacon, notes missing version #V, and emits an acoustic SYNC_REQUEST. The sender automatically re-broadcasts packets with zero manual action.",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = LightText
                    )
                }
            }
        }
    }
}

@Composable
private fun ConstraintRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = MutedText)
        Text(text = value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = LightText)
    }
}
