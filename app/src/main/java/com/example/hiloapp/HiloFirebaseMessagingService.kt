package com.example.hiloapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging service for Hilo push notifications.
 *
 * SETUP REQUIRED:
 *  1. Create a Firebase project at https://console.firebase.google.com
 *  2. Add an Android app with package name "com.example.hiloapp"
 *  3. Download google-services.json and place it in the app/ directory
 *  4. The backend must have FCM_SERVER_KEY set to your Firebase server key
 *     (found in Firebase Console → Project Settings → Cloud Messaging)
 *
 * Once configured, the app will automatically:
 *  - Register the FCM token with the backend on login
 *  - Receive push notifications for new WhatsApp messages when the app is in background
 */
class HiloFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "HiloFCM"
        private const val CHANNEL_ID = "hilo_messages"
        private const val CHANNEL_NAME = "Mensajes de Hilo"
        private var notificationId = 1000

        /**
         * Call this after a successful login to register the current FCM token with the backend.
         * Uses the already-configured [HiloApi] session.
         */
        fun registerTokenWithServer() {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (token.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val result = HiloApi.registerFcmToken(token)
                        if (result.isSuccess) {
                            Log.d(TAG, "FCM token registered with server")
                        } else {
                            Log.w(TAG, "Failed to register FCM token: ${(result as? NetworkResult.Error)?.message}")
                        }
                    }
                }
            }.addOnFailureListener { e ->
                Log.w(TAG, "Could not get FCM token: ${e.message}")
            }
        }
    }

    /** Called when a new FCM token is generated (first run or token refresh). */
    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token generated")
        if (HiloApi.token.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                HiloApi.registerFcmToken(token)
            }
        }
    }

    /** Called when a push notification arrives while the app is in the foreground or background. */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Nuevo mensaje"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""
        val chatId = remoteMessage.data["chatId"]

        showNotification(title, body, chatId)
    }

    private fun showNotification(title: String, body: String, chatId: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel (required on Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de mensajes de WhatsApp monitoreados"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap action: open the app (optionally navigate to the specific chat)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (chatId != null) putExtra("chatId", chatId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId++, notification)
    }
}
