# Task 8 Report
**Status:** DONE
## Files created
- `adk-tools/src/main/java/com/google/adk/tools/TimeTool.kt` — returns current ISO 8601 datetime
- `adk-tools/src/test/java/com/google/adk/tools/TimeToolTest.kt` — verifies TimeTool returns non-null year-prefixed string
- `adk-tools/src/main/java/com/google/adk/tools/CalculatorTool.kt` — recursive-descent arithmetic evaluator (+, -, *, /, parens, decimals)
- `adk-tools/src/test/java/com/google/adk/tools/CalculatorToolTest.kt` — verifies expression evaluation and error on missing arg
- `adk-tools/src/main/java/com/google/adk/tools/WebSearchTool.kt` — DuckDuckGo HTML search with OkHttp
## Test summary
All tests pass:
- **TimeToolTest:** 1/1 PASS — `TimeTool returns current datetime string`
- **CalculatorToolTest:** 2/2 PASS — `CalculatorTool evaluates simple expression` (2+3*4=14.0), `CalculatorTool returns error for missing expression`
- **WebSearchTool:** No unit test (network dependency, per brief instructions)

Build: `.\gradlew.bat :adk-tools:test --no-daemon` → BUILD SUCCESSFUL (50 actionable tasks)
## Concerns (if any)
- WebSearchTool has no unit test as specified; relies on DuckDuckGo's HTML endpoint which may change parsing.
- CalculatorTool uses a pure recursive-descent parser (no javax.script), so it works identically on Android and JVM with no Rhino dependency.
- `adk-tools/build.gradle.kts` already includes OkHttp (`libs.okhttp`) and serialization dependencies correctly.
## Commit