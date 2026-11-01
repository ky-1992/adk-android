package com.google.adk.demo.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.adk.demo.DemoAgentOrchestrator
import com.google.adk.demo.ui.settings.SettingsViewModel

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val state by chatViewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Sync configuration from Settings when API key is set
    LaunchedEffect(settingsState.isConfigured) {
        if (settingsState.isConfigured) {
            val backend = settingsViewModel.createBackend()
            if (backend != null) {
                chatViewModel.configure(backend, DemoAgentOrchestrator.availableTools())
            }
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Error snackbar
        state.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(8.dp),
                action = {
                    TextButton(onClick = { chatViewModel.clearError() }) { Text("Dismiss") }
                },
            ) {
                Text(error)
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { item ->
                when (item) {
                    is ChatItem.User -> MessageBubble(text = item.text, isUser = true)
                    is ChatItem.Assistant -> MessageBubble(text = item.text, isUser = false)
                    is ChatItem.ToolInvocation -> ToolCallCard(
                        toolName = item.toolName,
                        arguments = item.arguments,
                        result = item.result,
                        error = item.error,
                    )
                }
            }

            // Streaming text
            if (state.streamingText.isNotEmpty()) {
                item {
                    StreamingText(text = state.streamingText)
                }
            }

            // Active tool calls
            state.toolCalls.values.filter { !it.completed }.forEach { tc ->
                item {
                    ToolCallCard(
                        toolName = tc.toolName,
                        arguments = tc.arguments,
                        result = tc.result,
                        error = tc.error,
                    )
                }
            }

            // Extra padding at bottom to avoid overlap with input
            item { Spacer(modifier = Modifier.padding(bottom = 8.dp)) }
        }

        // Input bar
        InputBar(
            enabled = !state.isStreaming,
            onSend = { text -> chatViewModel.sendMessage(text) },
        )
    }
}