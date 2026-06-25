package com.example.hiloapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hilo_session")

/**
 * Persists user session data (token, userId, phone, sessionId) across app restarts.
 * Uses Jetpack DataStore instead of SharedPreferences for coroutine-safe access.
 */
class SessionManager(private val context: Context) {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_MY_PHONE = stringPreferencesKey("my_phone")
        private val KEY_SESSION_ID = stringPreferencesKey("session_id")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
    }

    val tokenFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TOKEN] ?: ""
    }

    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        (prefs[KEY_TOKEN] ?: "").isNotEmpty()
    }

    suspend fun getToken(): String {
        return context.dataStore.data.first()[KEY_TOKEN] ?: ""
    }

    suspend fun getUserId(): String {
        return context.dataStore.data.first()[KEY_USER_ID] ?: ""
    }

    suspend fun getMyPhone(): String {
        return context.dataStore.data.first()[KEY_MY_PHONE] ?: ""
    }

    suspend fun getSessionId(): String? {
        return context.dataStore.data.first()[KEY_SESSION_ID]
    }

    suspend fun getBaseUrl(): String {
        return context.dataStore.data.first()[KEY_BASE_URL] ?: ""
    }

    suspend fun saveSession(token: String, userId: String) {
        context.dataStore.data.first() // ensure initialized
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USER_ID] = userId
        }
    }

    suspend fun saveWhatsAppInfo(phone: String, sessionId: String?) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MY_PHONE] = phone
            if (sessionId != null) {
                prefs[KEY_SESSION_ID] = sessionId
            }
        }
    }

    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = url
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
