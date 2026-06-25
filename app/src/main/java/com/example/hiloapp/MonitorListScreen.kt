package com.example.hiloapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorListScreen(chats: List<Chat>, onToggleMonitor: (Chat, Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(Color.White)) {
        Surface(
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text("Administrar Monitoreo", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "${chats.count { it.isMonitored }} chats monitoreados",
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
        if (chats.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
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
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                itemsIndexed(chats) { index, chat ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 40L)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                            initialOffsetY = { 24 },
                            animationSpec = tween(300, easing = EaseOutCubic)
                        )
                    ) {
                        MonitorItem(chat = chat, onToggle = { isChecked -> onToggleMonitor(chat, isChecked) })
                    }
                    if (index < chats.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                            thickness = 0.5.dp,
                            color = Color(0xFFEFECE6).copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonitorItem(chat: Chat, onToggle: (Boolean) -> Unit) {
    var isChecked by remember(chat.isMonitored) { mutableStateOf(chat.isMonitored) }
    val isUpdating by remember { mutableStateOf(false) }
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
                        isChecked = true
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
                    isChecked = false
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
}
