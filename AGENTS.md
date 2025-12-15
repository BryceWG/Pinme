# Repository Guidelines

## Project Structure & Module Organization

- `app/` is the Android application module (Kotlin + Jetpack Compose).
- Kotlin sources live in `app/src/main/java/com/brycewg/pinme/` with feature packages like `capture/`, `extract/`, `vllm/`, `notification/`, `widget/`, `db/`, `service/`, and `ui/`.
- Android resources live in `app/src/main/res/` (e.g., `drawable/`, `values/`, `xml/`, `raw/`).
- Repo-level docs/artifacts: `README.md` and `LICENSE`.

## Build, Test, and Development Commands

Prereqs: Android SDK (set by `local.properties`) and JDK 11+.

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Install to device/emulator
./gradlew test                 # JVM unit tests
./gradlew connectedAndroidTest # Instrumented tests
./gradlew lint                 # Android Lint
./gradlew clean                # Clean build outputs
```

## Coding Style & Naming Conventions

- Kotlin follows the official style (`kotlin.code.style=official`): 4-space indentation, no tabs.
- Names: packages `lowercase`, classes/objects `PascalCase`, functions/variables `camelCase`.
- Compose: `@Composable` functions are typically `PascalCase` for screens/components (for example, `ExtractHome`).
- Prefer small, focused changes; avoid formatting-only diffs unless required.

## Testing Guidelines

- Unit tests: `app/src/test/...` (JUnit4).
- Instrumented/UI tests: `app/src/androidTest/...` (AndroidX JUnit, Espresso, Compose UI test).
- Name tests `*Test` and focus coverage on parsing/workflow boundaries first (for example, `extract/` and `vllm/`).

Note: `.gitignore` currently excludes `app/src/test` and `app/src/androidTest`; update it if you intend to add tests to the repo.

## Commit & Pull Request Guidelines

- Git history uses short, imperative, sentence-case summaries (for example, “Add initial project setup …”); keep commits scoped and readable.
- PRs should describe behavior changes, include screenshots for UI/widget/notification updates, and list what you tested (API level and Flyme/Meizu device if relevant).

## Security & Configuration Tips

- Never commit `local.properties`, API keys, or private endpoints. Configure Base URL/Model/API Key via in-app Settings; avoid hardcoding secrets in `Constants.kt`.
- Keep Flyme-specific behavior behind capability checks and maintain non-Flyme fallbacks (see `notification/UnifiedNotificationManager.kt`).
