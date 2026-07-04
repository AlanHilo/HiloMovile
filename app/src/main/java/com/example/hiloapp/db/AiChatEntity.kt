package com.example.hiloapp.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.hiloapp.AiMessage

/**
 * Room entity for Hilo AI chat history.
 * Mirrors the server-side HiloAiChat table so conversations persist across app restarts.
 */
@Entity(
    tableName = "ai_chat",
    indices = [Index(value = ["userId"])]
)
data class AiChatEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: String   // ISO-8601, e.g. "2026-06-30T14:23:00.000Z"
)

fun AiChatEntity.toAiMessage(): AiMessage {
    val displayTime = try {
        val timePart = timestamp.substringAfter('T').substringBefore('.')
        val parts = timePart.split(':')
        if (parts.size >= 2) "${parts[0]}:${parts[1]}" else timePart
    } catch (e: Exception) {
        ""
    }
    return AiMessage(id = id, text = text, isFromUser = isFromUser, timestamp = displayTime)
}
