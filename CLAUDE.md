# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Bubble_Rikkahub is an Android wrapper app for [RikkaHub](https://github.com/rikkahub/rikkahub), an AI chat application. RikkaHub has a built-in Ktor HTTP server that exposes a REST API and a React-based web UI. This project packages that web-server functionality into a native Android app targeting **Android 15+ (API 35+)**, using Jetpack Compose with Material 3 (Material You) design — including system theme color extraction and dark mode support.

The long-term goal is to extend RikkaHub's capabilities within this Android wrapper, going beyond what the original app's web server can do.

**Original RikkaHub source references:** The upstream code lives at `me.rerere.rikkahub`; this project uses `com.bubble.rikkahub` as its namespace.

## Key Reference Files

- `WebAPI.txt` — Full documentation of RikkaHub's web server architecture, API endpoints, data flow, and tech stack (Ktor + CIO engine, React Router 7 + Tailwind web UI, Zustand state management, mDNS, JWT auth, MCP integration)
- `Webhtml/` — Downloaded copy of the RikkaHub web UI (saved from a LAN instance at `http://192.168.31.149:8080/`). Serves as reference for the web interface structure and how the API is consumed.
- `https://deepwiki.com/rikkahub/rikkahub/9.5-web-server-and-api-access` — Online documentation mirror of the web server stack.

## Build & Development Commands

```bash
# Build the project
./gradlew assembleDebug

# Run unit tests (JVM, local)
./gradlew test

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Run a single test class
./gradlew test --tests "com.bubble.rikkahub.ExampleUnitTest"

# Lint
./gradlew lint

# Clean build
./gradlew clean
```

## Architecture

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.2.10 |
| UI Framework | Jetpack Compose + Material 3 |
| Build System | Gradle (Kotlin DSL) with version catalogs |
| Target SDK | 36 (Android 15, "Baklava"), extension level 1 |
| Min SDK | 35 (Android 15) |
| Compose BOM | 2026.02.01 |
| AGP | 9.3.1 |

### Package Structure

```
com.bubble.rikkahub
├── MainActivity.kt          — Single-activity, Compose-based entry point
├── di/AppContainer.kt       — Manual DI; Ktor HttpClient (CIO, HttpTimeout 30s), Room, repos, ConnectionMonitor
├── data/remote/
│   ├── RikkaHubApi.kt       — REST + SSE client (conversations, messages, settings, assistant switch)
│   ├── ConnectionMonitor.kt — Shared online/offline StateFlow + 10s probe + reconnect callback
│   └── dto/                 — kotlinx-serialization DTOs (Conversation*, StreamEvents, SettingsDto…)
├── data/repository/         — ConversationRepository (caches), ChatRepository (send queue), CustomizationRepository
├── data/local/              — Room: customizations, cached_conversations, pending_messages (DB v2)
├── domain/model/            — Conversation, Message (@Serializable), AssistantInfo, SendMode, AvatarMode, ListTheme
├── ui/
│   ├── navigation/NavGraph.kt — Bottom nav (icons only), TopAppBar on list screen, ChatViewModel scoped to entry
│   ├── screens/conversations/ — Conversation list + assistant switcher + offline banner
│   ├── screens/chat/          — ChatViewModel + ChatScreen (bubble list, typing indicator, input bar)
│   └── screens/settings/      — Server URL, split delimiters, send/avatar modes, list theme, "my profile"
└── ui/theme/
    ├── Color.kt             — Static fallback colors (purple/pink palette)
    ├── Type.kt              — Material 3 Typography definitions
    └── Theme.kt             — Theme composable with dynamic color + dark mode support
```

### Theme System

The theme (`Bubble_RikkahubTheme`) follows the standard Material 3 pattern with three tiers:
1. **Dynamic color** (Android 12+): Uses `dynamicDarkColorScheme`/`dynamicLightColorScheme` for Material You theming — automatically extracts colors from the system wallpaper.
2. **Static dark**: Fallback `darkColorScheme` with Purple80/PurpleGrey80/Pink80.
3. **Static light**: Fallback `lightColorScheme` with Purple40/PurpleGrey40/Pink40.

Dark theme follows `isSystemInDarkTheme()`. Dynamic color is enabled by default (`dynamicColor = true`).

### Original RikkaHub Web Server Architecture (from WebAPI.txt)

Understanding the upstream architecture is critical for extending it:

- **Server engine**: Ktor with CIO (Coroutine I/O) engine
- **Android service**: `WebServerService` runs as a foreground service with persistent notification
- **Network modes**: localhost-only (127.0.0.1) or LAN (0.0.0.0) with mDNS registration (`_http._tcp`)
- **Auth**: Optional JWT authentication for API access
- **Settings**: `webServerEnabled`, `webServerPort` (default 8080, range 1024–65535), `webServerLocalhostOnly`, `webServerJwtEnabled`

