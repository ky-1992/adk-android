// adk-appfunction/src/main/java/com/google/adk/appfunction/AppFunctionResults.kt
package com.google.adk.appfunction

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * AppFunction 结果的统一结构化形式，executor 与 [AppFunctionTool] 共用同一套形状：
 *
 * - 成功：`{"success": true, "data": {...}}`（data 可省略）
 * - 失败：`{"success": false, "error": {"code": "...", "message": "...", "parameter": "..."}}`
 *
 * `parameter` 仅在错误与某个具体参数相关时出现。
 */
object AppFunctionResults {
    /** 错误码：执行时注册表中已不存在该函数（如已被注销）。 */
    const val CODE_UNKNOWN_FUNCTION = "UNKNOWN_FUNCTION"

    /** 错误码：缺少 parameterSchema 中 required 声明的参数。 */
    const val CODE_MISSING_REQUIRED_PARAMETER = "MISSING_REQUIRED_PARAMETER"

    /** 错误码：参数值与 parameterSchema 声明的类型不符（或无法解析为目标类型）。 */
    const val CODE_INVALID_PARAMETER_TYPE = "INVALID_PARAMETER_TYPE"

    /** 错误码：executor 执行过程中抛出异常。 */
    const val CODE_EXECUTION_ERROR = "EXECUTION_ERROR"

    /** 构造成功结果：`{"success": true, "data": data?}`。 */
    fun success(data: JsonObject? = null): JsonObject = buildJsonObject {
        put("success", true)
        if (data != null) put("data", data)
    }

    /** 构造失败结果：`{"success": false, "error": {code, message, parameter?}}`。 */
    fun error(code: String, message: String, parameter: String? = null): JsonObject = buildJsonObject {
        put("success", false)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
            if (parameter != null) put("parameter", parameter)
        }
    }
}
