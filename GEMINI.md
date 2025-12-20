# PinMe - Intelligent Screen Capture & Extraction

## Project Overview

**PinMe** (`com.brycewg.pinme`) is an Android application designed to intelligently capture screen content and extract key information (e.g., pickup codes, train tickets, verification codes) using Vision Large Language Models (LLMs). The extracted information is then presented via Meizu Flyme's Live Notifications (Capsule) or a standard Android AppWidget for quick access.

## Technology Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material3)
- **Architecture:** Modern Android Architecture (Compose + Room + Coroutines)
- **Database:** Room (SQLite abstraction)
- **Networking:** OkHttp (for LLM API communication)
- **AI/LLM Integration:** OpenAI-compatible API client (supports Zhipu AI, SiliconFlow, Custom)
- **Key Android APIs:**
  - `MediaProjection`: For screen capture.
  - `AccessibilityService`: For silent/background capture.
  - `Glance`: For building AppWidgets with Compose-like syntax.
  - `NotificationManager`: For system and Flyme-specific notifications.

## Project Structure

```text
app/src/main/java/com/brycewg/pinme/
├── Constants.kt                # App-wide constants (Prefs keys, LLM defaults)
├── MainActivity.kt             # Main entry point / Configuration UI
├── capture/                    # Screen capture logic
│   ├── CaptureActivity.kt      # Foreground capture (MediaProjection)
│   ├── Accessibility...        # Background/Silent capture service
│   └── QuickCaptureTile...     # Quick Settings Tile implementation
├── db/                         # Room Database definitions
│   ├── AppDatabase.kt          # Database holder
│   ├── Entity.kt               # Data models (ExtractEntity, PreferenceEntity)
│   └── PinMeDao.kt             # Data Access Objects
├── extract/                    # Core Business Logic
│   ├── ExtractWorkflow.kt      # Orchestrates Capture -> LLM -> DB flow
│   └── ExtractParsing.kt       # Parses LLM JSON response
├── notification/               # Notification handling
│   └── UnifiedNotification...  # Handles Flyme Live Capsules & Standard Notifs
├── ui/                         # Jetpack Compose UI
│   ├── components/             # Reusable UI elements
│   ├── layouts/                # Screen layouts (Settings, Home, etc.)
│   └── theme/                  # Theme definitions
├── vllm/                       # LLM Client Network Layer
│   └── VllmClient.kt           # OkHttp client for OpenAI-compatible APIs
└── widget/                     # Home Screen Widget
    └── PinMeWidget.kt          # Glance AppWidget implementation
```

## Core Workflows

### 1. Screen Capture

Triggered via Quick Settings Tile, Widget, or Shortcut.

- **Foreground:** `CaptureActivity` requests `MediaProjection` permission.
- **Background:** `AccessibilityCaptureService` or Root (if enabled) captures silently.
- **Output:** Generates a `Bitmap` of the current screen.

### 2. Information Extraction (`ExtractWorkflow.kt`)

1.  **Preprocessing:** Bitmap is compressed to JPEG and encoded to Base64 (max width 1080px).
2.  **Prompting:** Constructs a system prompt based on user-configured "Market Items" (types of info to extract) and a user prompt.
3.  **Inference:** Sends request to the configured LLM provider (Zhipu, SiliconFlow, or Custom).
4.  **Parsing:** `ExtractParsing` validates and parses the JSON response from the LLM.
5.  **Storage:** Saves the result (`ExtractEntity`) to the Room database.

### 3. Display & Notification

- **Flyme Live Notification:** If supported (Meizu devices), displays a dynamic "capsule" in the status bar.
- **Standard Notification:** Fallback for other devices.
- **Widget:** `PinMeWidget` updates to show the most recent extraction.

## Build & Run

### Prerequisites

- JDK 11+
- Android SDK (Min SDK 33, Target SDK 36)

### Common Commands

Never use `./gradlew build` in this project.

## Configuration

### Constants & Preferences

Defined in `Constants.kt`. Key preferences include:

- `PREF_LLM_PROVIDER`: Selected LLM provider (`zhipu`, `siliconflow`, `custom`).
- `PREF_LLM_API_KEY`: API Key for the provider.
- `PREF_LLM_MODEL`: Model ID (e.g., `glm-4v-flash`, `Qwen/Qwen2.5-VL-72B-Instruct`).

### Database Models (`db/Entity.kt`)

- `PreferenceEntity`: Key-value storage for app settings.
- `ExtractEntity`: Stores extraction history (Title, Content, Emoji, Raw Output).
- `MarketItemEntity`: Configurable extraction types (e.g., "Pickup Code", "Train Ticket") that guide the LLM's focus.

## Development Guidelines

- **Communication:** In Chinese.
- **Style:** Follow Official Kotlin Coding Conventions.
- **UI:** Use Jetpack Compose for all new UI.
- **Privacy:** Never log sensitive user screen content.
- **Threading:** Use Coroutines for all I/O and heavy computation (LLM calls, DB access).
- **Flyme Integration:** Check for feature availability before calling Flyme-specific APIs (`UnifiedNotificationManager.isLiveCapsuleCustomizationAvailable()`).
