package com.google.adk.tools

import com.google.adk.model.JsonSchema
import com.google.adk.tool.Tool
import com.google.adk.tool.ToolContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

class FileWriteTool : Tool {
    override val name = "file_write"
    override val description = "Writes content to a file on device storage."
    override val parameterSchema: JsonSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Absolute path to the file")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "Content to write")
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("path"), JsonPrimitive("content"))))
    }

    override suspend fun execute(args: Map<String, Any?>, context: ToolContext): Any? {
        val path = args["path"]?.toString() ?: return "Error: no path"
        val content = args["content"]?.toString() ?: return "Error: no content"
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            "Successfully wrote ${content.length} characters to $path"
        } catch (e: Exception) {
            "Write error: ${e.message}"
        }
    }
}