package com.example.hiloapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorListScreen(
    chats: List<Chat>,
    onToggleMonitor: (Chat, Boolean) -> Unit,
    onToggleAiAutoReply: (Chat, Boolean) -> Unit = { _, _ -> },
    onSyncHistory: (Chat, onDone: (Boolean) -> Unit) -> Unit = { _, cb -> cb(false) },
    isPartialList: Boolean = false,
    openWaHealthMessage: String? = null,
    isLoading: Boolean = false,
    isRestartingServices: Boolean = false,
    onRetryLoadChats: () -> Unit = {},
    onRestartServices: ((onDone: (Boolean, String?) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0=Monitoreados, 1=Disponibles
    val monitoredChats = remember(chats) { chats.filter { it.isMonitored } }
    val availableChats = remember(chats) { chats.filter { !it.isMonitored } }
    val listState = rememberLazyListState()

    // Auto-scroll to top whenever the user switches between Monitoreados / Disponibles
    LaunchedEffect(selectedTab) {
        listState.animateScrollToItem(0)
    }

    val pageBg = Color(0xFFF7F8FA)
    val cardBg = Color.White
    val dividerColor = Color(0xFFE8EBEF)
    val accent = Color(0xFF10B981)

    Column(modifier = modifier.fillMaxSize().background(pageBg)) {
        Surface(
            color = cardBg,
            shadowElevation = 2.dp
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text("Administrar Monitoreo", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "${chats.count { it.isMonitored }} chats monitoreados",
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onRestartServices?.invoke { _, _ -> } },
                        enabled = !isRestartingServices && onRestartServices != null
                    ) {
                        if (isRestartingServices) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF10B981)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reiniciar servicios OpenWA",
                                tint = Color(0xFF10B981)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cardBg
                )
            )
        }

        // OpenWA health banner
        if (!openWaHealthMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFFFFE6E6), shape = RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "OpenWA: $openWaHealthMessage",
                    fontSize = 12.sp,
                    color = Color(0xFF8A1C1C),
                    lineHeight = 16.sp
                )
            }
        }

        // Partial list warning banner
        if (isPartialList) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFFFFF3CD), shape = RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚠️ Sincronizando conversaciones...",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFF856404)
                        )
                        Text(
                            text = "WhatsApp tardó en responder. Hilo sigue reintentando en segundo plano. Ya puedes ver ${chats.size} chats.",
                            fontSize = 12.sp,
                            color = Color(0xFF856404),
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF856404)
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                        color = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cargando chats disponibles...",
                        color = Color(0xFF8E8E93),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Esto puede tardar unos segundos",
                        color = Color(0xFF8E8E93).copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else if (chats.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                // Empty state — no chats available at all
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "⚙️", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No hay chats disponibles para configurar.",
                        color = Color(0xFF8E8E93),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        } else {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = cardBg,
                contentColor = Color.Black
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Monitoreados (${monitoredChats.size})",
                            color = if (selectedTab == 0) accent else Color(0xFF8E8E93),
                            fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Disponibles (${availableChats.size})",
                            color = if (selectedTab == 1) accent else Color(0xFF8E8E93),
                            fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(pageBg)
            ) {
                if (selectedTab == 0 && monitoredChats.isNotEmpty()) {
                    item {
                        MonitorSectionHeader(
                            title = "Chats monitoreados",
                            count = monitoredChats.size,
                            color = accent
                        )
                    }
                    itemsIndexed(
                        items = monitoredChats,
                        key = { _, chat -> chat.id }
                    ) { index, chat ->
                        MonitorListItem(
                            chat = chat,
                            onToggle = { isChecked -> onToggleMonitor(chat, isChecked) },
                            onToggleAiAutoReply = { aiAutoReply -> onToggleAiAutoReply(chat, aiAutoReply) },
                            onSyncHistory = { done -> onSyncHistory(chat, done) },
                            animateIndex = index
                        )
                        if (index < monitoredChats.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                                thickness = 0.5.dp,
                                color = dividerColor
                            )
                        }
                    }
                }

                if (selectedTab == 1 && availableChats.isNotEmpty()) {
                    item {
                        MonitorSectionHeader(
                            title = "Chats disponibles",
                            subtitle = "Activa el switch para comenzar a monitorear",
                            count = availableChats.size,
                            color = Color(0xFF7A7F87)
                        )
                    }
                    itemsIndexed(
                        items = availableChats,
                        key = { _, chat -> chat.id }
                    ) { index, chat ->
                        MonitorListItem(
                            chat = chat,
                            onToggle = { isChecked -> onToggleMonitor(chat, isChecked) },
                            onToggleAiAutoReply = { aiAutoReply -> onToggleAiAutoReply(chat, aiAutoReply) },
                            onSyncHistory = { done -> onSyncHistory(chat, done) },
                            animateIndex = index
                        )
                        if (index < availableChats.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                                thickness = 0.5.dp,
                                color = dividerColor
                            )
                        }
                    }
                }

                if (selectedTab == 0 && monitoredChats.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No tienes chats monitoreados todavía.",
                                color = Color(0xFF8E8E93),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                if (selectedTab == 1 && availableChats.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hay chats disponibles por monitorear.",
                                color = Color(0xFF8E8E93),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonitorSectionHeader(
    title: String,
    subtitle: String? = null,
    count: Int,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF3F5F8))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.Black
            )
            Box(
                modifier = Modifier
                    .background(color.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF7A7F87)
            )
        }
    }
}

