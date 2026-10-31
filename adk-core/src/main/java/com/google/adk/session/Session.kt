package com.google.adk.session

import com.google.adk.event.AgentEvent
import com.google.adk.model.ChatMessage
import com.google.adk.model.MessageRole
import java.time.Instant

data class Session(
    val id: String,
    val userId: String,
    val appName: String,
    val agentName: String,
    val state: Map<String, Any?>,
    val events: List<AgentEvent>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Session.eventsToMessages(): List<ChatMessage> {
    val messages = mutableListOf<ChatMessage>()
    val pendingText = StringBuilder()
    for (event in events) {
        when (event) {
            is AgentEvent.TextDelta -> {
                pendingText.append(event.text)
                if (event.isFinal) {
                    messages.add(ChatMessage(MessageRole.ASSISTANT, pendingText.toString()))
                    pendingText.clear()
                }
            }

            is AgentEvent.ToolCallRequest -> {
                messages.add(ChatMessage(MessageRole.ASSISTANT, "", toolCalls = listOf(event)))
            }

            is AgentEvent.ToolCallResult -> {
                val content = event.error ?: event.result?.toString() ?: ""
                messages.add(
                    ChatMessage(
                        MessageRole.TOOL,
                        content,
                        toolCallId = event.callId,
                        name = event.toolName,
                    )
                )
            }

            else -> {}
        }
    }
    return messages
}