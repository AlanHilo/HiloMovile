package com.example.hiloapp.db

import androidx.room.Entity
import androidx.room.Index
import com.example.hiloapp.Message

/**
 * Room entity that mirrors [Message] for local SQLite persistence.
 * Messages are stored per chatId and ordered newest-first via timestampRaw DESC.
 */
@Entity(
    tableName = "messages",
    primaryKeys = ["chatId", "id"],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["timestampRaw"])
    ]
)
data class MessageEntity(
    val id: String,
    val chatId: String,
    val text: String,
    val timestamp: String,       // display "HH:mm"
    val timestampRaw: String,    // ISO-8601 for ordering, e.g. "2026-06-30T14:23:00.000Z"
    val isFromMe: Boolean,
    val type: String,
    val mediaUrl: String?,
    val senderId: String?,
    val senderName: String?
)

fun MessageEntity.toMessage() = Message(
    id = id,
    text = text,
    timestamp = timestamp,
    timestampRaw = timestampRaw,
    isFromMe = isFromMe,
    type = type,
    mediaUrl = mediaUrl,
    senderId = senderId,
    senderName = senderName
)

fun Message.toEntity(chatId: String) = MessageEntity(
    id = id,
    chatId = chatId,
    text = text,
    timestamp = timestamp,
    timestampRaw = timestampRaw,
    isFromMe = isFromMe,
    type = type,
    mediaUrl = mediaUrl,
    senderId = senderId,
    senderName = senderName
)
