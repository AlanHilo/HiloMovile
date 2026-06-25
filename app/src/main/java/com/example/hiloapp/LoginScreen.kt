package com.example.hiloapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: (Boolean) -> Unit,
    onRegisterClick: () -> Unit,
    onBack: () -> Unit
) {
    val serverUrl = BuildConfig.SERVER_URL
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    val isConnecting by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.error.collectAsState()

    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    // Shake animation for error
    var triggerShake by remember { mutableStateOf(false) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            triggerShake = true
        }
    }

    val shakeOffset by animateFloatAsState(
        targetValue = if (triggerShake) 1f else 0f,
        animationSpec = if (triggerShake) {
            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            tween(0)
        },
        finishedListener = { triggerShake = false },
        label = "shake"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
                initialOffsetY = { 60 },
                animationSpec = tween(500, easing = EaseOutCubic)
            )
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = if (triggerShake) kotlin.math.sin(shakeOffset * Math.PI.toFloat() * 4) * 12f else 0f
                    }
                    .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.08f)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF8F3)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "hilo",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Iniciar Sesión",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        letterSpacing = (-0.5).sp
                    )

                    Text(
                        text = "Conéctate al motor de Hilo",
                        fontSize = 14.sp,
                        color = Color(0xFF6E6E73),
                        modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
                    )

                    if (errorMessage != null) {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFEF4444),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo electrónico", color = Color(0xFF8E8E93)) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF8E8E93)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = Color.Black,
                            unfocusedBorderColor = Color(0xFFE5E5EA),
                            focusedLabelColor = Color.Black,
                            unfocusedLabelColor = Color(0xFF8E8E93),
                            cursorColor = Color.Black,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contraseña
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña", color = Color(0xFF8E8E93)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF8E8E93)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = Color.Black,
                            unfocusedBorderColor = Color(0xFFE5E5EA),
                            focusedLabelColor = Color.Black,
                            unfocusedLabelColor = Color(0xFF8E8E93),
                            cursorColor = Color.Black,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    if (isConnecting) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp)
                    } else {
                        Button(
                            onClick = {
                                if (email.isBlank() || password.isBlank()) {
                                    triggerShake = true
                                    return@Button
                                }
                                authViewModel.login(email, password, serverUrl) { isWhatsAppReady ->
                                    onLoginSuccess(isWhatsAppReady)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C0C0C)),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 8.dp
                            )
                        ) {
                            Text("CONECTAR E INICIAR SESIÓN", fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.5.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TextButton(onClick = onRegisterClick) {
                            Text("¿No tienes cuenta? Regístrate aquí", color = Color.Black, fontWeight = FontWeight.SemiBold)
                        }
                        
                        TextButton(onClick = {
                            authViewModel.clearError()
                            onBack()
                        }) {
                            Text("Volver", color = Color(0xFF8E8E93))
                        }
                    }
                }
            }
        }
    }
}
