package com.google.adk.tool

data class ToolContext(
    val androidContext: android.content.Context? = null,
    val sessionState: Map<String, Any?> = emptyMap(),
    val agentName: String = "",
)