@Composable
fun MonitorListItem(
    chat: Chat,
    onToggle: (Boolean) -> Unit,
    onToggleAiAutoReply: (Boolean) -> Unit = {},
    onSyncHistory: (onDone: (Boolean) -> Unit) -> Unit = {},
    animateIndex: Int = 0
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(animateIndex * 40L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
            initialOffsetY = { 24 },
            animationSpec = tween(300, easing = EaseOutCubic)
        )
    ) {
        MonitorItem(
            chat = chat,
            onToggle = onToggle,
            onToggleAiAutoReply = onToggleAiAutoReply,
            onSyncHistory = onSyncHistory
        )
    }
}

@Composable
fun MonitorItem(
    chat: Chat,
    onToggle: (Boolean) -> Unit,
    onToggleAiAutoReply: (Boolean) -> Unit = {},
    onSyncHistory: (onDone: (Boolean) -> Unit) -> Unit = {}
) {
    val isChecked = chat.isMonitored
    val aiAutoReply = chat.aiAutoReply
    var isSyncing by remember { mutableStateOf(false) }
    val isUpdating = false
    var showConsentDialog by remember { mutableStateOf(false) }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = {
                Text(
                    text = "📋 Activar Monitoreo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            },
            text = {
                Text(
                    text = "Estás a punto de activar el monitoreo para el chat \"${chat.contactName}\".\n\n" +
                            "Al activar esta opción, aceptas nuestras políticas de uso y privacidad. " +
                            "Hilo comenzará a guardar y analizar los mensajes de esta conversación con IA. " +
                            "Asimismo, se enviará una notificación al chat para avisar del monitoreo de acuerdo con la regulación vigente.",
                    fontSize = 14.sp,
                    color = Color.Black,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConsentDialog = false
                        onToggle(true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Aceptar y Monitorear", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConsentDialog = false
                    }
                ) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarGradient = Brush.linearGradient(
                colors = listOf(Color(0xFF10B981), Color(0xFF059669))
            )

            // Avatar with ring
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFECE6).copy(alpha = 0.5f))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(brush = avatarGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chat.contactName.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
                // Monitored indicator dot
                if (isChecked) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.contactName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                    letterSpacing = (-0.2).sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isChecked) {
                        Text(
                            text = "✨ ",
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = if (isChecked) "IA analizando este chat" else "No monitoreado",
                        color = if (isChecked) Color(0xFF10B981) else Color(0xFF6E6E73),
                        fontSize = 14.sp,
                        fontWeight = if (isChecked) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }

            Switch(
                checked = isChecked,
                onCheckedChange = { newValue ->
                    if (newValue) {
                        showConsentDialog = true
                    } else {
                        onToggle(false)
                    }
                },
                enabled = !isUpdating,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF10B981),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE5E5EA)
                )
            )
        }

        // AI auto-reply + sync history row — only visible when monitored
        AnimatedVisibility(
            visible = isChecked,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 84.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Hilo Auto-respuesta",
                    fontSize = 13.sp,
                    color = Color(0xFF3C3C43),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = aiAutoReply,
                    onCheckedChange = { newValue ->
                        onToggleAiAutoReply(newValue)
                    },
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE5E5EA)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Sync history icon button
                IconButton(
                    onClick = {
                        if (!isSyncing) {
                            isSyncing = true
                            onSyncHistory { isSyncing = false }
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF8E8E93)
                        )
                    } else {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Sincronizar historial",
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
