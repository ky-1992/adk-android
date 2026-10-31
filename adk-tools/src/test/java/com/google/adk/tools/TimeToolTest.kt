package com.google.adk.tools

import com.google.adk.tool.ToolContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class TimeToolTest {
    @Test
    fun `TimeTool returns current datetime string`() = runTest {
        val tool = TimeTool()
        val result = tool.execute(emptyMap(), ToolContext())
        assertNotNull(result)
        assertContains(result.toString(), "20") // at least year prefix
    }
}