# Keyboard Masterpiece Android IME

A clean offline Android keyboard app built in Kotlin with a C++ JNI gesture classifier. The repository has been converted from the previous web/Replit workspace into a production-oriented Android project rooted at this directory.

## Build

```bash
chmod +x ./gradlew
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Included

- Android InputMethodService (IME) for Android 8.0+ (`minSdk 26`).
- Custom hardware-accelerated Kotlin `View` keyboard renderer; no Compose, React, TypeScript, web runtime, backend or internet permission.
- QWERTY, number row, symbols, numpad, emoji/GIF placeholder panel, clipboard/editing panel.
- JNI/C++ low-latency gesture path classifier with Kotlin fallback.
- Local suggestions, autocorrect, next-word prediction and personal dictionary using SharedPreferences.
- Haptic/sound feedback, popup previews, long-press alternates, backspace repeat, spacebar cursor movement.
- Light/dark theme, incognito mode, font/key-height settings, one-handed/floating/split layout modes.
- GitHub Actions and Codemagic APK build configuration.

## Privacy

The app is offline-first and declares no internet, microphone, contacts, storage or account permissions. Voice input is a system input-method switch hook rather than an in-app recorder.
