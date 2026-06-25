package com.example.hiloapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles state and business logic for the Hilo AI chatbot screen.
 */
class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<AiMessage>>(listOf(
        AiMessage("1", "¡Hola! Soy Hilo AI. ¿En qué te puedo ayudar hoy? Puedes pedirme resúmenes de tus chats o buscar información específica.", false)
    ))
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _isAiTyping = MutableStateFlow(false)
    val isAiTyping: StateFlow<Boolean> = _isAiTyping.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank() || _isAiTyping.value) return

        val userMsg = AiMessage(System.currentTimeMillis().toString(), text, true)
        _messages.value = _messages.value + userMsg
        _isAiTyping.value = true

        viewModelScope.launch {
            val res = HiloApi.chatWithAi(text)
            val reply = when (res) {
                is NetworkResult.Success -> res.data
                is NetworkResult.Error -> "Hubo un error de conexión con la IA: ${res.message}"
                is NetworkResult.Loading -> "Hilo AI está pensando..."
            }
            val aiMsg = AiMessage((System.currentTimeMillis() + 1).toString(), reply, false)
            _isAiTyping.value = false
            _messages.value = _messages.value + aiMsg
        }
    }

    fun clear() {
        _messages.value = listOf(
            AiMessage("1", "¡Hola! Soy Hilo AI. ¿En qué te puedo ayudar hoy? Puedes pedirme resúmenes de tus chats o buscar información específica.", false)
        )
        _isAiTyping.value = false
    }
}
