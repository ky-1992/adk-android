package com.google.adk.agent

import com.google.adk.model.ModelBackend
import com.google.adk.tool.Tool

data class LlmAgent(
    override val name: String,
    override val description: String,
    val model: ModelBackend,
    val instruction: String,
    val tools: List<Tool> = emptyList(),
    val subAgents: List<Agent> = emptyList(),
    val outputKey: String? = null,
    val disallowTransferToParent: Boolean = false,
    val disallowTransferToPeers: Boolean = false,
) : Agent