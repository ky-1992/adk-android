package com.google.adk.agent

import com.google.adk.model.ModelBackend
import com.google.adk.tool.Tool

sealed interface Agent {
    val name: String
    val description: String
}