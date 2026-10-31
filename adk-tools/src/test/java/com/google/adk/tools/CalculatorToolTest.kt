package com.google.adk.tools

import com.google.adk.tool.ToolContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CalculatorToolTest {
    @Test
    fun `CalculatorTool evaluates simple expression`() = runTest {
        val tool = CalculatorTool()
        val result = tool.execute(mapOf("expression" to "2 + 3 * 4"), ToolContext())
        assertEquals("14.0", result.toString())
    }

    @Test
    fun `CalculatorTool returns error for missing expression`() = runTest {
        val tool = CalculatorTool()
        val result = tool.execute(emptyMap(), ToolContext())
        assertContains(result.toString(), "Error")
    }
}