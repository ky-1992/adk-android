package com.google.adk.demo.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.adk.ark.ArkModelBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsState(
    val apiKey: String = "",
    val endpointId: String = "",
    val modelId: String = "doubao-pro-32k",
    val availableModels: List<String> = listOf("doubao-pro-32k", "doubao-lite-32k", "doubao-pro-128k"),
    val isConfigured: Boolean = false,
    val testStatus: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun updateApiKey(key: String) {
        _state.update { it.copy(apiKey = key, isConfigured = key.isNotBlank()) }
    }

    fun updateEndpointId(id: String) {
        _state.update { it.copy(endpointId = id) }
    }

    fun updateModelId(id: String) {
        _state.update { it.copy(modelId = id) }
    }

    fun createBackend(): ArkModelBackend? {
        val s = _state.value
        if (s.apiKey.isBlank()) return null
        return ArkModelBackend(
            modelId = s.modelId,
            apiKey = s.apiKey,
            endpointId = s.endpointId.ifBlank { null },
        )
    }
}