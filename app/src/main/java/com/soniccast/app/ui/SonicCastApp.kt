package com.soniccast.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soniccast.app.ui.screens.*
import com.soniccast.app.ui.theme.*
import com.soniccast.app.ui.viewmodel.DebugViewModel
import com.soniccast.app.ui.viewmodel.ReceiverViewModel
import com.soniccast.app.ui.viewmodel.SenderViewModel

enum class SonicCastTab(val label: String, val icon: ImageVector) {
    TRANSMITTER("Transmitter", Icons.Default.VolumeUp),
    RECEIVER("Receiver", Icons.Default.Hearing),
    DIAGNOSTICS("Diagnostics", Icons.Default.BugReport),
    GUIDE("Judge Guide", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonicCastApp(
    senderViewModel: SenderViewModel = viewModel(),
    receiverViewModel: ReceiverViewModel = viewModel(),
    debugViewModel: DebugViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(SonicCastTab.RECEIVER) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SONICCAST",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp,
                            color = CyanPrimary
                        )
                        Text(
                            text = "Acoustic One-to-Many • PS02 + Challenge 1 & 2",
                            fontSize = 10.sp,
                            color = MutedText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = LightText
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 0.dp
            ) {
                SonicCastTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkBackground,
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary,
                            unselectedIconColor = MutedText,
                            unselectedTextColor = MutedText
                        )
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                SonicCastTab.TRANSMITTER -> SenderScreen(senderViewModel)
                SonicCastTab.RECEIVER -> ReceiverScreen(receiverViewModel)
                SonicCastTab.DIAGNOSTICS -> DebugScreen(debugViewModel)
                SonicCastTab.GUIDE -> HowItWorksScreen()
            }
        }
    }
}
