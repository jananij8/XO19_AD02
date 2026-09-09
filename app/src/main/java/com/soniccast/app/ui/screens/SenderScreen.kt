package com.soniccast.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccast.app.data.transport.TransmitterState
import com.soniccast.app.ui.components.StatusChipsRow
import com.soniccast.app.ui.theme.*
import com.soniccast.app.ui.viewmodel.SenderViewModel

@Composable
fun SenderScreen(viewModel: SenderViewModel) {
    val messageText by viewModel.messageInput.collectAsState()
    val transmitterState by viewModel.transmitterState.collectAsState()
    val acksHeard by viewModel.acksHeard.collectAsState()
    val syncRequestsHeard by viewModel.syncRequestsHeard.collectAsState()
    val recoveredReceivers by viewModel.recoveredReceivers.collectAsState()
    val currentVersion by viewModel.versionNumber.collectAsState()
    val logs by viewModel.logs.collectAsState()

    val isBroadcasting = transmitterState == TransmitterState.BROADCASTING_INITIAL ||
            transmitterState == TransmitterState.REPAIRING_NACK ||
            transmitterState == TransmitterState.DYNAMIC_SYNC_RETRANSMIT

    val estimatedPackets = (messageText.toByteArray(Charsets.UTF_8).size / 16).coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status Chips Header
        item {
            StatusChipsRow(
                chips = listOf(
                    "ACOUSTIC CHANNEL (1200/2200 Hz)" to CyanPrimary,
                    "BELL 202 FSK" to CyanSecondary,
                    "CRC-8 INTEGRITY" to AccentGreen,
                    "ZERO NETWORK" to LightText
                )
            )
        }

        // Broadcast Control Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GridBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Acoustic Transmitter",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanPrimary
                        )

                        val (statusText, statusColor) = when (transmitterState) {
                            TransmitterState.IDLE -> "READY" to MutedText
                            TransmitterState.BROADCASTING_INITIAL -> "BROADCASTING" to CyanPrimary
                            TransmitterState.WAITING_FEEDBACK -> "LISTENING ACKS" to AccentOrange
                            TransmitterState.REPAIRING_NACK -> "REPAIR RETRANSMIT" to AccentOrange
                            TransmitterState.ANNOUNCING_BEACON -> "BEACON ACTIVE" to AccentGreen
                            TransmitterState.DYNAMIC_SYNC_RETRANSMIT -> "SYNC RETRANSMIT" to AccentOrange
                            TransmitterState.COMPLETED -> "COMPLETED" to AccentGreen
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = statusText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { viewModel.onMessageInputChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Broadcast Message or URL") },
                        placeholder = { Text("Enter message to broadcast acoustically...") },
                        maxLines = 3,
                        enabled = !isBroadcasting,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = GridBorder,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${messageText.length} chars • ~$estimatedPackets pkts + 1 parity",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedText
                        )
                        Text(
                            text = "Version #V$currentVersion",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { viewModel.onMessageInputChanged("Exam Hall A: Section 2 begins at 10:00 AM. Passkey: 7892") },
                            label = { Text("Exam Code", fontSize = 11.sp) },
                            enabled = !isBroadcasting
                        )
                        SuggestionChip(
                            onClick = { viewModel.onMessageInputChanged("Emergency Evacuation: Exit via Stairwell B immediately") },
                            label = { Text("Alert", fontSize = 11.sp) },
                            enabled = !isBroadcasting
                        )
                        SuggestionChip(
                            onClick = { viewModel.onMessageInputChanged("https://soniccast.local/offline-manifest.html") },
                            label = { Text("URL", fontSize = 11.sp) },
                            enabled = !isBroadcasting
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.startBroadcast() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isBroadcasting && messageText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = DarkBackground,
                            disabledContainerColor = DarkSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBroadcasting) "Broadcasting Acoustic Stream..." else "Broadcast via Sound",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Surprise Challenge 2: Dynamic Group Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DYNAMIC GROUP (Surprise Challenge 2)",
                            style = MaterialTheme.typography.titleSmall,
                            color = CyanPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentGreen.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "ACTIVE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Announcement Beacon", fontSize = 11.sp, color = MutedText)
                            Text(
                                text = if (transmitterState == TransmitterState.ANNOUNCING_BEACON) "● Broadcasting (every 5s)" else "Idle",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (transmitterState == TransmitterState.ANNOUNCING_BEACON) AccentGreen else MutedText
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Acoustic ACKs Heard", fontSize = 11.sp, color = MutedText)
                            Text(
                                text = "$acksHeard phones",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = GridBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Late-Join Sync Requests", fontSize = 11.sp, color = MutedText)
                            Text(
                                text = "$syncRequestsHeard requests",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentOrange
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Recovered Late Receivers", fontSize = 11.sp, color = MutedText)
                            Text(
                                text = "$recoveredReceivers synchronized ✓",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Zero manual resend required: Newly joined receivers automatically discover the beacon, request missing frames, and sync.",
                        fontSize = 10.sp,
                        color = MutedText
                    )
                }
            }
        }

        // Manual Retransmit Demo Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GridBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Selective Retransmission Diagnostic (Challenge 1)",
                        style = MaterialTheme.typography.labelLarge,
                        color = LightText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Demonstrate out-of-turn selective retransmission of a single dropped packet:",
                        fontSize = 11.sp,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (i in 0 until estimatedPackets.coerceAtMost(4)) {
                            OutlinedButton(
                                onClick = { viewModel.retransmitSpecificPacket(i) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Resend P$i", fontSize = 11.sp, color = CyanPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Real-Time Acoustic Log Console
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GridBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACOUSTIC EVENT LOG",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedText
                        )
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MutedText, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (logs.isEmpty()) {
                        Text("Ready. Press Broadcast to initiate acoustic sound stream.", fontSize = 11.sp, color = MutedText)
                    } else {
                        logs.takeLast(6).forEach { log ->
                            Text(
                                text = "› $log",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = LightText.copy(alpha = 0.85f),
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
