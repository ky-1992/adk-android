package com.google.adk.demo.ui.chat

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.adk.demo.DemoAgentOrchestrator
import com.google.adk.demo.ui.settings.SettingsViewModel
import com.google.adk.demo.ui.theme.CarMinTouchTargetDp
import com.google.adk.demo.ui.theme.LocalIsCarUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel(),
    // Share ONE SettingsViewModel at Activity scope so the Chat tab sees
    // changes made on the Settings tab (each nav destination would otherwise
    // get its own instance with a stale, init-once snapshot).
    settingsViewModel: SettingsViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
    onNavigateToSessions: () -> Unit = {},
) {
    val state by chatViewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Automotive context (landscape / large screen): bigger spacing, wider
    // bubbles and >= 56dp touch targets. Phone layout stays untouched.
    val isCarUi = LocalIsCarUi.current

    // Re-sync the backend whenever any setting changes (api key / model / endpoint).
    LaunchedEffect(settingsState) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ADK Demo") },
                actions = {
                    IconButton(
                        onClick = { chatViewModel.newSession() },
                        // Automotive safety: minimum 56dp touch target.
                        modifier = if (isCarUi) {
                            Modifier.size(CarMinTouchTargetDp.dp)
                        } else {
                            Modifier
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "New Session")
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
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
                contentPadding = PaddingValues(
                    horizontal = if (isCarUi) 32.dp else 16.dp,
                    vertical = if (isCarUi) 20.dp else 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(if (isCarUi) 12.dp else 8.dp),
            ) {
                items(state.messages) { item ->
                    when (item) {
                        is ChatItem.User -> MessageBubble(
                            text = item.text,
                            isUser = true,
                            maxWidth = bubbleMaxWidth(isCarUi),
                        )
                        is ChatItem.Assistant -> {
                            // Completed assistant replies render as sanitized
                            // HTML in a styled WebView when they contain HTML
                            // markup; plain-text replies fall back to the
                            // plain bubble (never garbled).
                            if (HtmlSanitizer.isRenderableHtml(item.text)) {
                                HtmlMessageBubble(
                                    html = item.text,
                                    maxWidth = bubbleMaxWidth(isCarUi),
                                )
                            } else {
                                MessageBubble(
                                    text = item.text,
                                    isUser = false,
                                    maxWidth = bubbleMaxWidth(isCarUi),
                                )
                            }
                        }
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
}

/** Assistant/user bubbles get a wider max width on car-sized screens. */
private fun bubbleMaxWidth(isCarUi: Boolean): Dp = if (isCarUi) 520.dp else 300.dp
