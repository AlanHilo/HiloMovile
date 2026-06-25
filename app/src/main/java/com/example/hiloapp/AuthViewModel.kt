package com.example.hiloapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles authentication state: login, register, session persistence,
 * WhatsApp status, pairing. Replaces scattered auth logic from MainActivity.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    val sessionManager = SessionManager(application)

    // Whether we're still checking if a saved session exists
    private val _isCheckingSession = MutableStateFlow(true)
    val isCheckingSession: StateFlow<Boolean> = _isCheckingSession.asStateFlow()

    // true = user has a valid token
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // true = WhatsApp is connected and ready
    private val _isWhatsAppReady = MutableStateFlow(false)
    val isWhatsAppReady: StateFlow<Boolean> = _isWhatsAppReady.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        // Set up session expiration listener
        HiloApi.sessionExpiredListener = object : SessionExpiredListener {
            override fun onSessionExpired() {
                viewModelScope.launch {
                    sessionManager.clearSession()
                    _isLoggedIn.value = false
                    _isWhatsAppReady.value = false
                    _error.value = "Tu sesión ha expirado. Inicia sesión de nuevo."
                }
            }
        }

        // Try to restore saved session
        tryRestoreSession()
    }

    private fun tryRestoreSession() {
        viewModelScope.launch {
            _isCheckingSession.value = true
            val savedToken = sessionManager.getToken()
            if (savedToken.isNotEmpty()) {
                val savedUserId = sessionManager.getUserId()
                val savedPhone = sessionManager.getMyPhone()
                val savedSessionId = sessionManager.getSessionId()
                val savedBaseUrl = sessionManager.getBaseUrl()

                HiloApi.restoreSession(savedToken, savedUserId, savedPhone, savedSessionId, savedBaseUrl)
                _isLoggedIn.value = true

                // Check WhatsApp status
                val statusResult = HiloApi.getWhatsAppStatus()
                if (statusResult is NetworkResult.Success) {
                    val status = statusResult.data
                    _isWhatsAppReady.value = status.status == "ready" || status.status == "connected"
                    if (status.phone != null) {
                        sessionManager.saveWhatsAppInfo(status.phone, status.id)
                    }
                }
            }
            _isCheckingSession.value = false
        }
    }

    fun login(email: String, password: String, serverUrl: String, onResult: (isWhatsAppReady: Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Set the base URL
            HiloApi.baseUrl = serverUrl
            sessionManager.saveBaseUrl(serverUrl)

            when (val result = HiloApi.login(email, password)) {
                is NetworkResult.Success -> {
                    // Persist session
                    sessionManager.saveSession(HiloApi.token, HiloApi.userId)

                    _isLoggedIn.value = true

                    // Check WhatsApp
                    val statusResult = HiloApi.getWhatsAppStatus()
                    var whatsAppReady = false
                    if (statusResult is NetworkResult.Success) {
                        val status = statusResult.data
                        whatsAppReady = status.status == "ready" || status.status == "connected"
                        _isWhatsAppReady.value = whatsAppReady
                        if (status.phone != null) {
                            sessionManager.saveWhatsAppInfo(status.phone, status.id)
                        }
                    }

                    _isLoading.value = false
                    onResult(whatsAppReady)
                }
                is NetworkResult.Error -> {
                    _error.value = result.message
                    _isLoading.value = false
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun register(email: String, password: String, serverUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _successMessage.value = null

            HiloApi.baseUrl = serverUrl
            sessionManager.saveBaseUrl(serverUrl)

            when (val result = HiloApi.register(email, password)) {
                is NetworkResult.Success -> {
                    _successMessage.value = "¡Registro exitoso! Inicia sesión."
                    _isLoading.value = false
                }
                is NetworkResult.Error -> {
                    _error.value = result.message
                    _isLoading.value = false
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            HiloApi.clearSession()
            sessionManager.clearSession()
            _isLoggedIn.value = false
            _isWhatsAppReady.value = false
            _error.value = null
        }
    }

    fun clearError() { _error.value = null }
    fun clearSuccess() { _successMessage.value = null }

    fun markWhatsAppReady() {
        _isWhatsAppReady.value = true
    }
}
