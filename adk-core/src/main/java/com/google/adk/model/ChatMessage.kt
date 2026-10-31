// adk-core/src/main/java/com/google/adk/model/ChatMessage.kt
package com.google.adk.model

import com.google.adk.event.AgentEvent

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val toolCalls: List<AgentEvent.ToolCallRequest>? = null,
    val toolCallId: String? = null,
    val name: String? = null,
)

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }