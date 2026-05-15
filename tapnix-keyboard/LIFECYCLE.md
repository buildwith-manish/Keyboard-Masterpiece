# TapNix IME Lifecycle Management Report

## Service Lifecycle

```
┌─────────────────────────────────────────────────────────┐
│                   TapNixIMEService                      │
│                                                         │
│  onCreate()                                             │
│    ├── savedStateRegistryController.performRestore()    │
│    └── lifecycleRegistry → ON_CREATE                    │
│                                                         │
│  onCreateInputView()   ← called every keyboard show     │
│    ├── ComposeView created                              │
│    ├── ViewTree owners set on decor + composeView       │
│    └── KeyboardRoot Compose tree inflated               │
│                                                         │
│  onStartInputView(info, restarting)                     │
│    ├── lifecycleRegistry → ON_RESUME                    │
│    ├── viewModel.onStartInput()                         │
│    ├── detectAndApplyPasswordMode(info)                 │
│    └── clipboardEngine.registerClipboardListener()      │
│         (skipped if password field)                     │
│                                                         │
│  onFinishInputView(finishingInput)                      │
│    ├── lifecycleRegistry → ON_PAUSE                     │
│    ├── viewModel.onFinishInput()                        │
│    │    ├── cancelAllLongPress()                        │
│    │    └── record current word                         │
│    └── clipboardEngine.unregisterClipboardListener()    │
│                                                         │
│  onDestroy()                                            │
│    ├── lifecycleRegistry → ON_DESTROY                   │
│    ├── serviceScope.cancel()                            │
│    ├── _viewModelStore.clear()                          │
│    ├── clipboardEngine.dispose()                        │
│    └── inputView = null                                 │
└─────────────────────────────────────────────────────────┘
```

## ViewModel Lifecycle

The `KeyboardViewModel` is scoped to the `ViewModelStore` inside `TapNixIMEService`.

- Created lazily on first access via `ViewModelProvider`
- Survives orientation changes (the IME service is not recreated for orientation)
- Cleared explicitly in `onDestroy()` via `_viewModelStore.clear()`
- `viewModelScope` is a `SupervisorJob` + `Main.immediate` — cancelled on ViewModel clear

## Orientation Changes

The IME framework handles orientation changes differently from Activities:
- `onCreateInputView()` is called again after rotation
- A **new** `ComposeView` is created and inflated
- The `ViewModel` is **reused** (not recreated) — state is preserved
- `StateFlow` collectors in the new Compose tree re-subscribe automatically

## Configuration Changes

`onConfigurationChanged()` is not overridden — the IME framework handles it.
The keyboard dynamically adapts to available width via `fillMaxWidth()` and
`GridCells.Adaptive` in the emoji grid.

## Memory Safety Checklist

| Scenario | Protection |
|----------|-----------|
| Service destroyed while typing | `ON_DESTROY` + `serviceScope.cancel()` stops all coroutines |
| Coroutine holding reference to ComposeView | `inputView = null` in `onDestroy`, scope cancelled |
| LongPress running after keyboard hidden | `cancelAllLongPress()` in `onFinishInput` |
| Clipboard listener after keyboard hidden | `unregisterClipboardListener()` in `onFinishInputView` |
| ViewModel holding activity context | Only `applicationContext` stored |
| Room connection after process kill | Room handles connection cleanup via finalizers |
| DataStore coroutine after process kill | Coroutine scope cancelled, DataStore is crash-safe |

## Coroutine Scope Hierarchy

```
serviceScope (SupervisorJob + Main.immediate)
  └── [short-lived: not used directly, safety net]

viewModelScope (SupervisorJob + Main.immediate, auto-cancelled)
  ├── StateFlow collection jobs (stateIn)
  ├── LongPressEngine jobs (per-pointer, in activeJobs map)
  ├── Suggestion update jobs
  └── Clipboard/Emoji fire-and-forget jobs

engineScope (SupervisorJob + IO, in ClipboardEngine)
  └── Clipboard capture jobs

ioScope (SupervisorJob + IO, injected into engines)
  ├── EmojiEngine record jobs
  └── SuggestionEngine record jobs
```

## Security Notes

- Password fields detected via `InputType.TYPE_MASK_VARIATION` bitmask check
- Clipboard listener is **not registered** when `isPasswordMode = true`
- Suggestions are hidden in password mode
- No clipboard capture happens in password fields
- All clipboard data stored locally in Room — never transmitted
- Incognito mode skips word recording and clipboard capture
