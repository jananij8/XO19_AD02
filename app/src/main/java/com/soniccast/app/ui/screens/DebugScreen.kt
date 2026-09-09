package com.soniccast.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccast.app.ui.theme.*
import com.soniccast.app.ui.viewmodel.DebugViewModel

@Composable
fun DebugScreen(viewModel: DebugViewModel) {
    val isRunning by viewModel.isRunning.collectAsState()
    val results by viewModel.results.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                        text = "In-Memory DSP & Protocol Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyanPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Executes hardware-independent DSP simulations in RAM: CRC-8 checks, Goertzel filters, Bell 202 CPFSK round-trip, XOR Parity self-healing, and Dynamic Group beacon verification.",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.runAllDiagnostics() },
                        enabled = !isRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = DarkBackground),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                color = DarkBackground,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running In-Silico DSP Tests...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Full DSP Diagnostic Suite", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (results.isEmpty() && !isRunning) {
            item {
                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GridBorder, RoundedCornerShape(12.dp))
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tap 'Run Full DSP Diagnostic Suite' to verify all algorithms.",
                            fontSize = 12.sp,
                            color = MutedText
                        )
                    }
                }
            }
        } else {
            items(results) { res ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (res.passed) AccentGreen.copy(alpha = 0.5f) else AccentRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (res.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (res.passed) AccentGreen else AccentRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = res.testName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = LightText
                                )
                            }
                            Text(
                                text = "${res.executionTimeMs} ms",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyanPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = res.details,
                            fontSize = 11.sp,
                            color = MutedText,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
