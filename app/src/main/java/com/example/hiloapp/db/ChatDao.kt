package com.example.hiloapp.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatDao {
    /** Returns all cached chats, monitored first. */
    @Query("SELECT * FROM chats ORDER BY isMonitored DESC, contactName ASC")
    suspend fun getAllChats(): List<ChatEntity>

    /** Returns only monitored chats. */
    @Query("SELECT * FROM chats WHERE isMonitored = 1 ORDER BY contactName ASC")
    suspend fun getMonitoredChats(): List<ChatEntity>

    /** Upserts a batch of chats. Existing chats with the same id are replaced. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chats: List<ChatEntity>)

    /** Updates only the monitoring flag for a single chat. */
    @Query("UPDATE chats SET isMonitored = :isMonitored WHERE id = :chatId")
    suspend fun updateMonitored(chatId: String, isMonitored: Boolean)

    /** Updates the AI auto-reply flag for a single chat. */
    @Query("UPDATE chats SET aiAutoReply = :aiAutoReply WHERE id = :chatId")
    suspend fun updateAiAutoReply(chatId: String, aiAutoReply: Boolean)

    /** Deletes all chats (called on logout). */
    @Query("DELETE FROM chats")
    suspend fun deleteAll()
}