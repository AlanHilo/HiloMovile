package com.example.hiloapp

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    chats: List<Chat>, // Monitored chats only
    allChats: List<Chat>, // All chats from WhatsApp
    isLoading: Boolean = false,
    aiChatViewModel: AiChatViewModel,
    onChatClick: (Chat) -> Unit,
    onToggleMonitor: (Chat, Boolean) -> Unit,
    onLoadAllChats: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                NavigationBar(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (selectedTabIndex == 0) Icons.Filled.Chat else Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "Chats"
                            )
                        },
                        label = {
                            Text(
                                "Chats",
                                fontWeight = if (selectedTabIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF10B981),
                            selectedTextColor = Color(0xFF10B981),
                            unselectedIconColor = Color(0xFF8E8E93),
                            unselectedTextColor = Color(0xFF8E8E93),
                            indicatorColor = Color(0xFFE6F4EA)
                        )
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (selectedTabIndex == 1) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "Monitor"
                            )
                        },
                        label = {
                            Text(
                                "Monitor",
                                fontWeight = if (selectedTabIndex == 1) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF10B981),
                            selectedTextColor = Color(0xFF10B981),
                            unselectedIconColor = Color(0xFF8E8E93),
                            unselectedTextColor = Color(0xFF8E8E93),
                            indicatorColor = Color(0xFFE6F4EA)
                        )
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (selectedTabIndex == 2) Icons.Filled.SmartToy else Icons.Outlined.SmartToy,
                                contentDescription = "Hilo AI"
                            )
                        },
                        label = {
                            Text(
                                "Hilo AI",
                                fontWeight = if (selectedTabIndex == 2) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF10B981),
                            selectedTextColor = Color(0xFF10B981),
                            unselectedIconColor = Color(0xFF8E8E93),
                            unselectedTextColor = Color(0xFF8E8E93),
                            indicatorColor = Color(0xFFE6F4EA)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Crossfade(
            targetState = selectedTabIndex,
            animationSpec = tween(200),
            label = "tabCrossfade"
        ) { tabIndex ->
            when (tabIndex) {
                0 -> {
                    ChatListContent(
                        chats = chats,
                        isLoading = isLoading,
                        onChatClick = onChatClick,
                        onLogout = onLogout,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                1 -> {
                    LaunchedEffect(Unit) {
                        onLoadAllChats()
                    }
                    MonitorListScreen(
                        chats = allChats,
                        onToggleMonitor = onToggleMonitor,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                2 -> {
                    AiChatScreen(
                        aiChatViewModel = aiChatViewModel,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }
}
