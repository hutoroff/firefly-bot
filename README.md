# Firefly Bot

A Telegram bot that creates transactions in [Firefly III](https://www.firefly-iii.org/) (API v6.1.0) via a conversational wizard interface.

## Requirements

- Docker + Docker Compose, **or** JDK 21 for local development (the Gradle wrapper `./gradlew` is included — no separate Gradle installation needed)
- A running Firefly III instance with a Personal Access Token
- A Telegram bot token from [@BotFather](https://t.me/BotFather)

## First-time setup

### 1. Create a Telegram bot

1. Open Telegram and start a chat with [@BotFather](https://t.me/BotFather)
2. Send `/newbot` and follow the prompts
3. Copy the **bot token** (format: `123456789:ABC-DEF...`) — this is `TELEGRAM_BOT_TOKEN`
4. The bot username you chose (without `@`) is `TELEGRAM_BOT_USERNAME`

### 2. Find your Telegram user ID

Send a message to [@userinfobot](https://t.me/userinfobot) — it replies with your numeric user ID.
This value goes into `TELEGRAM_ALLOWED_USER_ID`. Only this user can interact with the bot.

### 3. Create a Firefly III Personal Access Token

In your Firefly III instance: **Profile → OAuth → Personal Access Tokens → Create new token**.
Copy the token — this is `FIREFLY_TOKEN`.

### 4. Configure the bot

```bash
cp .env.example .env
# Edit .env with your values
```

## Configuration

| Variable                   | Required | Description                                       |
|----------------------------|----------|---------------------------------------------------|
| `TELEGRAM_BOT_TOKEN`       | Yes      | Token from BotFather                              |
| `TELEGRAM_BOT_USERNAME`    | Yes      | Bot username without `@` (e.g. `my_firefly_bot`) |
| `TELEGRAM_ALLOWED_USER_ID` | Yes      | Numeric Telegram user ID allowed to use the bot  |
| `FIREFLY_HOST`             | Yes      | Base URL of your Firefly III instance             |
| `FIREFLY_TOKEN`            | Yes      | Personal Access Token from Firefly III            |
| `SESSION_FILE_PATH`        | No       | Path for wizard session storage (default: `sessions.json`; Docker default: `/app/data/sessions.json`) |

Startup fails immediately if any required variable is missing or blank.

## Deploying to a server

The bot is a single Docker container with no exposed ports (it uses Telegram long-polling). Any Linux host with Docker and Docker Compose installed is sufficient.

### 1. Get the files onto the server

**Option A — clone the repository** (builds the image from source):
```bash
git clone <repo-url>
cd firefly_bot
```

**Option B — download only two files** (uses the pre-built image from Docker Hub):

Download `docker-compose.yml` and `.env.example` from the repository, then replace the `build: .` key in `docker-compose.yml` with the published image tag listed on the [GitHub Releases page](../../releases).

### 2. Create the env file

```bash
cp .env.example .env
# Edit .env with production values (never commit this file)
```

### 3. Start the bot in the background

```bash
# Option A (build from source):
docker compose up -d --build

# Option B (pre-built image):
docker compose up -d
```

The bot starts polling Telegram immediately. Wizard sessions are persisted to `./data/sessions.json` on the host (Docker bind-mounts it to `/app/data/sessions.json` inside the container). The `data/` directory is created automatically.

### 4. Verify it is running

```bash
docker compose ps          # should show firefly-bot as "running"
docker compose logs -f     # stream logs; Ctrl-C to stop tailing
```

### Updating to a new version

**Option A (built from source):**
```bash
git pull
docker compose up -d --build    # rebuilds the image and recreates the container
```

**Option B (pre-built image):**
```bash
docker compose pull             # fetches the new image from Docker Hub
docker compose up -d            # recreates the container with the new image
```

Sessions are preserved across updates via the `./data` volume.

### Stopping the bot

```bash
docker compose down    # stops and removes the container; data/ volume is untouched
```

## Running locally (development)

```bash
# Build
JAVA_HOME=~/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home ./gradlew shadowJar --no-daemon

# Load env vars from .env, then run
export $(grep -v '^#' .env | xargs)
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

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Bot exits immediately at startup | Missing or blank env var | Check container logs; the error names the missing variable |
| Bot does not respond to `/new` | Wrong user ID | Verify `TELEGRAM_ALLOWED_USER_ID` matches your Telegram user ID |
| "Firefly III" calls fail | Invalid token or wrong host | Confirm `FIREFLY_HOST` has no trailing slash and `FIREFLY_TOKEN` is valid |
| Sessions lost after restart | Volume not mounted | Ensure `./data` volume is mounted and `SESSION_FILE_PATH` points to it |

Logs go to stdout (visible via `docker compose logs -f`). Third-party libraries (Ktor, OkHttp, Telegram SDK) are silenced to WARN; application logs are at INFO.

## Architecture

The project follows **hexagonal architecture** (ports & adapters):

```
Telegram update
      │
  FireflyBot              (inbound adapter — adapter/in/telegram/)
      │
  WizardUseCase           (inbound port interface)
      │
  WizardService           (application service — pure business logic, no Telegram imports)
      │
  AccountRepository / CategoryRepository / TagRepository
  TransactionRepository / WizardSessionRepository   (outbound ports)
      │
  FireflyAccountAdapter / FireflyCategoryAdapter / FireflyTagAdapter
  FireflyTransactionAdapter ──▶ Firefly III REST API
  JsonFileWizardSessionRepository ──▶ sessions.json  (production)
  InMemoryWizardSessionRepository                    (tests only)
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
    Tag.kt                              Tag(id, name)
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

  adapter/out/persistence/
    JsonFileWizardSessionRepository.kt  Implements WizardSessionRepository (JSON file, production)
    WizardSessionDto.kt                 Flat serializable DTO for session persistence

  adapter/out/mock/
    InMemoryWizardSessionRepository.kt  Implements WizardSessionRepository (ConcurrentHashMap, tests only)
```
