package com.google.adk.model

import com.google.adk.event.AgentEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface ModelBackend {
    val modelId: String

    fun generate(
        messages: List<ChatMessage>,
        tools: List<ToolDeclaration>,
        config: GenerationConfig,
    ): Flow<AgentEvent>
}

data class ToolDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String> = emptyList(),
    val streaming: Boolean = true,
)

typealias JsonSchema = JsonObject