package com.soniccast.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccast.app.data.transport.ReceiverState
import com.soniccast.app.ui.components.AcousticRadar
import com.soniccast.app.ui.components.PacketVisualizer
import com.soniccast.app.ui.components.StatusChipsRow
import com.soniccast.app.ui.theme.*
import com.soniccast.app.ui.viewmodel.ReceiverViewModel

@Composable
fun ReceiverScreen(viewModel: ReceiverViewModel) {
    val context = LocalContext.current
    val receiverState by viewModel.receiverState.collectAsState()
    val receptionState by viewModel.receptionState.collectAsState()
    val dynamicGroupState by viewModel.dynamicGroupState.collectAsState()
    val isCalibrating by viewModel.isCalibrating.collectAsState()
    val audioRms by viewModel.audioRms.collectAsState()
    val simulatePacketLoss by viewModel.simulatePacketLoss.collectAsState()
    val logs by viewModel.logs.collectAsState()

    val isListening = receiverState != ReceiverState.IDLE

    LaunchedEffect(Unit) {
        if (!isListening) {
            viewModel.startListening()
        }
    }

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
                    "ACOUSTIC RADAR" to CyanPrimary,
                    "PASSIVE LISTENING" to AccentGreen,
                    "CRC-8 INTEGRITY" to AccentGreen,
                    "ZERO NETWORK" to LightText
                )
            )
        }

        // Noise Floor Calibration Banner
        item {
            AnimatedVisibility(visible = isCalibrating) {
                Surface(
                    color = AccentOrange.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = AccentOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Calibrating Ambient Noise Floor (1.0s)...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentOrange
                            )
                            Text(
                                text = "Sampling background room acoustic floor to establish adaptive detection threshold.",
                                fontSize = 11.sp,
                                color = LightText.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // Passive Radar & Live Acoustic Monitor Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GridBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Acoustic Receiver Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanPrimary
                        )

                        val (statusText, statusColor) = when (receiverState) {
                            ReceiverState.IDLE -> "IDLE" to MutedText
                            ReceiverState.CALIBRATING_NOISE -> "CALIBRATING" to AccentOrange
                            ReceiverState.LISTENING -> "PASSIVE RADAR" to AccentGreen
                            ReceiverState.RECEIVING_PACKETS -> "RECEIVING STREAM" to CyanPrimary
                            ReceiverState.RECOVERING_NACK -> "NACK RECOVERY" to AccentOrange
                            ReceiverState.SYNCING_DYNAMIC_GROUP -> "DYNAMIC SYNC" to AccentOrange
                            ReceiverState.COMPLETE -> "100% VERIFIED" to AccentGreen
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Animated Acoustic Radar
                    AcousticRadar(
                        rmsEnergy = audioRms,
                        isListening = isListening
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Real-Time Acoustic RMS Energy: ${String.format("%.3f", audioRms)}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MutedText
                    )
                }
            }
        }

        // Packet Integrity Matrix
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GridBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val total = receptionState?.totalPackets ?: 4
                    val receivedCount = receptionState?.receivedCount ?: 0
                    val missing = receptionState?.missingIndices ?: emptyList()
                    val receivedSet = (0 until total).filter { it !in missing }.toSet()
                    val isRepaired = receptionState?.isRepairedViaParity == true || receptionState?.isRepairedViaNack == true

                    PacketVisualizer(
                        totalPackets = total,
                        receivedIndices = receivedSet,
                        missingIndices = missing,
                        isRepaired = isRepaired,
                        hasParity = true
                    )
                }
            }
        }

        // Surprise Challenge 1: Demo Loss Simulation Switch
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentOrange.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Simulate Partial Loss (Drop P1)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AccentOrange
                        )
                        Text(
                            text = "Drops Packet #1 once to visibly prove automatic self-healing via XOR Parity FEC & NACK to judges.",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }
                    Switch(
                        checked = simulatePacketLoss,
                        onCheckedChange = { viewModel.toggleSimulatePacketLoss(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentOrange,
                            checkedTrackColor = AccentOrange.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        // Surprise Challenge 2: Dynamic Group Late-Join Card
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

                        val syncChipColor = when (dynamicGroupState.syncStatus) {
                            "SYNC COMPLETE ✓" -> AccentGreen
                            "SYNCING..." -> AccentOrange
                            else -> MutedText
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = syncChipColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, syncChipColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = dynamicGroupState.syncStatus,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = syncChipColor,
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
                            Text("Group Discovery", fontSize = 11.sp, color = MutedText)
                            Text(
                                text = dynamicGroupState.groupDiscovery,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LightText
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Latest Message Detected", fontSize = 11.sp, color = MutedText)
                            Text(
                                text = "${dynamicGroupState.latestMessageStatus} (V#${dynamicGroupState.version})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CyanPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Autonomous sync: When entering late, receiver listens for acoustic beacon, requests missing packets, and verifies with CRC-8.",
                        fontSize = 10.sp,
                        color = MutedText
                    )
                }
            }
        }

        // Reconstructed Message Card
        item {
            val messageText = receptionState?.reconstructedText ?: dynamicGroupState.latestMessageText
            if (messageText.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AccentGreen.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MESSAGE RECEIVED & VERIFIED ✓",
                                style = MaterialTheme.typography.titleSmall,
                                color = AccentGreen
                            )
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkBackground,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = messageText,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = LightText,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Verification Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "CRC-8 100% VERIFIED",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                            }

                            if (receptionState?.isRepairedViaParity == true) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentOrange.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "SELF-HEALED VIA PARITY ⚡",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentOrange
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("SonicCast Message", messageText))
                                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = DarkBackground),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Message", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (messageText.startsWith("http://") || messageText.startsWith("https://")) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(messageText))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open URL", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Real-time Event Log Console
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
                            text = "RECEIVER EVENT LOG",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedText
                        )
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MutedText, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (logs.isEmpty()) {
                        Text("Listening for acoustic carrier wave...", fontSize = 11.sp, color = MutedText)
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
