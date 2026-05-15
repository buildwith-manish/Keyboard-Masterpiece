# TapNix Architecture Report

## Overview

TapNix uses a strict MVVM / Clean Architecture with a single-directional data flow:

```
IME Service → ViewModel (StateFlow) → Compose UI (collectAsStateWithLifecycle)
      ↑                                          |
      └──────────── User Actions ────────────────┘
```

## Layer Responsibilities

### 1. IME Layer (`TapNixIMEService`)

**Responsibility:** Android platform boundary. Bridges the Android IME framework with Compose.

Key decisions:
- Implements `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner` manually — because `InputMethodService` is a Service, not a `ComponentActivity`
- Uses `LifecycleRegistry` to emit correct lifecycle events to Compose
- Sets ViewTree owners on both the decor view AND the ComposeView (required for nested Compose to resolve owners)
- Runs in a separate `:keyboard` process for isolation and stability
- `serviceScope` is a `SupervisorJob` scope — cancelled on `onDestroy`, prevents all coroutine leaks

### 2. ViewModel Layer (`KeyboardViewModel`)

**Responsibility:** Single source of truth. All UI state exposed as `StateFlow`.

Key decisions:
- Uses `stateIn(SharingStarted.Eagerly)` for all hot flows — avoids cold-start lag on panel switches
- `LongPressEngine` owned by ViewModel (scoped to `viewModelScope`) — auto-cancels on ViewModel clear
- Emoji search debounced 150ms via `debounce()` before `flatMapLatest` — prevents grid thrash
- Clipboard search debounced 200ms
- `onCharCommitted()` handles shift state machine (OFF → ON → CAPS_LOCK → OFF)
- Word recording is fire-and-forget via `ioScope.launch` — never blocks UI

### 3. Engine Layer

**LongPressEngine:**
- Per-pointer `Map<Int, Job>` for multitouch safety
- Runs on `Dispatchers.Default`, emits on `Main.immediate`
- Adaptive acceleration: `intervalMs *= 0.90`, floor at `minIntervalMs`
- `cancelAll()` called from `onFinishInput()` to prevent runaway loops

**ClipboardEngine:**
- Clipboard listener registered only when keyboard is visible (not in password fields)
- Chunked paste: 4096-char batches with 2ms yield between chunks
- Room `TEXT` column for full content (SQLite supports up to 2GB text)
- Preview column (200 chars) for list rendering — avoids loading 500K chars into LazyColumn

**EmojiEngine:**
- Static emoji data in `EmojiData.kt` — loaded once, never garbage collected
- Room only used for recent/favorite tracking (small dataset)
- Search runs on `Dispatchers.Default`, returns `Flow<List<Emoji>>`
- Stable emoji keys in `LazyVerticalGrid` — `key = { emoji.unicode }` prevents recomposition

**SuggestionEngine:**
- Cold-start fallback: 120 common English words for instant first-run suggestions
- Room `word_frequency` table: unique index on `(word, language)`
- `incrementFrequency()` is an `UPDATE` not `UPSERT` — avoids write amplification
- `getSuggestions()` uses `LIKE :prefix || '%'` prefix search with `LIMIT 5`

### 4. Database Layer (Room)

```
Entities:
  clipboard         — id, preview, full_text, is_pinned, label, created_at, size_chars
  emoji_history     — unicode (PK), name, use_count, is_favorite, last_used
  word_frequency    — id, word, frequency, last_used, language

Indexes:
  clipboard.is_pinned    — fast pinned filter
  clipboard.created_at   — fast date sort
  emoji_history.last_used — fast recent sort
  word_frequency.(word, language) — unique, fast prefix scan
```

### 5. Settings Layer (DataStore)

All 11 settings stored as typed `Preferences` keys. Exposed as `Flow<KeyboardSettings>` mapped and `distinctUntilChanged` to prevent unnecessary StateFlow updates.

## State Machine: Shift Key

```
OFF ──(tap)──→ ON ──(tap)──→ CAPS_LOCK ──(tap)──→ OFF
              │
              └──(char committed)──→ OFF   (one-shot)
```

## Memory Safety

| Risk | Solution |
|------|----------|
| ViewModel retained after service destroy | `_viewModelStore.clear()` in `onDestroy()` |
| Compose recomposing after destroy | `lifecycleRegistry ON_DESTROY` stops collectors |
| Clipboard listener dangling | `unregisterClipboardListener()` in `onFinishInputView` |
| Coroutine leak on LongPress | `longPressEngine.cancelAll()` in `onFinishInput` |
| OOM on huge clipboard | 500K char cap, lazy load, chunked paste |
| Static context reference | Only `applicationContext` ever stored |
| ANR on clipboard paste | 4096-char chunked commits with 2ms yield |

## Lifecycle Events Flow

```
Device boot
  → IME enabled → TapNixIMEService process started

User taps text field
  → onCreate()         → SavedState restore, ON_CREATE
  → onCreateInputView() → ComposeView + ViewTree owners wired
  → onStartInputView() → ON_RESUME, detect password, register clipboard listener

User hides keyboard
  → onFinishInputView() → ON_PAUSE, cancel long-press, unregister listener

App killed / memory pressure
  → onDestroy() → ON_DESTROY, cancel scope, clear ViewModelStore, dispose engines
```

## Recomposition Control

| Pattern | Applied to |
|---------|-----------|
| `collectAsStateWithLifecycle()` | All StateFlow consumers |
| `key = { item.stableId }` | LazyColumn, LazyVerticalGrid |
| `remember { lambda }` | Key button callbacks |
| `derivedStateOf` | Computed display values |
| `stateIn(Eagerly)` | Hot flows from engines |
| `debounce(150)` | Emoji search input |
| `debounce(200)` | Clipboard search input |
| `distinctUntilChanged()` | Settings flow |
| `Crossfade(160ms)` | Panel switching |
