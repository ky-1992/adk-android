package com.google.adk.ark

import com.google.adk.event.AgentEvent
import com.google.adk.event.ErrorPhase
import com.google.adk.event.TokenUsage
import com.google.adk.model.ChatMessage
import com.google.adk.model.GenerationConfig
import com.google.adk.model.MessageRole
import com.google.adk.model.ModelBackend
import com.google.adk.model.ToolDeclaration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ArkModelBackend(
    override val modelId: String,
    private val apiKey: String,
    private val endpointId: String? = null,
    private val baseUrl: String = "https://ark.cn-beijing.volces.com/api/v3",
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) : ModelBackend {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    override fun generate(
        messages: List<ChatMessage>,
        tools: List<ToolDeclaration>,
        config: GenerationConfig,
    ): Flow<AgentEvent> = flow {
        emit(AgentEvent.RunStarted(0))

        try {
            val apiUrl = if (endpointId != null) {
                "$baseUrl/endpoints/$endpointId/chat/completions"
            } else {
                "$baseUrl/chat/completions"
            }

            val arkMessages = messages.map { it.toArkMessage() }
            val arkTools = if (tools.isNotEmpty()) {
                tools.map { ArkTool(function = ArkFunction(it.name, it.description, it.parameters)) }
            } else null

            val requestBody = ArkChatRequest(
                model = modelId,
                messages = arkMessages,
                tools = arkTools,
                stream = config.streaming,
                temperature = config.temperature,
                topP = config.topP,
                maxTokens = config.maxOutputTokens,
            )

            val jsonBody = json.encodeToString(requestBody)
            val request = Request.Builder()
                .url(apiUrl)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                emit(AgentEvent.RunError(
                    RuntimeException("ARK API error: $errorBody"),
                    ErrorPhase.MODEL_CALL,
                ))
                return@flow
            }

            if (config.streaming) {
                val responseBody = response.body ?: run {
                    emit(AgentEvent.RunError(
                        RuntimeException("Empty response body"),
                        ErrorPhase.MODEL_CALL,
                    ))
                    return@flow
                }

                val textBuilder = StringBuilder()
                val parser = SseParser()
                parser.parse(responseBody.byteStream()) { data ->
                    if (data == "[DONE]") return@parse
                    try {
                        val chunk = json.decodeFromString<ArkChatChunk>(data)
                        val delta = chunk.choices?.firstOrNull()?.delta ?: return@parse

                        // Tool call delta
                        delta.toolCalls?.forEach { tc ->
                            try {
                                val parsedArgs = parseArguments(tc.function.arguments)
                                emit(AgentEvent.ToolCallRequest(tc.id, tc.function.name, parsedArgs))
                            } catch (_: Exception) {
                                emit(AgentEvent.ToolCallRequest(tc.id, tc.function.name, emptyMap()))
                            }
                        }

                        // Text content
                        delta.content?.let { content ->
                            textBuilder.append(content)
                            val isFinal = chunk.choices?.firstOrNull()?.finishReason != null
                            emit(AgentEvent.TextDelta(content, isFinal))
                        }

                        // Usage
                        chunk.usage?.let { usage ->
                            emit(AgentEvent.FinalResponse(
                                textBuilder.toString(),
                                TokenUsage(
                                    input = usage.promptTokens,
                                    output = usage.completionTokens,
                                ),
                            ))
                        }
                    } catch (_: Exception) {
                        // Skip malformed chunks
                    }
                }

                // Fallback FinalResponse if no usage was emitted
                if (textBuilder.isNotEmpty()) {
                    emit(AgentEvent.FinalResponse(textBuilder.toString(), TokenUsage(0, 0)))
                }
            } else {
                // Non-streaming
                val responseJson = response.body?.string() ?: ""
                val text = extractContent(responseJson)
                emit(AgentEvent.TextDelta(text, isFinal = true))
                emit(AgentEvent.FinalResponse(text, TokenUsage(0, 0)))
            }

            emit(AgentEvent.RunCompleted)

        } catch (e: Exception) {
            emit(AgentEvent.RunError(e, ErrorPhase.MODEL_CALL))
        }
    }.flowOn(Dispatchers.IO)

    private fun ChatMessage.toArkMessage(): ArkMessage = ArkMessage(
        role = when (role) {
            MessageRole.SYSTEM -> "system"
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.TOOL -> "tool"
        },
        content = content,
        toolCalls = toolCalls?.map {
            ArkToolCall(
                id = it.callId,
                function = ArkFunctionCall(
                    name = it.toolName,
                    arguments = Json.encodeToString(
                        it.arguments.mapValues { entry -> entry.value?.toString() ?: "null" }
                    ),
                ),
            )
        },
        toolCallId = toolCallId,
        name = name,
    )

    private fun parseArguments(argumentsJson: String): Map<String, Any?> {
        val element = json.parseToJsonElement(argumentsJson)
        if (element !is JsonObject) return emptyMap()
        return element.mapValues { (_, v) ->
            when (v) {
                is JsonPrimitive -> v.content
                else -> v.toString()
            }
        }
    }

    private fun extractContent(responseJson: String): String {
        return try {
            val obj = json.parseToJsonElement(responseJson).jsonObject
            obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content ?: ""
        } catch (_: Exception) { "" }
    }
}