package com.google.adk.tool

import com.google.adk.model.JsonSchema

interface Tool {
    val name: String
    val description: String
    val parameterSchema: JsonSchema

    suspend fun execute(args: Map<String, Any?>, context: ToolContext): Any?
}