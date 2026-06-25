package com.example.hiloapp

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sealed class for network results — better than kotlin.Result for UI state.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int = 0) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()

    val isSuccess get() = this is Success
    fun dataOrNull(): T? = (this as? Success)?.data
}

/**
 * Callback interface for session expiration — lets the API layer
 * notify the UI layer when a 401 is received.
 */
interface SessionExpiredListener {
    fun onSessionExpired()
}

object HiloApi {
    private val TAG = "HiloApi"
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    var baseUrl: String = BuildConfig.SERVER_URL
        set(value) {
            field = if (value.endsWith("/")) value.trimEnd('/') else value
        }
    var token: String = ""
    var userId: String = ""
    var myPhone: String = ""
    var mySessionId: String? = null

    var sessionExpiredListener: SessionExpiredListener? = null

    // Max messages to keep in memory per chat (sliding window)
    const val MAX_MESSAGES_IN_MEMORY = 500

    /**
     * Auth interceptor — automatically adds Bearer token and handles 401.
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // Auto-add token if available and not already set
        if (token.isNotEmpty() && originalRequest.header("Authorization") == null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        // Handle 401 — token expired
        if (response.code == 401 && token.isNotEmpty()) {
            Log.w(TAG, "Received 401 — session expired")
            token = ""
            userId = ""
            sessionExpiredListener?.onSessionExpired()
        }

        response
    }

    /**
     * Retry interceptor — retries on network errors (up to 2 retries).
     */
    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var response: Response? = null
        var lastException: IOException? = null

        for (attempt in 0..2) {
            try {
                response?.close()
                response = chain.proceed(request)
                if (response.isSuccessful || response.code != 503) {
                    return@Interceptor response
                }
            } catch (e: IOException) {
                lastException = e
                if (attempt < 2) {
                    Thread.sleep((attempt + 1) * 1000L)
                }
            }
        }

        response ?: throw lastException ?: IOException("Request failed after retries")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(retryInterceptor)
        .build()

    fun isConfigured(): Boolean {
        return baseUrl.isNotEmpty() && token.isNotEmpty()
    }

    /**
     * Restore session from persisted data (called on app start).
     */
    fun restoreSession(savedToken: String, savedUserId: String, savedPhone: String, savedSessionId: String?, savedBaseUrl: String) {
        if (savedToken.isNotEmpty()) token = savedToken
        if (savedUserId.isNotEmpty()) userId = savedUserId
        if (savedPhone.isNotEmpty()) myPhone = savedPhone
        if (savedSessionId != null) mySessionId = savedSessionId
        if (savedBaseUrl.isNotEmpty()) baseUrl = savedBaseUrl
    }

    fun clearSession() {
        token = ""
        userId = ""
        myPhone = ""
        mySessionId = null
    }

    // =========================================================================
    // AUTH
    // =========================================================================

