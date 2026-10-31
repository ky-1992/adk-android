// adk-core/src/test/java/com/google/adk/event/AgentEventTest.kt
package com.google.adk.event

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AgentEventTest {

    @Test
    fun `TextDelta should carry text and final flag`() {
        val event = AgentEvent.TextDelta("Hello", isFinal = false)
        assertIs<AgentEvent>(event)
        assertEquals("Hello", event.text)
        assertEquals(false, event.isFinal)
    }

    @Test
    fun `ToolCallRequest should carry callId toolName and arguments`() {
        val event = AgentEvent.ToolCallRequest("call-1", "web_search", mapOf("query" to "test"))
        assertEquals("call-1", event.callId)
        assertEquals("web_search", event.toolName)
        assertEquals("test", event.arguments["query"])
    }

    @Test
    fun `RunError should carry throwable and phase`() {
        val ex = RuntimeException("boom")
        val event = AgentEvent.RunError(ex, ErrorPhase.MODEL_CALL)
        assertEquals("boom", event.error.message)
        assertEquals(ErrorPhase.MODEL_CALL, event.phase)
    }
}