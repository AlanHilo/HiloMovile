package com.example.hiloapp

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket as EngineWebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Singleton managing the Socket.IO connection to the backend /events namespace.
 *
 * Protocol:
 *  1. Connect to {serverUrl}/events with JWT Bearer token in auth.token
 *  2. After connect, subscribe to message.received and message.sent from all sessions
 *  3. Any incoming message event triggers all registered [MessageListener]s
 *
 * The app uses a hybrid approach:
 *  - When socket IS connected: socket events drive UI refreshes (zero polling drain)
 *  - When socket IS NOT connected: MessagesViewModel falls back to HTTP polling every 3s
 *
 * SETUP NOTE: Backend gateway must have `allowEIO3: true` to accept socket.io-client-java 2.x.
 */
object HiloSocketManager {
    private const val TAG = "HiloSocketManager"

    private var socket: Socket? = null

    data class MessageEvent(
        val event: String,
        val chatId: String?,
        val messageId: String?,
        val messageType: String?,
        val mediaUrl: String?,
        val senderName: String?,
        val text: String?
    )

    /**
     * Listeners called whenever a message.received or message.sent event arrives.
     * Each callback is invoked on the socket event thread — callers must dispatch
     * to the correct context (e.g., viewModelScope.launch) themselves.
     */
    private val listeners = CopyOnWriteArrayList<(MessageEvent) -> Unit>()

    /** True if the socket is currently connected and subscribed. */
    val isConnected: Boolean
        get() = socket?.connected() == true

    /**
     * Opens a Socket.IO connection to [serverUrl]/events authenticated with [jwtToken].
     * Safe to call multiple times — returns immediately if already connected.
     */
    fun connect(serverUrl: String, jwtToken: String) {
        if (socket?.connected() == true) return
        disconnect() // Clean up any stale socket

        if (serverUrl.isBlank() || jwtToken.isBlank()) return

        try {
            val opts = IO.Options().apply {
                auth = mapOf("token" to jwtToken)
                // Force WebSocket transport — skip long-polling upgrade handshake
                transports = arrayOf(EngineWebSocket.NAME)
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 2000
                reconnectionDelayMax = 10000
            }

            socket = IO.socket("$serverUrl/events", opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected to $serverUrl/events")
                // Subscribe to all message events across all sessions
                socket?.emit("message", JSONObject().apply {
                    put("type", "subscribe")
                    put("sessionId", "*")
                    put("events", JSONArray().apply {
                        put("message.received")
                        put("message.sent")
                    })
                })
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "Unknown"
                Log.w(TAG, "Socket connection error: $error")
            }

            socket?.on(Socket.EVENT_DISCONNECT) { args ->
                Log.d(TAG, "Socket disconnected: ${args.firstOrNull()}")
            }

            socket?.on("message") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                if (data.optString("type") == "event") {
                    val payload = data.optJSONObject("payload")
                    val event = payload?.optString("event") ?: ""
                    if (event == "message.received" || event == "message.sent") {
                        Log.d(TAG, "New message event: $event")
                        val eventData = payload?.optJSONObject("data") ?: payload?.optJSONObject("message")
                        val chatId = eventData?.optString("groupId").takeUnless { it.isNullOrBlank() }
                            ?: eventData?.optString("chatId").takeUnless { it.isNullOrBlank() }
                            ?: payload?.optString("sessionId").takeUnless { it.isNullOrBlank() }
                        val messageId = eventData?.optString("waMessageId").takeUnless { it.isNullOrBlank() }
                            ?: eventData?.optString("messageId").takeUnless { it.isNullOrBlank() }
                            ?: eventData?.optString("id").takeUnless { it.isNullOrBlank() }
                        val messageType = eventData?.optString("type").takeUnless { it.isNullOrBlank() }
                            ?: eventData?.optString("messageType").takeUnless { it.isNullOrBlank() }
                        val mediaUrl = eventData?.optString("mediaUrl").takeUnless { it.isNullOrBlank() }
                        val senderName = eventData?.optString("senderName").takeUnless { it.isNullOrBlank() }
                            ?: eventData?.optString("contactName").takeUnless { it.isNullOrBlank() }
                            ?: eventData?.optString("fromName").takeUnless { it.isNullOrBlank() }
                        val text = eventData?.optString("content").takeUnless { it.isNullOrBlank() }
                            ?: eventData?.optString("text").takeUnless { it.isNullOrBlank() }
                            ?: eventData?.optString("body").takeUnless { it.isNullOrBlank() }
                        notifyListeners(
                            MessageEvent(
                                event = event,
                                chatId = chatId,
                                messageId = messageId,
                                messageType = messageType,
                                mediaUrl = mediaUrl,
                                senderName = senderName,
                                text = text
                            )
                        )
                    }
                }
            }

            socket?.connect()
            Log.d(TAG, "Socket connecting…")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create socket: ${e.message}", e)
        }
    }

    /** Closes the socket connection and clears all listeners. */
    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
        Log.d(TAG, "Socket disconnected")
    }

    /** Register [callback] to be invoked when any new message event arrives. */
    fun addListener(callback: (MessageEvent) -> Unit) {
        if (!listeners.contains(callback)) listeners.add(callback)
    }

    /** Remove a previously registered [callback]. */
    fun removeListener(callback: (MessageEvent) -> Unit) {
        listeners.remove(callback)
    }

    private fun notifyListeners(event: MessageEvent) {
        for (listener in listeners) {
            try {
                listener(event)
            } catch (e: Exception) {
                Log.e(TAG, "Listener threw an exception", e)
            }
        }
    }
}
