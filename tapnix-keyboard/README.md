# TapNix Keyboard

A production-grade Android keyboard application built with Kotlin, Jetpack Compose, and the Android IME framework.

## Features

- Full QWERTY keyboard with long-press alternate characters
- Emoji keyboard with 3,600+ emojis across 8 categories
- **Emoji Spam** — hold any emoji for continuous rapid-fire repeat (adaptive acceleration from 110ms → 28ms)
- Smart clipboard history — 50 entries, 500K char support, chunked paste, pin/search
- 6 built-in themes: Default, AMOLED Black, Ocean Blue, Forest Green, Candy, Dusk
- Word suggestions with personal frequency learning
- Incognito mode + password field detection
- Haptic feedback, auto-capitalize, double-space period
- Full settings panel — inline and in dedicated Settings Activity

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 |
| IME | InputMethodService |
| Async | Coroutines + Flow |
| Database | Room (WAL mode) |
| Settings | DataStore Preferences |
| DI | Hilt |
| Architecture | MVVM / Clean Architecture |
| Build | Gradle 8.11 + AGP 8.7 |

## Project Structure

```
app/src/main/java/com/tapnix/keyboard/
├── TapNixApplication.kt          — Hilt entry point
├── MainActivity.kt               — Setup/onboarding screen
├── ime/
│   └── TapNixIMEService.kt       — Core IME service (lifecycle + Compose wiring)
├── viewmodel/
│   └── KeyboardViewModel.kt      — Single source of truth for all keyboard state
├── engine/
│   ├── LongPressEngine.kt        — Adaptive emoji spam engine (zero leaks/ANR)
│   ├── ClipboardEngine.kt        — Chunk-safe clipboard handling (500K chars)
│   ├── EmojiEngine.kt            — Emoji search + recent tracking
│   └── SuggestionEngine.kt       — Word frequency suggestion engine
├── database/
│   ├── TapNixDatabase.kt         — Room database (WAL mode)
│   ├── entities/                 — ClipboardEntity, EmojiEntity, WordFrequencyEntity
│   └── daos/                     — ClipboardDao, EmojiDao, WordDao
├── data/
│   ├── Models.kt                 — KeyboardTheme, KeyboardSettings, Emoji, etc.
│   ├── EmojiData.kt              — 3,600+ emoji in 8 categories with keywords
│   └── KeyboardLayout.kt         — QWERTY and numeric key definitions
├── settings/
│   └── SettingsRepository.kt     — DataStore-backed preferences
├── di/
│   ├── DatabaseModule.kt
│   ├── RepositoryModule.kt
│   └── EngineModule.kt
└── ui/
    ├── theme/TapNixTheme.kt      — Dynamic Compose theming
    ├── keyboard/
    │   ├── KeyboardRoot.kt       — Root panel switcher with Crossfade animation
    │   ├── QwertyPanel.kt        — Full QWERTY layout
    │   ├── NumericPanel.kt       — Symbols/numbers panel
    │   ├── KeyButton.kt          — Individual key with press animation + long press
    │   └── SuggestionsBar.kt     — Scrollable suggestions bar
    ├── emoji/
    │   └── EmojiPanel.kt         — Full emoji panel with search + category tabs
    ├── clipboard/
    │   └── ClipboardPanel.kt     — Clipboard history with pin/search/paste
    └── settings/
        ├── SettingsPanel.kt      — Inline keyboard settings panel
        └── SettingsActivity.kt   — Full settings screen
```

## Building the APK

### Prerequisites

- Android Studio Meerkat (2024.3) or later
- JDK 17+
- Android SDK with API 35

### Steps

```bash
# 1. Clone the repo
git clone https://github.com/buildwith-manish/tapnix-android-keyboard.git
cd tapnix-android-keyboard

# 2. Open in Android Studio
# File → Open → select the tapnix-keyboard/ folder

# 3. Sync Gradle
# Android Studio will prompt you to sync — click "Sync Now"

# 4. Build debug APK
./gradlew assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk

# 5. Build release APK (requires signing config)
./gradlew assembleRelease
```

### Install on device

```bash
# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Or: Android Studio → Run button (Shift+F10)
```

### Enable the keyboard on device

1. Settings → General Management → Language and Input → On-screen keyboard → Manage keyboards
2. Enable **TapNix**
3. In any text field, tap the keyboard icon in the navigation bar
4. Select **TapNix**

## Architecture

### IME Service + Compose

The `TapNixIMEService` implements `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner` to give the Compose tree full lifecycle awareness inside the IME — which is a Service, not an Activity:

```
TapNixIMEService
  ├── implements LifecycleOwner       → LifecycleRegistry
  ├── implements ViewModelStoreOwner  → ViewModelStore (cleared on onDestroy)
  └── implements SavedStateRegistryOwner → SavedStateRegistryController

onCreateInputView():
  → ComposeView created
  → ViewTree owners set on decor + input view
  → KeyboardRoot { viewModel, imeService } inflated
```

### Emoji Spam Engine

```
User holds emoji (finger down)
  → LongPressEngine.start(pointerId, "😂")
  → delay(500ms)                      ← initial threshold
  → loop:
      withContext(Main.immediate) { commitText("😂") }
      delay(intervalMs)               ← starts at 110ms
      intervalMs *= 0.90              ← adaptive acceleration
      coerceAtLeast(28ms)             ← floor (~35 emojis/sec at peak)
User releases (finger up)
  → LongPressEngine.cancel(pointerId) ← immediate Job.cancel()
  → stops instantly, no stuck state
```

### Clipboard Large Text

```
Text copied (e.g. 50,000 chars)
  → ClipboardEngine.captureFromSystem()
  → Truncate to 500,000 chars max
  → Store full_text in Room TEXT column
  → Store preview (first 200 chars) separately
  → Prune oldest unpinned if > 47 entries

User taps Paste:
  → ClipboardEngine.commitToInput(id)
  → Load entity.fullText from Room (IO dispatcher)
  → If text ≤ 4096 chars: single commitText() call
  → If text > 4096 chars: chunked loop
      → commitText(chunk) on Main.immediate
      → delay(2ms) between chunks        ← prevents ANR
      → repeat until complete
```

## Performance

| Metric | Target | Architecture |
|--------|--------|-------------|
| Key tap → char | <16ms | Main thread only, no IO |
| Emoji panel open | <100ms | Pre-loaded static data |
| Suggestion update | <50ms | Coroutine on IO dispatcher |
| Clipboard open | <150ms | Room Flow → StateFlow |
| Theme switch | <200ms | DataStore → StateFlow |
| Emoji spam peak | ~35/sec | 28ms floor interval |

## Pushing to GitHub

```bash
cd tapnix-keyboard

# Initialize git
git init
git add .
git commit -m "Initial commit: TapNix production keyboard"

# Add your remote
git remote add origin https://github.com/buildwith-manish/tapnix-android-keyboard.git

# Push
git push -u origin main
```

## Supported Android Versions

- Minimum: Android 12 (API 31) — required for `WindowInsetsCompat` improvements
- Target: Android 15 (API 35)

## License

MIT License — see LICENSE file for details.
