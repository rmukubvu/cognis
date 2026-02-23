# Cognis Dashboard

Tiny React operations dashboard for Cognis trust metrics + audit trail.

## Features

- KPI cards: success rate, P95 latency, avg cost/task, safety incident rate
- Execution quality + funnel snapshot
- Audit trail filters (event type, time window, payload search)
- CSV export for compliance/audit handoff

## Run

```bash
cd /Users/robson/nanobot4j/cognis-dashboard
npm install
npm run dev
```

By default it reads from `http://127.0.0.1:8787`.

Set a custom backend URL:

```bash
VITE_COGNIS_BASE_URL=http://<host>:<port> npm run dev
```

## Endpoints used

- `GET /dashboard/summary`
- `GET /audit/events?limit=300`
