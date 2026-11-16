// adk-appfunction/src/main/java/com/google/adk/appfunction/AppFunction.kt
package com.google.adk.appfunction

import com.google.adk.model.JsonSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 一个可被 LLM 自动智能调用的 AppFunction（车机应用能力）。
 *
 * @property id 全局唯一标识（如 "set_temperature"），注册表按 id 去重；
 *   桥接为 ADK Tool 后工具名会带上 "app_" 前缀（即 `app_<id>`）。
 * @property name 中文友好名（如 "设置空调温度"），面向开发者与 UI 展示。
 * @property description 面向 LLM 的功能说明（建议中文，写清何时调用、参数含义），
 *   会原样作为 Tool 描述暴露给模型。
 * @property parameterSchema 参数 JSON Schema（顶层建议为 object），供 LLM 生成 functionCall 参数。
 * @property executor 实际执行体：入参是经 [AppFunctionTool] 校验/规范化后的参数 [JsonObject]，
 *   返回结果 [JsonObject]。结果可用 [AppFunctionResults.success] / [AppFunctionResults.error]
 *   构造统一结构，其中 error 形式为 `{"success": false, "error": {"code", "message", "parameter?"}}`。
 */
class AppFunction(
    val id: String,
    val name: String,
    val description: String,
    val parameterSchema: JsonSchema = DEFAULT_PARAMETER_SCHEMA,
    val executor: suspend (JsonObject) -> JsonObject,
) {
    companion object {
        /** 缺省参数 schema：不带任何参数的空 object。 */
        val DEFAULT_PARAMETER_SCHEMA: JsonSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { }
        }
    }
}
