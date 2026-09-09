package com.soniccast.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccast.app.ui.theme.*

enum class PacketVisualStatus {
    PENDING,
    VERIFIED,
    MISSING,
    REPAIRED
}

@Composable
fun PacketVisualizer(
    totalPackets: Int,
    receivedIndices: Set<Int>,
    missingIndices: List<Int>,
    isRepaired: Boolean = false,
    hasParity: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PACKET INTEGRITY MATRIX (CRC-8)",
                style = MaterialTheme.typography.labelMedium,
                color = MutedText
            )
            Text(
                text = "${receivedIndices.size}/$totalPackets Packets",
                style = MaterialTheme.typography.labelMedium,
                color = if (receivedIndices.size == totalPackets) AccentGreen else CyanPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(totalPackets) { idx ->
                val status = when {
                    idx in missingIndices -> PacketVisualStatus.MISSING
                    isRepaired && idx !in missingIndices -> PacketVisualStatus.REPAIRED
                    idx in receivedIndices -> PacketVisualStatus.VERIFIED
                    else -> PacketVisualStatus.PENDING
                }

                PacketTile(
                    label = "P$idx",
                    status = status
                )
            }

            if (hasParity) {
                item {
                    PacketTile(
                        label = "P_XOR",
                        status = if (isRepaired) PacketVisualStatus.REPAIRED else PacketVisualStatus.VERIFIED
                    )
                }
            }
        }
    }
}

@Composable
fun PacketTile(
    label: String,
    status: PacketVisualStatus
) {
    val (bgColor, borderColor, textColor) = when (status) {
        PacketVisualStatus.PENDING -> Triple(
            DarkSurfaceVariant.copy(alpha = 0.5f),
            GridBorder,
            MutedText
        )
        PacketVisualStatus.VERIFIED -> Triple(
            AccentGreen.copy(alpha = 0.15f),
            AccentGreen,
            AccentGreen
        )
        PacketVisualStatus.MISSING -> Triple(
            AccentRed.copy(alpha = 0.2f),
            AccentRed,
            AccentRed
        )
        PacketVisualStatus.REPAIRED -> Triple(
            AccentOrange.copy(alpha = 0.2f),
            AccentOrange,
            AccentOrange
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .width(54.dp)
            .height(48.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = textColor
            )
            val subLabel = when (status) {
                PacketVisualStatus.PENDING -> "WAIT"
                PacketVisualStatus.VERIFIED -> "CRC OK"
                PacketVisualStatus.MISSING -> "DROP"
                PacketVisualStatus.REPAIRED -> "REPAIR"
            }
            Text(
                text = subLabel,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = textColor.copy(alpha = 0.8f)
            )
        }
    }
}
