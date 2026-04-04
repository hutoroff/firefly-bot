# CLAUDE.md

Instructions for Claude Code when working on this repository.

## Build & run

```bash
# Fat JAR (used by Docker)
gradle shadowJar

# Run locally (requires .env exported or env vars set)
java -jar build/libs/firefly-bot-1.0.0.jar

# Docker
docker compose up --build
```

There are no automated tests yet. Verify changes by building (`gradle shadowJar`) and
checking for compilation errors before declaring a task done.

## Stack

| Concern        | Library / version                          |
|----------------|--------------------------------------------|
| Language       | Kotlin 2.0.21, JVM 21                      |
| DI             | Koin 3.5.6                                 |
| Telegram       | telegrambots 6.9.7.1 (TelegramLongPollingBot) |
| HTTP client    | Ktor 2.3.12, OkHttp engine                 |
| Serialization  | kotlinx-serialization-json 1.7.3           |
| Logging        | logback-classic 1.5.8 + kotlin-logging-jvm 6.0.9 (io.github.oshai) |
| Build          | Gradle 8.10, Shadow plugin 8.1.1           |

## Conventions

- **Logging**: import `io.github.oshai.kotlinlogging.KotlinLogging`; declare logger as
  `private val log = KotlinLogging.logger {}` at class level.
- **Log only metadata, never message content** — this is a finance bot; user message text
  is sensitive. Log `userId`/`chatId` identifiers only.
- **Error handling**: always catch `TelegramApiException` around `execute()` calls and log
  the error; do not let it propagate into the polling loop.
- **Env vars**: use `AppConfig.fromEnv()`; do not read `System.getenv` directly elsewhere.
  `requireEnv` trims and rejects blank values at startup.
- **Models**: Firefly III amount is a `String`, not a numeric type. Transaction `type` is
  one of `"withdrawal"`, `"deposit"`, `"transfer"`.
- Use `@SerialName` for every JSON field whose name differs from the Kotlin property name.

## Key files

| File | Role |
|------|------|
| `di/AppModule.kt` | Single Koin module; add new bindings here |
| `config/AppConfig.kt` | All config; add env vars here |
| `firefly/FireflyClient.kt` | All Firefly III API calls |
| `telegram/FireflyBot.kt` | All Telegram command handling |

## Adding a new Telegram command

1. Add a `when` branch in `FireflyBot.onUpdateReceived` matching the command string.
2. Implement a private `handle*` method that calls `execute()` inside a try/catch.
3. If the command needs Firefly III, inject the call through `fireflyClient`.
