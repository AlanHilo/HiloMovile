package com.example.hiloapp

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hiloapp.db.HiloDatabase
import com.example.hiloapp.db.toChat
import com.example.hiloapp.db.toEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Handles state and business logic for the home screen (chat list, monitoring toggles, polling).
 *
 * Caching strategy:
 *  - Room DB is the source of truth for the chat list — survives app restarts.
 *  - On start: emit from Room instantly, then sync from server.
 *  - loadAllChats: retries with exponential backoff until the full list is obtained.
 *  - All server responses are persisted to Room so the user always sees something.
 */
class ChatsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HiloDatabase.getInstance(application)
    private val messageDao = db.messageDao()
    private val chatDao = db.chatDao()

    // Monitored chats (only those active) — polled every 5s using database-only fast route
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    // All available chats from WhatsApp session — cached in Room, synced from server
    private val _allChats = MutableStateFlow<List<Chat>>(emptyList())
    val allChats: StateFlow<List<Chat>> = _allChats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Separate loading state for the "all chats" (Monitor tab) fetch
    private val _isLoadingAllChats = MutableStateFlow(false)
    val isLoadingAllChats: StateFlow<Boolean> = _isLoadingAllChats.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isTogglingMonitor = MutableStateFlow(false)
    val isTogglingMonitor: StateFlow<Boolean> = _isTogglingMonitor.asStateFlow()

    // True when the backend returned a partial WhatsApp chat list (timeout fetching from WA Web)
    private val _isPartialList = MutableStateFlow(false)
    val isPartialList: StateFlow<Boolean> = _isPartialList.asStateFlow()
    private val _openWaHealthMessage = MutableStateFlow<String?>(null)
    val openWaHealthMessage: StateFlow<String?> = _openWaHealthMessage.asStateFlow()
    private val _isRestartingServices = MutableStateFlow(false)
    val isRestartingServices: StateFlow<Boolean> = _isRestartingServices.asStateFlow()

    // Local overrides for monitoring state — prevents polling from reverting user toggles
    private val monitorOverrides = mutableStateMapOf<String, Boolean>()

    private var pollingJob: Job? = null
    private var allChatsJob: Job? = null
    private var allChatsPeriodicJob: Job? = null
    private var socketListening = false
    private var lastSocketRefreshAt = 0L

    private var allChatsLoaded = false

    // Socket.IO callback — any new message triggers an instant list refresh.
    private val socketCallback: (HiloSocketManager.MessageEvent) -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastSocketRefreshAt >= 1500L) {
            lastSocketRefreshAt = now
            viewModelScope.launch {
                refreshMonitoredChats(showLoading = false)
                refreshAllChats(silent = true, suppressError = true)
            }
        }
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return

        if (!socketListening) {
            HiloSocketManager.addListener(socketCallback)
            socketListening = true
        }

        if (allChatsPeriodicJob?.isActive != true) {
            allChatsPeriodicJob = viewModelScope.launch {
                while (true) {
                    delay(15_000)
                    refreshAllChats(silent = true, suppressError = true)
                }
            }
        }

        pollingJob = viewModelScope.launch {
            // Warm up from Room instantly
            val cachedMonitored = chatDao.getMonitoredChats()
            if (cachedMonitored.isNotEmpty()) {
                _chats.value = cachedMonitored.map { it.toChat() }
            }
            val cachedAll = chatDao.getAllChats()
            if (cachedAll.isNotEmpty()) {
                _allChats.value = cachedAll.map { it.toChat() }
            }

            // Always trigger a full refresh from backend.
            // Cache can contain only monitored rows from previous sessions,
            // so relying on cache presence can hide "Disponibles".
            loadAllChats()

            refreshMonitoredChats(showLoading = _chats.value.isEmpty())
            while (true) {
                delay(5000)
                refreshMonitoredChats(showLoading = false)
            }
        }
    }

    /**
     * Loads ALL chats from the server with exponential backoff retry.
     * - First emits cached chats from Room instantly (no blank screen).
     * - Then syncs from server, retrying with increasing delays until full list.
     * - Persists all results to Room so data survives restarts.
     */
    fun loadAllChats() {
        allChatsJob?.cancel()
        allChatsJob = viewModelScope.launch {
            _error.value = null

            // ── Step 1: Emit cached chats from Room instantly ──
            val cached = chatDao.getAllChats()
            if (cached.isNotEmpty()) {
                _allChats.value = cached.map { it.toChat() }
                allChatsLoaded = true
            } else {
                _isLoadingAllChats.value = true
            }

            // ── Step 2: Sync from server with exponential backoff ──
            var attempt = 0
            val maxAttempts = 8 // 3s + 6s + 12s + 24s + 48s + 96s + 192s + 384s ≈ 12 min total

            while (attempt < maxAttempts) {
                val fullList = refreshAllChats(silent = false, suppressError = false)
                if (fullList == true) break
                if (fullList == null) break // hard error

                attempt++
                if (attempt >= maxAttempts) break

                // Exponential backoff: 3s, 6s, 12s, 24s, 48s, 96s, 192s, 384s
                val delayMs = min(3000L * (1L shl attempt), 600_000L)
                _isLoadingAllChats.value = false
                delay(delayMs)
            }
            _isLoadingAllChats.value = false
        }
    }

    private suspend fun refreshMonitoredChats(showLoading: Boolean) {
        if (showLoading) _isLoading.value = true
        when (val result = HiloApi.getGroups(monitoredOnly = true)) {
            is NetworkResult.Success -> {
                val serverChats = result.data.chats.map { chat ->
                    val localOverride = monitorOverrides[chat.id]
                    if (localOverride != null) chat.copy(isMonitored = localOverride) else chat
                }

                // Keep optimistic local "monitor=true" toggles visible in Chats tab even if
                // backend monitoredOnly list is still catching up.
                val serverIds = serverChats.map { it.id }.toSet()
                val locallyForcedMonitored = _allChats.value
                    .filter { monitorOverrides[it.id] == true && it.id !in serverIds }
                    .map { it.copy(isMonitored = true) }

                val mergedMonitored = (serverChats + locallyForcedMonitored)
                    .filter { it.isMonitored }
                    .distinctBy { it.id }

                val enriched = enrichWithLastMessages(mergedMonitored)
                _chats.value = enriched
                chatDao.insertAll(enriched.map { it.toEntity() })
                _error.value = null
            }
            is NetworkResult.Error -> {
                if (showLoading) _error.value = result.message
            }
            is NetworkResult.Loading -> {}
        }
        if (showLoading) _isLoading.value = false
    }

    /**
     * @return true when full list is received, false when partial list is received, null on hard error
     */
    private suspend fun refreshAllChats(silent: Boolean, suppressError: Boolean): Boolean? {
        if (!silent) _isLoadingAllChats.value = true
        when (val result = HiloApi.getGroups(monitoredOnly = false)) {
            is NetworkResult.Success -> {
                val groupsResult = result.data
                val serverChats = groupsResult.chats.map { chat ->
                    val localOverride = monitorOverrides[chat.id]
                    if (localOverride != null) chat.copy(isMonitored = localOverride) else chat
                }
                val enriched = enrichWithLastMessages(serverChats)
                chatDao.insertAll(enriched.map { it.toEntity() })
                _allChats.value = enriched
                allChatsLoaded = true
                _isPartialList.value = groupsResult.isPartialList
                _openWaHealthMessage.value = if (groupsResult.isPartialList) {
                    "OpenWA responde parcial: ${enriched.size} chats cargados. Reintentando..."
                } else if (enriched.size < 50) {
                    "Posible falla de OpenWA: solo ${enriched.size} chats detectados."
                } else {
                    null
                }
                if (!silent) _isLoadingAllChats.value = false
                return !groupsResult.isPartialList
            }
            is NetworkResult.Error -> {
                if (!suppressError) _error.value = result.message
                _openWaHealthMessage.value = "OpenWA no responde correctamente: ${result.message}"
                if (!silent) _isLoadingAllChats.value = false
                return null
            }
            is NetworkResult.Loading -> {}
        }
        if (!silent) _isLoadingAllChats.value = false
        return null
    }

    /** Manual retry — called from the UI "Reintentar" button */
    fun retryLoadAllChats() {
        loadAllChats()
    }

    fun restartOpenWaServices(onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isRestartingServices.value = true
            when (val restart = HiloApi.restartWhatsAppServices()) {
                is NetworkResult.Success -> {
                    delay(1500)
                    loadAllChats()
                    _isRestartingServices.value = false
                    onDone(true, null)
                }
                is NetworkResult.Error -> {
                    _isRestartingServices.value = false
                    onDone(false, restart.message)
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        allChatsPeriodicJob?.cancel()
        allChatsPeriodicJob = null
        if (socketListening) {
            HiloSocketManager.removeListener(socketCallback)
            socketListening = false
        }
    }

    /**
     * Enriches a list of [Chat]s with the real last message stored in Room DB.
     * If no messages are cached for a chat, the server-provided placeholder is kept.
     */
    private suspend fun enrichWithLastMessages(chats: List<Chat>): List<Chat> {
        return chats.map { chat ->
            val last = messageDao.getLastMessage(chat.id)
            if (last != null) {
                val displayText = when {
                    last.type == "image"    -> "🖼️ Imagen"
                    last.type == "video"    -> "🎥 Video"
                    last.type == "audio" || last.type == "ptt" -> "🎤 Audio"
                    last.type == "document" -> "📎 Archivo"
                    last.text.isNotBlank()  -> last.text
                    else                    -> chat.lastMessage
                }
                // Convert ISO timestamp to HH:mm for display
                val displayTime = try {
                    val timePart = last.timestampRaw.substringAfter('T').substringBefore('.')
                    val parts = timePart.split(':')
                    if (parts.size >= 2) "${parts[0]}:${parts[1]}" else chat.timestamp
                } catch (e: Exception) { chat.timestamp }
                chat.copy(lastMessage = displayText, timestamp = displayTime)
            } else {
                chat
            }
        }
    }

    fun toggleMonitoring(chat: Chat, isMonitored: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isTogglingMonitor.value = true
            
            // Immediately save local override so polling doesn't revert
            monitorOverrides[chat.id] = isMonitored

            // Persist to Room immediately
            chatDao.updateMonitored(chat.id, isMonitored)
            
            // 1. Update monitored list
            if (isMonitored) {
                val currentMonitored = _chats.value.toMutableList()
                if (currentMonitored.none { it.id == chat.id }) {
                    currentMonitored.add(chat.copy(isMonitored = true))
                    _chats.value = currentMonitored
                }
            } else {
                _chats.value = _chats.value.filter { it.id != chat.id }
            }

            // 2. Update all chats list
            _allChats.value = _allChats.value.map {
                if (it.id == chat.id) it.copy(isMonitored = isMonitored) else it
            }

            // 2.1 Keep Chats tab in sync immediately (optimistic UI)
            if (isMonitored) {
                val monitored = _chats.value.toMutableList()
                val updatedChat = chat.copy(isMonitored = true)
                val idx = monitored.indexOfFirst { it.id == chat.id }
                if (idx >= 0) monitored[idx] = updatedChat else monitored.add(updatedChat)
                _chats.value = monitored
            } else {
                _chats.value = _chats.value.filter { it.id != chat.id }
            }

            when (val res = HiloApi.toggleMonitoring(chat.id, isMonitored, chat.sessionId)) {
                is NetworkResult.Success -> {
                    onResult(true)
                }
                is NetworkResult.Error -> {
                    // Revert the local override on failure
                    monitorOverrides.remove(chat.id)
                    
                    // Revert in Room
                    chatDao.updateMonitored(chat.id, !isMonitored)
                    
                    _allChats.value = _allChats.value.map {
                        if (it.id == chat.id) it.copy(isMonitored = !isMonitored) else it
                    }

                    if (isMonitored) {
                        _chats.value = _chats.value.filter { it.id != chat.id }
                    } else {
                        val currentMonitored = _chats.value.toMutableList()
                        if (currentMonitored.none { it.id == chat.id }) {
                            currentMonitored.add(chat.copy(isMonitored = true))
                            _chats.value = currentMonitored
                        }
                    }
                    onResult(false)
                }
                is NetworkResult.Loading -> {}
            }
            _isTogglingMonitor.value = false
        }
    }

    fun clear() {
        stopPolling()
        allChatsJob?.cancel()
        allChatsJob = null
        allChatsLoaded = false
        _chats.value = emptyList()
        _allChats.value = emptyList()
        monitorOverrides.clear()
        _error.value = null
        _isPartialList.value = false
        // Clear Room cache on logout
        viewModelScope.launch {
            chatDao.deleteAll()
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Toggles the AI auto-reply flag for a monitored group without changing its monitoring state.
     */
    fun toggleAiAutoReply(chat: Chat, aiAutoReply: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Optimistic local update
            chatDao.updateAiAutoReply(chat.id, aiAutoReply)
            _allChats.value = _allChats.value.map {
                if (it.id == chat.id) it.copy(aiAutoReply = aiAutoReply) else it
            }
            _chats.value = _chats.value.map {
                if (it.id == chat.id) it.copy(aiAutoReply = aiAutoReply) else it
            }

            when (val res = HiloApi.toggleMonitoring(chat.id, chat.isMonitored, chat.sessionId, aiAutoReply)) {
                is NetworkResult.Success -> onResult(true)
                is NetworkResult.Error -> {
                    // Revert on failure
                    chatDao.updateAiAutoReply(chat.id, !aiAutoReply)
                    _allChats.value = _allChats.value.map {
                        if (it.id == chat.id) it.copy(aiAutoReply = !aiAutoReply) else it
                    }
                    _chats.value = _chats.value.map {
                        if (it.id == chat.id) it.copy(aiAutoReply = !aiAutoReply) else it
                    }
                    onResult(false)
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    /**
     * Forces a WhatsApp history sync for a monitored group.
     */
    fun syncHistory(groupId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (val res = HiloApi.syncHistory(groupId)) {
                is NetworkResult.Success -> onResult(true)
                is NetworkResult.Error -> onResult(false)
                is NetworkResult.Loading -> {}
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
