package com.example.hiloapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListContent(
    chats: List<Chat>,
    isLoading: Boolean = false,
    onChatClick: (Chat) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var engineStatus by remember { mutableStateOf("ready") }
    var startingEngine by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while(true) {
            val res = HiloApi.getWhatsAppStatus()
            if (res is NetworkResult.Success) {
                engineStatus = res.data.status
            }
            delay(5000)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar Sesión", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("¿Estás seguro de que deseas cerrar sesión en tu cuenta de Hilo?", color = Color(0xFF48484A)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Cerrar Sesión", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", color = Color(0xFF8E8E93))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(modifier = modifier.fillMaxSize().background(Color.White)) {
        // Top bar with subtle elevation
        Surface(
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text("Chats", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 28.sp)
                        if (engineStatus == "ready") {
                            Text(
                                "Conectado",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Cerrar Sesión",
                            tint = Color(0xFF8E8E93)
                        )
                    }
                }
            )
        }

        // Status Bar
        if (engineStatus != "ready") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = Color(0xFFEF4444).copy(alpha = 0.1f))
                    .background(Color(0xFFFEF2F2), shape = RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Motor de WhatsApp Apagado",
                        color = Color(0xFF991B1B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            startingEngine = true
                            coroutineScope.launch {
                                val res = HiloApi.startWhatsAppEngine()
                                if (res is NetworkResult.Error) {
                                    errorMessage = res.message
                                }
                                delay(2000)
                                startingEngine = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C0C0C)),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !startingEngine,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(if (startingEngine) "Iniciando..." else "Iniciar Motor", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFFFFE6E6), shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = Color(0xFF8A1C1C),
                    fontSize = 12.sp
                )
            }
        }

        if (isLoading && chats.isEmpty()) {
            // Shimmer loading skeletons
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                items(8) {
                    ShimmerChatItem()
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "💬",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No hay chats disponibles.",
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
                    // Staggered entry animation
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
                        ChatItem(chat = chat, onClick = { onChatClick(chat) })
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
fun ShimmerChatItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar skeleton
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFECE6).copy(alpha = shimmerAlpha))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Name skeleton
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFEFECE6).copy(alpha = shimmerAlpha))
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Message skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFEFECE6).copy(alpha = shimmerAlpha * 0.7f))
            )
        }
    }
}

@Composable
fun ChatItem(chat: Chat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatarGradient = Brush.linearGradient(
            colors = listOf(Color(0xFF10B981), Color(0xFF059669))
        )

        // Avatar with outer ring
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEFECE6).copy(alpha = 0.5f))
                    .padding(2.dp)
            ) {
                if (chat.avatarUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(chat.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
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
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
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
                    )
                } else {
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
            }

            // Monitored indicator dot
            if (chat.isMonitored) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.contactName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    letterSpacing = (-0.2).sp
                )
                Text(
                    text = chat.timestamp,
                    fontSize = 12.sp,
                    color = if (chat.unreadCount > 0) Color(0xFF10B981) else Color(0xFF8E8E93),
                    fontWeight = if (chat.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.lastMessage,
                    fontSize = 14.sp,
                    color = if (chat.isMonitored) Color(0xFF10B981) else Color(0xFF6E6E73),
                    fontWeight = if (chat.isMonitored) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    lineHeight = 18.sp
                )
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(22.dp)
                            .shadow(2.dp, CircleShape, ambientColor = Color(0xFF10B981).copy(alpha = 0.3f))
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chat.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
