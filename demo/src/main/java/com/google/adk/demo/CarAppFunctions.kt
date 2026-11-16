package com.google.adk.demo

import com.google.adk.appfunction.AppFunction
import com.google.adk.appfunction.AppFunctionRegistry
import com.google.adk.appfunction.AppFunctionResults
import com.google.adk.appfunction.toTools
import com.google.adk.model.JsonSchema
import com.google.adk.tool.Tool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 模拟车控状态。AppFunction executor 直接读写该内存状态模拟真实车辆控制器；
 * `app_car_status` 可查询完整状态。
 */
data class CarState(
    val acOn: Boolean = false,
    val acTemp: Int = 22,
    val acFanSpeed: Int = 3,
    val mediaPlaying: Boolean = false,
    val currentSong: String? = null,
    val currentArtist: String? = null,
    val mediaVolume: Int = 50,
    val navigationDestination: String? = null,
    val windows: Map<String, Int> = mapOf(
        "front_left" to 0,
        "front_right" to 0,
        "rear_left" to 0,
        "rear_right" to 0,
    ),
    val sunroofPercent: Int = 0,
    val seatHeating: Map<String, Int> = mapOf(
        "driver" to 0,
        "passenger" to 0,
    ),
)

object CarSimulation {
    private val _state = MutableStateFlow(CarState())
    val state: StateFlow<CarState> get() = _state

    fun update(transform: (CarState) -> CarState) {
        _state.update(transform)
    }
}

/**
 * 模拟车控 AppFunction 集合：空调（开关/温度/风量）、多媒体（播放/暂停/切歌/音量）、
 * 导航（目的地/取消）、车窗与天窗（开度百分比）、座椅加热（档位）与整车状态查询。
 *
 * 每个 [AppFunction] 均带中文 name/description 与 JSON Schema 参数说明，executor 返回
 * [AppFunctionResults] 统一结构（成功 `{"success":true,"data":{"status":"ok",...}}`，
 * 失败 `{"success":false,"error":{code,message,parameter?}}`）。经 [AppFunctionTool]
 * 桥接后工具名为 `app_<id>`，LLM 可对「打开空调调到 24 度」「播放周杰伦的歌并导航去
 * 公司」等话术自动 functionCall。
 */
object CarAppFunctions {

    /** 注册表单例（lazy）：availableTools() 会被反复调用，注册必须只发生一次。 */
    private val registry: AppFunctionRegistry by lazy {
        AppFunctionRegistry().apply {
            register(acPower())
            register(acSetTemperature())
            register(acSetFanSpeed())
            register(mediaPlay())
            register(mediaPause())
            register(mediaSkip())
            register(mediaSetVolume())
            register(navSetDestination())
            register(navCancel())
            register(windowSet())
            register(sunroofSet())
            register(seatHeating())
            register(carStatus())
        }
    }

    /** 车控能力桥接后的 ADK Tool 列表，可直接加入 LlmAgent.tools。 */
    fun tools(): List<Tool> = registry.toTools()

    // ------------------------------------------------------------------
    // schema / 结果辅助
    // ------------------------------------------------------------------

    private fun param(type: String, description: String): JsonObject = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    private fun objectSchema(properties: JsonObject, required: List<String> = emptyList()): JsonSchema =
        buildJsonObject {
            put("type", "object")
            put("properties", properties)
            if (required.isNotEmpty()) {
                put("required", JsonArray(required.map { JsonPrimitive(it) }))
            }
        }

    /** 统一成功结果：{"success":true,"data":{"status":"ok", ...pairs}}，null 值对跳过。 */
    private fun ok(vararg pairs: Pair<String, Any?>): JsonObject =
        AppFunctionResults.success(
            buildJsonObject {
                put("status", "ok")
                pairs.forEach { (key, value) ->
                    when (value) {
                        null -> {}
                        is Boolean -> put(key, value)
                        is Int -> put(key, value)
                        is String -> put(key, value)
                        is JsonObject -> put(key, value)
                        else -> put(key, value.toString())
                    }
                }
            },
        )

    /** 范围/取值校验失败等执行类错误（缺参/类型错误由 AppFunctionTool 层统一返回）。 */
    private fun reject(message: String, parameter: String? = null): JsonObject =
        AppFunctionResults.error(AppFunctionResults.CODE_EXECUTION_ERROR, message, parameter)

