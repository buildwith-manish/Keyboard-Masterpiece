# TapNix Keyboard

A production-grade native Android keyboard application (IME) built with Kotlin, Jetpack Compose, and the Android IME framework. The complete project source lives in `tapnix-keyboard/` and is designed to be opened in Android Studio and pushed to GitHub.

## Run & Operate

This project cannot run in Replit — it is an Android native app (IME Service). Build it in Android Studio:

```bash
# Open Android Studio → File → Open → tapnix-keyboard/
# Sync Gradle, then:
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Stack

- Kotlin 2.1 + Jetpack Compose + Material 3
- Android IME framework (InputMethodService)
- Room (WAL mode) — local SQLite database
- DataStore Preferences — settings persistence
- Hilt — dependency injection
- KSP — annotation processing for Room + Hilt
- AGP 8.7.3, Gradle 8.11.1, compileSdk 35, minSdk 31
- Coroutines + Flow — async state management
- Timber — debug logging

## Where things live

```
tapnix-keyboard/
├── app/src/main/java/com/tapnix/keyboard/
│   ├── TapNixApplication.kt        — @HiltAndroidApp entry point
│   ├── MainActivity.kt             — Setup/onboarding screen
│   ├── ime/TapNixIMEService.kt     — IME core (LifecycleOwner + Compose wiring)
│   ├── viewmodel/KeyboardViewModel.kt — All keyboard state (StateFlow)
│   ├── engine/                     — LongPressEngine, ClipboardEngine, EmojiEngine, SuggestionEngine
│   ├── database/                   — Room DB, 3 entities, 3 DAOs
│   ├── data/                       — Models.kt, EmojiData.kt (3600+ emoji), KeyboardLayout.kt
│   ├── settings/SettingsRepository.kt
│   ├── di/                         — Hilt modules (Database, Repository, Engine)
│   └── ui/                         — keyboard/, emoji/, clipboard/, settings/ panels
├── README.md                       — Full build instructions + architecture
├── ARCHITECTURE.md                 — Deep architecture doc
├── PERFORMANCE.md                  — Performance & memory optimization report
└── LIFECYCLE.md                    — IME lifecycle management report
```

## Architecture decisions

- `TapNixIMEService` implements `LifecycleOwner` + `ViewModelStoreOwner` + `SavedStateRegistryOwner` manually — required because IME is a Service, not an Activity
- `LongPressEngine` uses per-pointer Job map — multitouch safe emoji spam with adaptive acceleration (500ms start → 28ms floor = ~35/sec peak)
- Clipboard stores preview (200 chars) + full_text (up to 500K) separately — fast list rendering, lazy paste
- Large paste uses chunked commits (4096 chars + 2ms yield) — prevents ANR
- All StateFlows use `stateIn(Eagerly)` — zero cold-start lag on panel switches
- IME process isolated as `:keyboard` — crash in keyboard can't crash the host app

## Product

- Full QWERTY keyboard + numeric/symbols panel
- Emoji panel with 3,600+ emojis, 8 categories, search — hold to spam
- Clipboard manager: 50-entry history, pin, search, 500K char support
- 6 themes: Default, AMOLED Black, Ocean Blue, Forest Green, Candy, Dusk
- Haptic feedback, auto-capitalize, double-space period, word suggestions
- Incognito mode, password field detection
- Inline settings panel + full SettingsActivity

## User preferences

- Project is output-only (files written to tapnix-keyboard/ for Android Studio)
- Target GitHub repo: github.com/buildwith-manish/tapnix-android-keyboard
- Package: com.tapnix.keyboard
- minSdk=31, targetSdk=35

## Gotchas

- The `gradle-wrapper.jar` binary must be added before `./gradlew` will work in Android Studio. Use "Sync Project with Gradle Files" in Android Studio — it downloads the jar automatically.
- `TapNixIMEService` runs in `:keyboard` process — must wire ViewTree owners on both `window.decorView` AND the `ComposeView` itself or Compose will crash.
- Never call `runBlocking` except in `getSettingsSync()` (used only by the IME Service on first cold-start before coroutines are ready).

## Pointers

- `tapnix-keyboard/README.md` — full build + setup instructions
- `tapnix-keyboard/ARCHITECTURE.md` — deep architecture and design decisions
- `tapnix-keyboard/PERFORMANCE.md` — memory and latency analysis
- `tapnix-keyboard/LIFECYCLE.md` — IME lifecycle diagrams
