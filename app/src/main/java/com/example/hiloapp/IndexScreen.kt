package com.example.hiloapp

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class OnboardingStep { FRAME1, FRAME2, FRAME3 }

data class CardMetric(val value: String, val label: String)

val card1Metrics = listOf(
    CardMetric("50+", "chats"),
    CardMetric("Realtime", "sincronización"),
    CardMetric("100%", "control")
)

val card2Metrics = listOf(
    CardMetric("24/7", "análisis"),
    CardMetric("5s", "auto-reply"),
    CardMetric("98%", "precisión")
)

val card3Metrics = listOf(
    CardMetric("PDF/Img", "formatos"),
    CardMetric("50MB", "límite"),
    CardMetric("100%", "visor")
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun IndexScreen(onLoginClick: () -> Unit) {
    var step by remember { mutableStateOf(OnboardingStep.FRAME1) }
    var triggerMetrics by remember { mutableStateOf(false) }
    var showCtaButton by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Centralized State Machine for Onboarding transitions
    LaunchedEffect(step) {
        when (step) {
            OnboardingStep.FRAME1 -> {
                triggerMetrics = false
                showCtaButton = false
                delay(1800) // Show Frame 1 for 1.8 seconds
                step = OnboardingStep.FRAME2
            }
            OnboardingStep.FRAME2 -> {
                triggerMetrics = false
                showCtaButton = false
                delay(1800) // Show Frame 2 for 1.8 seconds
                step = OnboardingStep.FRAME3
            }
            OnboardingStep.FRAME3 -> {
                triggerMetrics = true
                delay(1200) // Wait for slot machine numbers to settle
                showCtaButton = true
            }
        }
    }

    // Particle floating animation for Frame 2
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "particleFloat"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // SCROLLABLE CONTENT AREA (Pinning the CTA button to the bottom)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // TOP BRAND MARK AND RESET BUTTON
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "hilo",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                // Small rewind button for demonstration/replay
                if (step == OnboardingStep.FRAME3 && showCtaButton) {
                    IconButton(
                        onClick = {
                            step = OnboardingStep.FRAME1
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reiniciar animación",
                            tint = Color.Gray
                        )
                    }
                }
            }

            // HEADER TITLES
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                    },
                    label = "headerTitle"
                ) { targetStep ->
                    val titleText = when (targetStep) {
                        OnboardingStep.FRAME1 -> "Tus chats de\nWhatsApp con\nInteligencia Artificial"
                        OnboardingStep.FRAME2 -> "Hilo clasifica y\nmonitorea todo."
                        OnboardingStep.FRAME3 -> "Información y\ncontrol al\ninstante."
                    }
                    Text(
                        text = titleText,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        lineHeight = 42.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // INTERACTIVE ANIMATING AREA WITH DYNAMIC HEIGHT
            val boxHeight by animateDpAsState(
                targetValue = if (step == OnboardingStep.FRAME3) 520.dp else 340.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "boxHeight"
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(boxHeight),
                contentAlignment = Alignment.TopCenter
            ) {
                val maxWidth = this.maxWidth

                // CARDS POSITIONING ANIMATIONS
                // Card 1
                val card1Width by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> 210.dp
                        OnboardingStep.FRAME2 -> 210.dp
                        OnboardingStep.FRAME3 -> maxWidth - 48.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card1Height by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> 85.dp
                        OnboardingStep.FRAME2 -> 85.dp
                        OnboardingStep.FRAME3 -> 125.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card1X by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> (-30).dp
                        OnboardingStep.FRAME2 -> 0.dp
                        OnboardingStep.FRAME3 -> 0.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card1Y by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> 30.dp
                        OnboardingStep.FRAME2 -> 70.dp
                        OnboardingStep.FRAME3 -> 92.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card1Rotation by animateFloatAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> -6f
                        OnboardingStep.FRAME2 -> -2f
                        OnboardingStep.FRAME3 -> 0f
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )

                // Card 2
                val card2Width by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> 210.dp
                        OnboardingStep.FRAME2 -> 210.dp
                        OnboardingStep.FRAME3 -> maxWidth - 48.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card2Height by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> 85.dp
                        OnboardingStep.FRAME2 -> 85.dp
                        OnboardingStep.FRAME3 -> 125.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card2X by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> 35.dp
                        OnboardingStep.FRAME2 -> 0.dp
                        OnboardingStep.FRAME3 -> 0.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card2Y by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> 110.dp
                        OnboardingStep.FRAME2 -> 95.dp
                        OnboardingStep.FRAME3 -> 232.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card2Rotation by animateFloatAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> 4f
                        OnboardingStep.FRAME2 -> 0f
                        OnboardingStep.FRAME3 -> 0f
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )

                // Card 3
                val card3Width by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> 210.dp
                        OnboardingStep.FRAME2 -> 210.dp
                        OnboardingStep.FRAME3 -> maxWidth - 48.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card3Height by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> 85.dp
                        OnboardingStep.FRAME2 -> 85.dp
                        OnboardingStep.FRAME3 -> 125.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card3X by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> (-40).dp
                        OnboardingStep.FRAME2 -> 0.dp
                        OnboardingStep.FRAME3 -> 0.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card3Y by animateDpAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> 200.dp
                        OnboardingStep.FRAME2 -> 120.dp
                        OnboardingStep.FRAME3 -> 372.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                val card3Rotation by animateFloatAsState(
                    targetValue = when (step) {
                        OnboardingStep.FRAME1 -> -5f
                        OnboardingStep.FRAME2 -> 2f
                        OnboardingStep.FRAME3 -> 0f
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )

                // Frame 3 Top Header Card "Hilo index"
                val headerCardAlpha by animateFloatAsState(
                    targetValue = if (step == OnboardingStep.FRAME3) 1f else 0f,
                    animationSpec = tween(500)
                )
                val headerCardY by animateDpAsState(
                    targetValue = if (step == OnboardingStep.FRAME3) 15.dp else 0.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )

                // Floating green dots for Frame 2 (classification process)
                val particleAlpha by animateFloatAsState(
                    targetValue = if (step == OnboardingStep.FRAME2) 1f else 0f,
                    animationSpec = tween(300)
                )
                if (particleAlpha > 0.01f) {
                    // Dot 1
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .offset(x = (-45).dp, y = (60).dp + floatAnim.dp)
                            .graphicsLayer { alpha = particleAlpha }
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    // Dot 2
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .offset(x = 55.dp, y = (80).dp - floatAnim.dp)
                            .graphicsLayer { alpha = particleAlpha }
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    // Dot 3
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .offset(x = (-60).dp, y = (110).dp + (floatAnim * 0.8f).dp)
                            .graphicsLayer { alpha = particleAlpha }
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    // Dot 4
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .offset(x = 45.dp, y = (135).dp + floatAnim.dp)
                            .graphicsLayer { alpha = particleAlpha }
                            .background(Color(0xFF10B981), CircleShape)
                    )
                }

                // Hilo index Header Card
                Box(
                    modifier = Modifier
                        .size(width = maxWidth - 48.dp, height = 62.dp)
                        .offset(x = 0.dp, y = headerCardY)
                        .graphicsLayer { alpha = headerCardAlpha }
                        .background(Color(0xFFFAF8F3), shape = RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            color = Color(0xFFEFECE6),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Hilo index",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        // Capsule status indicator
                        Box(
                            modifier = Modifier
                                .size(width = 30.dp, height = 48.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(Color(0xFF0F0F0F))
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                        }
                    }
                }

                // CARD 1: Monitoreo / Chats
                val card1Subtitle = when (step) {
                    OnboardingStep.FRAME1 -> "Controla tus grupos de WhatsApp"
                    OnboardingStep.FRAME2 -> "se sincronizan"
                    OnboardingStep.FRAME3 -> "volumen de chats"
                }
                val card1Title = if (step == OnboardingStep.FRAME3) "Chats" else "Monitoreo"
                OnboardingCard(
                    title = card1Title,
                    subtitle = card1Subtitle,
                    step = step,
                    metrics = card1Metrics,
                    cardWidth = card1Width,
                    cardHeight = card1Height,
                    offsetX = card1X,
                    offsetY = card1Y,
                    rotation = card1Rotation,
                    alpha = 1f,
                    triggerMetrics = triggerMetrics
                )

                // CARD 2: Hilo AI
                val card2Subtitle = when (step) {
                    OnboardingStep.FRAME1 -> "Respuestas y resúmenes automáticos"
                    OnboardingStep.FRAME2 -> "se auto-responden"
                    OnboardingStep.FRAME3 -> "asistente 24/7"
                }
                OnboardingCard(
                    title = "Hilo AI",
                    subtitle = card2Subtitle,
                    step = step,
                    metrics = card2Metrics,
                    cardWidth = card2Width,
                    cardHeight = card2Height,
                    offsetX = card2X,
                    offsetY = card2Y,
                    rotation = card2Rotation,
                    alpha = 1f,
                    triggerMetrics = triggerMetrics
                )

                // CARD 3: Multimedia
                val card3Subtitle = when (step) {
                    OnboardingStep.FRAME1 -> "Documentos, imágenes y audios"
                    OnboardingStep.FRAME2 -> "se catalogan"
                    OnboardingStep.FRAME3 -> "archivos y visores"
                }
                OnboardingCard(
                    title = "Multimedia",
                    subtitle = card3Subtitle,
                    step = step,
                    metrics = card3Metrics,
                    cardWidth = card3Width,
                    cardHeight = card3Height,
                    offsetX = card3X,
                    offsetY = card3Y,
                    rotation = card3Rotation,
                    alpha = 1f,
                    triggerMetrics = triggerMetrics
                )
            }
            // Bottom spacing inside the scrollable content area to ensure smooth scrolling past the last element
            Spacer(modifier = Modifier.height(16.dp))
        }

        // FIXED BOTTOM ACTION BUTTON (Never overlaps content when scrolled)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            val buttonAlpha by animateFloatAsState(
                targetValue = if (showCtaButton) 1f else 0f,
                animationSpec = tween(500),
                label = "ctaButtonAlpha"
            )

            if (showCtaButton) {
                Button(
                    onClick = onLoginClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C0C0C)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .graphicsLayer { alpha = buttonAlpha }
                ) {
                    Text(
                        text = "INICIAR SESIÓN",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(56.dp))
            }
        }
    }
}

