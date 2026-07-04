package com.example.hiloapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chat: Chat,
    messagesViewModel: MessagesViewModel,
    onBack: () -> Unit
) {
    val messages by messagesViewModel.messages.collectAsState()
    val isInitialLoading by messagesViewModel.isInitialLoading.collectAsState()
    val isLoadingMore by messagesViewModel.isLoadingMore.collectAsState()
    val isRefreshing by messagesViewModel.isRefreshing.collectAsState()
    val searchResults by messagesViewModel.searchResults.collectAsState()
    val isSearching by messagesViewModel.isSearching.collectAsState()

    LaunchedEffect(chat.id) {
        messagesViewModel.loadMessages(chat.id)
    }

    val onSendMessage: (String) -> Unit = { text ->
        messagesViewModel.sendMessage(text) { /* Optionally handle result */ }
    }

    val onSendMediaMessage: (type: String, base64: String, mimetype: String, filename: String?, caption: String) -> Unit = { type, base64, mimetype, filename, caption ->
        messagesViewModel.sendMediaMessage(type, base64, mimetype, filename, caption) { _, _ -> }
    }

    val onLoadMore: () -> Unit = {
        messagesViewModel.loadMore()
    }
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Search state
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    // Export CSV state
    var showMoreMenu    by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportFromDate   by remember { mutableStateOf("") }
    var exportToDate     by remember { mutableStateOf("") }
    var isExporting      by remember { mutableStateOf(false) }
    var exportMessage    by remember { mutableStateOf<String?>(null) }

    // Debounced search — waits 450ms after the user stops typing
    LaunchedEffect(searchText) {
        if (searchText.isNotBlank()) {
            kotlinx.coroutines.delay(450)
            messagesViewModel.performSearch(searchText)
        } else {
            messagesViewModel.clearSearch()
        }
    }

    // Clear search when search bar is closed
    LaunchedEffect(isSearchOpen) {
        if (!isSearchOpen) {
            searchText = ""
            messagesViewModel.clearSearch()
        }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isPreparingMedia by remember { mutableStateOf(false) }
    var pendingMedia by remember { mutableStateOf<PendingMedia?>(null) }

    // Media Viewer States
    var activeMediaViewer by remember { mutableStateOf<Message?>(null) }
    var pendingViewMessage by remember { mutableStateOf<Message?>(null) }

    // Audio Recording States
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<java.io.File?>(null) }
    var recordingStartMillis by remember { mutableStateOf(0L) }

    // Audio recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTime = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingTime += 1
            }
        }
    }

    // Audio recording functions
    fun startRecording(ctx: Context) {
        try {
            val file = java.io.File(ctx.cacheDir, "voice_record_${System.currentTimeMillis()}.mp4")
            audioFile = file
            
            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(ctx)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }
            
            recorder.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            recordingStartMillis = System.currentTimeMillis()
            isRecording = true
        } catch (e: Exception) {
            android.util.Log.e("AudioRecord", "Failed to start recording", e)
            Toast.makeText(ctx, "Error al iniciar grabación: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    fun stopRecording(ctx: Context, send: Boolean) {
        val recorder = mediaRecorder ?: return
        mediaRecorder = null
        isRecording = false
        try {
            recorder.stop()
            recorder.release()
        } catch (e: Exception) {
            android.util.Log.e("AudioRecord", "Failed to stop recording", e)
        }
        
        val file = audioFile
        if (file != null && file.exists()) {
            val duration = System.currentTimeMillis() - recordingStartMillis
            if (send && duration > 800) {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val bytes = file.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onSendMediaMessage("audio", base64, "audio/mp4", file.name, "")
                        }
                    } catch (e: Exception) {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(ctx, "Error al procesar audio: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        file.delete()
                    }
                }
            } else {
                file.delete()
                if (send && duration <= 800) {
                    Toast.makeText(ctx, "Mantén presionado para grabar", Toast.LENGTH_SHORT).show()
                }
            }
        }
        audioFile = null
    }

    // Permission Request Launchers
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                Toast.makeText(context, "Permiso concedido. Mantén presionado para grabar.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val viewMediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsMap ->
            val allGranted = permissionsMap.values.all { it }
            if (allGranted) {
                activeMediaViewer = pendingViewMessage
            } else {
                Toast.makeText(context, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
            }
            pendingViewMessage = null
        }
    )

    fun checkAndShowMedia(message: Message) {
        val sdk = android.os.Build.VERSION.SDK_INT
        val missingPermissions = mutableListOf<String>()
        
        if (sdk >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val perm = when (message.type) {
                "image", "sticker" -> android.Manifest.permission.READ_MEDIA_IMAGES
                "video" -> android.Manifest.permission.READ_MEDIA_VIDEO
                "audio", "ptt" -> android.Manifest.permission.READ_MEDIA_AUDIO
                else -> null
            }
            if (perm != null && androidx.core.content.ContextCompat.checkSelfPermission(context, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(perm)
            }
        } else {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        if (missingPermissions.isEmpty()) {
            activeMediaViewer = message
        } else {
            pendingViewMessage = message
            viewMediaPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    // Picker Launchers
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isPreparingMedia = true
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val fileInfo = getFileInfo(context, uri)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isPreparingMedia = false
                    if (fileInfo != null) {
                        pendingMedia = PendingMedia("image", uri, fileInfo.name, fileInfo.mimeType, fileInfo.base64)
                    } else {
                        Toast.makeText(context, "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isPreparingMedia = true
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val fileInfo = getFileInfo(context, uri)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isPreparingMedia = false
                    if (fileInfo != null) {
                        pendingMedia = PendingMedia("video", uri, fileInfo.name, fileInfo.mimeType, fileInfo.base64)
                    } else {
                        Toast.makeText(context, "Error al cargar el video", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isPreparingMedia = true
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val fileInfo = getFileInfo(context, uri)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isPreparingMedia = false
                    if (fileInfo != null) {
                        pendingMedia = PendingMedia("audio", uri, fileInfo.name, fileInfo.mimeType, fileInfo.base64)
                    } else {
                        Toast.makeText(context, "Error al cargar el audio", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isPreparingMedia = true
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val fileInfo = getFileInfo(context, uri)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isPreparingMedia = false
                    if (fileInfo != null) {
                        pendingMedia = PendingMedia("document", uri, fileInfo.name, fileInfo.mimeType, fileInfo.base64)
                    } else {
                        Toast.makeText(context, "Error al cargar el documento", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Attachment menu dialog
    if (showAttachmentMenu) {
        AlertDialog(
            onDismissRequest = { showAttachmentMenu = false },
            title = { Text("Adjuntar archivo", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachmentOption(
                        icon = Icons.Default.Add,
                        label = "Imagen",
                        bgColor = Color(0xFFE8F5E9),
                        iconColor = Color(0xFF2E7D32),
                        onClick = {
                            showAttachmentMenu = false
                            imagePicker.launch("image/*")
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.PlayCircle,
                        label = "Video",
                        bgColor = Color(0xFFE3F2FD),
                        iconColor = Color(0xFF1565C0),
                        onClick = {
                            showAttachmentMenu = false
                            videoPicker.launch("video/*")
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.VolumeUp,
                        label = "Audio",
                        bgColor = Color(0xFFFFF3E0),
                        iconColor = Color(0xFFE65100),
                        onClick = {
                            showAttachmentMenu = false
                            audioPicker.launch("audio/*")
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.Description,
                        label = "Documento",
                        bgColor = Color(0xFFF3E5F5),
                        iconColor = Color(0xFF6A1B9A),
                        onClick = {
                            showAttachmentMenu = false
                            documentPicker.launch("*/*")
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachmentMenu = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Preparing media overlay
    if (isPreparingMedia) {
        Dialog(onDismissRequest = {}) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .background(Color.White, shape = RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp)
            }
        }
    }

    // Export CSV dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isExporting) showExportDialog = false },
            title = { Text("Exportar mensajes", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Rango de fechas opcional (AAAA-MM-DD).\nDeja vacío para exportar todo.",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280)
                    )
                    OutlinedTextField(
                        value = exportFromDate,
                        onValueChange = { exportFromDate = it },
                        label = { Text("Desde (opcional)") },
                        placeholder = { Text("2025-01-01") },
                        singleLine = true,
                        enabled = !isExporting,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = exportToDate,
                        onValueChange = { exportToDate = it },
                        label = { Text("Hasta (opcional)") },
                        placeholder = { Text("2025-12-31") },
                        singleLine = true,
                        enabled = !isExporting,
                        modifier = Modifier.fillMaxWidth()
                    )
                    exportMessage?.let { msg ->
                        val isError = msg.startsWith("Error")
                        Text(
                            text = msg,
                            color = if (isError) Color(0xFFDC2626) else Color(0xFF10B981),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF10B981)
                    )
                } else {
                    TextButton(
                        onClick = {
                            isExporting = true
                            exportMessage = null
                            messagesViewModel.exportCsv(
                                from = exportFromDate.trim().ifBlank { null },
                                to   = exportToDate.trim().ifBlank { null }
                            ) { success, msg ->
                                isExporting = false
                                exportMessage = if (success) "✅ Guardado en $msg" else "Error: $msg"
                            }
                        }
                    ) {
                        Text("Exportar", color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    enabled = !isExporting
                ) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Pagination trigger
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && messages.isNotEmpty() && lastIndex >= messages.size - 5) {
                    onLoadMore()
                }
            }
    }

    val avatarGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF10B981), Color(0xFF059669))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Surface(
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    TopAppBar(
                        title = {
                            if (isSearchOpen) {
                                OutlinedTextField(
                                    value = searchText,
                                    onValueChange = { searchText = it },
                                    placeholder = { Text("Buscar mensajes...", color = Color(0xFF8E8E93), fontSize = 14.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black,
                                        focusedBorderColor = Color(0xFF10B981),
                                        unfocusedBorderColor = Color(0xFFE5E5EA),
                                        cursorColor = Color(0xFF10B981),
                                        focusedContainerColor = Color(0xFFF2F2F7),
                                        unfocusedContainerColor = Color(0xFFF2F2F7)
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                // Avatar with ring
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
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
                                                        fontSize = 16.sp
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
                                                        fontSize = 16.sp
                                                    )
                                                }
                                            }
                                        )
                                    } else {
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
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = chat.contactName, 
                                        color = Color.Black, 
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        letterSpacing = (-0.2).sp
                                    )
                                    if (chat.isMonitored) {
                                        Text(
                                            text = "IA monitoreando",
                                            color = Color(0xFF10B981),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (isSearchOpen) {
                                    isSearchOpen = false
                                } else {
                                    onBack()
                                }
                            }) {
                                Icon(
                                    if (isSearchOpen) Icons.Default.Close else Icons.Default.ArrowBack,
                                    contentDescription = if (isSearchOpen) "Cerrar búsqueda" else "Back",
                                    tint = Color.Black
                                )
                            }
                        },
                        actions = {
                            if (!isSearchOpen) {
                                IconButton(onClick = { isSearchOpen = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.Black)
                                }
                                Box {
                                    IconButton(onClick = { showMoreMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = Color.Black)
                                    }
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Exportar CSV") },
                                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                            onClick = {
                                                showMoreMenu = false
                                                exportFromDate = ""
                                                exportToDate = ""
                                                exportMessage = null
                                                showExportDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.White
                        )
                    )
                }
            },
            bottomBar = {
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        // Inline Pending Media Attachment Preview
                        AnimatedVisibility(
                            visible = pendingMedia != null,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it })
                        ) {
                            if (pendingMedia != null) {
                                val media = pendingMedia!!
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF2F2F7))
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Thumbnail or Icon
                                    if (media.type == "image") {
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(media.uri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Thumbnail preview",
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .background(Color.White, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (media.type) {
                                                    "video" -> Icons.Default.PlayCircle
                                                    "audio" -> Icons.Default.VolumeUp
                                                    else -> Icons.Default.Description
                                                },
                                                contentDescription = "File icon",
                                                tint = Color.Black,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = media.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = when (media.type) {
                                                "image" -> "Imagen seleccionada"
                                                "video" -> "Video seleccionado"
                                                "audio" -> "Audio seleccionado"
                                                else -> "Documento seleccionado"
                                            },
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    IconButton(
                                        onClick = { pendingMedia = null }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove attachment",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        // Recording indicator overlay inside the input bar area
                        if (isRecording) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Grabando audio... ${formatTime(recordingTime * 1000)}",
                                        color = Color.Red,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = { stopRecording(context, send = false) }
                                ) {
                                    Text("Cancelar", color = Color.Gray)
                                }
                            }
                        } else {
                            // Standard message input bar (with Attach, Text Field, and Send/Record FAB)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showAttachmentMenu = true },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Adjuntar archivo",
                                        tint = Color(0xFF8E8E93)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                TextField(
                                    value = textState,
                                    onValueChange = { textState = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text(if (pendingMedia != null) "Añade un comentario..." else "Escribe un mensaje...", color = Color(0xFF8E8E93), fontSize = 15.sp) },
                                    shape = RoundedCornerShape(28.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = Color(0xFFF2F2F7),
                                        unfocusedContainerColor = Color(0xFFF2F2F7)
                                    ),
                                    maxLines = 4
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // Send or Audio recording FAB
                                var isSendingMedia by remember { mutableStateOf(false) }
                                
                                if (textState.isNotBlank() || pendingMedia != null) {
                                    FloatingActionButton(
                                        onClick = {
                                            if (pendingMedia != null) {
                                                val media = pendingMedia!!
                                                isSendingMedia = true
                                                coroutineScope.launch {
                                                    try {
                                                        onSendMediaMessage(media.type, media.base64, media.mimeType, media.name, textState)
                                                        pendingMedia = null
                                                        textState = ""
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Error al enviar: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    } finally {
                                                        isSendingMedia = false
                                                    }
                                                }
                                            } else {
                                                onSendMessage(textState)
                                                textState = ""
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
                                        if (isSendingMedia) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                } else {
                                    // Record voice message button
                                    val pointerInputModifier = Modifier
                                        .size(48.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    val hasPermission = ContextCompat.checkSelfPermission(
                                                        context,
                                                        android.Manifest.permission.RECORD_AUDIO
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                    
                                                    if (!hasPermission) {
                                                        recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                    } else {
                                                        startRecording(context)
                                                        try {
                                                            tryAwaitRelease()
                                                            stopRecording(context, send = true)
                                                        } catch (e: Exception) {
                                                            stopRecording(context, send = false)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    
                                    FloatingActionButton(
                                        onClick = {
                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (!hasPermission) {
                                                recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            } else {
                                                Toast.makeText(context, "Mantén presionado para grabar audio", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color.White,
                                        shape = CircleShape,
                                        modifier = pointerInputModifier,
                                        elevation = FloatingActionButtonDefaults.elevation(
                                            defaultElevation = 4.dp,
                                            pressedElevation = 8.dp
                                        )
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Record Audio", modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            if (isInitialLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color(0xFFF9F9FB)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cargando mensajes...",
                            color = Color(0xFF8E8E93),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (searchResults != null) {
                // Search results view
                when {
                    isSearching -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color(0xFFF9F9FB)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF10B981), strokeWidth = 3.dp)
                        }
                    }
                    searchResults!!.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color(0xFFF9F9FB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔍", fontSize = 40.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Sin resultados para \"$searchText\"", color = Color(0xFF8E8E93), fontSize = 14.sp)
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color(0xFFF9F9FB)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(searchResults!!, key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    isGroup = chat.id.endsWith("@g.us"),
                                    onMediaClick = {}
                                )
                            }
                        }
                    }
                }
            } else if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color(0xFFF9F9FB)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🗨️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No hay mensajes en este chat.",
                            color = Color(0xFF8E8E93),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color(0xFFF9F9FB))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        reverseLayout = true
                    ) {
                        items(messages, key = { "${chat.id}_${it.id}" }) { message ->
                            MessageBubble(
                                message = message,
                                isGroup = chat.id.endsWith("@g.us"),
                                onMediaClick = { clickedMessage ->
                                    checkAndShowMedia(clickedMessage)
                                }
                            )
                        }
                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.Black,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Lightweight loading overlay for heavy refresh processes.
                    AnimatedVisibility(
                        visible = isRefreshing && messages.isNotEmpty(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sincronizando mensajes...",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // In-app media player/viewer sliding overlay
        AnimatedVisibility(
            visible = activeMediaViewer != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (activeMediaViewer != null) {
                BottomMediaViewer(
                    message = activeMediaViewer!!,
                    onClose = { activeMediaViewer = null }
                )
            }
        }
    }
}

@Composable
fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bgColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(2.dp, CircleShape, ambientColor = bgColor)
                .background(bgColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Black)
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isGroup: Boolean = false,
    onMediaClick: (Message) -> Unit
) {
    val isAi = message.isFromMe && message.text.trim().startsWith("🤖")
    val isMine = message.isFromMe && !isAi
    val isOther = !message.isFromMe

    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = when {
        isMine -> Color(0xFFDCF8C6)
        isAi -> Color(0xFFF3E5F5)
        else -> Color.White
    }
    val textColor = Color.Black

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (message.isFromMe) 18.dp else 4.dp,
        bottomEnd = if (message.isFromMe) 4.dp else 18.dp
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // Determine display name
                val displayName = remember(message.senderId, message.senderName, isAi, isMine) {
                    when {
                        isAi -> "Hilo AI ✨"
                        isMine -> "Tú"
                        else -> {
                            val sId = message.senderId ?: ""
                            val sName = message.senderName ?: ""
                            if (sName.isNotEmpty() && sName != "Desconocido" && !sName.contains("@c.us")) {
                                sName
                            } else if (sId.isNotEmpty()) {
                                sId.substringBefore("@")
                            } else {
                                "Contacto"
                            }
                        }
                    }
                }

                // Show name for everyone in groups, or always for AI to be clear
                if (isGroup || isAi || isMine) {
                    val nameColor = when {
                        isAi -> Color(0xFF9C27B0)
                        isMine -> Color(0xFF2E7D32)
                        else -> Color(0xFF10B981)
                    }
                    Text(
                        text = displayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = nameColor,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                val isMediaMessage = message.type == "image" || message.type == "video" ||
                        message.type == "sticker" || message.type == "audio" ||
                        message.type == "document" || message.type == "ptt"

                if (message.mediaUrl != null || isMediaMessage) {
                    val clickModifier = if (message.mediaUrl != null) {
                        Modifier.clickable { onMediaClick(message) }
                    } else {
                        Modifier
                    }

                    if (message.mediaUrl == null) {
                        // Media detected but no downloadable URL available
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF2F2F7).copy(alpha = 0.6f), shape = RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (message.type) {
                                    "image", "sticker" -> Icons.Default.Image
                                    "video" -> Icons.Default.PlayCircle
                                    "audio", "ptt" -> Icons.Default.VolumeUp
                                    else -> Icons.Default.Description
                                },
                                contentDescription = "Multimedia",
                                tint = Color(0xFF8E8E93),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (message.type) {
                                        "image" -> "Imagen"
                                        "sticker" -> "Sticker"
                                        "video" -> "Video"
                                        "audio" -> "Audio"
                                        "ptt" -> "Nota de voz"
                                        else -> "Documento"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Black
                                )
                                Text("No disponible para descargar", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    } else if (message.type == "image" || message.type == "sticker") {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(message.mediaUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Media",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(clickModifier),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            loading = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .background(Color.Gray.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.Gray, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            },
                            error = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .background(Color.Gray.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No se pudo cargar la imagen", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    } else if (message.type == "video") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.8f))
                                .then(clickModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(message.mediaUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Video Thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                loading = {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
                                },
                                error = {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Reproducir Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    } else if (message.type == "audio" || message.type == "ptt") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF2F2F7).copy(alpha = 0.6f), shape = RoundedCornerShape(10.dp))
                                .then(clickModifier)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF0C0C0C), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Audio message",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (message.type == "ptt") "Mensaje de voz" else "Audio", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                                Text("Tocar para escuchar", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE8F5E9).copy(alpha = 0.7f), shape = RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFC8E6C9), shape = RoundedCornerShape(10.dp))
                                .then(clickModifier)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Document",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.text.ifBlank { "Documento sin nombre" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text("Documento • Tocar para abrir", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                
                // Only show text if it's not a media message or if it's media with caption
                if (message.mediaUrl == null || (message.type != "document" && message.text.isNotBlank())) {
                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        color = textColor,
                        lineHeight = 21.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
                Text(
                    text = message.timestamp,
                    fontSize = 10.sp,
                    color = Color(0xFF8E8E93),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// Media Helper Classes and Functions
data class PendingMedia(
    val type: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val base64: String
)

data class FileInfo(val name: String, val mimeType: String, val base64: String)

fun getFileInfo(context: Context, uri: Uri): FileInfo? {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
    var name = "archivo"
    
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
    }
    
    return try {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        FileInfo(name, mimeType, base64)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun BottomMediaViewer(
    message: Message,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
            .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Drag handle and top bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (message.type) {
                            "image", "sticker" -> "Imagen"
                            "video" -> "Video"
                            "audio", "ptt" -> "Mensaje de voz / Audio"
                            else -> "Documento"
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (message.senderName != null) {
                        Text(
                            text = "De: ${message.senderName}",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
                
                Row {
                    if (message.mediaUrl != null) {
                        val filename = remember(message) {
                            when (message.type) {
                                "image" -> "img_${message.id}.jpg"
                                "sticker" -> "stk_${message.id}.webp"
                                "video" -> "vid_${message.id}.mp4"
                                "audio", "ptt" -> "aud_${message.id}.mp3"
                                else -> message.text.ifBlank { "doc_${message.id}.pdf" }
                            }
                        }
                        IconButton(
                            onClick = {
                                downloadFileWithManager(context, message.mediaUrl, filename)
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Descargar", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (message.mediaUrl == null) {
                    Text("No hay URL de medio disponible", color = Color.Gray, fontSize = 14.sp)
                } else {
                    when (message.type) {
                        "image", "sticker" -> {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(message.mediaUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Media Full Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                loading = {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                                },
                                error = {
                                    Text("Error al cargar la imagen", color = Color.Red, fontSize = 14.sp)
                                }
                            )
                        }
                        
                        "video" -> {
                            AndroidView(
                                factory = { ctx ->
                                    android.widget.VideoView(ctx).apply {
                                        setVideoURI(Uri.parse(message.mediaUrl))
                                        val mediaController = android.widget.MediaController(ctx)
                                        mediaController.setAnchorView(this)
                                        setMediaController(mediaController)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            Toast.makeText(ctx, "Error al reproducir video", Toast.LENGTH_SHORT).show()
                                            true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        
                        "audio", "ptt" -> {
                            AudioPlaybackView(mediaUrl = message.mediaUrl)
                        }
                        
                        else -> { // Document
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Document Icon",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(72.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = message.text.ifBlank { "Documento sin nombre" },
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                val filename = message.text.ifBlank { "documento.pdf" }
                                Button(
                                    onClick = {
                                        downloadFileWithManager(context, message.mediaUrl, filename)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Descargar documento", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
            
            // Show caption if it exists
            if (message.text.isNotBlank() && message.type != "document") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
fun AudioPlaybackView(mediaUrl: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    
    val mediaPlayer = remember { android.media.MediaPlayer() }
    
    DisposableEffect(mediaUrl) {
        try {
            mediaPlayer.setDataSource(context, Uri.parse(mediaUrl))
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener { mp ->
                duration = mp.duration
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                position = 0
                mediaPlayer.seekTo(0)
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayer", "Error preparing audio", e)
        }
        
        onDispose {
            try {
                mediaPlayer.stop()
                mediaPlayer.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                try {
                    position = mediaPlayer.currentPosition
                } catch (e: Exception) {
                    break
                }
                delay(200)
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    try {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error de reproducción", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF10B981), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Slider(
            value = position.toFloat(),
            onValueChange = { valPos ->
                try {
                    mediaPlayer.seekTo(valPos.toInt())
                    position = valPos.toInt()
                } catch (e: Exception) {
                    // Ignore
                }
            },
            valueRange = 0f..maxOf(1f, duration.toFloat()),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF10B981),
                activeTrackColor = Color(0xFF10B981),
                inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(position), color = Color.Gray, fontSize = 12.sp)
            Text(text = formatTime(duration), color = Color.Gray, fontSize = 12.sp)
        }
    }
}

fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun downloadFileWithManager(context: Context, url: String, filename: String) {
    try {
        val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(filename)
            setDescription("Descargando archivo...")
            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "Descarga iniciada: $filename", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error al descargar: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
