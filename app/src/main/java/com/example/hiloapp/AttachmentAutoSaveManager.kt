package com.example.hiloapp

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AttachmentAutoSaveManager {
    private val client = OkHttpClient()

    private fun isAttachmentType(type: String?): Boolean {
        val t = type?.lowercase() ?: return false
        return t == "image" || t == "video" || t == "audio" || t == "document" || t == "sticker" || t == "ptt"
    }

    private fun mimeAndExt(type: String?): Pair<String, String> {
        return when (type?.lowercase()) {
            "image", "sticker" -> "image/jpeg" to ".jpg"
            "video" -> "video/mp4" to ".mp4"
            "audio", "ptt" -> "audio/mpeg" to ".mp3"
            "document" -> "application/octet-stream" to ".bin"
            else -> "application/octet-stream" to ".bin"
        }
    }

    fun tryAutoSaveAttachment(
        context: Context,
        event: HiloSocketManager.MessageEvent
    ) {
        if (!isAttachmentType(event.messageType)) return
        val chatId = event.chatId ?: return
        val messageId = event.messageId ?: return
        val downloadUrl = event.mediaUrl ?: HiloApi.getMediaUrl(chatId, messageId)

        runCatching {
            val req = Request.Builder().url(downloadUrl).get().build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return
                val bytes = response.body?.bytes() ?: return
                saveToDownloads(context, bytes, event.messageType)
            }
        }
    }

    private fun saveToDownloads(context: Context, bytes: ByteArray, type: String?) {
        val (mime, ext) = mimeAndExt(type)
        val fileName = "hilo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}$ext"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Hilo")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return

        resolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
            output.flush()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
    }
}
