package com.google.adk.agent

sealed interface Agent {
    val name: String
    val description: String
}