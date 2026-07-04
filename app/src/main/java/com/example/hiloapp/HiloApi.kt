package com.example.hiloapp

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    private fun encPath(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun encQuery(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

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

    suspend fun startWhatsAppEngine(): NetworkResult<Pair<Boolean, String?>> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/auth/whatsapp-start"
        val body = ByteArray(0).toRequestBody(null, 0, 0)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                val json = try { gson.fromJson(responseStr, Map::class.java) } catch (_: Exception) { null }
                if (response.isSuccessful) {
                    val ok = json?.get("ok") as? Boolean ?: true
                    val status = json?.get("status") as? String ?: "unknown"
                    val message = json?.get("message") as? String
                    if (ok) {
                        NetworkResult.Success(true to status)
                    } else {
                        NetworkResult.Error(message ?: "Error al iniciar motor: $status")
                    }
                } else {
                    val message = json?.get("message") as? String ?: "Error al iniciar motor: ${response.code}"
                    NetworkResult.Error(message, response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start WhatsApp engine exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    /**
     * Restarts OpenWA services by trying stop+start sequence when available.
     * Fallback: start endpoint only.
     */
    suspend fun restartWhatsAppServices(): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        val stopUrl = "$baseUrl/api/hilo/auth/whatsapp-stop"
        val startUrl = "$baseUrl/api/hilo/auth/whatsapp-start"
        val emptyBody = ByteArray(0).toRequestBody(null, 0, 0)

        fun post(url: String): Pair<Boolean, Int> {
            val req = Request.Builder().url(url).post(emptyBody).build()
            client.newCall(req).execute().use { response ->
                return response.isSuccessful to response.code
            }
        }

        try {
            // Stop can fail if endpoint doesn't exist in older backend; ignore.
            runCatching { post(stopUrl) }
            delay(1200)
            val (startOk, startCode) = post(startUrl)
            if (startOk) NetworkResult.Success(true)
            else NetworkResult.Error("No se pudo reiniciar OpenWA ($startCode)", startCode)
        } catch (e: Exception) {
            Log.e(TAG, "Restart OpenWA exception", e)
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

    suspend fun getGroups(monitoredOnly: Boolean = false): NetworkResult<GroupsResult> = withContext(Dispatchers.IO) {
        // Some backends fail (500) with extra query params like limit.
        // We try robustly in this order:
        // 1) preferred url with limit
        // 2) legacy url without limit
        val candidateUrls = buildList {
            if (monitoredOnly) {
                add("$baseUrl/api/hilo/groups?monitoredOnly=true&limit=1000")
                add("$baseUrl/api/hilo/groups?monitoredOnly=true")
            } else {
                add("$baseUrl/api/hilo/groups?limit=1000")
                add("$baseUrl/api/hilo/groups")
            }
        }

        try {
            var lastErrorCode: Int? = null
            for (url in candidateUrls) {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val rawGroups = gson.fromJson(responseStr, Array<RawGroup>::class.java)
                        val isPartial = rawGroups.any { it.whatsappListPartial }
                        val chats = rawGroups.map { g ->
                            val resolvedName = when {
                                !g.displayName.isNullOrBlank() -> g.displayName
                                !g.pushName.isNullOrBlank() -> g.pushName
                                g.name.isNotBlank() -> g.name
                                else -> g.id.substringBefore("@")
                            }
                            Chat(
                                id = g.id,
                                contactName = resolvedName,
                                lastMessage = if (g.isMonitored) "Monitoreando chat" else "Monitoreo inactivo",
                                timestamp = "",
                                unreadCount = 0,
                                avatarUrl = getAvatarUrl(g.id),
                                isMonitored = g.isMonitored,
                                sessionId = g.sessionId,
                                aiAutoReply = g.aiAutoReply
                            )
                        }
                        return@withContext NetworkResult.Success(
                            GroupsResult(chats = chats, isPartialList = isPartial)
                        )
                    }
                    lastErrorCode = response.code
                    Log.w(TAG, "getGroups failed on $url (${response.code})")
                }
            }
            NetworkResult.Error("Error al cargar grupos (${lastErrorCode ?: 500})", lastErrorCode ?: 500)
        } catch (e: Exception) {
            Log.e(TAG, "Get groups exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun toggleMonitoring(
        groupId: String,
        isMonitored: Boolean,
        sessionId: String?,
        aiAutoReply: Boolean? = null
    ): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        val safeGroupId = encPath(groupId)
        val url = "$baseUrl/api/hilo/groups/$safeGroupId"
        val actualSessionId = sessionId ?: mySessionId
        val payloadMap = mutableMapOf<String, Any?>("isMonitored" to isMonitored, "sessionId" to actualSessionId)
        if (aiAutoReply != null) payloadMap["aiAutoReply"] = aiAutoReply
        val payload = gson.toJson(payloadMap)
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

    /**
     * Retrieves the full AI chat conversation history from the server.
     * The backend persists every exchange in the HiloAiChat table, so this
     * restores the conversation after an app restart.
     */
    suspend fun getAiChatHistory(): NetworkResult<List<RawAiChatItem>> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/summaries/chat"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val items = gson.fromJson(responseStr, Array<RawAiChatItem>::class.java)
                    NetworkResult.Success(items.toList())
                } else {
                    NetworkResult.Error("Error al cargar historial IA (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get AI chat history exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    /**
     * Global AI chat (not tied to a specific WhatsApp group).
     * Kept for compatibility with AiChatViewModel.
     */
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

    suspend fun chatWithAiForGroup(groupId: String, message: String): NetworkResult<String> = withContext(Dispatchers.IO) {
        val safeGroupId = encPath(groupId)
        val url = "$baseUrl/api/hilo/summaries/$safeGroupId/chat"
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
            Log.e(TAG, "Chat AI for group exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun generateSummary(groupId: String, date: String? = null): NetworkResult<RawSummary> = withContext(Dispatchers.IO) {
        val url = buildString {
            val safeGroupId = encPath(groupId)
            append("$baseUrl/api/hilo/summaries/$safeGroupId/generate")
            if (!date.isNullOrBlank()) {
                append("?date=${encQuery(date)}")
            }
        }
        val body = ByteArray(0).toRequestBody(null, 0, 0)
        val request = Request.Builder().url(url).post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    NetworkResult.Success(gson.fromJson(responseStr, RawSummary::class.java))
                } else {
                    val msg = try { gson.fromJson(responseStr, Map::class.java)["message"] as? String ?: "Error al generar resumen" } catch (e: Exception) { "Error al generar resumen (${response.code})" }
                    NetworkResult.Error(msg, response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generate summary exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun syncHistory(groupId: String, limit: Int = 9999): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        val safeGroupId = encPath(groupId)
        val url = "$baseUrl/api/hilo/groups/$safeGroupId/sync-history?limit=$limit"
        val body = ByteArray(0).toRequestBody(null, 0, 0)
        val request = Request.Builder().url(url).post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) NetworkResult.Success(true)
                else NetworkResult.Error("Error al sincronizar historial (${response.code})", response.code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync history exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun searchMessages(query: String, groupId: String): NetworkResult<List<Message>> = withContext(Dispatchers.IO) {
        val encoded = encQuery(query.trim())
        val safeGroupId = encPath(groupId)
        val url = "$baseUrl/api/hilo/messages/search?q=$encoded&groupId=$safeGroupId"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val rawMessages = gson.fromJson(responseStr, Array<RawMessage>::class.java)
                    val messages = rawMessages.map { m ->
                        val isFromMe = m.senderId == "Me" ||
                                       m.senderName == "Tú" ||
                                       (myPhone.isNotEmpty() && m.senderId.contains(myPhone))
                        val time = try {
                            val timePart = m.timestamp.substringAfter('T').substringBefore('.')
                            val parts = timePart.split(':')
                            if (parts.size >= 2) "${parts[0]}:${parts[1]}" else timePart
                        } catch (e: Exception) { "" }
                        Message(id = m.id, text = m.content, timestamp = time, timestampRaw = m.timestamp,
                                isFromMe = isFromMe, type = m.type, senderId = m.senderId, senderName = m.senderName)
                    }
                    NetworkResult.Success(messages)
                } else {
                    NetworkResult.Error("Error en búsqueda (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search messages exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    // =========================================================================
    // SUMMARIES
    // =========================================================================

    suspend fun getLatestSummaries(): NetworkResult<List<RawSummary>> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/hilo/summaries/latest"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val items = gson.fromJson(responseStr, Array<RawSummary>::class.java)
                    NetworkResult.Success(items.toList())
                } else {
                    NetworkResult.Error("Error al cargar resúmenes (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get summaries exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    suspend fun sendMessage(groupId: String, text: String): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        val safeGroupId = encPath(groupId)
        val url = "$baseUrl/api/hilo/messages?groupId=$safeGroupId"
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
        val safeGroupId = encPath(groupId)
        val url = "$baseUrl/api/hilo/messages?groupId=$safeGroupId"
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
        val safeGroupId = encPath(groupId)
        val url = "$baseUrl/api/hilo/messages?groupId=$safeGroupId&page=$page&limit=$limit"
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
                        val normalizedType = m.type.lowercase()
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

                        val mediaMessageId = if (m.waMessageId.isNotBlank()) m.waMessageId else m.id
                        val hasMedia = normalizedType == "image" ||
                            normalizedType == "video" ||
                            normalizedType == "sticker" ||
                            normalizedType == "audio" ||
                            normalizedType == "document" ||
                            normalizedType == "ptt"

                        Message(
                            id = m.id,
                            text = m.content,
                            timestamp = time,
                            timestampRaw = m.timestamp,
                            isFromMe = isFromMe,
                            type = normalizedType,
                            mediaUrl = if (hasMedia) getMediaUrl(groupId, mediaMessageId) else null,
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
        return "$baseUrl/api/hilo/groups/${encPath(groupId)}/avatar"
    }

    fun getMediaUrl(groupId: String, messageId: String): String {
        return "$baseUrl/api/hilo/messages/${encPath(groupId)}/media/${encPath(messageId)}"
    }

    /**
     * Downloads all messages for [groupId] as a CSV string.
     * [from] and [to] are optional ISO date strings (YYYY-MM-DD) to filter the range.
     */
    suspend fun exportMessages(
        groupId: String,
        from: String? = null,
        to: String? = null
    ): NetworkResult<String> = withContext(Dispatchers.IO) {
        var url = "$baseUrl/api/hilo/messages/export?groupId=${encPath(groupId)}"
        if (!from.isNullOrBlank()) url += "&from=$from"
        if (!to.isNullOrBlank()) url += "&to=$to"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    NetworkResult.Success(body)
                } else {
                    NetworkResult.Error("Error al exportar (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export messages exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
    }

    /**
     * Registers the device FCM push notification token with the backend.
     * Called after login and whenever Firebase generates a new token.
     */
    suspend fun registerFcmToken(fcmToken: String): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        if (token.isEmpty()) return@withContext NetworkResult.Error("Not authenticated")
        val url = "$baseUrl/api/hilo/auth/fcm-token"
        val body = gson.toJson(mapOf("token" to fcmToken)).toRequestBody(JSON)
        val request = Request.Builder().url(url).patch(body).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 204) {
                    NetworkResult.Success(true)
                } else {
                    NetworkResult.Error("FCM token registration failed (${response.code})", response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register FCM token exception", e)
            NetworkResult.Error("Error de red: ${e.message}")
        }
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
    val pushName: String? = null,
    val isMonitored: Boolean,
    val sessionId: String?,
    val aiAutoReply: Boolean = false,
    val whatsappListPartial: Boolean = false
)

data class GroupsResult(
    val chats: List<Chat>,
    val isPartialList: Boolean = false
)

data class RawMessage(
    val id: String,
    val waMessageId: String = "",
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

/**
 * Server-side AI chat entry from the HiloAiChat table.
 * role is either "user" or "assistant".
 */
data class RawAiChatItem(
    val id: String,
    val userId: String,
    val groupId: String,
    val role: String,
    val content: String,
    val timestamp: String
)

/** Server-side daily summary entry from the HiloDailySummary table. */
data class RawSummary(
    val id: String,
    val groupId: String,
    val date: String,
    val content: String,
    val messageCount: Int,
    val generationDurationMs: Int?
)
