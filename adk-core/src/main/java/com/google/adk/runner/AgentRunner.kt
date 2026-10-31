// adk-core/src/main/java/com/google/adk/runner/AgentRunner.kt
package com.google.adk.runner

import com.google.adk.agent.Agent
import com.google.adk.agent.LlmAgent
import com.google.adk.event.AgentEvent
import com.google.adk.event.ErrorPhase
import com.google.adk.model.ChatMessage
import com.google.adk.model.GenerationConfig
import com.google.adk.model.MessageRole
import com.google.adk.model.ToolDeclaration
import com.google.adk.session.Session
import com.google.adk.session.SessionStore
import com.google.adk.session.eventsToMessages
import com.google.adk.tool.ToolContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID

class AgentRunner(
    private val sessionStore: SessionStore,
) {
    fun run(
        agent: Agent,
        sessionId: String,
        userId: String,
        newMessage: UserMessage,
        runConfig: RunConfig = RunConfig(),
    ): Flow<AgentEvent> = flow {
        val llmAgent = agent as? LlmAgent
            ?: run {
                emit(
                    AgentEvent.RunError(
                        IllegalStateException("Only LlmAgent is supported"),
                        ErrorPhase.MODEL_CALL,
                    )
                )
                return@flow
            }

        emit(AgentEvent.RunStarted(0))

        try {
            // Load or create session
            var session = sessionStore.getSession(sessionId, userId)
            if (session == null) {
                session = Session(
                    id = sessionId,
                    userId = userId,
                    appName = "adk",
                    agentName = agent.name,
                    state = emptyMap(),
                    events = emptyList(),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
                sessionStore.createSession(session)
            }

            // Build messages
            val messages = buildMessageList(llmAgent, session, newMessage)

            // Generate
            val genConfig = GenerationConfig(
                temperature = runConfig.temperature,
                topP = runConfig.topP,
                streaming = runConfig.streaming,
            )
            val modelEvents = llmAgent.model.generate(
                messages = messages,
                tools = llmAgent.tools.map { ToolDeclaration(it.name, it.description, it.parameterSchema) },
                config = genConfig,
            )

            // Collect model events, handle tool calls
            var iterations = 0
            var terminal = false
            while (!terminal && iterations < runConfig.maxToolIterations) {
                var hasToolCalls = false

                modelEvents.collect { event ->
                    when (event) {
                        is AgentEvent.TextDelta -> {
                            emit(event)
                            if (event.isFinal) {
                                appendAssistantMessage(messages, event.text)
                            }
                        }

                        is AgentEvent.ToolCallRequest -> {
                            emit(event)
                            hasToolCalls = true
                            // Execute tool
                            val tool = llmAgent.tools.find { it.name == event.toolName }
                            val result = if (tool != null) {
                                try {
                                    val ctx = ToolContext(sessionState = session.state)
                                    val res = tool.execute(event.arguments, ctx)
                                    AgentEvent.ToolCallResult(event.callId, event.toolName, res)
                                } catch (e: Exception) {
                                    AgentEvent.ToolCallResult(
                                        event.callId,
                                        event.toolName,
                                        null,
                                        e.message,
                                    )
                                }
                            } else {
                                AgentEvent.ToolCallResult(
                                    event.callId,
                                    event.toolName,
                                    null,
                                    "Tool not found",
                                )
                            }
                            emit(result)
                            appendToolMessages(messages, event, result)
                        }

                        is AgentEvent.RunError -> {
                            emit(event)
                            terminal = true
                        }

                        is AgentEvent.RunCompleted -> { /* inner completion, handled below */ }

                        else -> {}
                    }
                    // Persist event
                    sessionStore.appendEvent(sessionId, event)
                }

                if (!hasToolCalls) terminal = true
                iterations++
            }

            emit(AgentEvent.RunCompleted)
        } catch (e: Exception) {
            emit(AgentEvent.RunError(e, ErrorPhase.MODEL_CALL))
        }
    }

    private fun buildMessageList(
        agent: LlmAgent,
        session: Session,
        newMessage: UserMessage,
    ): MutableList<ChatMessage> {
        val msgs = mutableListOf<ChatMessage>()
        // System prompt
        msgs.add(
            ChatMessage(
                role = MessageRole.SYSTEM,
                content = agent.instruction,
            )
        )
        // History from session events
        msgs.addAll(session.eventsToMessages())
        // New user message
        msgs.add(
            ChatMessage(
                role = MessageRole.USER,
                content = newMessage.content,
            )
        )
        return msgs
    }

    private fun appendAssistantMessage(
        messages: MutableList<ChatMessage>,
        text: String,
    ) {
        messages.add(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = text,
            )
        )
    }

    private fun appendToolMessages(
        messages: MutableList<ChatMessage>,
        request: AgentEvent.ToolCallRequest,
        result: AgentEvent.ToolCallResult,
    ) {
        messages.add(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "",
                toolCalls = listOf(request),
            )
        )
        val resultContent =
            if (result.error != null) "Error: ${result.error}" else result.result?.toString() ?: ""
        messages.add(
            ChatMessage(
                role = MessageRole.TOOL,
                content = resultContent,
                toolCallId = result.callId,
                name = result.toolName,
            )
        )
    }
}