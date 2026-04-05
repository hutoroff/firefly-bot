# Firefly Bot

A Telegram bot that creates transactions in [Firefly III](https://www.firefly-iii.org/) (API v6.1.0) via a conversational wizard interface.

## Requirements

- Docker + Docker Compose, **or** JDK 21 for local development
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
JAVA_HOME=~/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home gradle shadowJar --no-daemon
java -jar build/libs/firefly-bot-1.0.0.jar
```

## Usage

Send `/new` to start a guided wizard that walks you through creating a transaction:

1. Choose type — **Transfer**, **Withdrawal**, or **Deposit**
2. Select accounts (asset accounts from Firefly III; expense/revenue accounts searchable by name)
3. Enter amount(s); cross-currency transfers prompt for both sides separately
4. Pick a category (Withdrawal and Deposit)
5. Review the preview — optionally change the date/time or add a tag
6. Submit — the transaction is created in Firefly III

All interactions happen inside a single message that is edited in place (no message spam).

## Architecture

The project follows **hexagonal architecture** (ports & adapters):

```
Telegram update
      │
  FireflyBot          (inbound adapter — adapter/in/telegram/)
      │
  WizardUseCase       (inbound port interface)
      │
  WizardService       (application service — pure business logic, no Telegram imports)
      │
  AccountRepository / CategoryRepository / TagRepository
  TransactionRepository / WizardSessionRepository   (outbound ports)
      │
  FireflyAccountAdapter / FireflyCategoryAdapter / FireflyTagAdapter
  FireflyTransactionAdapter ──▶ Firefly III REST API
  InMemoryWizardSessionRepository (ConcurrentHashMap)
```

## Project structure

```
src/main/kotlin/com/fireflybot/
  Main.kt                               Entry point — wires Koin and registers the bot
  config/AppConfig.kt                   Reads and validates environment variables
  di/AppModule.kt                       Koin module — all bindings in one place

  domain/model/
    Account.kt                          Account(id, name, currencyCode)
    Category.kt                         Category(id, name)
    TransactionType.kt                  Enum: TRANSFER, WITHDRAWAL, DEPOSIT
    Transaction.kt                      Sealed class hierarchy with previewText() / successText()

  application/
    port/in/WizardUseCase.kt            Inbound port interface
    port/out/AccountRepository.kt       Outbound port — asset / expense / revenue account lookup
    port/out/CategoryRepository.kt      Outbound port — category list
    port/out/TagRepository.kt           Outbound port — tag list
    port/out/TransactionRepository.kt   Outbound port — create transaction
    port/out/WizardSessionRepository.kt Outbound port — session persistence
    wizard/WizardStep.kt                Sealed class — all wizard states
    wizard/WizardSession.kt             Immutable per-chat state (step, accounts, amounts, …)
    wizard/WizardResult.kt              Sealed class — what the service wants to render
    wizard/WizardService.kt             Implements WizardUseCase; zero Telegram imports

  adapter/in/telegram/
    FireflyBot.kt                       Slim dispatcher: update → useCase → presenter
    TelegramWizardPresenter.kt          All Telegram rendering (keyboards, edit/send)

  adapter/out/firefly/
    FireflyTransactionAdapter.kt        Implements TransactionRepository via Ktor HTTP
    FireflyAccountAdapter.kt            Implements AccountRepository (paginated + search)
    FireflyCategoryAdapter.kt           Implements CategoryRepository (paginated)
    FireflyTagAdapter.kt                Implements TagRepository (paginated)
    dto/                                Serialization DTOs for all Firefly III responses

  adapter/out/mock/
    InMemoryWizardSessionRepository.kt  Implements WizardSessionRepository (ConcurrentHashMap)
```
