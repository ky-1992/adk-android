package com.google.adk.tools

import com.google.adk.model.JsonSchema
import com.google.adk.tool.Tool
import com.google.adk.tool.ToolContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class WebFetchTool : Tool {
    override val name = "web_fetch"
    override val description = "Fetches the content of a web page given its URL."
    override val parameterSchema: JsonSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "The URL to fetch")
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("url"))))
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(args: Map<String, Any?>, context: ToolContext): Any? {
        val url = args["url"]?.toString() ?: return "Error: no URL provided"
        return try {
            val request = Request.Builder().url(url)
                .addHeader("User-Agent", "ADK-Android/1.0")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "Empty response"
            body.take(5000)
        } catch (e: Exception) {
            "Fetch error: ${e.message}"
        }
    }
}