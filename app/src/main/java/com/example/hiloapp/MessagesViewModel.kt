package com.example.hiloapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hiloapp.db.HiloDatabase
import com.example.hiloapp.db.toEntity
import com.example.hiloapp.db.toMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles state and business logic for the chat detail screen.
 *
 * Persistence strategy (WhatsApp-like):
 *  - Room DB is the single source of truth for displayed messages.
 *  - On [loadMessages]: emit from Room instantly (no blank screen), then sync from server.
 *  - Polling: upsert server page-1 to Room, reload only if new messages exist.
 *  - [loadMore]: fetches older pages, upserts to Room, reloads state from Room.
 *  - All messages survive app restarts and background kills.
 */
class MessagesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HiloDatabase.getInstance(application)
    private val messageDao = db.messageDao()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(false)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // null = not in search mode; empty list = searched but no results
    private val _searchResults = MutableStateFlow<List<Message>?>(null)
    val searchResults: StateFlow<List<Message>?> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var currentPage = 1
    private var totalPages = 1
    private var activeChatId: String? = null
    private var pollingJob: Job? = null

    // Socket.IO callback — called by HiloSocketManager on any new message event
    private val socketCallback: (HiloSocketManager.MessageEvent) -> Unit = { evt ->
        val active = activeChatId
        if (active != null && (evt.chatId == null || evt.chatId == active)) {
            viewModelScope.launch { refreshFirstPage() }
        }
    }
    private var socketListening = false

    fun loadMessages(chatId: String) {
        // Avoid stale screen bug: clear previous chat messages immediately
        // when switching chats so old messages are not briefly shown.
        if (activeChatId != chatId) {
            _messages.value = emptyList()
        } else {
            // Same chat selected again -> force refresh
            viewModelScope.launch { refreshFirstPage() }
            return
        }
        activeChatId = chatId
        stopPolling()
        currentPage = 1
        totalPages = 1

        viewModelScope.launch {
            _error.value = null

            // Step 1: Load from Room immediately — no spinner if we already have cached messages
            val cached = messageDao.getMessages(chatId)
            if (cached.isNotEmpty()) {
                _messages.value = cached.map { it.toMessage() }
            } else {
                _isInitialLoading.value = true
            }

            // Step 2: Sync from server
            when (val result = HiloApi.getMessages(chatId, page = 1)) {
                is NetworkResult.Success -> {
                    val pageResult = result.data
                    // Upsert server messages to Room
                    messageDao.insertAll(pageResult.messages.map { it.toEntity(chatId) })
                    // Reload from Room (now contains merged server + any pre-existing data)
                    val fresh = messageDao.getMessages(chatId)
                    _messages.value = fresh.map { it.toMessage() }
                    totalPages = pageResult.totalPages
                    currentPage = 1
                    startUpdates(chatId)
                }
                is NetworkResult.Loading -> {}
                is NetworkResult.Error -> {}
            }
            _isInitialLoading.value = false
        }
    }

    /**
     * Start receiving message updates for [chatId].
     * Preferred: WebSocket events from [HiloSocketManager] (zero battery drain).
     * Fallback: HTTP polling every 3s if the socket is not connected.
     */
    private fun startUpdates(chatId: String) {
        pollingJob?.cancel()
        pollingJob = null

        if (HiloSocketManager.isConnected) {
            // WebSocket path — no HTTP polling needed
            if (!socketListening) {
                HiloSocketManager.addListener(socketCallback)
                socketListening = true
            }
        } else {
            // Fallback: HTTP polling every 3s
            pollingJob = viewModelScope.launch {
                while (activeChatId == chatId) {
                    delay(3000)
                    when (val pollResult = HiloApi.getMessages(chatId, page = 1)) {
                        is NetworkResult.Success -> {
                            val incoming = pollResult.data.messages
                            val currentIds = _messages.value.map { it.id }.toSet()
                            val hasNew = incoming.any { it.id !in currentIds }

                            if (hasNew) {
                                // Upsert new messages to Room
                                messageDao.insertAll(incoming.map { it.toEntity(chatId) })
                                // Reload state from Room
                                val fresh = messageDao.getMessages(chatId)
                                _messages.value = fresh.map { it.toMessage() }
                            }
                        }
                        else -> { /* suppress background polling errors */ }
                    }
                }
            }
        }
    }

    fun loadMore() {
        val chatId = activeChatId ?: return
        if (_isLoadingMore.value || currentPage >= totalPages) return

        _isLoadingMore.value = true
        viewModelScope.launch {
            val nextPage = currentPage + 1
            when (val result = HiloApi.getMessages(chatId, page = nextPage)) {
                is NetworkResult.Success -> {
                    val pageResult = result.data
                    if (pageResult.messages.isNotEmpty()) {
                        // Upsert older messages to Room
                        messageDao.insertAll(pageResult.messages.map { it.toEntity(chatId) })
                        // Reload all from Room — ORDER BY timestampRaw DESC gives newest-first
                        val fresh = messageDao.getMessages(chatId)
                        _messages.value = fresh.map { it.toMessage() }
                        currentPage = nextPage
                    }
                }
                is NetworkResult.Error -> { /* suppress pagination error */ }
                is NetworkResult.Loading -> {}
            }
            _isLoadingMore.value = false
        }
    }

    fun sendMessage(text: String, onResult: (Boolean) -> Unit) {
        val chatId = activeChatId ?: return
        viewModelScope.launch {
            // Optimistic local echo so message appears immediately in this app
            val nowIso = java.time.Instant.now().toString()
            val displayTime = try {
                val timePart = nowIso.substringAfter('T').substringBefore('.')
                val parts = timePart.split(':')
                if (parts.size >= 2) "${parts[0]}:${parts[1]}" else "Ahora"
            } catch (_: Exception) { "Ahora" }
            val optimistic = Message(
                id = "local_${System.currentTimeMillis()}_${chatId}",
                text = text,
                timestamp = displayTime,
                timestampRaw = nowIso,
                isFromMe = true,
                type = "text",
                mediaUrl = null,
                senderId = "Me",
                senderName = "Tú"
            )
            _messages.value = listOf(optimistic) + _messages.value

            when (HiloApi.sendMessage(chatId, text)) {
                is NetworkResult.Success -> {
                    val trimmed = text.trim()
                    // Trigger IA action when user mentions @hilo in a monitored chat.
                    // We keep the WhatsApp message flow and additionally ask backend AI for this group.
                    if (trimmed.contains("@hilo", ignoreCase = true)) {
                        HiloApi.chatWithAiForGroup(chatId, trimmed)
                    }
                    refreshFirstPage()
                    onResult(true)
                }
                is NetworkResult.Error -> {
                    // rollback optimistic message on failure
                    _messages.value = _messages.value.filterNot { it.id == optimistic.id }
                    onResult(false)
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun sendMediaMessage(
        type: String,
        base64: String,
        mimetype: String,
        filename: String?,
        caption: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val chatId = activeChatId ?: return
        viewModelScope.launch {
            when (val res = HiloApi.sendMediaMessage(chatId, type, base64, mimetype, filename, caption)) {
                is NetworkResult.Success -> {
                    refreshFirstPage()
                    onResult(true, null)
                }
                is NetworkResult.Error -> onResult(false, res.message)
                is NetworkResult.Loading -> {}
            }
        }
    }

    private suspend fun refreshFirstPage() {
        val chatId = activeChatId ?: return
        _isRefreshing.value = true
        when (val res = HiloApi.getMessages(chatId, page = 1)) {
            is NetworkResult.Success -> {
                messageDao.insertAll(res.data.messages.map { it.toEntity(chatId) })
                val fresh = messageDao.getMessages(chatId)
                _messages.value = fresh.map { it.toMessage() }
            }
            else -> {}
        }
        _isRefreshing.value = false
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        if (socketListening) {
            HiloSocketManager.removeListener(socketCallback)
            socketListening = false
        }
    }

    fun performSearch(query: String) {
        val chatId = activeChatId ?: return
        if (query.isBlank()) {
            _searchResults.value = null
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            when (val res = HiloApi.searchMessages(query, chatId)) {
                is NetworkResult.Success -> _searchResults.value = res.data
                is NetworkResult.Error   -> _searchResults.value = emptyList()
                is NetworkResult.Loading -> {}
            }
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        _searchResults.value = null
        _isSearching.value = false
    }

    fun clear() {
        stopPolling() // also removes socket listener
        activeChatId = null
        _messages.value = emptyList()
        _error.value = null
        _searchResults.value = null
        // Room data intentionally kept — persists across sessions
    }

    /**
     * Exports all messages for the active chat as a CSV file saved to Downloads.
     * [from] / [to] are optional date strings in YYYY-MM-DD format.
     * [onResult] receives (success, message) where message is the saved path on success or error text.
     */
    fun exportCsv(from: String?, to: String?, onResult: (Boolean, String) -> Unit) {
        val chatId = activeChatId ?: return
        viewModelScope.launch {
            when (val result = HiloApi.exportMessages(chatId, from, to)) {
                is NetworkResult.Success -> {
                    try {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        val filename = "hilo-export-$ts.csv"
                        val savedPath = saveToDownloads(getApplication(), filename, result.data)
                        onResult(true, savedPath)
                    } catch (e: Exception) {
                        onResult(false, "Error al guardar: ${e.message}")
                    }
                }
                is NetworkResult.Error -> onResult(false, result.message)
                is NetworkResult.Loading -> {}
            }
        }
    }

    private fun saveToDownloads(app: android.app.Application, filename: String, content: String): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/csv")
            }
            val uri = app.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: throw Exception("No se pudo crear el archivo en Descargas")
            app.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            "Descargas/$filename"
        } else {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(dir, filename)
            file.writeText(content, Charsets.UTF_8)
            file.absolutePath
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}

