// adk-appfunction/src/main/java/com/google/adk/appfunction/AppFunctionTool.kt
package com.google.adk.appfunction

import com.google.adk.model.JsonSchema
import com.google.adk.tool.Tool
import com.google.adk.tool.ToolContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 把单个 [AppFunction] 桥接为独立的 ADK [Tool]，使 LLM 能对其自动智能 functionCall：
 *
 * - 工具名为 `app_<function.id>`，描述与参数 schema 原样来自 [AppFunction]，
 *   因此 AgentRunner 据此生成的 [com.google.adk.model.ToolDeclaration] 可直接暴露给模型；
 * - 执行时按 id 从 [registry] 重新解析函数：函数已被注销时返回 UNKNOWN_FUNCTION 结构化错误；
 * - 执行前按 parameterSchema 校验参数（required 缺失、类型不符都会得到结构化错误且不调用
 *   executor），并把上游后端常见的"字符串化"参数值（如 `"25"`、`"true"`、嵌套 JSON 字符串）
 *   规范化回 schema 声明的 JSON 类型后再传给 executor；
 * - executor 抛出的异常被捕获并降级为 EXECUTION_ERROR 结构化 JSON（[CancellationException]
 *   会原样重抛），保证 [Tool.execute] 不向 Runner 抛异常。
 */
class AppFunctionTool(
    private val registry: AppFunctionRegistry,
    val function: AppFunction,
) : Tool {
    override val name: String = "app_${function.id}"
    override val description: String = function.description
    override val parameterSchema: JsonSchema = function.parameterSchema

    override suspend fun execute(args: Map<String, Any?>, context: ToolContext): JsonObject {
        val current = registry.getById(function.id)
            ?: return AppFunctionResults.error(
                AppFunctionResults.CODE_UNKNOWN_FUNCTION,
                "未找到 AppFunction '${function.id}'，可能已被注销",
            )

        val normalized = try {
            normalizeArguments(args, current.parameterSchema)
        } catch (e: ArgumentsException) {
            return AppFunctionResults.error(e.code, e.message ?: "参数校验失败", e.parameter)
        }

        return try {
            current.executor(normalized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppFunctionResults.error(
                AppFunctionResults.CODE_EXECUTION_ERROR,
                "执行 AppFunction '${current.id}' 失败: ${e.message}",
            )
        }
    }

    /**
     * 参数校验与规范化：
     * 1) required 声明的参数必须存在；
     * 2) 已声明 properties 的参数按其 type 规范化（string/number/integer/boolean/array/object/null），
     *    不符时抛出 [ArgumentsException]；
     * 3) 未声明或未标注 type 的参数原样透传（JSON Schema 默认允许额外属性）。
     */
    private fun normalizeArguments(args: Map<String, Any?>, schema: JsonObject): JsonObject {
        val raw = JsonObject(args.mapValues { (_, value) -> value.toJsonElement() })
        val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
        val required = schema["required"] as? JsonArray ?: JsonArray(emptyList())

        for (element in required) {
            val key = (element as? JsonPrimitive)?.contentOrNull ?: continue
            if (!raw.containsKey(key)) {
                throw ArgumentsException(
                    AppFunctionResults.CODE_MISSING_REQUIRED_PARAMETER,
                    "缺少必填参数 '$key'",
                    parameter = key,
                )
            }
        }

        return buildJsonObject {
            raw.forEach { (key, value) ->
                val propertySchema = properties[key] as? JsonObject
                put(key, propertySchema?.let { coerceValue(key, value, it) } ?: value)
            }
        }
    }

    /** 按 property schema 的 type 把参数值规范化为声明的 JSON 类型。 */
    private fun coerceValue(parameter: String, value: JsonElement, schema: JsonObject): JsonElement {
        val type = (schema["type"] as? JsonPrimitive)?.contentOrNull?.lowercase() ?: return value
        val primitive = value as? JsonPrimitive

        fun fail(expected: String): Nothing = throw ArgumentsException(
            AppFunctionResults.CODE_INVALID_PARAMETER_TYPE,
            "参数 '$parameter' 应为 $expected 类型",
            parameter = parameter,
        )

        return when (type) {
            "string" -> if (primitive != null) JsonPrimitive(primitive.content) else fail("string")
            "number" -> if (primitive != null) {
                primitive.content.toDoubleOrNull()
                    ?.let { JsonPrimitive(it) } ?: fail("number")
            } else {
                fail("number")
            }
            "integer" -> if (primitive != null) {
                val asLong = primitive.content.toLongOrNull()
                    ?: primitive.content.toDoubleOrNull()
                        ?.takeIf { it % 1.0 == 0.0 }?.toLong()
                asLong?.let { JsonPrimitive(it) } ?: fail("integer")
            } else {
                fail("integer")
            }
            "boolean" -> when {
                primitive?.content.equals("true", ignoreCase = true) -> JsonPrimitive(true)
                primitive?.content.equals("false", ignoreCase = true) -> JsonPrimitive(false)
                else -> fail("boolean")
            }
            "array" -> (value as? JsonArray)
                ?: (primitive?.content?.parseJsonOrNull() as? JsonArray)
                ?: fail("array")
            "object" -> (value as? JsonObject)
                ?: (primitive?.content?.parseJsonOrNull() as? JsonObject)
                ?: fail("object")
            "null" -> if (value is JsonNull) value else fail("null")
            else -> value // 未声明/未知 type：不校验，原样透传
        }
    }

    /** 把上游传入的任意参数值尽力转换为 [JsonElement]，未识别类型降级为字符串。 */
    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }

    private fun String.parseJsonOrNull(): JsonElement? =
        try {
            Json.parseToJsonElement(this)
        } catch (_: Exception) {
            null
        }

    /** 参数校验失败，由 [execute] 捕获并转换为结构化错误 JSON。 */
    private class ArgumentsException(
        val code: String,
        message: String,
        val parameter: String? = null,
    ) : RuntimeException(message)
}

/**
 * 把注册表中的每个 [AppFunction] 一一桥接为独立的 [AppFunctionTool]。
 * 返回的工具列表可直接加入 LlmAgent.tools，由 LLM 自动选择并 functionCall。
 */
fun AppFunctionRegistry.toTools(): List<AppFunctionTool> = getAll().map { AppFunctionTool(this, it) }
