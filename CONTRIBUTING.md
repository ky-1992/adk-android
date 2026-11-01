# Contributing to ADK Android

## Development Setup

1. Clone the repository
2. Open `adk-android/` in Android Studio
3. Sync Gradle and build

## Project Structure

```
adk-android/
├── adk-core/           # Core agent infrastructure
├── adk-ark/            # ARK model backend
├── adk-tools/          # Built-in tools
├── adk-session-sqlite/ # Room-based session persistence
├── demo/               # Demo application
├── gradle/             # Gradle wrapper and version catalog
├── build.gradle.kts    # Root build file
└── settings.gradle.kts # Module registration
```

## Building & Testing

```bash
# Full build
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run specific module tests
./gradlew :adk-core:test
./gradlew :adk-ark:test
./gradlew :adk-tools:test

# Build demo APK
./gradlew :demo:assembleDebug
```

## Code Style

- Kotlin with 4-space indentation
- PascalCase for types, camelCase for functions/properties
- Public API in `com.google.adk.*` package
- Internal implementation in `.internal` subpackages
- All public types must have KDoc comments

## Pull Request Checklist

- [ ] Code compiles: `./gradlew assembleDebug`
- [ ] Tests pass: `./gradlew test`
- [ ] New public API has KDoc
- [ ] No breaking changes to sealed types or data classes
- [ ] Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/)

## License
Apache 2.0 — contributions are welcome under the same license.