    // ------------------------------------------------------------------
    // 空调
    // ------------------------------------------------------------------

    private fun acPower() = AppFunction(
        id = "ac_power",
        name = "空调开关",
        description = "打开或关闭空调。参数 on 为 true 打开、false 关闭。用户说打开空调、关闭空调、开空调时调用。",
        parameterSchema = objectSchema(
            properties = buildJsonObject {
                put("on", param("boolean", "true=打开空调，false=关闭空调"))
            },
            required = listOf("on"),
        ),
        executor = { args ->
            val on = args["on"]?.jsonPrimitive?.booleanOrNull
                ?: return@AppFunction reject("参数 on 必须为布尔值", "on")
            CarSimulation.update { it.copy(acOn = on) }
            if (on) {
                ok("ac" to "on", "temp" to CarSimulation.state.value.acTemp)
            } else {
                ok("ac" to "off")
            }
        },
    )

    private fun acSetTemperature() = AppFunction(
        id = "ac_set_temperature",
        name = "设置空调温度",
        description = "设置空调目标温度，整数，单位摄氏度，范围 16-30（如「空调调到 24 度」→ temp=24）。",
        parameterSchema = objectSchema(
            properties = buildJsonObject {
                put("temp", param("integer", "目标温度（℃），16-30"))
            },
            required = listOf("temp"),
        ),
        executor = { args ->
            val temp = args["temp"]?.jsonPrimitive?.intOrNull
                ?: return@AppFunction reject("参数 temp 必须为整数", "temp")
            if (temp !in 16..30) {
                return@AppFunction reject("空调温度需在 16-30℃ 之间，收到 $temp", "temp")
            }
            CarSimulation.update { it.copy(acTemp = temp, acOn = true) }
            ok("ac" to "on", "temp" to temp)
        },
    )

    private fun acSetFanSpeed() = AppFunction(
        id = "ac_set_fan_speed",
        name = "设置空调风量",
        description = "设置空调风量档位，整数 1-7（7 最大）。调大/调小风量时先换算成档位再调用。",
        parameterSchema = objectSchema(
            properties = buildJsonObject {
                put("level", param("integer", "风量档位 1-7"))
            },
            required = listOf("level"),
        ),
        executor = { args ->
            val level = args["level"]?.jsonPrimitive?.intOrNull
                ?: return@AppFunction reject("参数 level 必须为整数", "level")
            if (level !in 1..7) {
                return@AppFunction reject("空调风量档位需在 1-7 之间，收到 $level", "level")
            }
            CarSimulation.update { it.copy(acFanSpeed = level, acOn = true) }
            ok("ac_fan_speed" to level)
        },
    )

    // ------------------------------------------------------------------
    // 多媒体
    // ------------------------------------------------------------------

    private fun mediaPlay() = AppFunction(
        id = "media_play",
        name = "播放音乐",
        description = "开始播放音乐。可指定歌曲名或歌手名（如「播放周杰伦的歌」→ artist=周杰伦）；不带参数则播放当前音乐。",
        parameterSchema = objectSchema(
            properties = buildJsonObject {
                put("song", param("string", "要播放的歌曲名，可选"))
                put("artist", param("string", "要播放的歌手名，可选"))
            },
        ),
        executor = { args ->
            val song = args["song"]?.jsonPrimitive?.contentOrNull
            val artist = args["artist"]?.jsonPrimitive?.contentOrNull
            CarSimulation.update {
                it.copy(
                    mediaPlaying = true,
                    currentSong = song ?: it.currentSong ?: "默认歌单",
                    currentArtist = artist ?: it.currentArtist,
                )
            }
            ok(
                "media" to "playing",
                "song" to (song ?: CarSimulation.state.value.currentSong),
                "artist" to (artist ?: CarSimulation.state.value.currentArtist),
            )
        },
    )

    private fun mediaPause() = AppFunction(
        id = "media_pause",
        name = "暂停播放",
        description = "暂停当前音乐播放。用户说暂停音乐、停止播放时调用。",
        parameterSchema = AppFunction.DEFAULT_PARAMETER_SCHEMA,
        executor = { _ ->
            CarSimulation.update { it.copy(mediaPlaying = false) }
            ok("media" to "paused")
        },
    )

