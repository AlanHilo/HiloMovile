package com.example.hiloapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SummariesViewModel(application: Application) : AndroidViewModel(application) {

    private val _summaries = MutableStateFlow<List<SummaryItem>>(emptyList())
    val summaries: StateFlow<List<SummaryItem>> = _summaries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // groupId currently being generated — drives per-card loading indicator
    private val _generatingGroupId = MutableStateFlow<String?>(null)
    val generatingGroupId: StateFlow<String?> = _generatingGroupId.asStateFlow()

    private var lastGroupFingerprint: String = ""

    /**
     * Fetches daily AI summaries and aligns them with monitored chats.
     * Every monitored chat gets a card (existing summary or pending placeholder).
     */
    fun loadSummaries(groups: List<Chat> = emptyList()) {
        val monitored = groups.filter { it.isMonitored }
        val fingerprint = monitored.map { it.id }.sorted().joinToString("|")
        if (fingerprint == lastGroupFingerprint && _summaries.value.isNotEmpty()) return
        lastGroupFingerprint = fingerprint

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val result = HiloApi.getLatestSummaries()) {
                is NetworkResult.Success -> {
                    val existingByGroup = result.data.associateBy { it.groupId }
                    _summaries.value = monitored.map { chat ->
                        val raw = existingByGroup[chat.id]
                        if (raw != null) {
                            SummaryItem(
                                id = raw.id,
                                groupId = raw.groupId,
                                groupName = chat.contactName,
                                date = raw.date,
                                content = raw.content,
                                messageCount = raw.messageCount
                            )
                        } else {
                            SummaryItem(
                                id = "pending_${chat.id}",
                                groupId = chat.id,
                                groupName = chat.contactName,
                                date = "Sin resumen",
                                content = "Aun no hay resumen para este chat. Pulsa actualizar para generarlo.",
                                messageCount = 0
                            )
                        }
                    }
                }
                is NetworkResult.Error -> _error.value = result.message
                is NetworkResult.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun forceReload(groups: List<Chat> = emptyList()) {
        lastGroupFingerprint = ""
        loadSummaries(groups)
    }

    /**
     * Forces generation of a new summary for one group and updates its card.
     * [date] in YYYY-MM-DD; null means today.
     */
    fun generateSummary(groupId: String, date: String? = null, onDone: (Boolean, String?) -> Unit) {
        _generatingGroupId.value = groupId
        viewModelScope.launch {
            val syncRes = HiloApi.syncHistory(groupId, limit = 9999)
            if (syncRes is NetworkResult.Error) {
                _generatingGroupId.value = null
                onDone(false, "No se pudo sincronizar historial antes del resumen: ${syncRes.message}")
                return@launch
            }

            when (val res = HiloApi.generateSummary(groupId, date)) {
                is NetworkResult.Success -> {
                    val raw = res.data
                    _summaries.value = _summaries.value.map { s ->
                        if (s.groupId == groupId) {
                            s.copy(
                                id = raw.id,
                                date = raw.date,
                                content = raw.content,
                                messageCount = raw.messageCount
                            )
                        } else s
                    }
                    onDone(true, null)
                }
                is NetworkResult.Error -> onDone(false, res.message)
                is NetworkResult.Loading -> {}
            }
            _generatingGroupId.value = null
        }
    }

    /**
     * Generates summaries for every monitored chat sequentially.
     * [date] in YYYY-MM-DD; null means today.
     */
    fun generateAllForMonitored(groups: List<Chat>, date: String? = null, onDone: (okCount: Int, failCount: Int) -> Unit) {
        val monitored = groups.filter { it.isMonitored }
        if (monitored.isEmpty()) {
            onDone(0, 0)
            return
        }
        viewModelScope.launch {
            var ok = 0
            var fail = 0
            for (chat in monitored) {
                _generatingGroupId.value = chat.id
                val syncRes = HiloApi.syncHistory(chat.id, limit = 9999)
                if (syncRes is NetworkResult.Error) {
                    fail++
                    continue
                }

                when (val res = HiloApi.generateSummary(chat.id, date)) {
                    is NetworkResult.Success -> {
                        val raw = res.data
                        val idx = _summaries.value.indexOfFirst { it.groupId == chat.id }
                        if (idx >= 0) {
                            _summaries.value = _summaries.value.toMutableList().also { list ->
                                list[idx] = list[idx].copy(
                                    id = raw.id,
                                    date = raw.date,
                                    content = raw.content,
                                    messageCount = raw.messageCount
                                )
                            }
                        } else {
                            _summaries.value = _summaries.value + SummaryItem(
                                id = raw.id,
                                groupId = raw.groupId,
                                groupName = chat.contactName,
                                date = raw.date,
                                content = raw.content,
                                messageCount = raw.messageCount
                            )
                        }
                        ok++
                    }
                    is NetworkResult.Error -> fail++
                    is NetworkResult.Loading -> {}
                }
            }
            _generatingGroupId.value = null
            onDone(ok, fail)
        }
    }

    fun clearError() { _error.value = null }
}