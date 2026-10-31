// adk-core/src/test/java/com/google/adk/runner/AgentRunnerTest.kt
package com.google.adk.runner

import com.google.adk.agent.Agent
import com.google.adk.event.AgentEvent
import com.google.adk.model.ChatMessage
import com.google.adk.model.GenerationConfig
import com.google.adk.model.ModelBackend
import com.google.adk.model.ToolDeclaration
import com.google.adk.session.SessionStore
import com.google.adk.session.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeModelBackend(
    override val modelId: String = "fake",
    private val responseText: String = "Hello!",
) : ModelBackend {
    override fun generate(
        messages: List<ChatMessage>,
        tools: List<ToolDeclaration>,
        config: GenerationConfig,
    ): Flow<AgentEvent> = flow {
        emit(AgentEvent.RunStarted(0))
        emit(AgentEvent.TextDelta(responseText, isFinal = true))
        emit(AgentEvent.RunCompleted)
    }
}

class FakeSessionStore : SessionStore {
    private val sessions = mutableMapOf<String, Session>()
    override suspend fun createSession(session: Session): Session {
        sessions[session.id] = session; return session
    }
    override suspend fun getSession(sessionId: String, userId: String) = sessions[sessionId]
    override suspend fun listSessions(userId: String, appName: String?) = sessions.values.toList()
    override suspend fun appendEvent(sessionId: String, event: AgentEvent) {}
    override suspend fun updateState(sessionId: String, state: Map<String, Any?>) {}
    override suspend fun deleteSession(sessionId: String) { sessions.remove(sessionId) }
}

class AgentRunnerTest {

    @Test
    fun `runner should emit text delta and run completed`() = runTest {
        val store = FakeSessionStore()
        val runner = AgentRunner(store)
        val agent = com.google.adk.agent.LlmAgent(
            name = "test-agent",
            description = "A test agent",
            model = FakeModelBackend(),
            instruction = "You are helpful.",
        )
        val events = mutableListOf<AgentEvent>()
        runner.run(
            agent = agent,
            sessionId = "s1",
            userId = "u1",
            newMessage = UserMessage("hi"),
        ).collect { events.add(it) }

        assertTrue(events.any { it is AgentEvent.TextDelta })
        assertTrue(events.any { it is AgentEvent.RunCompleted })
        assertEquals("Hello!", (events.first { it is AgentEvent.TextDelta } as AgentEvent.TextDelta).text)
    }
}