    private fun mediaSkip() = AppFunction(
        id = "media_skip",
        name = "切换歌曲",
        description = "切换歌曲。参数 direction 为 next（下一首）或 previous（上一首）。下一首、上一首时调用。",
        parameterSchema = objectSchema(
            properties = buildJsonObject {
                put("direction", param("string", "next=下一首，previous=上一首"))
            },
            required = listOf("direction"),
        ),
        executor = { args ->
            val direction = args["direction"]?.jsonPrimitive?.contentOrNull
                ?: return@AppFunction reject("参数 direction 必须为字符串", "direction")
            if (direction != "next" && direction != "previous") {
                return@AppFunction reject("参数 direction 只能为 next 或 previous", "direction")
            }
            CarSimulation.update { it.copy(mediaPlaying = true) }
            ok("media" to "playing", "track" to direction)
        },
    )

    private fun mediaSetVolume() = AppFunction(
        id = "media_set_volume",
        name = "设置媒体音量",
        description = "设置多媒体音量，整数 0-100（0 静音，100 最大）。调大/调小音量时先换算成 0-100 数值再调用。",
        parameterSchema = objectSchema(
            properties = buildJsonObject {
                put("volume", param("integer", "音量 0-100"))
            },
            required = listOf("volume"),
        ),
        executor = { args ->
            val volume = args["volume"]?.jsonPrimitive?.intOrNull
                ?: return@AppFunction reject("参数 volume 必须为整数", "volume")
            if (volume !in 0..100) {
                return@AppFunction reject("媒体音量需在 0-100 之间，收到 $volume", "volume")
            }
            CarSimulation.update { it.copy(mediaVolume = volume) }
            ok("media_volume" to volume)
        },
    )

    // ------------------------------------------------------------------
    // 导航
    // ------------------------------------------------------------------

