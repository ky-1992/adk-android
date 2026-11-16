// adk-appfunction/src/test/java/com/google/adk/appfunction/AppFunctionToolTest.kt
package com.google.adk.appfunction

import com.google.adk.tool.ToolContext
import com.google.adk.tool.toDeclaration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppFunctionToolTest {

    private val registry = AppFunctionRegistry()

    private fun temperatureFunction(
        executor: suspend (JsonObject) -> JsonObject = { AppFunctionResults.success() },
    ) = AppFunction(
        id = "set_temperature",
        name = "设置空调温度",
        description = "设置空调目标温度，单位摄氏度",
        parameterSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("temperature") {
                    put("type", "number")
                    put("description", "目标温度，单位摄氏度")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("temperature")) }
        },
        executor = executor,
    )

    @Test
    fun `工具名 描述与参数 schema 来自 AppFunction`() {
        val fn = temperatureFunction()
        registry.register(fn)
        val tool = AppFunctionTool(registry, fn)

        assertEquals("app_set_temperature", tool.name)
        assertEquals("设置空调目标温度，单位摄氏度", tool.description)
        assertEquals(fn.parameterSchema, tool.parameterSchema)
    }

    @Test
    fun `toDeclaration 可生成带 app_ 前缀的 LLM 工具声明`() {
        val fn = temperatureFunction()
        registry.register(fn)
        val tool = AppFunctionTool(registry, fn)

        val declaration = tool.toDeclaration()

        assertEquals("app_set_temperature", declaration.name)
        assertEquals(fn.description, declaration.description)
        assertEquals(fn.parameterSchema, declaration.parameters)
    }

    @Test
    fun `toTools 为注册表中每个 AppFunction 生成独立工具`() {
        registry.register(temperatureFunction())
        registry.register(
            AppFunction(
                id = "open_window",
                name = "打开车窗",
                description = "打开指定侧车窗",
                executor = { AppFunctionResults.success() },
            ),
        )

        val tools = registry.toTools()

        assertEquals(listOf("app_open_window", "app_set_temperature"), tools.map { it.name })
        assertEquals(listOf("open_window", "set_temperature"), tools.map { it.function.id })
    }

    @Test
    fun `缺少必填参数返回结构化错误且不调用 executor`() = runTest {
        var executed = false
        registry.register(temperatureFunction { executed = true; AppFunctionResults.success() })
        val tool = registry.toTools().single()

        val result = tool.execute(emptyMap(), ToolContext())

        assertFalse(executed)
        assertEquals(JsonPrimitive(false), result["success"])
        val error = result["error"]!!.jsonObject
        assertEquals("MISSING_REQUIRED_PARAMETER", error["code"]!!.jsonPrimitive.content)
        assertEquals("temperature", error["parameter"]!!.jsonPrimitive.content)
    }

    @Test
    fun `字符串化的数字参数被规范化为 number 再交给 executor`() = runTest {
        var received: JsonObject? = null
        registry.register(temperatureFunction { received = it; AppFunctionResults.success() })
        val tool = registry.toTools().single()

        val result = tool.execute(mapOf("temperature" to "25"), ToolContext())

        assertEquals(JsonPrimitive(25.0), received!!["temperature"])
        assertEquals(AppFunctionResults.success(), result)
    }

    @Test
    fun `整型与布尔字符串参数被规范化`() = runTest {
        var received: JsonObject? = null
        registry.register(
            AppFunction(
                id = "set_fan",
                name = "设置风扇",
                description = "设置风扇档位并开关",
                parameterSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("level") { put("type", "integer") }
                        putJsonObject("open") { put("type", "boolean") }
                    }
                    putJsonArray("required") {
                        add(JsonPrimitive("level"))
                        add(JsonPrimitive("open"))
                    }
                },
                executor = { received = it; AppFunctionResults.success() },
            ),
        )
        val tool = registry.toTools().single()

        tool.execute(mapOf("level" to "2", "open" to "TRUE"), ToolContext())

        assertEquals(JsonPrimitive(2), received!!["level"])
        assertEquals(JsonPrimitive(true), received!!["open"])
    }

    @Test
    fun `参数类型不符返回 INVALID_PARAMETER_TYPE 且不调用 executor`() = runTest {
        var executed = false
        registry.register(temperatureFunction { executed = true; AppFunctionResults.success() })
        val tool = registry.toTools().single()

        val result = tool.execute(mapOf("temperature" to "abc"), ToolContext())

        assertFalse(executed)
        val error = result["error"]!!.jsonObject
        assertEquals("INVALID_PARAMETER_TYPE", error["code"]!!.jsonPrimitive.content)
        assertEquals("temperature", error["parameter"]!!.jsonPrimitive.content)
    }

    @Test
    fun `函数被注销后返回 UNKNOWN_FUNCTION 错误`() = runTest {
        var executed = false
        val fn = temperatureFunction { executed = true; AppFunctionResults.success() }
        registry.register(fn)
        val tool = AppFunctionTool(registry, fn)
        registry.unregister(fn.id)

        val result = tool.execute(mapOf("temperature" to "25"), ToolContext())

        assertFalse(executed)
        val error = result["error"]!!.jsonObject
        assertEquals("UNKNOWN_FUNCTION", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `executor 抛出异常时降级为 EXECUTION_ERROR 结构化 JSON`() = runTest {
        registry.register(temperatureFunction { throw IllegalStateException("空调离线") })
        val tool = registry.toTools().single()

        val result = tool.execute(mapOf("temperature" to "25"), ToolContext())

        assertEquals(JsonPrimitive(false), result["success"])
        val error = result["error"]!!.jsonObject
        assertEquals("EXECUTION_ERROR", error["code"]!!.jsonPrimitive.content)
        assertTrue(error["message"]!!.jsonPrimitive.content.contains("空调离线"))
    }

    @Test
    fun `CancellationException 原样重抛不被吞掉`() = runTest {
        registry.register(temperatureFunction { throw CancellationException("cancelled") })
        val tool = registry.toTools().single()

        assertFailsWith<CancellationException> {
            tool.execute(mapOf("temperature" to "25"), ToolContext())
        }
    }

    @Test
    fun `未声明的额外参数原样透传`() = runTest {
        var received: JsonObject? = null
        registry.register(temperatureFunction { received = it; AppFunctionResults.success() })
        val tool = registry.toTools().single()

        tool.execute(mapOf("temperature" to "25", "memo" to "顺便开窗"), ToolContext())

        assertEquals(JsonPrimitive(25.0), received!!["temperature"])
        assertEquals(JsonPrimitive("顺便开窗"), received!!["memo"])
    }

    @Test
    fun `嵌套 object 与 array 参数支持且 JSON 字符串可解析回结构`() = runTest {
        var received: JsonObject? = null
        registry.register(seatFunction { received = it; AppFunctionResults.success() })
        val tool = registry.toTools().single()

        val result = tool.execute(
            mapOf(
                "position" to mapOf("x" to 1, "y" to 2),
                "zones" to """[{"id":"front"},{"id":"rear"}]""",
            ),
            ToolContext(),
        )

        assertEquals(buildJsonObject { put("x", 1); put("y", 2) }, received!!["position"])
        assertEquals(
            buildJsonArray {
                add(buildJsonObject { put("id", "front") })
                add(buildJsonObject { put("id", "rear") })
            },
            received!!["zones"],
        )
        assertEquals(AppFunctionResults.success(), result)
    }

    @Test
    fun `object 参数收到非法 JSON 字符串时报 INVALID_PARAMETER_TYPE`() = runTest {
        var executed = false
        registry.register(seatFunction { executed = true; AppFunctionResults.success() })
        val tool = registry.toTools().single()

        val result = tool.execute(mapOf("position" to "not-json"), ToolContext())

        assertFalse(executed)
        val error = result["error"]!!.jsonObject
        assertEquals("INVALID_PARAMETER_TYPE", error["code"]!!.jsonPrimitive.content)
        assertEquals("position", error["parameter"]!!.jsonPrimitive.content)
    }

    @Test
    fun `executor 返回值原样透传不做二次包装`() = runTest {
        val payload = buildJsonObject {
            put("status", "ok")
            put("current", 22.5)
        }
        registry.register(temperatureFunction { payload })
        val tool = registry.toTools().single()

        val result = tool.execute(mapOf("temperature" to "25"), ToolContext())

        assertEquals(payload, result)
    }

    private fun seatFunction(
        executor: suspend (JsonObject) -> JsonObject = { AppFunctionResults.success() },
    ) = AppFunction(
        id = "move_seat",
        name = "调节座椅",
        description = "按位置与区域调节座椅",
        parameterSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("position") { put("type", "object") }
                putJsonObject("zones") { put("type", "array") }
            }
        },
        executor = executor,
    )
}
