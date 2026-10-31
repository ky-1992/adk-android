package com.google.adk.tools

import com.google.adk.model.JsonSchema
import com.google.adk.tool.Tool
import com.google.adk.tool.ToolContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class TimeTool : Tool {
    override val name = "get_current_time"
    override val description = "Returns the current date and time in ISO 8601 format with timezone."
    override val parameterSchema: JsonSchema = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(args: Map<String, Any?>, context: ToolContext): Any? {
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
    }
}