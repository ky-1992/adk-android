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

class FileReadTool : Tool {
    override val name = "file_read"
    override val description = "Reads the content of a file from device storage."
    override val parameterSchema: JsonSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Absolute path to the file")
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("path"))))
    }

    override suspend fun execute(args: Map<String, Any?>, context: ToolContext): Any? {
        val path = args["path"]?.toString() ?: return "Error: no path"
        return try {
            val file = File(path)
            if (!file.exists()) return "File not found: $path"
            if (!file.canRead()) return "Cannot read file: $path"
            file.readText().take(5000)
        } catch (e: Exception) {
            "Read error: ${e.message}"
        }
    }
}