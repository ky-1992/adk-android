package com.google.adk.demo.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
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

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs = EncryptedSharedPreferences.create(
        "adk_settings",
        masterKeyAlias,
        application,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _state = MutableStateFlow(
        SettingsState(
            apiKey = prefs.getString("api_key", "") ?: "",
            endpointId = prefs.getString("endpoint_id", "") ?: "",
            modelId = prefs.getString("model_id", "doubao-pro-32k") ?: "doubao-pro-32k",
            isConfigured = !prefs.getString("api_key", "").isNullOrBlank(),
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun updateApiKey(key: String) {
        _state.update { it.copy(apiKey = key, isConfigured = key.isNotBlank()) }
        prefs.edit().putString("api_key", key).apply()
    }

    fun updateEndpointId(id: String) {
        _state.update { it.copy(endpointId = id) }
        prefs.edit().putString("endpoint_id", id).apply()
    }

    fun updateModelId(id: String) {
        _state.update { it.copy(modelId = id) }
        prefs.edit().putString("model_id", id).apply()
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