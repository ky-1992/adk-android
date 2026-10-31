package com.google.adk.tools

import com.google.adk.model.JsonSchema
import com.google.adk.tool.Tool
import com.google.adk.tool.ToolContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray

class WebSearchTool : Tool {
    override val name = "web_search"
    override val description = "Search the web for current information. Returns a list of relevant results."
    override val parameterSchema: JsonSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "The search query")
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("query"))))
    }

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun execute(args: Map<String, Any?>, context: ToolContext): Any? {
        val query = args["query"]?.toString() ?: return "Error: no query"
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val request = okhttp3.Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=$encodedQuery")
                .addHeader("User-Agent", "ADK-Android/1.0")
                .build()
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return "No results"
            extractResults(bodyString).take(5).joinToString("\n")
        } catch (e: Exception) {
            "Search error: ${e.message}"
        }
    }

    private fun extractResults(html: String): List<String> {
        val results = mutableListOf<String>()
        val snippetPattern = Regex("""class="result__snippet">(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        snippetPattern.findAll(html).forEach { match ->
            val text = match.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
            if (text.isNotBlank()) results.add(text)
        }
        return results.ifEmpty { listOf("No results found") }
    }
}