# CLAUDE.md

Instructions for Claude Code when working on this repository.

## Build & run

```bash
# Fat JAR (used by Docker)
JAVA_HOME=~/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home gradle shadowJar --no-daemon

# Run locally (requires .env exported or env vars set)
java -jar build/libs/firefly-bot-1.0.0.jar

# Docker
docker compose up --build
```

There are no automated tests yet. Verify changes by building (`gradle shadowJar`) and
checking for compilation errors before declaring a task done.

## Documentation rule

**After any change that affects architecture, package layout, key files, or conventions,
update `README.md`, `CLAUDE.md`, and `AGENTS.md` to stay accurate.**
This includes: adding/removing/renaming files or packages, changing DI bindings,
introducing new patterns or port interfaces, or changing the build setup.

## Stack

| Concern        | Library / version                          |
|----------------|--------------------------------------------|
| Language       | Kotlin 2.0.21, JVM 21                      |
| DI             | Koin 3.5.6                                 |
| Telegram       | telegrambots 6.9.7.1 (TelegramLongPollingBot) |
| HTTP client    | Ktor 2.3.12, OkHttp engine                 |
| Serialization  | kotlinx-serialization-json 1.7.3           |
| Logging        | logback-classic 1.5.8 + kotlin-logging-jvm 6.0.9 (io.github.oshai) |
| Build          | Gradle 9.4.1 (system), Shadow plugin 8.3.6 |

> **Build note**: system JDK is 25 but Kotlin 2.0.21 requires JDK 21. Always prefix
> Gradle commands with `JAVA_HOME=~/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home`.

## Conventions

- **Logging**: import `io.github.oshai.kotlinlogging.KotlinLogging`; declare logger as
  `private val log = KotlinLogging.logger {}` at class level.
- **Log only metadata, never message content** — this is a finance bot; user message text
  is sensitive. Log `userId`/`chatId` identifiers only.
- **Error handling**: always catch `TelegramApiException` around `execute()` calls and log
  the error; do not let it propagate into the polling loop.
- **Env vars**: use `AppConfig.fromEnv()`; do not read `System.getenv` directly elsewhere.
  `requireEnv` trims and rejects blank values at startup.
- **Models**: Firefly III amount is a `String`, not a numeric type. `TransactionType` is
  an enum with `.apiValue` (`"withdrawal"`, `"deposit"`, `"transfer"`).
- Use `@SerialName` for every JSON field whose name differs from the Kotlin property name.
- In Ktor 2.x `defaultRequest {}` blocks, use `headers.append()` not `header()` (the K2
  compiler cannot resolve the extension overload).

## Architecture

The project uses **hexagonal architecture** (ports & adapters):

```
Telegram ──(inbound adapter)──▶ Application (WizardService) ──(outbound ports)──▶ Firefly III / Mocks
```

Layers:

| Layer | Package | Rule |
|-------|---------|------|
| Domain | `domain/model/` | No external dependencies. Pure Kotlin data types. |
| Application | `application/` | Depends on domain + port interfaces only. Zero Telegram/Ktor imports. |
| Adapters | `adapter/` | Implement ports. May import Telegram SDK, Ktor, etc. |

## Key files

| File | Role |
|------|------|
| `di/AppModule.kt` | Single Koin module; add new bindings here |
| `config/AppConfig.kt` | All config; add env vars here |
| `application/port/in/WizardUseCase.kt` | Inbound port — what Telegram adapter calls |
| `application/port/out/*.kt` | Outbound ports — what the application needs from outside |
| `application/wizard/WizardService.kt` | Core wizard logic; must have zero Telegram imports |
| `adapter/in/telegram/FireflyBot.kt` | Slim dispatcher: update → useCase → presenter |
| `adapter/in/telegram/TelegramWizardPresenter.kt` | All Telegram rendering |
| `adapter/out/mock/MockData.kt` | Dev-time mock data |
| `adapter/out/firefly/FireflyTransactionAdapter.kt` | Real Firefly III HTTP calls |

## Switching mock → real adapters

Change the relevant binding in `di/AppModule.kt`:

```kotlin
// Real transactions:
single<TransactionRepository> { FireflyTransactionAdapter(get(), get()) }
```

## Adding a new Telegram command

1. Add a `when` branch in `FireflyBot.handleMessage` matching the command string.
2. Call `wizardUseCase.*` or add a new use-case method; return a `WizardResult`.
3. Pass the result to `presenter.render(this, chatId, messageId, result)`.
4. Do not call `bot.execute()` directly from `FireflyBot` for content — delegate to the presenter.
