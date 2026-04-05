# AGENTS.md

Guidance for AI coding agents working on this repository.

## Verify your work

After any code change, confirm it compiles:

```bash
JAVA_HOME=~/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home gradle shadowJar --no-daemon
```

A successful build produces `build/libs/firefly-bot-1.0.0.jar`. There are no automated
tests; compilation success is the minimum bar. Do not declare a task done until the build
passes cleanly.

## Update documentation

**After any change that affects architecture, package layout, key files, or conventions,
update `README.md`, `CLAUDE.md`, and `AGENTS.md` to stay accurate.** Keep all three files
consistent with each other and with the actual code.

## Project overview

Kotlin Telegram bot → Firefly III REST API (v6.1.0). Users interact with the bot via
Telegram; the bot translates user input into `POST /api/v1/transactions` calls.
The project uses hexagonal architecture (ports & adapters).

## Important constraints

- **Never log user message text.** User messages in a finance bot contain sensitive data
  (amounts, payees, account names). Log only `userId` and `chatId`.
- **Never read `System.getenv` directly.** All configuration flows through
  `AppConfig.fromEnv()` in `config/AppConfig.kt`.
- **Always catch `TelegramApiException`** around every `execute()` call and log the error.
  Do not let it propagate; the polling loop must stay alive.
- **Do not add new env variables** without updating both `AppConfig.kt` and `.env.example`.
- **Firefly III `amount` is always a `String`**, not a numeric type.
- **`WizardService` must have zero Telegram SDK imports.** Business logic must not depend
  on the inbound adapter. If you need to pass Telegram-specific data, model it in
  `WizardResult` or `WizardSession`, not as raw Telegram types.

## Architecture

```
adapter/in/telegram/
  FireflyBot.kt              — receives updates, calls WizardUseCase, delegates to presenter
  TelegramWizardPresenter.kt — all keyboard building, editMessage, sendMessage

application/
  port/in/WizardUseCase.kt         — inbound port (what FireflyBot calls)
  port/out/AccountRepository.kt    — outbound port
  port/out/CategoryRepository.kt   — outbound port
  port/out/TransactionRepository.kt — outbound port
  port/out/WizardSessionRepository.kt — outbound port
  wizard/WizardService.kt          — implements WizardUseCase; pure logic, no Telegram

domain/model/
  Account.kt / Category.kt         — core value types
  TransactionType.kt               — enum (TRANSFER, WITHDRAWAL, DEPOSIT)
  Transaction.kt                   — sealed class hierarchy

adapter/out/mock/                  — in-memory implementations (current)
adapter/out/firefly/               — real Firefly III HTTP implementations (swap in DI)

config/AppConfig.kt                — validated env-var config, read once at startup
di/AppModule.kt                    — all Koin bindings
Main.kt                            — starts Koin, registers bot with TelegramBotsApi
```

## WizardResult flow

`WizardService` methods return `WizardResult` (sealed class). `TelegramWizardPresenter.render()`
maps each subtype to Telegram API calls. Never return raw Telegram types from application
layer methods.

## Logging

```kotlin
// Correct
private val log = KotlinLogging.logger {}   // io.github.oshai.kotlinlogging
log.info  { "message" }
log.error(e) { "message" }

// Wrong — do not use SLF4J directly or mu.KotlinLogging
```

## Serialization

All Firefly III request/response models live in `adapter/out/firefly/dto/`, use
`@Serializable`, and `@SerialName` for snake_case JSON fields. The `Json` instance is
configured with `ignoreUnknownKeys = true`.

## Build environment

System Gradle 9.4.1; system JDK is 25 — incompatible with Kotlin 2.0.21. Always prefix
Gradle commands with `JAVA_HOME=~/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home`.

## Docker

The Dockerfile is a two-stage build. The builder stage uses the Gradle image (no wrapper
needed); the runtime stage uses a minimal JRE. The `.dockerignore` excludes `.env`,
`build/`, and `.git` — do not remove these exclusions.
