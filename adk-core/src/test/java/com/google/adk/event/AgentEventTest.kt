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

    @Test
    fun `RunStarted should carry input token count`() {
        val event = AgentEvent.RunStarted(42)
        assertEquals(42, event.inputTokenCount)
    }

    @Test
    fun `ToolCallResult should carry callId toolName result and optional error`() {
        val success = AgentEvent.ToolCallResult("call-1", "calc", 42)
        assertEquals("call-1", success.callId)
        assertEquals("calc", success.toolName)
        assertEquals(42, success.result)
        assertEquals(null, success.error)

        val failure = AgentEvent.ToolCallResult("call-2", "search", null, "timeout")
        assertEquals(null, failure.result)
        assertEquals("timeout", failure.error)
    }

    @Test
    fun `FinalResponse should carry text and token usage`() {
        val usage = TokenUsage(input = 100, output = 50)
        assertEquals(150, usage.total)
        val event = AgentEvent.FinalResponse("done", usage)
        assertEquals("done", event.text)
        assertEquals(100, event.tokenCount.input)
        assertEquals(50, event.tokenCount.output)
        assertEquals(150, event.tokenCount.total)
    }

    @Test
    fun `RunCompleted should be a singleton data object`() {
        val event = AgentEvent.RunCompleted
        assertIs<AgentEvent>(event)
        assertIs<AgentEvent.RunCompleted>(event)
    }
}