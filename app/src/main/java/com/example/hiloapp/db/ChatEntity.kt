package com.example.hiloapp.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.hiloapp.Chat

/**
 * Room entity for caching WhatsApp chat list locally.
 * Persisted so the user sees all chats instantly even when the backend times out.
 */
@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["isMonitored"])
    ]
)
data class ChatEntity(
    @PrimaryKey val id: String,
    val contactName: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int,
    val avatarUrl: String?,
    val isMonitored: Boolean,
    val sessionId: String?,
    val aiAutoReply: Boolean
)

fun ChatEntity.toChat() = Chat(
    id = id,
    contactName = contactName,
    lastMessage = lastMessage,
    timestamp = timestamp,
    unreadCount = unreadCount,
    avatarUrl = avatarUrl,
    isMonitored = isMonitored,
    sessionId = sessionId,
    aiAutoReply = aiAutoReply
)

fun Chat.toEntity() = ChatEntity(
    id = id,
    contactName = contactName,
    lastMessage = lastMessage,
    timestamp = timestamp,
    unreadCount = unreadCount,
    avatarUrl = avatarUrl,
    isMonitored = isMonitored,
    sessionId = sessionId,
    aiAutoReply = aiAutoReply
)