package com.example.hiloapp.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Single Room database for HiloApp.
 * Contains three tables:
 * - messages: local cache of WhatsApp messages per chat
 * - ai_chat:  persistent history of Hilo AI conversations
 * - chats:    cached WhatsApp chat list (survives restarts & backend timeouts)
 */
@Database(
    entities = [MessageEntity::class, AiChatEntity::class, ChatEntity::class],
    version = 3,
    exportSchema = false
)
abstract class HiloDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun aiChatDao(): AiChatDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: HiloDatabase? = null

        fun getInstance(context: Context): HiloDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HiloDatabase::class.java,
                    "hilo_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
