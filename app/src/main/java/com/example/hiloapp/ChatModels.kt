package com.example.hiloapp

data class Chat(
    val id: String,
    val contactName: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0,
    val avatarUrl: String? = null,
    val isMonitored: Boolean = false,
    val sessionId: String? = null,
    val aiAutoReply: Boolean = false
)

data class Message(
    val id: String,
    val text: String,
    val timestamp: String,          // display "HH:mm"
    val timestampRaw: String = "",  // ISO-8601 full timestamp for DB ordering
    val isFromMe: Boolean,
    val type: String = "text",
    val mediaUrl: String? = null,
    val senderId: String? = null,
    val senderName: String? = null
)
