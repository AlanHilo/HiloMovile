package com.example.hiloapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles state and business logic for the chat detail screen (message list, real-time polling, pagination, sending messages).
 */
class MessagesViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(false)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentPage = 1
    private var totalPages = 1
    private var activeChatId: String? = null
    private var pollingJob: Job? = null

    fun loadMessages(chatId: String) {
        if (activeChatId == chatId) return
        activeChatId = chatId
        stopPolling()
        _messages.value = emptyList()
        currentPage = 1
        totalPages = 1

        viewModelScope.launch {
            _isInitialLoading.value = true
            _error.value = null
            
            when (val result = HiloApi.getMessages(chatId, page = 1)) {
                is NetworkResult.Success -> {
                    val pageResult = result.data
                    _messages.value = pageResult.messages
                    totalPages = pageResult.totalPages
                    currentPage = 1
                    
                    // Start polling after successful load
                    startPolling(chatId)
                }
                is NetworkResult.Error -> {
                    _error.value = result.message
                }
                is NetworkResult.Loading -> {}
            }
            _isInitialLoading.value = false
        }
    }

    private fun startPolling(chatId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (activeChatId == chatId) {
                delay(3000)
                when (val pollResult = HiloApi.getMessages(chatId, page = 1)) {
                    is NetworkResult.Success -> {
                        val pageResult = pollResult.data
                        val newMessages = pageResult.messages
                        val currentList = _messages.value
                        val existingIds = currentList.map { it.id }.toSet()
                        val uniqueNewMessages = newMessages.filter { it.id !in existingIds }
                        
                        if (uniqueNewMessages.isNotEmpty()) {
                            var updatedList = uniqueNewMessages + currentList
                            // Enforce sliding window memory limit to prevent memory leaks
                            if (updatedList.size > HiloApi.MAX_MESSAGES_IN_MEMORY) {
                                updatedList = updatedList.take(HiloApi.MAX_MESSAGES_IN_MEMORY)
                            }
                            _messages.value = updatedList
                        }
                    }
                    else -> {
                        // Suppress background polling errors to keep UX smooth
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
                        val currentList = _messages.value
                        val existingIds = currentList.map { it.id }.toSet()
                        val uniqueMoreMessages = pageResult.messages.filter { it.id !in existingIds }
                        
                        var updatedList = currentList + uniqueMoreMessages
                        if (updatedList.size > HiloApi.MAX_MESSAGES_IN_MEMORY) {
                            updatedList = updatedList.take(HiloApi.MAX_MESSAGES_IN_MEMORY)
                        }
                        _messages.value = updatedList
                        currentPage = nextPage
                    }
                }
                is NetworkResult.Error -> {
                    // Suppress pagination error or log it
                }
                is NetworkResult.Loading -> {}
            }
            _isLoadingMore.value = false
        }
    }

    fun sendMessage(text: String, onResult: (Boolean) -> Unit) {
        val chatId = activeChatId ?: return
        viewModelScope.launch {
            when (HiloApi.sendMessage(chatId, text)) {
                is NetworkResult.Success -> {
                    refreshFirstPage()
                    onResult(true)
                }
                is NetworkResult.Error -> {
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
                is NetworkResult.Error -> {
                    onResult(false, res.message)
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    private suspend fun refreshFirstPage() {
        val chatId = activeChatId ?: return
        when (val res = HiloApi.getMessages(chatId, page = 1)) {
            is NetworkResult.Success -> {
                val pageResult = res.data
                val currentList = _messages.value
                val existingIds = currentList.map { it.id }.toSet()
                val uniqueNewMessages = pageResult.messages.filter { it.id !in existingIds }
                if (uniqueNewMessages.isNotEmpty()) {
                    _messages.value = uniqueNewMessages + currentList
                }
            }
            else -> {}
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun clear() {
        stopPolling()
        activeChatId = null
        _messages.value = emptyList()
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
