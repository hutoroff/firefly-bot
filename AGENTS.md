# AGENTS.md

Guidance for AI coding agents working on this repository.

## Verify your work

After any code change, confirm it compiles:

```bash
gradle shadowJar --no-daemon
```

A successful build produces `build/libs/firefly-bot-1.0.0.jar`. There are no automated
tests yet; compilation success is the minimum bar.

## Project overview

Kotlin Telegram bot → Firefly III REST API (v6.1.0). Users interact with the bot via
Telegram; the bot translates user input into `POST /api/v1/transactions` calls.

## Important constraints

- **Never log user message text.** User messages in a finance bot contain sensitive data
  (amounts, payees, account names). Log only `userId` and `chatId`.
- **Never read `System.getenv` directly.** All configuration flows through
  `AppConfig.fromEnv()` in `config/AppConfig.kt`.
- **Always catch `TelegramApiException`** around every `execute()` call and log the error.
  Do not let it propagate; the polling loop must stay alive.
- **Do not add new env variables** without updating both `AppConfig.kt` and `.env.example`.
- **Firefly III `amount` is always a `String`**, not a numeric type.

## Architecture

```
telegram/FireflyBot.kt          — receives updates, dispatches commands
firefly/FireflyClient.kt        — all Firefly III HTTP calls (Ktor + OkHttp)
config/AppConfig.kt             — validated env-var config, read once at startup
di/AppModule.kt                 — Koin bindings: AppConfig → HttpClient → FireflyClient → FireflyBot
Main.kt                         — starts Koin, registers bot with TelegramBotsApi
```

## Logging

```kotlin
// Correct
private val log = KotlinLogging.logger {}   // io.github.oshai.kotlinlogging
log.info  { "message" }
log.error(e) { "message" }

// Wrong — do not use SLF4J directly or microutils group
```

## Serialization

All Firefly III request/response models use `@Serializable` and `@SerialName` for
snake_case JSON fields. The `Json` instance is configured with `ignoreUnknownKeys = true`.

## Docker

The Dockerfile is a two-stage build. The builder stage uses the Gradle image (no wrapper
needed); the runtime stage uses a minimal JRE. The `.dockerignore` excludes `.env`,
`build/`, and `.git` — do not remove these exclusions.
