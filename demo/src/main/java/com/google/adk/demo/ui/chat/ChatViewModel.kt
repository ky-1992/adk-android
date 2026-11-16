package com.google.adk.demo.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.adk.agent.LlmAgent
import com.google.adk.demo.DemoAgentOrchestrator
import com.google.adk.demo.di.AppModule
import com.google.adk.event.AgentEvent
import com.google.adk.model.ModelBackend
import com.google.adk.runner.AgentRunner
import com.google.adk.runner.UserMessage
import com.google.adk.tool.Tool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatState(
    val messages: List<ChatItem> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val error: String? = null,
    val sessionId: String = UUID.randomUUID().toString(),
    val toolCalls: Map<String, ToolCallState> = emptyMap(),
)

sealed class ChatItem {
    data class User(val text: String) : ChatItem()
    data class Assistant(val text: String) : ChatItem()
    data class ToolInvocation(
        val callId: String,
        val toolName: String,
        val arguments: Map<String, Any?>,
        val result: Any? = null,
        val error: String? = null,
    ) : ChatItem()
}

data class ToolCallState(
    val callId: String,
    val toolName: String,
    val arguments: Map<String, Any?>,
    val result: Any? = null,
    val error: String? = null,
    val completed: Boolean = false,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = AppModule.sessionStore(application)
    private val runner = AgentRunner(sessionStore)

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var modelBackend: ModelBackend? = null
    private var tools: List<Tool> = emptyList()

    fun configure(model: ModelBackend, tools: List<Tool>) {
        this.modelBackend = model
        this.tools = tools
    }

    fun sendMessage(text: String) {
        val backend = modelBackend ?: run {
            _state.update { it.copy(error = "Model not configured. Please set API Key in Settings.") }
            return
        }

        _state.update { s ->
            s.copy(
                messages = s.messages + ChatItem.User(text),
                isStreaming = true,
                streamingText = "",
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                val agent = LlmAgent(
                    name = "demo-agent",
                    description = "ADK Demo Agent",
                    model = backend,
                    // Shared demo instruction (HTML-fragment replies) — same
                    // constant used by DemoAgentOrchestrator.buildAgent.
                    instruction = DemoAgentOrchestrator.INSTRUCTION,
                    tools = this@ChatViewModel.tools,
                )

                runner.run(
                    agent = agent,
                    sessionId = _state.value.sessionId,
                    userId = "demo-user",
                    newMessage = UserMessage(text),
                ).collect { event ->
                    handleEvent(event)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isStreaming = false) }
            }
        }
    }

    private fun handleEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.RunStarted -> { /* no-op */ }
            is AgentEvent.TextDelta -> {
                _state.update { s ->
                    val newText = s.streamingText + event.text
                    if (event.isFinal) {
                        s.copy(
                            messages = s.messages + ChatItem.Assistant(newText),
                            streamingText = "",
                            isStreaming = false,
                        )
                    } else {
                        s.copy(streamingText = newText)
                    }
                }
            }
            is AgentEvent.ToolCallRequest -> {
                _state.update { s ->
                    val newToolCalls = s.toolCalls.toMutableMap()
                    newToolCalls[event.callId] = ToolCallState(
                        callId = event.callId,
                        toolName = event.toolName,
                        arguments = event.arguments,
                        completed = false,
                    )
                    s.copy(toolCalls = newToolCalls)
                }
            }
            is AgentEvent.ToolCallResult -> {
                _state.update { s ->
                    val newToolCalls = s.toolCalls.toMutableMap()
                    val existing = newToolCalls[event.callId]
                    if (existing != null) {
                        newToolCalls[event.callId] = existing.copy(
                            result = event.result,
                            error = event.error,
                            completed = true,
                        )
                    }
                    s.copy(toolCalls = newToolCalls)
                }
            }
            is AgentEvent.FinalResponse -> {
                _state.update { it.copy(isStreaming = false) }
            }
            is AgentEvent.RunError -> {
                _state.update { it.copy(error = event.error.message, isStreaming = false) }
            }
            is AgentEvent.RunCompleted -> {
                _state.update { it.copy(isStreaming = false) }
            }
        }
    }

    fun newSession() {
        _state.update { it.copy(
            sessionId = UUID.randomUUID().toString(),
            messages = emptyList(),
            streamingText = "",
            error = null,
        ) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}