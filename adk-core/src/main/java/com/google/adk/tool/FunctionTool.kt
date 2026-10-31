package com.google.adk.tool

import com.google.adk.model.JsonSchema
import com.google.adk.model.ToolDeclaration

class FunctionTool(
    override val name: String,
    override val description: String,
    override val parameterSchema: JsonSchema,
    private val block: suspend (Map<String, Any?>, ToolContext) -> Any?,
) : Tool {
    override suspend fun execute(args: Map<String, Any?>, context: ToolContext): Any? {
        return block(args, context)
    }
}

fun Tool.toDeclaration(): ToolDeclaration = ToolDeclaration(
    name = name,
    description = description,
    parameters = parameterSchema,
)