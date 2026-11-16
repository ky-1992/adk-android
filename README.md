# ADK Android

**ADK（Agent Development Kit）for Android** —— 一个用于在 Android 上构建 AI Agent 的 Kotlin SDK。提供协程驱动的模块化架构，把模型后端、工具调用、会话持久化与流式聊天 UI 组合到一起：你定义 Agent，ADK 负责驱动「模型 ↔ 工具」循环并把每一步以事件流的形式交给你。

```
用户消息 ──▶ AgentRunner ──▶ ModelBackend(云端 LLM)
                ▲                  │
                │   工具结果回喂    ▼
                └────────────── Tools（自动 function call）
                │
                └──▶ SessionStore（会话持久化）
                └──▶ Flow<AgentEvent>（流式输出到 UI）
```

## 特性总览

- **流式 Agent 循环**：`AgentRunner.run()` 返回 `Flow<AgentEvent>`，逐 token 收到文本增量、工具调用请求/结果、完成与错误事件；模型要求调用工具时自动执行并把结果回喂，直到模型给出最终回答（轮数上限可配）。
- **可插拔模型后端**：实现 `ModelBackend` 接口即可接入任意 LLM。内置 `ArkModelBackend`（OpenAI 兼容 wire 协议 + SSE 流式解析），同时服务火山方舟与智谱两套端点。
- **工具调用**：实现 `Tool` 接口（name + description + JSON Schema + suspend execute），或用 `FunctionTool` 一行 lambda 搞定。内置 6 个通用工具。
- **车机能力桥接（adk-appfunction）**：把 App 功能注册为 `AppFunction`，自动桥接为带参数校验、类型规范化与结构化错误返回的 Agent 工具，LLM 可自动 function call。
- **会话持久化**：`SessionStore` 抽象 + Room/SQLite 实现，每个事件自动落库，重启后恢复完整对话上下文。
- **完整 Demo App**：Chat（流式气泡 + 工具调用卡片 + HTML 富文本渲染）、Sessions、Tools、Settings 四个界面，密钥加密存储。
- **全模块单元测试**：JUnit 5 + MockK + Robolectric，`./gradlew build` 一键验证。

## 模块结构

| 模块 | 说明 |
|------|------|
| **adk-core** | 核心类型：`Agent` / `LlmAgent`、`AgentRunner`、`AgentEvent`、`Session` / `SessionStore`、`Tool` / `FunctionTool`、`ModelBackend` |
| **adk-ark** | OpenAI 兼容模型后端：`ArkModelBackend`（SSE 流式、工具调用、usage 统计） |
| **adk-tools** | 内置工具：`TimeTool`、`CalculatorTool`、`WebSearchTool`、`WebFetchTool`、`FileReadTool`、`FileWriteTool` |
| **adk-session-sqlite** | Room 实现的持久化 `SessionStore` |
| **adk-appfunction** | App 能力 → Agent 工具的桥接层：`AppFunction` / `AppFunctionRegistry` / `AppFunctionTool` |
| **demo** | 演示 App：Chat / Sessions / Tools / Settings，含模拟车控功能 |

```
┌─────────────────────────────────────────────┐
│                 Demo App                    │
│    ChatScreen / Sessions / Tools / Settings │
├──────────────────┬──────────────────────────┤
│     adk-core     │        adk-ark           │
│   AgentRunner    │   ArkModelBackend        │
│   LlmAgent       │   SseParser              │
│   Session(Store) ├──────────────────────────┤
│   Tool           │      adk-tools           │
│   ModelBackend   │  6 个内置工具             │
├──────────────────┼──────────────────────────┤
│ adk-session-     │     adk-appfunction      │
│ sqlite (Room)    │  AppFunction → Tool 桥接 │
└──────────────────┴──────────────────────────┘
```

## 环境要求

- Android Studio Hedgehog (2024.1+) 或更高
- JDK 17+
- Android SDK：minSdk **34**（Android 14），compileSdk 35

## 快速开始

```bash
git clone https://github.com/ky-1992/adk-android.git
cd adk-android
./gradlew assembleDebug
```

运行 Demo：

1. 用 Android Studio 打开工程，把 **demo** 模块部署到 Android 14+ 的设备/模拟器
2. 在工程根目录 `local.properties` 里配置密钥（该文件已被 gitignore，不会入库）：
   ```properties
   arkApiKey=<你的 API Key>
   ```
3. 打开 App → **Settings** 页可覆盖密钥、选择模型（或填推理接入点 Endpoint ID）
4. 进入 **Chat** 开始对话，模型会按需自动调用工具

## 集成到你的 App