    suspend fun register(email: String, password: String): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/auth/register"
        val payload = gson.toJson(mapOf("email" to email, "password" to password))
        val body = payload.toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    NetworkResult.Success(true)
                } else {
                    Log.e(TAG, "Register failed: code=${response.code} body=$responseStr")
                    val msg = if (response.code == 409) "Email ya registrado" else "Error al registrar"
                    NetworkResult.Error(msg, response.code)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Register network error", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun login(email: String, password: String): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/auth/login"
        val payload = gson.toJson(mapOf("email" to email, "password" to password))
        val body = payload.toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            // Don't auto-add old token for login
            .removeHeader("Authorization")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val map = gson.fromJson(responseStr, Map::class.java)
                    token = (map["token"] as? String) ?: ""
                    val userMap = map["user"] as? Map<*, *>
                    userId = (userMap?.get("id") as? String) ?: ""
                    Log.d(TAG, "Login successful: userId=$userId")
                    NetworkResult.Success(token.isNotEmpty())
                } else {
                    Log.e(TAG, "Login failed: code=${response.code} body=$responseStr")
                    NetworkResult.Error("Error de inicio de sesión (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun getWhatsAppStatus(): NetworkResult<WhatsAppStatus> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/auth/whatsapp-status"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val statusObj = gson.fromJson(responseStr, WhatsAppStatus::class.java)
                    if (!statusObj.phone.isNullOrEmpty()) {
                        myPhone = statusObj.phone
                    } else if (statusObj.status == "qr_ready" || statusObj.status == "disconnected") {
                        myPhone = ""
                    }
                    mySessionId = statusObj.id
                    Log.d(TAG, "WhatsApp status: ${statusObj.status}")
                    NetworkResult.Success(statusObj)
                } else {
                    Log.e(TAG, "Get WhatsApp status failed: code=${response.code}")
                    NetworkResult.Error("Error al obtener estado de WhatsApp", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp status exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun startWhatsAppEngine(): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/auth/whatsapp-start"
        val body = ByteArray(0).toRequestBody(null, 0, 0)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    NetworkResult.Success(true)
                } else {
                    NetworkResult.Error("Error al iniciar motor: ${response.code}", response.code)
                }
            }
        } catch (e: Exception) {
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun getPairingCode(phoneNumber: String): NetworkResult<String> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/auth/pairing-code"
        val payload = gson.toJson(mapOf("phoneNumber" to phoneNumber))
        val body = payload.toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val map = gson.fromJson(responseStr, Map::class.java)
                    val code = (map["code"] as? String) ?: ""
                    Log.d(TAG, "Got pairing code: $code")
                    NetworkResult.Success(code)
                } else {
                    val errorMsg = try {
                        val errorMap = gson.fromJson(responseStr, Map::class.java)
                        (errorMap["message"] as? String) ?: "Error al solicitar código"
                    } catch (e: Exception) {
                        "Error al solicitar código (${response.code})"
                    }
                    NetworkResult.Error(errorMsg, response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pairing code exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    // =========================================================================
    // GROUPS / CHATS
    // =========================================================================

    suspend fun getGroups(monitoredOnly: Boolean = false): NetworkResult<List<Chat>> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/groups?monitoredOnly=$monitoredOnly"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val rawGroups = gson.fromJson(responseStr, Array<RawGroup>::class.java)
                    val chats = rawGroups.map { g ->
                        Chat(
                            id = g.id,
                            contactName = g.displayName ?: g.name,
                            lastMessage = if (g.isMonitored) "Monitoreando chat" else "Monitoreo inactivo",
                            timestamp = "",
                            unreadCount = 0,
                            avatarUrl = getAvatarUrl(g.id),
                            isMonitored = g.isMonitored,
                            sessionId = g.sessionId
                        )
                    }
                    NetworkResult.Success(chats)
                } else {
                    NetworkResult.Error("Error al cargar grupos (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get groups exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun toggleMonitoring(groupId: String, isMonitored: Boolean, sessionId: String?): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/groups/$groupId"
        val actualSessionId = sessionId ?: mySessionId
        val payload = gson.toJson(mapOf("isMonitored" to isMonitored, "sessionId" to actualSessionId))
        val body = payload.toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .patch(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    NetworkResult.Success(true)
                } else {
                    NetworkResult.Error("Error al actualizar monitoreo (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Toggle monitoring exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    // =========================================================================
    // AI
    // =========================================================================

    suspend fun chatWithAi(message: String): NetworkResult<String> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/summaries/chat"
        val payload = gson.toJson(mapOf("message" to message))
        val body = payload.toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = gson.fromJson(responseStr, Map::class.java)
                    val reply = json["response"] as? String ?: "Sin respuesta"
                    NetworkResult.Success(reply)
                } else {
                    NetworkResult.Error("Error AI (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chat AI exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    // =========================================================================
    // MESSAGES
    // =========================================================================

    suspend fun sendMessage(groupId: String, text: String): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/messages?groupId=$groupId"
        val payload = gson.toJson(mapOf("text" to text))
        val body = payload.toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    NetworkResult.Success(true)
                } else {
                    NetworkResult.Error("Error al enviar mensaje (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send message exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun sendMediaMessage(
        groupId: String,
        type: String,
        base64: String,
        mimetype: String,
        filename: String?,
        caption: String
    ): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/messages?groupId=$groupId"
        val mediaPayload = mapOf(
            "base64" to base64,
            "mimetype" to mimetype,
            "filename" to filename
        )
        val payload = mapOf(
            "text" to caption,
            "type" to type,
            "media" to mediaPayload
        )
        val body = gson.toJson(payload).toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    NetworkResult.Success(true)
                } else {
                    NetworkResult.Error("Error al enviar archivo multimedia (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send media message exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun getMessages(groupId: String, page: Int = 1, limit: Int = 100): NetworkResult<MessagesPageResult> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/messages?groupId=$groupId&page=$page&limit=$limit"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val resObj = gson.fromJson(responseStr, MessagesPageResponse::class.java)
                    val messages = resObj.messages.map { m ->
                        val isFromMe = m.senderId == "Me" || 
                                       m.senderName == "Tú" || 
                                       (myPhone.isNotEmpty() && m.senderId.contains(myPhone))
                        
                        val time = try {
                            val timePart = m.timestamp.substringAfter('T').substringBefore('.')
                            val parts = timePart.split(':')
                            if (parts.size >= 2) {
                                "${parts[0]}:${parts[1]}"
                            } else {
                                timePart
                            }
                        } catch (e: Exception) {
                            "12:00"
                        }

                        Message(
                            id = m.id,
                            text = m.content,
                            timestamp = time,
                            isFromMe = isFromMe,
                            type = m.type,
                            mediaUrl = if (m.type == "image" || m.type == "video" || m.type == "sticker" || m.type == "audio" || m.type == "document" || m.type == "ptt") getMediaUrl(groupId, m.waMessageId) else null,
                            senderId = m.senderId,
                            senderName = m.senderName
                        )
                    }
                    NetworkResult.Success(MessagesPageResult(
                        messages = messages,
                        total = resObj.total,
                        page = resObj.page,
                        totalPages = resObj.totalPages
                    ))
                } else {
                    NetworkResult.Error("Error al cargar mensajes (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get messages exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    fun getAvatarUrl(groupId: String): String {
        return "$baseUrl/api/hilo/groups/$groupId/avatar"
    }

    fun getMediaUrl(groupId: String, messageId: String): String {
        return "$baseUrl/api/hilo/messages/$groupId/media/$messageId"
    }
}

// =========================================================================
// DATA MODELS
// =========================================================================

data class WhatsAppStatus(
    val id: String?,
    val name: String?,
    val status: String,
    val phone: String?,
    val pushName: String?
)

data class RawGroup(
    val id: String,
    val name: String,
    val displayName: String?,
    val isMonitored: Boolean,
    val sessionId: String?
)

data class RawMessage(
    val id: String,
    val waMessageId: String,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val type: String,
    val timestamp: String
)

data class MessagesResponse(
    val messages: List<RawMessage>,
    val total: Int
)

data class MessagesPageResponse(
    val messages: List<RawMessage>,
    val total: Int,
    val page: Int,
    val totalPages: Int
)

data class MessagesPageResult(
    val messages: List<Message>,
    val total: Int,
    val page: Int,
    val totalPages: Int
)
