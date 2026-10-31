package com.google.adk.ark

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArkModelBackendTest {

    @Test
    fun `should parse SSE data lines`() = runTest {
        val sseBody = """data: {"id":"chatcmpl-1","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
data: {"id":"chatcmpl-1","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}
data: [DONE]"""

        val parser = SseParser()
        val chunks = mutableListOf<String>()
        parser.parse(sseBody.byteInputStream()) { chunk ->
            if (chunk != "[DONE]") chunks.add(chunk)
        }
        assertEquals(2, chunks.size)
    }

    @Test
    fun `should handle malformed SSE gracefully`() = runTest {
        val sseBody = """data: {"bad json
data: {"id":"ok","choices":[]}
data: [DONE]"""

        val parser = SseParser()
        val chunks = mutableListOf<String>()
        parser.parse(sseBody.byteInputStream()) { chunk ->
            if (chunk != "[DONE]") chunks.add(chunk)
        }
        assertEquals(1, chunks.size) // only the valid line
    }
}