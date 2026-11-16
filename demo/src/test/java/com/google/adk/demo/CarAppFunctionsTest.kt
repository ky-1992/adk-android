package com.google.adk.demo

import com.google.adk.appfunction.AppFunctionTool
import com.google.adk.appfunction.AppFunctionResults
import com.google.adk.tool.ToolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CarAppFunctionsTest {

    private fun tool(id: String): AppFunctionTool =
        CarAppFunctions.tools().first { it.name == "app_$id" } as AppFunctionTool

    private fun execute(id: String, args: Map<String, Any?>): JsonObject =
        runBlocking { tool(id).execute(args, ToolContext()) }

    @Test
    fun `car functions registered with app_ prefix and unique ids`() {
        val tools = CarAppFunctions.tools()
        val names = tools.map { it.name }
        assertEquals(
            setOf(
                "app_ac_power",
                "app_ac_set_temperature",
                "app_ac_set_fan_speed",
                "app_media_play",
                "app_media_pause",
                "app_media_skip",
                "app_media_set_volume",
                "app_nav_set_destination",
                "app_nav_cancel",
                "app_window_set",
                "app_sunroof_set",
                "app_seat_heating",
                "app_car_status",
            ),
            names.toSet(),
        )
        assertEquals(13, tools.size)
        assertTrue(tools.all { it.description.isNotBlank() })
        assertTrue(tools.all { it.parameterSchema.containsKey("type") })
    }

    @Test
    fun `availableTools keeps builtin tools and appends car tools`() {
        val names = DemoAgentOrchestrator.availableTools().map { it.name }
        // Builtin tools untouched.
        assertTrue(
            names.containsAll(
                listOf("get_current_time", "calculator", "web_search", "web_fetch", "file_read", "file_write"),
            ),
        )
        // 6 builtin + 13 car = 19.
        assertEquals(19, names.size)
    }

    @Test
    fun `ac_set_temperature accepts stringified arg and returns structured success`() {
        val result = execute("ac_set_temperature", mapOf("temp" to "24"))
        assertTrue(result["success"]!!.jsonPrimitive.boolean)
        val data = result["data"]!!.jsonObject
        assertEquals("ok", data["status"]!!.jsonPrimitive.content)
        assertEquals(24, data["temp"]!!.jsonPrimitive.int)
        assertEquals("on", data["ac"]!!.jsonPrimitive.content)
    }

    @Test
    fun `ac_set_temperature rejects out of range with structured error`() {
        val result = execute("ac_set_temperature", mapOf("temp" to 40))
        assertFalse(result["success"]!!.jsonPrimitive.boolean)
        val error = result["error"]!!.jsonObject
        assertEquals(AppFunctionResults.CODE_EXECUTION_ERROR, error["code"]!!.jsonPrimitive.content)
        assertEquals("temp", error["parameter"]!!.jsonPrimitive.content)
    }

    @Test
    fun `ac_power toggles state`() {
        val on = execute("ac_power", mapOf("on" to "true"))
        assertTrue(on["success"]!!.jsonPrimitive.boolean)
        assertEquals("on", on["data"]!!.jsonObject["ac"]!!.jsonPrimitive.content)

        val off = execute("ac_power", mapOf("on" to false))
        assertEquals("off", off["data"]!!.jsonObject["ac"]!!.jsonPrimitive.content)
    }

    @Test
    fun `media play pause skip and volume`() {
        val play = execute("media_play", mapOf("artist" to "周杰伦"))
        val playData = play["data"]!!.jsonObject
        assertEquals("playing", playData["media"]!!.jsonPrimitive.content)
        assertEquals("周杰伦", playData["artist"]!!.jsonPrimitive.content)

        val skip = execute("media_skip", mapOf("direction" to "next"))
        assertEquals("playing", skip["data"]!!.jsonObject["media"]!!.jsonPrimitive.content)

        val volume = execute("media_set_volume", mapOf("volume" to "30"))
        assertEquals(30, volume["data"]!!.jsonObject["media_volume"]!!.jsonPrimitive.int)

        val pause = execute("media_pause", emptyMap())
        assertEquals("paused", pause["data"]!!.jsonObject["media"]!!.jsonPrimitive.content)
    }

    @Test
    fun `navigation set and cancel`() {
        val nav = execute("nav_set_destination", mapOf("destination" to "公司"))
        val navData = nav["data"]!!.jsonObject
        assertEquals("navigating", navData["navigation"]!!.jsonPrimitive.content)
        assertEquals("公司", navData["destination"]!!.jsonPrimitive.content)

        val cancel = execute("nav_cancel", emptyMap())
        assertEquals("idle", cancel["data"]!!.jsonObject["navigation"]!!.jsonPrimitive.content)
    }

    @Test
    fun `window_set all updates every window`() {
        val result = execute("window_set", mapOf("position" to "all", "percent" to 50))
        assertTrue(result["success"]!!.jsonPrimitive.boolean)
        assertEquals(50, CarSimulation.state.value.windows["front_left"])
        assertEquals(50, CarSimulation.state.value.windows["rear_right"])
    }

    @Test
    fun `window_set rejects invalid percent and position`() {
        val badPercent = execute("window_set", mapOf("position" to "front_left", "percent" to 120))
        assertFalse(badPercent["success"]!!.jsonPrimitive.boolean)
        assertEquals("percent", badPercent["error"]!!.jsonObject["parameter"]!!.jsonPrimitive.content)

        val badPosition = execute("window_set", mapOf("position" to "trunk", "percent" to 20))
        assertFalse(badPosition["success"]!!.jsonPrimitive.boolean)
        assertEquals("position", badPosition["error"]!!.jsonObject["parameter"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sunroof and seat heating`() {
        val sunroof = execute("sunroof_set", mapOf("percent" to "80"))
        assertEquals(80, sunroof["data"]!!.jsonObject["sunroof_percent"]!!.jsonPrimitive.int)

        val seat = execute("seat_heating", mapOf("seat" to "driver", "level" to "3"))
        assertTrue(seat["success"]!!.jsonPrimitive.boolean)
        assertEquals(3, CarSimulation.state.value.seatHeating["driver"])

        val badLevel = execute("seat_heating", mapOf("seat" to "driver", "level" to 5))
        assertFalse(badLevel["success"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `car_status returns the full simulation state`() = runBlocking {
        // Arrange a known state.
        execute("ac_set_temperature", mapOf("temp" to 21))
        execute("nav_set_destination", mapOf("destination" to "公司"))
        val result = tool("car_status").execute(emptyMap(), ToolContext())
        val data = result["data"]!!.jsonObject
        assertEquals("ok", data["status"]!!.jsonPrimitive.content)
        assertEquals(21, data["ac"]!!.jsonObject["temp"]!!.jsonPrimitive.int)
        assertEquals("公司", data["navigation"]!!.jsonPrimitive.content)
        assertEquals(0, CarSimulation.state.value.sunroofPercent)
    }
}
