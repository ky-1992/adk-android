package com.google.adk.demo

import android.app.Application
import com.google.adk.agent.LlmAgent
import com.google.adk.ark.ArkModelBackend
import com.google.adk.runner.AgentRunner
import com.google.adk.session.sqlite.SqliteSessionStore
import com.google.adk.tool.Tool
import com.google.adk.tools.CalculatorTool
import com.google.adk.tools.TimeTool
import com.google.adk.tools.WebSearchTool

object DemoAgentOrchestrator {
    private var _sessionStore: SqliteSessionStore? = null
    private var _runner: AgentRunner? = null

    fun sessionStore(app: Application): SqliteSessionStore {
        return _sessionStore ?: SqliteSessionStore(app).also { _sessionStore = it }
    }

    fun runner(app: Application): AgentRunner {
        return _runner ?: AgentRunner(sessionStore(app)).also { _runner = it }
    }

    fun availableTools(): List<Tool> = listOf(
        TimeTool(),
        CalculatorTool(),
        WebSearchTool(),
    )

    fun buildAgent(backend: ArkModelBackend): LlmAgent = LlmAgent(
        name = "demo",
        description = "ADK Demo Agent",
        model = backend,
        instruction = "You are a helpful AI assistant. Use tools when they help answer the user's question.",
        tools = availableTools(),
    )
}