    private fun navSetDestination() = AppFunction(
        id = "nav_set_destination",
        name = "设置导航目的地",
        description = "开始导航到指定目的地（如「导航去公司」→ destination=公司）。设置目的地、开始导航时调用。",
        parameterSchema = objectSchema(
            properties = buildJsonObject {
                put("destination", param("string", "目的地名称或地址"))
            },
            required = listOf("destination"),
        ),
        executor = { args ->
            val destination = args["destination"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: return@AppFunction reject("参数 destination 必须为字符串", "destination")
            if (destination.isEmpty()) {
                return@AppFunction reject("目的地不能为空", "destination")
            }
            CarSimulation.update { it.copy(navigationDestination = destination) }
            ok("navigation" to "navigating", "destination" to destination)
        },
    )

    private fun navCancel() = AppFunction(
        id = "nav_cancel",
        name = "取消导航",
        description = "取消当前导航，结束路线引导。用户说取消导航、结束导航时调用。",
        parameterSchema = AppFunction.DEFAULT_PARAMETER_SCHEMA,
        executor = { _ ->
            CarSimulation.update { it.copy(navigationDestination = null) }
            ok("navigation" to "idle")
        },
    )

    // ------------------------------------------------------------------
    // 车窗与天窗
    // ------------------------------------------------------------------

    private fun windowSet() = AppFunction(
        id = "window_set",
        name = "设置车窗开度",
        description = "设置指定车窗的开度百分比。position 取 front_left/front_right/rear_left/rear_right/all（all=全部车窗），percent 为 0-100（0 全关，100 全开）。",
        parameterSchema = objectSchema(
            properties = buildJsonObject {
                put("position", param("string", "front_left/front_right/rear_left/rear_right/all"))
                put("percent", param("integer", "开度百分比 0-100"))
            },
            required = listOf("position", "percent"),
        ),
        executor = { args ->
            val position = args["position"]?.jsonPrimitive?.contentOrNull
                ?: return@AppFunction reject("参数 position 必须为字符串", "position")
            val percent = args["percent"]?.jsonPrimitive?.intOrNull
                ?: return@AppFunction reject("参数 percent 必须为整数", "percent")
            if (percent !in 0..100) {
                return@AppFunction reject("车窗开度需在 0-100 之间，收到 $percent", "percent")
            }
            val valid = listOf("front_left", "front_right", "rear_left", "rear_right", "all")
            if (position !in valid) {
                return@AppFunction reject("position 只能为 $valid 之一", "position")
            }
            val targets = if (position == "all") {
                listOf("front_left", "front_right", "rear_left", "rear_right")
            } else {
                listOf(position)
            }
            CarSimulation.update {
                it.copy(windows = it.windows + targets.associateWith { _ -> percent })
            }
            ok("window" to position, "percent" to percent)
        },
    )

    private fun sunroofSet() = AppFunction(
        id = "sunroof_set",
        name = "设置天窗开度",
        description = "设置天窗开度百分比，整数 0-100（0 全关，100 全开）。打开/关闭天窗、调节天窗时调用。",
        parameterSchema = objectSchema(
            properties = buildJsonObject {
                put("percent", param("integer", "开度百分比 0-100"))
            },
            required = listOf("percent"),
        ),
        executor = { args ->
            val percent = args["percent"]?.jsonPrimitive?.intOrNull
                ?: return@AppFunction reject("参数 percent 必须为整数", "percent")
            if (percent !in 0..100) {
                return@AppFunction reject("天窗开度需在 0-100 之间，收到 $percent", "percent")
            }
            CarSimulation.update { it.copy(sunroofPercent = percent) }
            ok("sunroof_percent" to percent)
        },
    )

    // ------------------------------------------------------------------
    // 座椅加热
    // ------------------------------------------------------------------

    private fun seatHeating() = AppFunction(
        id = "seat_heating",
        name = "设置座椅加热",
        description = "设置座椅加热档位。seat 取 driver（主驾）/passenger（副驾）/all，level 为 0-3（0 关闭，3 最高档）。",
        parameterSchema = objectSchema(
            properties = buildJsonObject {
                put("seat", param("string", "driver/passenger/all"))
                put("level", param("integer", "加热档位 0-3，0 为关闭"))
            },
            required = listOf("seat", "level"),
        ),
        executor = { args ->
            val seat = args["seat"]?.jsonPrimitive?.contentOrNull
                ?: return@AppFunction reject("参数 seat 必须为字符串", "seat")
            val level = args["level"]?.jsonPrimitive?.intOrNull
                ?: return@AppFunction reject("参数 level 必须为整数", "level")
            if (level !in 0..3) {
                return@AppFunction reject("座椅加热档位需在 0-3 之间，收到 $level", "level")
            }
            val valid = listOf("driver", "passenger", "all")
            if (seat !in valid) {
                return@AppFunction reject("seat 只能为 $valid 之一", "seat")
            }
            val targets = if (seat == "all") listOf("driver", "passenger") else listOf(seat)
            CarSimulation.update {
                it.copy(seatHeating = it.seatHeating + targets.associateWith { _ -> level })
            }
            ok("seat" to seat, "level" to level)
        },
    )

    // ------------------------------------------------------------------
    // 状态查询
    // ------------------------------------------------------------------

    private fun carStatus() = AppFunction(
        id = "car_status",
        name = "查询车控状态",
        description = "查询当前整车模拟状态：空调（开关/温度/风量）、媒体（播放状态/歌曲/音量）、导航目的地、车窗与天窗开度、座椅加热档位。用户询问车况时调用。",
        parameterSchema = AppFunction.DEFAULT_PARAMETER_SCHEMA,
        executor = { _ ->
            val s = CarSimulation.state.value
            AppFunctionResults.success(
                buildJsonObject {
                    put("status", "ok")
                    putJsonObject("ac") {
                        put("on", s.acOn)
                        put("temp", s.acTemp)
                        put("fan_speed", s.acFanSpeed)
                    }
                    putJsonObject("media") {
                        put("playing", s.mediaPlaying)
                        put("song", s.currentSong)
                        put("artist", s.currentArtist)
                        put("volume", s.mediaVolume)
                    }
                    put("navigation", s.navigationDestination)
                    put("windows", JsonObject(s.windows.mapValues { (_, v) -> JsonPrimitive(v) }))
                    put("sunroof_percent", s.sunroofPercent)
                    put("seat_heating", JsonObject(s.seatHeating.mapValues { (_, v) -> JsonPrimitive(v) }))
                },
            )
        },
    )
}