**REST API Endpoints:**
- `GET/POST /api/conversations` — List/create conversations
- `GET /api/conversations/{id}` — Conversation detail with message nodes
- `POST /api/chat/stream` — AI generation via SSE (Server-Sent Events)
- `POST /api/files/upload`, `GET/DELETE /api/files/{id}` — File management

**Web UI:** React (React Router 7) + Tailwind CSS + Zustand state management. Features: streaming chat, markdown/LaTeX rendering (via Streamdown), chain-of-thought reasoning display, file attachments, MCP tool picker, i18n (zh-CN, en-US).

### Current App State

Bubble RH is a **pure client** for a RikkaHub instance's web server (default `http://localhost:8080`, set in 设置 → 服务器地址). It is NOT a web wrapper — native Jetpack Compose UI.

**Implemented features:**
- Conversation list (delete / pin / refresh, flat vs card list theme), cached offline view.
- Chat screen: WeChat/Telegram-style bubbles, left = AI, right = user; AI messages are split into bubbles by `#…*`-style delimiters (`util/MessageSplitter.kt`).
- **AI replies are only revealed after generation fully finishes** (the SSE stream's incremental content is ignored); multi-bubble replies appear one-by-one with a 200–800 ms random delay, with a typing indicator while generating.
- Send modes: timer (auto-send after N s) or manual (press send on empty input to submit). User input bubbles are packed with delimiters into one message.
- Per-conversation custom avatar/emoji/nickname (stored in Room `customizations`); the user's own profile is stored under the reserved key `"__self__"`.
- Assistant switcher (top bar of the conversation list) — settings/assistant list come from the `settings` SSE event on `/api/events` (no plain GET exists); switch via `POST /api/settings/assistant`.
- Offline mode: messages cached per conversation; red banner on the main screen; undelivered sends queue in `pending_messages` and auto-flush on reconnect.
- Dark mode + dynamic color (Material 3).

**Key architecture notes:**
- ChatViewModel must be created via `viewModel(factory)` in `NavGraph` (scoped to the nav back-stack entry) so leaving a chat cancels its SSE stream — creating it inline caused crashes and endless loading.
- SSE stream requests use an **infinite timeout** (`HttpTimeoutConfig.INFINITE_TIMEOUT_MS`); the global client has a 30 s timeout. Stream collection is wrapped in try/catch.
- Message split delimiters are configurable in settings (default `#` / `*`); when no delimiters match, the whole text is one bubble.

**Verified against a live RikkaHub server (LAN 192.168.31.149):**
- `GET /api/conversations/{id}/stream` SSE emits `: heartbeat` comments, `snapshot` on connect, and `node_update` frames during generation — it **never emits a `done` event**. Completion is signaled by a `node_update` with `"isGenerating":false`. The snapshot/node_update JSON embeds a `"type"` field (ignored via `ignoreUnknownKeys`).
- `GET /api/events` emits `settings` (full Settings JSON as the raw `data`, no wrapper) on connect, and `conversation_list_invalidate` on changes. Assistant list = `settings.assistants[]` (`id`, `name`, `avatar.type/url`); active = `settings.assistantId`. Switch via `POST /api/settings/assistant` body `{"assistantId":"…"}`.
- `POST /api/conversations/{id}/messages` body `{"parts":[{"type":"text","text":"…"}]}` → `202 {"status":"accepted"}`. The Ktor client must check the response status; without it, 4xx/5xx are silently swallowed. 4xx (client error) should be surfaced; 5xx can be queued for retry.
- **CRITICAL**: the client's `Json` MUST set `encodeDefaults = true`. `TextPart.type` defaults to `"text"` and kotlinx.serialization's default `encodeDefaults=false` omits it from the POST body (`{"parts":[{"text":"…"}]}`), which the server rejects with **HTTP 500**. The DataStore `preferencesDataStore` delegate must be a top-level singleton (not inside the DI container class) or Activity recreation crashes with "multiple DataStores active for the same file".
- Assistant avatar URLs are `file:///data/user/0/me.rerere.rikkahub/...` (inside RikkaHub's sandbox) — not loadable by this app; filter non-http URLs.
- Chat completion detection has a poll fallback (`ensureStatusPoll`, every 4 s while generating) in case the stream's final `isGenerating:false` frame is missed.

## Design Requirements

- **Android 15+ (API 35+)**
- **Material Design latest version** (Material 3 / Material You)
- **System theme color extraction** (dynamic color / Monet)
- **Dark mode support** (follow system setting)
- UI should feel native, not a wrapped web page
