package com.google.adk.ark

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ArkChatRequest(
    val model: String,
    val messages: List<ArkMessage>,
    val tools: List<ArkTool>? = null,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable
data class ArkMessage(
    val role: String,
    val content: String,
    @SerialName("tool_calls") val toolCalls: List<ArkToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
data class ArkTool(
    val type: String = "function",
    val function: ArkFunction,
)

@Serializable
data class ArkFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
data class ArkToolCall(
    val id: String,
    val type: String = "function",
    val function: ArkFunctionCall,
)

@Serializable
data class ArkFunctionCall(
    val name: String,
    val arguments: String, // JSON string
)

@Serializable
data class ArkChatChunk(
    val id: String? = null,
    val choices: List<ArkChunkChoice>? = null,
    val usage: ArkUsage? = null,
)

@Serializable
data class ArkChunkChoice(
    val index: Int? = null,
    val delta: ArkDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ArkDelta(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ArkToolCall>? = null,
)

@Serializable
data class ArkUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int,
)