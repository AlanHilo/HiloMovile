package com.example.hiloapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    authViewModel: AuthViewModel,
    onPairSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pairingStatus by remember { mutableStateOf("Desconectado") }
    var isCheckingStatus by remember { mutableStateOf(true) }
    var myPhoneState by remember { mutableStateOf(HiloApi.myPhone) }

    val coroutineScope = rememberCoroutineScope()

    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    // Status polling loop
    LaunchedEffect(Unit) {
        var firstCheck = true
        while (true) {
            val statusRes = HiloApi.getWhatsAppStatus()
            if (statusRes is NetworkResult.Success) {
                myPhoneState = HiloApi.myPhone
                val status = statusRes.data.status
                pairingStatus = when (status) {
                    "ready" -> "¡Conectado!"
                    "initializing" -> "Cargando WhatsApp Web..."
                    "qr_ready" -> "Esperando vinculación en WhatsApp..."
                    "authenticating" -> "Autenticando..."
                    "disconnected" -> "Desconectado"
                    "failed" -> "Error en el motor"
                    else -> status
                }
                if (status == "ready") {
                    authViewModel.markWhatsAppReady()
                    onPairSuccess()
                    break
                }
                
                // Keep showing the verification/loading screen if initializing or authenticating
                isCheckingStatus = (status == "initializing" || status == "authenticating")
            } else {
                isCheckingStatus = false
            }
            firstCheck = false
            delay(4000)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                TopAppBar(
                    title = { Text("Vincular WhatsApp", color = Color.Black, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White),
            contentAlignment = Alignment.TopCenter
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
                        .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.08f)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF8F3)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isCheckingStatus) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            val titleText = when (pairingStatus) {
                                "Cargando WhatsApp Web..." -> "Iniciando WhatsApp..."
                                "Autenticando..." -> "Autenticando..."
                                "Esperando vinculación en WhatsApp..." -> "Verificando vinculación..."
                                else -> "Verificando estado..."
                            }
                            val subText = when (pairingStatus) {
                                "Cargando WhatsApp Web..." -> "El motor de OpenWA se está iniciando en el servidor. Por favor, espera."
                                "Autenticando..." -> "Conectando y restaurando la sesión de WhatsApp anterior."
                                else -> "Por favor, espera mientras consultamos el motor de OpenWA."
                            }

                            Text(
                                text = titleText,
                                color = Color.Black,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = subText,
                                color = Color(0xFF6E6E73),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        } else if (pairingCode == null) {
                            if (myPhoneState.isNotEmpty() && !isLoading) {
                                // Associated phone number view
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE6F4EA)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Smartphone,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Text(
                                    text = "Línea Corporativa Asociada",
                                    color = Color.Black,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                                    letterSpacing = (-0.3).sp
                                )

                                Text(
                                    text = "El sistema tiene vinculado el número: +${myPhoneState}\nEstado actual: $pairingStatus",
                                    color = Color(0xFF6E6E73),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 24.dp),
                                    lineHeight = 20.sp
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
                                            modifier = Modifier.padding(12.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                val isEngineStopped = pairingStatus == "Desconectado" || 
                                                     pairingStatus == "Error en el motor" || 
                                                     pairingStatus == "created" ||
                                                     pairingStatus.contains("error", ignoreCase = true)

                                Button(
                                    onClick = {
                                        isLoading = true
                                        errorMessage = null
                                        coroutineScope.launch {
                                            if (isEngineStopped) {
                                                val res = HiloApi.startWhatsAppEngine()
                                                isLoading = false
                                                if (res is NetworkResult.Success) {
                                                    isCheckingStatus = true
                                                } else {
                                                    errorMessage = (res as? NetworkResult.Error)?.message ?: "Error al iniciar motor."
                                                }
                                            } else {
                                                // Engine is running and ready for pairing, request pairing code for myPhone
                                                val res = HiloApi.getPairingCode(myPhoneState)
                                                isLoading = false
                                                if (res is NetworkResult.Success) {
                                                    pairingCode = res.data
                                                } else {
                                                    errorMessage = (res as? NetworkResult.Error)?.message ?: "Error al solicitar código"
                                                }
                                            }
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
                                    Text(
                                        text = if (isEngineStopped) "INICIAR MOTOR DE WHATSAPP" else "OBTENER CÓDIGO DE VINCULACIÓN",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                TextButton(onClick = { 
                                    HiloApi.myPhone = ""
                                    myPhoneState = ""
                                }) {
                                    Text("Vincular un número diferente", color = Color(0xFF8E8E93))
                                }
                            } else {
                                // Request code view
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE6F4EA)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Smartphone,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Text(
                                    text = "Vincular mediante Código",
                                    color = Color.Black,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                                    letterSpacing = (-0.3).sp
                                )

                                Text(
                                    text = "Ingresa tu número de WhatsApp para obtener un código de vinculación de 8 dígitos.",
                                    color = Color(0xFF6E6E73),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 24.dp),
                                    lineHeight = 20.sp
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
                                            modifier = Modifier.padding(12.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { phoneNumber = it },
                                    label = { Text("Número (ej: 5215512345678)", color = Color(0xFF8E8E93)) },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF8E8E93)) },
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
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Procesando...\n(Esto puede tardar hasta 30s en frío)",
                                        color = Color(0xFF6E6E73),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Button(
                                        onClick = {
                                            if (phoneNumber.isBlank()) {
                                                errorMessage = "Ingresa tu número de teléfono"
                                                return@Button
                                            }
                                            isLoading = true
                                            errorMessage = null
                                            coroutineScope.launch {
                                                val res = HiloApi.getPairingCode(phoneNumber)
                                                isLoading = false
                                                if (res is NetworkResult.Success) {
                                                    pairingCode = res.data
                                                } else {
                                                    val errMsg = (res as? NetworkResult.Error)?.message ?: "Error al solicitar código"
                                                    if (errMsg.contains("already authenticated", ignoreCase = true)) {
                                                        onPairSuccess()
                                                    } else {
                                                        errorMessage = errMsg
                                                    }
                                                }
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
                                        Text("OBTENER CÓDIGO", fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.5.sp)
                                    }
                                }
                            }

                        } else {
                            // Code obtained view
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE6F4EA)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = "Introduce el código en tu celular",
                                color = Color.Black,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 20.dp, bottom = 20.dp),
                                letterSpacing = (-0.3).sp
                            )

                            // Code card with scale animation
                            var codeVisible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                delay(200)
                                codeVisible = true
                            }
                            AnimatedVisibility(
                                visible = codeVisible,
                                enter = scaleIn(
                                    initialScale = 0.8f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                ) + fadeIn()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .shadow(6.dp, RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .border(2.dp, Color(0xFF0C0C0C), RoundedCornerShape(16.dp))
                                        .padding(vertical = 20.dp, horizontal = 32.dp)
                                ) {
                                    Text(
                                        text = formatCode(pairingCode!!),
                                        color = Color.Black,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 3.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            // Instructions
                            Text(
                                text = "Instrucciones de Vinculación:",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                InstructionRow("1", "Abre la aplicación de WhatsApp (en este celular o tu principal).")
                                InstructionRow("2", "Ve a Configuración / Menú > Dispositivos Vinculados.")
                                InstructionRow("3", "Selecciona 'Vincular un dispositivo' y luego 'Vincular con el número de teléfono'.")
                                InstructionRow("4", "Introduce el código anterior para completar la sincronización.")
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            HorizontalDivider(color = Color(0xFFEFECE6))

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFF10B981),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = pairingStatus,
                                    color = Color(0xFF6E6E73),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TextButton(onClick = { pairingCode = null }) {
                                Text("Cambiar número", color = Color(0xFF8E8E93))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstructionRow(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color(0xFF48484A),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun formatCode(code: String): String {
    if (code.length == 8) {
        return "${code.substring(0, 4)} - ${code.substring(4, 8)}"
    }
    return code
}
