# Contributing To Cognis

## Prerequisites

- Java 21
- Maven 3.9+
- Node 20+ (only for `cognis-dashboard`)

## Setup

```bash
cd /path/to/cognis
cp .env.example .env
```

If `.env.example` does not appear in Finder, show hidden files with `Cmd+Shift+.` or run `ls -la`.

Set at least one provider key in `.env`:

- `OPENROUTER_API_KEY`
- or `OPENAI_API_KEY`
- or `ANTHROPIC_API_KEY`

## Build And Test

Core tests:

```bash
cd /path/to/cognis
mvn test -pl cognis-core
```

App compile check (includes module wiring):

```bash
cd /path/to/cognis
mvn -pl cognis-app -am -DskipTests compile
```

Dashboard checks:

```bash
cd /path/to/cognis/cognis-dashboard
npm install
npm run typecheck
npm run build
```

## Conversation Store Backends

Conversation persistence is configurable:

- Default: `COGNIS_CONVERSATION_STORE=sqlite`
- Optional: `COGNIS_CONVERSATION_STORE=file`

Optional custom SQLite file path:

- `COGNIS_CONVERSATION_SQLITE_PATH=/absolute/path/to/conversations.db`

Defaults:

- SQLite file: `<workspace>/.cognis/conversations.db`
- File mode history: `<workspace>/memory/history.json`

Migration note:

- There is no automatic import from file history (`memory/history.json`) into SQLite.

## Testing Without WhatsApp

You can fully test Cognis without WhatsApp integration.

Fastest path:

```bash
cd /path/to/cognis
./scripts/smoke-test.sh
```

Useful smoke-test options:

- `COGNIS_SMOKE_RUN_CORE_TESTS=0 ./scripts/smoke-test.sh` to skip targeted core tests.
- `COGNIS_SMOKE_BUILD_APP=0 ./scripts/smoke-test.sh` to skip app compile checks.

1. React Native app path (primary)

- Start gateway and connect app to `ws://127.0.0.1:8787/ws?client_id=<id>`.

2. CLI path

```bash
cd /path/to/cognis
java -jar cognis-app/target/cognis-app-0.1.0-SNAPSHOT.jar agent "hello"
```

3. Raw WebSocket path (smoke test)

```bash
websocat ws://127.0.0.1:8787/ws?client_id=smoke-test
```

Then send:

```json
{"type":"message","content":"hello","msg_id":"m1"}
```

4. Verify persisted conversation rows (SQLite default)

```bash
sqlite3 ~/.cognis/workspace/.cognis/conversations.db "select created_at,prompt from conversation_turns order by created_at desc limit 5;"
```
