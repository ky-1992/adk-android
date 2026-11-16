package com.google.adk.demo.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// Activity-scoped so ChatScreen shares this same instance (see ChatScreen).
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        // API Key
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = { viewModel.updateApiKey(it) },
            label = { Text("ARK API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // Endpoint ID (optional)
        OutlinedTextField(
            value = state.endpointId,
            onValueChange = { viewModel.updateEndpointId(it) },
            label = { Text("Endpoint ID (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Leave blank to use model ID directly") },
        )

        // Model selection
        Text("Model", style = MaterialTheme.typography.titleSmall)
        state.availableModels.forEach { model ->
            Row(modifier = Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = state.modelId == model,
                    onClick = { viewModel.updateModelId(model) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(model, modifier = Modifier.padding(vertical = 12.dp))
            }
        }

        // Status
        if (state.isConfigured) {
            Text(
                "✓ Configured — ${state.modelId}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                "⚠ API Key required",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}