package com.google.adk.tool

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.google.adk.model.ToolDeclaration
import kotlinx.serialization.json.JsonObject

class FunctionToolTest {

    @Test
    fun `FunctionTool wraps a lambda as a Tool`() = runTest {
        val schema: JsonObject = buildJsonObject {
            putJsonObject("properties") {
                putJsonObject("a") { put("type", "number") }
                putJsonObject("b") { put("type", "number") }
            }
            putJsonArray("required") { }
        }

        val tool = FunctionTool(
            name = "add",
            description = "Add two numbers",
            parameterSchema = schema,
            block = { args, _ ->
                val a = (args["a"] as Number).toDouble()
                val b = (args["b"] as Number).toDouble()
                a + b
            },
        )

        assertEquals("add", tool.name)
        assertEquals("Add two numbers", tool.description)

        val result = tool.execute(mapOf("a" to 3, "b" to 4), ToolContext())
        assertEquals(7.0, result)
    }

    @Test
    fun `toDeclaration should convert Tool to ToolDeclaration`() {
        val schema: JsonObject = buildJsonObject {
            putJsonObject("properties") {
                putJsonObject("query") { put("type", "string") }
            }
            putJsonArray("required") { }
        }
        val tool = FunctionTool("search", "Search web", schema) { _, _ -> "done" }
        val decl = tool.toDeclaration()

        assertIs<ToolDeclaration>(decl)
        assertEquals("search", decl.name)
        assertEquals("Search web", decl.description)
        assertEquals(schema, decl.parameters)
    }

    @Test
    fun `FunctionTool with null return should not crash`() = runTest {
        val schema: JsonObject = buildJsonObject {
            putJsonObject("properties") { }
            putJsonArray("required") { }
        }
        val tool = FunctionTool("noop", "Does nothing", schema) { _, _ -> null }
        val result = tool.execute(emptyMap(), ToolContext())
        assertEquals(null, result)
    }
}