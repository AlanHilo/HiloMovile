package com.example.hiloapp.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AiChatDao {
    /** Returns AI conversation history in chronological order (oldest first). */
    @Query("SELECT * FROM ai_chat WHERE userId = :userId ORDER BY timestamp ASC")
    suspend fun getHistory(userId: String): List<AiChatEntity>

    /** Upserts a batch of AI chat entries. Server-assigned IDs are used as primary keys. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<AiChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: AiChatEntity)

    @Query("DELETE FROM ai_chat WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
