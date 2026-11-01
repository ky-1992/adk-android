package com.google.adk.demo.ui.tools

import androidx.lifecycle.ViewModel
import com.google.adk.demo.DemoAgentOrchestrator
import com.google.adk.tool.Tool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ToolsState(
    val tools: List<ToolItem> = emptyList(),
)

data class ToolItem(
    val tool: Tool,
    val enabled: Boolean,
)

class ToolsViewModel : ViewModel() {

    private val _state = MutableStateFlow(ToolsState())
    val state: StateFlow<ToolsState> = _state.asStateFlow()

    init {
        _state.value = ToolsState(
            tools = DemoAgentOrchestrator.availableTools().map { ToolItem(it, true) },
        )
    }

    fun toggleTool(name: String) {
        _state.update { s ->
            s.copy(tools = s.tools.map { if (it.tool.name == name) it.copy(enabled = !it.enabled) else it })
        }
    }
}