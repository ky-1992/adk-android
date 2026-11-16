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
import com.google.adk.tools.WebFetchTool
import com.google.adk.tools.FileReadTool
import com.google.adk.tools.FileWriteTool

object DemoAgentOrchestrator {
    private var _sessionStore: SqliteSessionStore? = null
    private var _runner: AgentRunner? = null

    /**
     * Shared agent instruction for the demo app. Requires replies as HTML
     * fragments (headings / lists / tables / code blocks) so the demo can
     * render them in a styled WebView; scripts and full-document structure
     * are explicitly forbidden and additionally stripped client-side by
     * ui.chat.HtmlSanitizer before rendering.
     */
    const val INSTRUCTION = """
        You are a helpful AI assistant. Use tools when they help answer the user's question.

        Reply formatting (HTML):
        - Reply with an HTML FRAGMENT, never a full HTML document: no <!DOCTYPE>, <html>, <head>
          or <body> wrappers, and no markdown code fences around the reply.
        - Use these tags for structure: <p>, <h1>-<h6>, <ul>/<ol>/<li>,
          <table>/<thead>/<tbody>/<tr>/<th>/<td>, <pre>/<code>, <blockquote>, <strong>, <em>,
          <a>, <br>, <hr>.
        - A short simple answer may be plain text without any HTML tag.
        - NEVER output <script> tags, event handler attributes (onclick, onerror, ...),
          javascript: URLs, or <style>/<iframe>/<form> tags.
    """

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
        WebFetchTool(),
        FileReadTool(),
        FileWriteTool(),
        // 模拟车控能力（空调/多媒体/导航/车窗/天窗/座椅加热/状态查询），
        // 经 AppFunctionTool 桥接为 app_<id> 工具，LLM 自动 functionCall。
    ) + CarAppFunctions.tools()

    fun buildAgent(backend: ArkModelBackend): LlmAgent = LlmAgent(
        name = "demo",
        description = "ADK Demo Agent",
        model = backend,
        instruction = INSTRUCTION,
        tools = availableTools(),
    )
}