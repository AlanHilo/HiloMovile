package com.example.hiloapp

data class Chat(
    val id: String,
    val contactName: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0,
    val avatarUrl: String? = null,
    val isMonitored: Boolean = false,
    val sessionId: String? = null
)

data class Message(
    val id: String,
    val text: String,
    val timestamp: String,
    val isFromMe: Boolean,
    val type: String = "text",
    val mediaUrl: String? = null,
    val senderId: String? = null,
    val senderName: String? = null
)
