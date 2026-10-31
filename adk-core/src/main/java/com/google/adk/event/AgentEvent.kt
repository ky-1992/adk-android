// adk-core/src/main/java/com/google/adk/event/AgentEvent.kt
package com.google.adk.event

sealed class AgentEvent {
    data class RunStarted(val inputTokenCount: Int = 0) : AgentEvent()
    data class TextDelta(val text: String, val isFinal: Boolean = false) : AgentEvent()
    data class ToolCallRequest(
        val callId: String,
        val toolName: String,
        val arguments: Map<String, Any?>,
    ) : AgentEvent()
    data class ToolCallResult(
        val callId: String,
        val toolName: String,
        val result: Any?,
        val error: String? = null,
    ) : AgentEvent()
    data class FinalResponse(val text: String, val tokenCount: TokenUsage) : AgentEvent()
    data class RunError(val error: Throwable, val phase: ErrorPhase) : AgentEvent()
    data object RunCompleted : AgentEvent()
}

data class TokenUsage(val input: Int, val output: Int) {
    val total: Int get() = input + output
}

enum class ErrorPhase { MODEL_CALL, TOOL_EXECUTION, SESSION_SAVE }