本 SDK 暂未发布到 Maven 仓库，按源码模块接入：

```kotlin
// settings.gradle.kts
include(":adk-core")
include(":adk-ark")
include(":adk-session-sqlite")
include(":adk-tools")
include(":adk-appfunction")   // 可选：App 能力桥接
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":adk-core"))
    implementation(project(":adk-ark"))            // 云端 LLM 后端
    implementation(project(":adk-session-sqlite")) // 会话持久化
    implementation(project(":adk-tools"))          // 内置工具
    implementation(project(":adk-appfunction"))    // 可选
}
```

> 注意：模块脚本引用本仓库的 version catalog（`gradle/libs.versions.toml`），并入你的工程时需同步相应条目（okhttp、room、serialization、coroutines 等）。

别忘了网络权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 核心概念

### 1. 定义 Agent

```kotlin
val agent = LlmAgent(
    name = "my-agent",
    description = "My first agent",
    model = backend,                     // ModelBackend 实现
    instruction = "你是一个有帮助的助手。",  // 系统提示词
    tools = listOf(TimeTool(), CalculatorTool()),
)
```

`LlmAgent` 还预留了 `subAgents`、`outputKey`、`disallowTransferToParent/Peers` 等多 Agent 扩展字段。

### 2. 运行与事件流

```kotlin
val runner = AgentRunner(sessionStore)

runner.run(
    agent = agent,
    sessionId = UUID.randomUUID().toString(),
    userId = "user-1",
    newMessage = UserMessage("你好！"),
    runConfig = RunConfig(temperature = 0.7f, maxToolIterations = 8),
).collect { event ->
    when (event) {
        is AgentEvent.TextDelta -> print(event.text)     // 流式文本增量
        is AgentEvent.ToolCallRequest -> /* 工具被调用 */
        is AgentEvent.ToolCallResult -> /* 工具返回 */
        is AgentEvent.FinalResponse -> /* 最终回答 + Token 用量 */
        is AgentEvent.RunError -> /* 错误（带阶段信息） */
        AgentEvent.RunCompleted -> /* 本轮结束 */
        else -> {}
    }
}
```

`AgentEvent` 事件一览：

| 事件 | 含义 |
|------|------|
| `RunStarted` | 运行开始 |
| `TextDelta(text, isFinal)` | 流式文本增量，`isFinal` 标记该轮文本结束 |
| `ToolCallRequest(callId, toolName, arguments)` | 模型请求调用工具 |
| `ToolCallResult(callId, toolName, result, error?)` | 工具执行结果 |
| `FinalResponse(text, tokenCount)` | 最终完整回答与 Token 用量 |
| `RunError(error, phase)` | 错误，phase 区分 MODEL_CALL / TOOL_EXECUTION / SESSION_SAVE |
| `RunCompleted` | 整轮运行完成 |

### 3. 工具（Function Calling）

```kotlin
// 方式一：lambda 快捷定义
val weatherTool = FunctionTool(
    name = "get_weather",
    description = "查询城市天气",
    parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("city") { put("type", "string") }
        }
        put("required", "city")
    },
) { args, ctx ->
    "北京晴，25 度"
}

// 方式二：实现 Tool 接口（内置工具均为此方式）
class MyTool : Tool {
    override val name = "my_tool"
    override val description = "..."
    override val parameterSchema: JsonSchema = ...
    override suspend fun execute(args: Map<String, Any?>, context: ToolContext): Any? = ...
}
```

内置工具（adk-tools）：

| 工具 | 功能 |
|------|------|
| `TimeTool` | 当前时间查询 |
| `CalculatorTool` | 数学表达式计算 |
| `WebSearchTool` | 联网搜索 |
| `WebFetchTool` | 抓取网页内容 |
| `FileReadTool` / `FileWriteTool` | 文件读写 |

### 4. 会话持久化

```kotlin
val store = SqliteSessionStore(context)   // Room 实现
val session = store.getSession(sessionId, userId)  // 恢复历史事件
```

`AgentRunner` 在运行中自动：加载或创建 `Session` → 把历史事件转为消息上下文 → 每个新事件 `appendEvent` 落库。历史长度由 `RunConfig.maxEvents` 截断控制。

## 模型接入

### 火山方舟（Ark / Doubao·DeepSeek·Kimi·GLM 目录模型）

```kotlin
val backend = ArkModelBackend(
    modelId = "glm-5-3-flash-260828",   // 方舟公共目录完整模型标识
    apiKey = apiKey,                     // Authorization: Bearer 自动添加
    // endpointId = "ep-xxxx",           // 可选：推理接入点
    // baseUrl = "https://ark.cn-beijing.volces.com/api/v3",  // 默认值
)
```

