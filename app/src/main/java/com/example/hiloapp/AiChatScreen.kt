package com.example.hiloapp

import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.repeatable

data class AiMessage(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    aiChatViewModel: AiChatViewModel,
    modifier: Modifier = Modifier
) {
    val messages by aiChatViewModel.messages.collectAsState()
    val isAiTyping by aiChatViewModel.isAiTyping.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isAiTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size + (if (isAiTyping) 1 else 0) - 1)
        }
    }

    // Subtle gradient background
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF9F9FB), Color(0xFFFAF8F3).copy(alpha = 0.3f))
    )

    Column(modifier = modifier.fillMaxSize().background(backgroundGradient)) {
        Surface(
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // AI Avatar in top bar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1A1A1A), Color(0xFF0C0C0C))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("H", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Hilo AI", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                if (isAiTyping) "Escribiendo..." else "En línea",
                                color = if (isAiTyping) Color(0xFF8E8E93) else Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
        
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                AiMessageBubble(msg)
            }
            if (isAiTyping) {
                item {
                    AiTypingBubble()
                }
            }
        }

        // Input Area
        Surface(
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = { Text("Pregúntale algo a Hilo AI...", color = Color(0xFF8E8E93), fontSize = 15.sp) },
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        unfocusedBorderColor = Color(0xFFE5E5EA),
                        focusedBorderColor = Color(0xFF0C0C0C),
                        cursorColor = Color.Black,
                        focusedContainerColor = Color(0xFFF2F2F7),
                        unfocusedContainerColor = Color(0xFFF2F2F7)
                    ),
                    maxLines = 4
                )
                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isAiTyping) {
                            val textToSend = inputText
                            inputText = ""
                            aiChatViewModel.sendMessage(textToSend)
                        }
                    },
                    containerColor = Color(0xFF0C0C0C),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Enviar", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun AiTypingBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Hilo AI Avatar with gradient
        Box(
            modifier = Modifier
                .size(36.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1A1A1A), Color(0xFF0C0C0C))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("H", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            ),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Hilo AI está pensando",
                    fontSize = 14.sp,
                    color = Color(0xFF6E6E73),
                    modifier = Modifier.padding(end = 4.dp)
                )
                TypingDots()
            }
        }
    }
}

@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val dots = listOf(
        remember { Animatable(0f) },
        remember { Animatable(0f) },
        remember { Animatable(0f) }
    )

    dots.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            delay(index * 180L)
            while (true) {
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = repeatable(
                        iterations = 1,
                        animation = tween(350, easing = LinearOutSlowInEasing)
                    )
                )
                animatable.animateTo(
                    targetValue = 0f,
                    animationSpec = repeatable(
                        iterations = 1,
                        animation = tween(350, easing = LinearOutSlowInEasing)
                    )
                )
                delay(350L)
            }
        }
    }

    Row(
        modifier = modifier.height(12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        dots.forEach { animatable ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .offset(y = (-animatable.value * 6).dp)
                    .background(Color(0xFF6E6E73), CircleShape)
            )
        }
    }
}

@Composable
fun AiMessageBubble(message: AiMessage) {
    val isFromMe = message.isFromUser

    val bubbleColor = if (isFromMe) Color(0xFFDCF8C6) else Color.White
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isFromMe) 18.dp else 4.dp,
        bottomEnd = if (isFromMe) 4.dp else 18.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isFromMe) {
            // Hilo AI Avatar with gradient and shadow
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1A1A1A), Color(0xFF0C0C0C))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("H", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.text,
                    fontSize = 15.sp,
                    color = Color.Black,
                    lineHeight = 21.sp
                )
                Text(
                    text = message.timestamp,
                    fontSize = 10.sp,
                    color = Color(0xFF8E8E93),
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }
        }
    }
}
