package com.example.hiloapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlertsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AlertSettingsStore(application)

    private val _settings = MutableStateFlow(AlertSettings())
    val settings: StateFlow<AlertSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            store.settingsFlow.collect { _settings.value = it }
        }
    }

    fun setPriorityEnabled(value: Boolean) {
        viewModelScope.launch { store.setPriorityEnabled(value) }
    }

    fun setAutoSaveAttachments(value: Boolean) {
        viewModelScope.launch { store.setAutoSaveAttachments(value) }
    }
}
