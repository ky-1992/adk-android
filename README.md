# ADK Android

ADK (Agent Development Kit) for Android is a Kotlin SDK for building AI agents on Android. It provides a modular, coroutine-driven architecture that integrates model backends, tools, sessions, and streaming chat UI.

## Modules

| Module | Description |
|--------|-------------|
| **adk-core** | Core types: `Agent`, `LlmAgent`, `AgentRunner`, `Session`, `SessionStore`, `Tool`, `ModelBackend` |
| **adk-ark** | ARK (火山方舟) model backend: `ArkModelBackend` with SSE streaming |
| **adk-tools** | Built-in tools: `TimeTool`, `CalculatorTool`, `WebSearchTool`, `WebFetchTool`, `FileReadTool`, `FileWriteTool` |
| **adk-session-sqlite** | Room-based `SessionStore` implementation with persistent storage |
| **demo** | Demo app with Chat, Sessions, Tools, and Settings screens |

## Quick Start

### Prerequisites
- Android Studio Hedgehog (2024.1+) or later
- JDK 17+
- Android SDK 34+

### Build

```bash
cd adk-android
./gradlew assembleDebug
```

### Run the Demo

1. Open `adk-android/` in Android Studio
2. Deploy the **demo** module to a device/emulator running Android 14+
3. Go to **Settings** → enter your ARK API Key → choose a model
4. Go to **Chat** and start conversing with the agent

### Integrate the SDK

Add dependencies to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.google.adk:adk-core:1.0.0")
    implementation("com.google.adk:adk-ark:1.0.0")         // ARK backend
    implementation("com.google.adk:adk-tools:1.0.0")        // built-in tools
    implementation("com.google.adk:adk-session-sqlite:1.0.0") // persistent sessions
}
```

### Minimal Usage

```kotlin
val sessionStore = SqliteSessionStore(context)
val backend = ArkModelBackend(
    modelId = "doubao-pro-32k",
    apiKey = "your-api-key",
)
val agent = LlmAgent(
    name = "my-agent",
    description = "My first agent",
    model = backend,
    instruction = "You are a helpful assistant.",
    tools = listOf(TimeTool(), CalculatorTool()),
)
val runner = AgentRunner(sessionStore)

runner.run(
    agent = agent,
    sessionId = UUID.randomUUID().toString(),
    userId = "user-1",
    newMessage = UserMessage("Hello!"),
).collect { event ->
    when (event) {
        is AgentEvent.TextDelta -> print(event.text)
        is AgentEvent.RunError -> println("Error: ${event.error.message}")
        else -> {}
    }
}
```

## Architecture

```
┌─────────────────────────────────┐
│            Demo App             │
│  ChatScreen / SettingsScreen    │
├─────────────────────────────────┤
│ adk-core       │ adk-ark        │
│ AgentRunner    │ ArkModelBackend│
│ LlmAgent       │ SseParser       │
│ Session        ├────────────────┤
│ Tool           │ adk-tools      │
│ SessionStore   │ 6 built-in     │
├────────────────┤ tools          │
│ adk-session-   │                │
│ sqlite         │                │
│ Room DAO       │                │
└────────────────┴────────────────┘
```

## API Overview

### Core Types
- `Agent` / `LlmAgent` — agent definitions with instructions, tools, and model bindings
- `AgentRunner` — coroutine-based event loop with tool iteration
- `AgentEvent` — sealed event hierarchy (TextDelta, ToolCallRequest, ToolCallResult, etc.)
- `ModelBackend` — model abstraction; implement for any LLM provider
- `Tool` / `FunctionTool` — tool interface and lambda-based implementation
- `Session` / `SessionStore` — conversation persistence

### Streaming
`AgentRunner.run()` returns `Flow<AgentEvent>`. Collect it in a coroutine scope to receive streaming text deltas, tool calls, and completions.

### Tools
Tools implement the `Tool` interface with a `parameterSchema: JsonSchema` and a suspend `execute(args, context)` method. Use `FunctionTool("name", "desc", schema) { args, ctx -> ... }` for quick lambda-based tools.

## Minimum SDK
API 34 (Android 14.0)

## License
Apache 2.0