@Composable
fun OnboardingCard(
    title: String,
    subtitle: String,
    step: OnboardingStep,
    metrics: List<CardMetric>,
    cardWidth: Dp,
    cardHeight: Dp,
    offsetX: Dp,
    offsetY: Dp,
    rotation: Float,
    alpha: Float,
    triggerMetrics: Boolean
) {
    Box(
        modifier = Modifier
            .size(width = cardWidth, height = cardHeight)
            .offset(x = offsetX, y = offsetY)
            .graphicsLayer {
                rotationZ = rotation
                this.alpha = alpha
                shadowElevation = 3.dp.toPx()
                shape = RoundedCornerShape(16.dp)
                clip = true
            }
            .background(Color(0xFFFAF8F3))
            .border(
                width = 1.dp,
                color = Color(0xFFEFECE6),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        if (step == OnboardingStep.FRAME3) {
            // Frame 3 Layout: Grid structure showing metric stats columns
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                // Row of 3 columns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    metrics.forEachIndexed { index, metric ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            SlotMachineText(
                                targetValue = metric.value,
                                triggerRoll = triggerMetrics,
                                delayMillis = (index * 250).toLong(), // Staggered delay
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            )
                            Text(
                                text = metric.label,
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (index < metrics.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(Color(0xFFEFECE6))
                            )
                        }
                    }
                }
            }
        } else {
            // Frame 1 and 2 Layout: simple Category + Description
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun SlotMachineText(
    targetValue: String,
    triggerRoll: Boolean,
    delayMillis: Long,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    var currentValue by remember { mutableStateOf(targetValue) }

    LaunchedEffect(triggerRoll) {
        if (triggerRoll) {
            delay(delayMillis)
            val duration = 800L
            val interval = 50L
            val steps = duration / interval

            for (i in 0 until steps) {
                val temp = targetValue.map { char ->
                    when {
                        char.isDigit() -> "0123456789".random()
                        char.isLetter() -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ".random()
                        else -> char
                    }
                }.joinToString("")
                currentValue = temp
                delay(interval)
            }
            currentValue = targetValue
        } else {
            currentValue = targetValue
        }
    }

    Text(
        text = currentValue,
        style = style,
        modifier = modifier
    )
}
