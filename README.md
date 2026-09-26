# DERIV AI

A safety-first foundation for an AI-assisted Deriv trading platform. It deliberately does **not** fabricate live prices, balances, signals, trades, or results. Until server-side Deriv OAuth is configured, the dashboard reports **DATA UNAVAILABLE** and no order route exists.

## Current scope

- Phase 1 design and safety boundaries documented.
- Server-side WebSocket adapter with message validation, subscriptions, stale-data protection, reconnect backoff, and dynamic `active_symbols` / `contracts_for` discovery.
- Deterministic no-trade / risk quality gate and idempotent execution state machine.
- Responsive dashboard with a clear paper-mode default and disabled live actions.
- Native Node test suite for critical no-trade and duplicate-execution rules.

## Run locally

```bash
cp .env.example .env
npm test
npm run check
npm start
```

Open `http://localhost:3000`. Configure server-only OAuth variables and a PostgreSQL implementation before enabling authenticated data or trading. This foundation intentionally contains no real-money buy capability.

> Trading involves risk. Past performance does not guarantee future results.
