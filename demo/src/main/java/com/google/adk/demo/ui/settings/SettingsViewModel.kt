package com.google.adk.demo.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.adk.ark.ArkModelBackend
import com.google.adk.demo.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fallback key injected from local.properties via BuildConfig (arkApiKey=...).
 * local.properties is gitignored; never hardcode keys in source.
 */
private val DEFAULT_API_KEY: String get() = BuildConfig.ARK_API_KEY

private const val ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"

/**
 * ARK public model catalog entries (model field values).
 * "Published" in the catalog does not guarantee account entitlement —
 * actual callability depends on your key and real call results.
 */
private val ARK_MODELS = listOf(
    "doubao-seed-2-1-pro-260915",          // Doubao flagship text
    "doubao-seed-evolving-latest-version", // Doubao self-evolving
    "deepseek-v4-1-flash-261017",          // DeepSeek
    "deepseek-v4-1-flash-economy-261017",  // DeepSeek economy
    "deepseek-v4-flash-ga-260731",         // DeepSeek
    "kimi-k3-1-pro-261024",                // Kimi
    "glm-5-3-flash-260828",                // GLM-5.3-Flash
    "glm-5-2-260617",                      // GLM-5.2
)

private const val DEFAULT_MODEL_ID = "glm-5-3-flash-260828"

data class SettingsState(
    val apiKey: String = "",
    val endpointId: String = "",
    val modelId: String = DEFAULT_MODEL_ID,
    val availableModels: List<String> = ARK_MODELS,
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
        run {
            val savedKey = prefs.getString("api_key", null)
            val effectiveKey = savedKey?.takeIf { it.isNotBlank() } ?: DEFAULT_API_KEY
            SettingsState(
                apiKey = effectiveKey,
                endpointId = prefs.getString("endpoint_id", "") ?: "",
                // Guard against stale saved IDs that are not in the ARK catalog.
                modelId = prefs.getString("model_id", null)
                    ?.takeIf { ARK_MODELS.contains(it) }
                    ?: DEFAULT_MODEL_ID,
                isConfigured = effectiveKey.isNotBlank(),
            )
        }
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
        // ARK catalog model IDs are lowercase and case-sensitive; normalize just in case.
        val modelId = s.modelId.trim().lowercase()
        return ArkModelBackend(
            modelId = modelId,
            apiKey = s.apiKey.trim(),
            // Optional: route through an ARK inference endpoint (ep-xxxx) instead of
            // calling the public model ID directly.
            endpointId = s.endpointId.takeIf { it.isNotBlank() },
            baseUrl = ARK_BASE_URL,
        )
    }
}