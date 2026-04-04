# Firefly Bot

A Telegram bot that creates transactions in [Firefly III](https://www.firefly-iii.org/) (API v6.1.0) via a conversational interface.

## Requirements

- Docker + Docker Compose, **or** JDK 21 + Gradle 8.10 for local development
- A running Firefly III instance with a Personal Access Token
- A Telegram bot token from [@BotFather](https://t.me/BotFather)

## Configuration

Copy the example env file and fill in all values:

```bash
cp .env.example .env
```

| Variable                | Description                                       |
|-------------------------|---------------------------------------------------|
| `TELEGRAM_BOT_TOKEN`    | Token from BotFather                              |
| `TELEGRAM_BOT_USERNAME` | Bot username without `@` (e.g. `my_firefly_bot`) |
| `FIREFLY_HOST`          | Base URL of your Firefly III instance             |
| `FIREFLY_TOKEN`         | Personal Access Token from Firefly III            |

Startup fails immediately if any variable is missing or blank.

## Running

### Docker (recommended)

```bash
docker compose up --build
```

### Local

```bash
gradle shadowJar
java -jar build/libs/firefly-bot-1.0.0.jar
```

## Project structure

```
src/main/kotlin/com/fireflybot/
  Main.kt                               Entry point — wires Koin and registers the bot
  config/AppConfig.kt                   Reads and validates environment variables
  di/AppModule.kt                       Koin module definitions
  firefly/
    FireflyClient.kt                    POST /api/v1/transactions
    model/TransactionRequest.kt         StoreTransactionRequest, TransactionSplit
    model/TransactionResponse.kt        TransactionSingle, TransactionRead
  telegram/
    FireflyBot.kt                       Telegram long-polling bot
```

## Architecture

```
Telegram update
      │
  FireflyBot (TelegramLongPollingBot)
      │
  FireflyClient (Ktor/OkHttp)
      │
  Firefly III REST API
```

Dependency injection is handled by Koin. The single module (`appModule`) provides
`AppConfig` → `HttpClient` → `FireflyClient` → `FireflyBot`.
