package com.example.hiloapp.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageDao {
    /** Returns messages newest-first, matching the ViewModel's expected ordering (reverseLayout = true). */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestampRaw DESC")
    suspend fun getMessages(chatId: String): List<MessageEntity>

    /** Upserts a batch of messages. Existing messages with the same id are replaced. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun countForChat(chatId: String): Int

    /** Returns the single most recent message for a chat, or null if no messages exist. */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestampRaw DESC LIMIT 1")
    suspend fun getLastMessage(chatId: String): MessageEntity?
}
