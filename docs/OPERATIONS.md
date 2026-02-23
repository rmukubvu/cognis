# Cognis Operations Runbook

This runbook is for local demo operations and day-to-day reliability checks.

## Scope

- Start/stop Cognis reliably
- Validate core runtime health
- Validate mobile and dashboard connectivity
- Troubleshoot common issues quickly

## 1) Startup

### Docker (recommended)

```bash
cd /path/to/cognis
cp -n .env.example .env
# set provider key(s) in .env
docker compose up -d --build
```

### Local (Maven)

```bash
cd /path/to/cognis
mvn -pl cognis-app -am package
java -jar cognis-app/target/cognis-app-0.1.0-SNAPSHOT.jar gateway --port 8787
```

## 2) Shutdown / Restart

```bash
cd /path/to/cognis
docker compose down
docker compose up -d --build
```

Logs:

```bash
docker compose logs -f cognis
```

## 3) Health Checks

### API health

```bash
curl -s http://127.0.0.1:8787/healthz
```

Expected:

```json
{"status":"ok"}
```

### Observability endpoints

```bash
curl -s http://127.0.0.1:8787/dashboard/summary
curl -s 'http://127.0.0.1:8787/audit/events?limit=20'
```

### Payments endpoints

```bash
curl -s http://127.0.0.1:8787/payments/policy
curl -s http://127.0.0.1:8787/payments/status
```

## 4) Dashboard Operations

Start dashboard:

```bash
cd /path/to/cognis/cognis-dashboard
npm install
npm run dev
```

If Vite picks a different port (`5174`, etc.), use the printed URL.

Set explicit backend:

```bash
VITE_COGNIS_BASE_URL=http://127.0.0.1:8787 npm run dev
```

## 5) Mobile Operations

WebSocket URL format:

```text
ws://127.0.0.1:8787/ws?client_id=<client-id>
```

Quick connectivity behavior:

- Send `ping` -> expect `pong`
- Send chat message -> expect `typing`, `text_delta` stream, `ack`

## 6) Common Issues & Fixes

### A) Dashboard shows `Failed to fetch`

Checks:

1. Verify backend reachable in browser:
   - `http://127.0.0.1:8787/healthz`
2. Verify dashboard backend URL is correct (`VITE_COGNIS_BASE_URL`)
3. Hard-refresh browser after backend restart

Likely cause:

- backend not running, wrong host/port, or stale dev server config.

### B) CORS error from dashboard

Symptom:

- Browser says missing `Access-Control-Allow-Origin`.

Fix:

1. Restart Cognis after latest code changes:
   - `docker compose up -d --build`
2. Use local origins (`http://localhost:<port>` or `http://127.0.0.1:<port>`).

### C) Messages echo/duplicate in mobile

Fix:

1. Ensure backend is rebuilt/restarted with latest gateway logic.
2. Start a new chat thread after update to avoid stale UI artifacts.

### D) LLM errors (`provider ... not configured`)

Fix:

1. Confirm `.env` has matching key for selected provider.
2. Confirm `COGNIS_PROVIDER` is set correctly.
3. Restart container to regenerate/refresh config if needed.

### E) Web search fails

Symptom:

- `Error: BRAVE_API_KEY not configured`

Fix:

1. Set `WEB_SEARCH_API_KEY` in `.env`
2. Restart service.

## 7) Data and State

Default Docker volume mapping:

- host: `./docker-data`
- container: `/home/cognis/.cognis`

Important files:

- `.cognis/workspace/.cognis/observability/audit-events.json`
- `.cognis/workspace/.cognis/payments/ledger.json`
- `.cognis/workspace/.cognis/cron/jobs.json`
- `.cognis/workspace/memory/history.json`

## 8) Demo Readiness Checklist

Before a live demo:

1. `docker compose up -d --build`
2. `curl /healthz` returns `ok`
3. `curl /dashboard/summary` returns non-empty metrics
4. Dashboard opens and refreshes without browser errors
5. Mobile app can connect and receive one non-duplicated streamed response
6. Payments policy endpoint can read/write successfully

## 9) Recovery Procedure (Fast)

If behavior is inconsistent:

```bash
cd /path/to/cognis
docker compose down
docker compose up -d --build
docker compose logs -f cognis
```

Then re-run checks from Sections 3 and 4.

