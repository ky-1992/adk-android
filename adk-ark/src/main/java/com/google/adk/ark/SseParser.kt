package com.google.adk.ark

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class SseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun parse(input: InputStream, onData: suspend (String) -> Unit) {
        BufferedReader(InputStreamReader(input)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.startsWith("data: ")) {
                    val data = l.removePrefix("data: ")
                    if (data == "[DONE]") {
                        onData(data)
                    } else {
                        try {
                            json.parseToJsonElement(data)
                            onData(data)
                        } catch (_: Exception) {
                            // Skip malformed JSON lines gracefully
                        }
                    }
                }
            }
        }
    }
}