`ArkModelBackend` 构造参数：

| 参数 | 必填 | 说明 |
|------|------|------|
| `modelId` | ✓ | 模型标识 |
| `apiKey` | ✓ | API Key，自动以 `Bearer` 头发送 |
| `endpointId` | | 推理接入点 ID，设置后走 `/endpoints/{id}/chat/completions` |
| `baseUrl` | | 默认 `https://ark.cn-beijing.volces.com/api/v3` |
| `httpClient` | | 默认连接 30s / 读 60s，可注入自定义 OkHttp（调超时、加日志拦截器、共享连接池） |

### 智谱 GLM 直连

`ArkModelBackend` 的 wire 协议与智谱 v4 的 OpenAI 兼容端点完全一致（含 SSE 与工具调用），改 `baseUrl` 即可：

```kotlin
val backend = ArkModelBackend(
    modelId = "glm-5.3",
    apiKey = zhipuApiKey,
    baseUrl = "https://open.bigmodel.cn/api/paas/v4",
)
```

### 密钥安全（重要）

| 形态 | 适用 |
|------|------|
| `local.properties` → `BuildConfig` 注入 | 开发调试（Demo 采用，见 `demo/build.gradle.kts`） |
| `EncryptedSharedPreferences` 运行时录入 | Demo Settings 页做法，AES 加密落盘 |
| **后端代理持有密钥，App 只调自家后端** | **生产发布强烈建议**：发布版 App 内不应嵌入任何 API 密钥 |

## adk-appfunction：App 能力桥接

把应用/车机功能声明为 `AppFunction` 并注册，即可让 LLM 自动调用：

```kotlin
val registry = AppFunctionRegistry()

registry.register(
    AppFunction(
        id = "set_ac_temperature",
        name = "设置空调温度",
        description = "把空调温度设为指定摄氏度（16-30）",
        parameterSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("temp") {
                    put("type", "integer")
                    put("description", "目标温度（℃）")
                }
            }
            put("required", "temp")
        },
    ) { args ->
        val temp = args["temp"]!!.jsonPrimitive.int
        AppFunctionResults.success("temperature" to temp)
    }
)

// 注册表整体桥接为工具列表，交给 Agent
val agent = LlmAgent(
    /* ... */
    tools = registry.toTools(),   // 工具名自动带 app_ 前缀
)
```

桥接层（`AppFunctionTool`）保证：

- **参数校验**：required 缺失、类型不符都会返回结构化错误，且不会执行 executor；
- **类型规范化**：上游模型常见的"字符串化"参数（`"25"`、`"true"`、嵌套 JSON 字符串）自动转回 schema 声明的类型；
- **结构化错误**：`UNKNOWN_FUNCTION` / `MISSING_REQUIRED_PARAMETER` / `INVALID_PARAMETER_TYPE` / `EXECUTION_ERROR` 统一 JSON 结构，executor 异常不会击穿 Runner；
- **动态注册表**：执行时按 id 重新解析，函数注销后调用会得到明确错误；注册表本身线程安全（`ConcurrentHashMap`）。

Demo 内置一套模拟车控（`CarAppFunctions`）：空调（开关/温度/风量）、多媒体（播放/暂停/切歌/音量）、导航（设目的地/取消）、车窗与天窗开度、座椅加热档位、整车状态查询。

## Demo App

| 界面 | 功能 |
|------|------|
| **Chat** | 流式对话、Markdown/HTML 富文本消息（经 `HtmlSanitizer` 消毒）、工具调用过程卡片、会话内错误提示 |
| **Sessions** | 历史会话列表，恢复上下文继续聊 |
| **Tools** | 查看与开关各工具（含模拟车控功能） |
| **Settings** | API Key（加密存储）、模型选择（方舟公共目录）、推理接入点 |

## 测试

```bash
./gradlew test          # 全模块单元测试
./gradlew build         # 编译 + 测试 + lint
```

各模块自带单元测试（AgentRunner 事件循环、SSE 解析、FunctionTool、SessionStore、AppFunction 校验与桥接、HtmlSanitizer 等）。

## 目录结构

```
adk-android/
├── adk-core/            # 核心抽象（Agent/Runner/Event/Session/Tool/ModelBackend）
├── adk-ark/             # OpenAI 兼容模型后端（SSE 流式）
├── adk-tools/           # 6 个内置工具
├── adk-session-sqlite/  # Room 会话持久化
├── adk-appfunction/     # App 能力 → Agent 工具桥接
└── demo/                # 演示 App（含模拟车控）
```

## License

Apache 2.0