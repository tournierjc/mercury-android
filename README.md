# Mercury Android

An **unofficial Android client for [Mercury Agent](https://github.com/cosmicstack-labs/mercury-agent)**, with an
interface inspired by the Grok app: near-black canvas, one accent colour, big composer, streaming answers,
hold-the-mic dictation, and nothing else in the way.

Not affiliated with Cosmic Stack. It talks to the HTTP API that Mercury's own web dashboard already exposes —
no plugin, no patch, no fork of the agent.

> Status: early. The chat path (login → send → stream → approve) works against Mercury 1.4.2.

## What it does

- **Sign in** to your Mercury instance with the dashboard credentials (session cookie, stored encrypted).
- **Stream** answers token by token over Mercury's SSE feed, with tool steps shown as chips above the reply.
- **Approve** permission requests (`yes` / `always` / `no`) from the phone, instead of walking back to the terminal.
- **Browse sessions** in a drawer and resume any of them with its history.
- **Dictate** with the system speech recogniser — the text lands in the composer, never auto-sent.
- **Markdown** rendering for what agents actually emit: fenced code, lists, headings, `inline code`, **bold**, *italic*.

## Requirements

1. Mercury running with the web dashboard enabled:
   ```bash
   MERCURY_WEB_ENABLED=true MERCURY_PORT=6174
   ```
   The dashboard binds `127.0.0.1` only, so the phone needs a route to it. Recommended:
   ```bash
   tailscale serve --bg --https=8443 http://127.0.0.1:6174
   # then use https://<host>.<tailnet>.ts.net:8443 in the app
   ```
   An SSH tunnel from the phone (`adb reverse`, Termux `ssh -L`, …) also works.
2. A dashboard user. `docker exec -it mercury mercury web-reset-password` sets the password;
   the username is in `~/.mercury/web-config.json` (usually `mercury`).

## Pont appareil (device bridge)

The app can expose the phone's own data to Mercury, read-only, over the tailnet —
so the agent on the Spark can answer "what's on my agenda tomorrow?" or "who texted
me?" without any server-side plugin.

Toggle **Pont appareil** in the drawer:

- **Calendrier** (`READ_CALENDAR`) — events, `GET /calendar?from=&to=&limit=`
- **Contacts** (`READ_CONTACTS`) — names, numbers, e-mails, `GET /contacts?q=&limit=`
- **SMS** (`READ_SMS`) — inbox, `GET /sms?limit=&address=`
- **Messages d'apps** — notification access, `GET /messages?limit=` (SMS + notifications,
  the only way to see WhatsApp/Signal/Messenger content: Android has no readable store for them)
- **Statut** — battery, network, timezone, `GET /device/status`
- `GET /health` — version, device, active providers (no token)

Security model:

- every route except `/health` requires the bearer token (constant-time compare) in
  `Authorization: Bearer …` — or `?token=` because Mercury's `fetch_url` sends no headers;
- the token is generated on the device, stored in Keystore-backed encrypted prefs, and
  revocable in one tap;
- the socket binds to the Tailscale address when present, 120 requests/minute cap;
- nothing is cached, nothing is written to disk: each request re-reads the provider.

Mercury side: `MERCURY_ALLOW_PRIVATE_FETCH=1` in `mercury.env` (SSRF protection
otherwise blocks private addresses), then copy `mercury-skills/phone-bridge/SKILL.md`
into `~/.mercury/skills/phone-bridge/` and paste the URL + token from the app.

Caveat: `READ_SMS` is a **restricted permission** for Google Play — an app whose core
function is not SMS cannot ship there with it. Sideloading is unaffected. The
notification-based message access has no such restriction.

## Build

JDK 17+ and the Android SDK (compileSdk 35) are required.

```bash
./gradlew testDebugUnitTest   # 6 test classes, no device needed
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

A prebuilt debug APK is attached to every release and by CI on `main`.

### Building on an ARM64 host (DGX Spark, arm64 VM)

Google ships `aapt2` for **x86_64 only** — no `linux_arm64` classifier exists on Google Maven, so any Android
build on an arm64 host fails with `Exec format error` (this is language-agnostic: `cargo-apk`/`xbuild` for a
Rust UI would hit the same wall). The repo ships a builder image that runs the official `aapt2` under
`qemu-user` inside a container, so nothing changes on the host:

```bash
./scripts/build-arm64.sh                       # :core:test + :app:assembleDebug
./scripts/build-arm64.sh :app:assembleRelease  # any other Gradle task
```

It builds `docker/Dockerfile.builder` (Android SDK 35 + qemu-user + amd64 `libc`/`libstdc++`), then runs Gradle
with `-Pandroid.aapt2FromMavenOverride=/opt/aapt2-qemu/bin/aapt2`. Expect ~20 s once the image and the Gradle
cache are warm.

## How it works

```
app/src/main/java/io/github/tournierjc/mercury/
├── data/
│   ├── MercuryApi.kt        OkHttp client for /api/auth/login + /api/chat/*
│   ├── SseParser.kt         SSE framing, chunk-boundary safe
│   ├── ChatEvent.kt         wire events → typed events (unknown types ignored)
│   ├── ChatReducer.kt       pure state machine (streaming, steps, approvals)
│   ├── SessionCookieJar.kt  persists mercury_session, drops expired cookies
│   └── Models.kt            kotlinx.serialization DTOs
├── vm/ChatViewModel.kt      coroutines, one SSE subscription per session
└── ui/                      Compose: Connect, Chat, Composer, PermissionCard, drawer
```

Endpoints used — all of them already exist in the Mercury dashboard:

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/login` | form-encoded login, answers 302 + `Set-Cookie: mercury_session` |
| GET | `/api/health` | runtime liveness |
| GET | `/api/chat/threads` | session list |
| GET | `/api/chat/threads/{id}` | history |
| POST | `/api/chat/send` | `{content, sessionId}` → `{sessionId}` |
| GET | `/api/chat/events?sessionId=` | SSE: `connected`, `text_delta`, `text_done`, `thinking`, `step_start`, `step_done`, `permission_request`, `permission_continue`, `session_switched`, `error` |
| POST | `/api/chat/permission/{id}` | `{action: yes\|always\|no}` |

Things Mercury emits that this client deliberately ignores: `status`, `provider`, `bot_activity`,
`loop_warning`, `heartbeat`, `choice_prompt`. Guessing their payload shape would be worse than waiting for a
real sample.

## Limitations

- Streaming is text-only: no image/artifact rendering yet, no attachments out.
- The SSE flow is not auto-reconnected; a dropped stream surfaces an error and the next send re-opens it.
- Bots, kanban, scheduler and the memory explorer are not exposed.
- Voice is one-shot dictation via the system recogniser — no streaming speech, no wake word.
- `usesCleartextTraffic` is on so that `http://` tunnels work in development; point it at the HTTPS
  tailnet URL and traffic stays TLS.

## Privacy

The app contacts exactly one host: the Mercury instance you type in. No analytics, no crash reporting, no
third-party SDKs, and the session cookie is stored with `EncryptedSharedPreferences` (Android Keystore).

## License

MIT — see [LICENSE](LICENSE). "Mercury" here refers to the agent; the Grok app is only a visual reference.
