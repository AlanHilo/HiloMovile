package com.example.hiloapp

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles state and business logic for the home screen (chat list, monitoring toggles, polling).
 */
class ChatsViewModel(application: Application) : AndroidViewModel(application) {

    // Monitored chats (only those active) — polled every 5s using database-only fast route
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    // All available chats from WhatsApp session — fetched on-demand when user configures monitoring
    private val _allChats = MutableStateFlow<List<Chat>>(emptyList())
    val allChats: StateFlow<List<Chat>> = _allChats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isTogglingMonitor = MutableStateFlow(false)
    val isTogglingMonitor: StateFlow<Boolean> = _isTogglingMonitor.asStateFlow()

    // Local overrides for monitoring state — prevents polling from reverting user toggles
    private val monitorOverrides = mutableStateMapOf<String, Boolean>()

    private var pollingJob: Job? = null

    fun startPolling() {
        if (pollingJob != null && pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            var firstLoad = _chats.value.isEmpty()
            while (true) {
                if (firstLoad) {
                    _isLoading.value = true
                }
                // Poll ONLY monitored chats from the high-performance database route (prevents OpenWA overload)
                when (val result = HiloApi.getGroups(monitoredOnly = true)) {
                    is NetworkResult.Success -> {
                        val serverChats = result.data
                        // Merge: use local overrides if present, otherwise use server state
                        _chats.value = serverChats.map { chat ->
                            val localOverride = monitorOverrides[chat.id]
                            if (localOverride != null) {
                                chat.copy(isMonitored = localOverride)
                            } else {
                                chat
                            }
                        }
                        _error.value = null
                    }
                    is NetworkResult.Error -> {
                        if (firstLoad) {
                            _error.value = result.message
                        }
                    }
                    is NetworkResult.Loading -> {}
                }
                if (firstLoad) {
                    _isLoading.value = false
                    firstLoad = false
                }
                delay(5000)
            }
        }
    }

    fun loadAllChats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            // Load ALL chats from the WhatsApp session (called on-demand when entering the monitor tab)
            when (val result = HiloApi.getGroups(monitoredOnly = false)) {
                is NetworkResult.Success -> {
                    val serverChats = result.data
                    _allChats.value = serverChats.map { chat ->
                        val localOverride = monitorOverrides[chat.id]
                        if (localOverride != null) {
                            chat.copy(isMonitored = localOverride)
                        } else {
                            chat
                        }
                    }
                }
                is NetworkResult.Error -> {
                    _error.value = result.message
                }
                is NetworkResult.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun toggleMonitoring(chat: Chat, isMonitored: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isTogglingMonitor.value = true
            
            // Immediately save local override so polling doesn't revert
            monitorOverrides[chat.id] = isMonitored
            
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

            when (val res = HiloApi.toggleMonitoring(chat.id, isMonitored, chat.sessionId)) {
                is NetworkResult.Success -> {
                    onResult(true)
                }
                is NetworkResult.Error -> {
                    // Revert the local override on failure
                    monitorOverrides.remove(chat.id)
                    
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
        _chats.value = emptyList()
        _allChats.value = emptyList()
        monitorOverrides.clear()
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
