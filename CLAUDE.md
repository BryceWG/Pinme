# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PinMe is an Android app (Kotlin, Jetpack Compose) that captures screenshots via a Quick Settings tile, uses vision LLMs to extract key information (pickup codes, train tickets, verification codes, etc.), and displays results through Flyme Live Notifications (Meizu-specific) or standard notifications plus a home screen widget.

**Package:** `com.brycewg.pinme`
**Min SDK:** 33 | **Target SDK:** 36

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

### Core Flow
1. **QuickCaptureTileService** - Quick Settings tile triggers `CaptureActivity`
2. **CaptureActivity** - Requests MediaProjection permission, captures screen as Bitmap
3. **ExtractWorkflow** - Sends screenshot to vision LLM, parses JSON response
4. **UnifiedNotificationManager** - Shows result via Flyme Live Notification (if available) or standard notification
5. **PinMeWidget** - Glance AppWidget displays recent extractions

### Key Components

| Package | Purpose |
|---------|---------|
| `capture/` | Screen capture via MediaProjection API |
| `extract/` | LLM workflow and JSON parsing |
| `vllm/` | OpenAI-compatible API client (OkHttp) |
| `notification/` | Notification handling with Flyme Live Notification support |
| `widget/` | Glance AppWidget implementation |
| `db/` | Room database for preferences and extraction history |
| `service/` | LiveNotification service for Flyme integration |

### LLM Configuration
Supports multiple providers configured in `Constants.kt`:
- **智谱 AI (ZHIPU)** - Default, uses `glm-4v-flash`
- **硅基流动 (SiliconFlow)** - Uses `Qwen/Qwen2.5-VL-72B-Instruct`
- **Custom** - User-specified OpenAI-compatible endpoint

Settings stored in Room database via `PreferenceEntity`.

### Database Schema
- `PreferenceEntity` - Key-value settings storage
- `ExtractEntity` - Extraction history (title, content, raw output, timestamp)

### Flyme-Specific Features
`UnifiedNotificationManager.isLiveCapsuleCustomizationAvailable()` checks:
- Meizu device manufacturer
- Flyme version >= 11
- Live notification permission granted

Uses custom notification extras for capsule styling (background color, icon, content color).
