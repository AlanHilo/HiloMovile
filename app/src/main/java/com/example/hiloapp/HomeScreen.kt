package com.example.hiloapp

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.NotificationsActive
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
    chats: List<Chat>,
    allChats: List<Chat>,
    isLoading: Boolean = false,
    isLoadingAllChats: Boolean = false,
    aiChatViewModel: AiChatViewModel,
    alertsViewModel: AlertsViewModel,
    summariesViewModel: SummariesViewModel,
    onChatClick: (Chat) -> Unit,
    onToggleMonitor: (Chat, Boolean) -> Unit,
    onToggleAiAutoReply: (Chat, Boolean) -> Unit = { _, _ -> },
    onSyncHistory: (Chat, (Boolean) -> Unit) -> Unit = { _, cb -> cb(false) },
    isPartialList: Boolean = false,
    openWaHealthMessage: String? = null,
    isRestartingServices: Boolean = false,
    onRetryLoadChats: () -> Unit = {},
    onRestartServices: ((onDone: (Boolean, String?) -> Unit) -> Unit)? = null,
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
                            BadgedBox(
                                badge = {
                                    if (chats.isNotEmpty()) {
                                        Badge(
                                            containerColor = Color(0xFF10B981),
                                            contentColor = Color.White
                                        ) {
                                            val countLabel = if (chats.size > 99) "99+" else chats.size.toString()
                                            Text(countLabel, fontSize = 10.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    if (selectedTabIndex == 1) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Monitor"
                                )
                            }
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
                    NavigationBarItem(
                        icon = {
                            Icon(
                                Icons.Filled.NotificationsActive,
                                contentDescription = "Alertas"
                            )
                        },
                        label = {
                            Text(
                                "Alertas",
                                fontWeight = if (selectedTabIndex == 3) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        selected = selectedTabIndex == 3,
                        onClick = { selectedTabIndex = 3 },
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
                                Icons.Filled.Article,
                                contentDescription = "Resúmenes"
                            )
                        },
                        label = {
                            Text(
                                "Resumen",
                                fontWeight = if (selectedTabIndex == 4) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        selected = selectedTabIndex == 4,
                        onClick = { selectedTabIndex = 4 },
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
                    MonitorListScreen(
                        chats = allChats,
                        onToggleMonitor = onToggleMonitor,
                        onToggleAiAutoReply = onToggleAiAutoReply,
                        onSyncHistory = onSyncHistory,
                        isPartialList = isPartialList,
                        openWaHealthMessage = openWaHealthMessage,
                        isLoading = isLoadingAllChats,
                        isRestartingServices = isRestartingServices,
                        onRetryLoadChats = onRetryLoadChats,
                        onRestartServices = onRestartServices,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                2 -> {
                    LaunchedEffect(Unit) {
                        aiChatViewModel.loadHistory()
                    }
                    AiChatScreen(
                        aiChatViewModel = aiChatViewModel,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                3 -> {
                    AlertsScreen(
                        alertsViewModel = alertsViewModel,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                4 -> {
                    LaunchedEffect(allChats) {
                        summariesViewModel.loadSummaries(allChats.filter { it.isMonitored })
                    }
                    SummariesScreen(
                        summariesViewModel = summariesViewModel,
                        monitoredChats = allChats,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }
}