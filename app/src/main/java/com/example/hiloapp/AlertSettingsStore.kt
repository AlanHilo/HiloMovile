package com.example.hiloapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.alertSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hilo_alert_settings")

data class AlertSettings(
    val priorityEnabled: Boolean = true,
    val autoSaveAttachments: Boolean = true
)

class AlertSettingsStore(private val context: Context) {

    companion object {
        private val KEY_PRIORITY_ENABLED = booleanPreferencesKey("priority_enabled")
        private val KEY_AUTO_SAVE_ATTACHMENTS = booleanPreferencesKey("auto_save_attachments")
    }

    val settingsFlow: Flow<AlertSettings> = context.alertSettingsDataStore.data.map { prefs ->
        AlertSettings(
            priorityEnabled = prefs[KEY_PRIORITY_ENABLED] ?: true,
            autoSaveAttachments = prefs[KEY_AUTO_SAVE_ATTACHMENTS] ?: true
        )
    }

    suspend fun setPriorityEnabled(value: Boolean) {
        context.alertSettingsDataStore.edit { prefs ->
            prefs[KEY_PRIORITY_ENABLED] = value
        }
    }

    suspend fun setAutoSaveAttachments(value: Boolean) {
        context.alertSettingsDataStore.edit { prefs ->
            prefs[KEY_AUTO_SAVE_ATTACHMENTS] = value
        }
    }
}