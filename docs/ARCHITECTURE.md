# DERIV AI architecture and integration plan

## Documentation review

The specified official Deriv documentation was reviewed as the required integration source. The hosted documentation endpoints were unavailable from this build environment (proxy returned HTTP 403; the documentation browser returned HTTP 401), so no live schema assertions were inferred from sample prices or legacy payloads. The connector isolates all protocol mapping and validates raw inbound messages before they enter the system. Integration must be verified again against the linked current docs during credentialed staging before enabling Phase 12.

### API contract boundary

The adapter uses Deriv's WebSocket request/response model and the current request names required by the specification: `active_symbols`, `ticks`, `ticks_history`, `contracts_for`, `proposal`, `buy`, `sell`, `proposal_open_contract`, `balance`, `portfolio`, and `authorize`. It treats `active_symbols[].underlying_symbol` as the canonical instrument identifier and refuses malformed responses. `req_id` correlates requests. Streaming subscriptions are owned by one connection manager, not by UI clients. OAuth credentials are never sent to the browser or logged.

Before production activation, the staging verification checklist must confirm: OAuth authorization-code + PKCE exchange endpoint and scopes; authorized WebSocket URL and App ID handling; active-symbol category fields; tick/tick-history fields; contract availability fields; proposal/buy/sell/open-contract payloads; permission fields; documented rate limits; and error object shape. Any schema difference is corrected only in `apps/api/src/deriv-connector.js`.

## Architecture plan

```text
Browser (no Deriv credential)
  -> same-origin API/session cookie
  -> API/auth service -> encrypted credential vault + PostgreSQL
                     -> DerivConnector -> Deriv WebSocket
                     -> event bus -> market data / signal / risk / execution workers
                     -> audit log, immutable orders, metrics
```

The web client receives sanitized account and market projections only. PostgreSQL is authoritative for identities, subscription access, risk configuration, audit records, signals, proposals, orders, settlements, and aggregate metrics. Raw ticks use partitioned retention storage/object archival; never retain unbounded data in the primary database.

## Database plan

Use migrations and transactions. Core tables: `users`, `subscriptions`, `access_codes`, `deriv_accounts`, `oauth_connections`, `markets`, partitioned `ticks`, `market_snapshots`, `strategies`, `strategy_versions`, `signals`, `signal_features`, `proposals`, append-only `orders`, `open_contracts`, `settlements`, `risk_events`, `trading_sessions`, `performance_metrics`, `backtests`, `backtest_trades`, `notifications`, `devices`, `audit_logs`, and `system_events`. Index `(account_id, created_at)` for execution history, unique `orders.execution_id`, and unique active subscriptions/connection keys. Encrypt credential ciphertext at rest with a rotated server-held key.

## Trading engine plan

Execution is a state machine: `CREATED -> GATED -> PROPOSAL_REQUESTED -> PROPOSAL_VALIDATED -> BUY_SUBMITTED -> CONFIRMED -> MONITORING -> SETTLED`, with terminal `REJECTED`, `EXPIRED`, and `CANCELLED` states. A database uniqueness constraint on `execution_id` is checked before every submission and after reconnect; retries first reconcile with Deriv. Proposal and signal freshness are checked immediately before any future buy operation. This repository intentionally omits the buy operation until phases 1–11 pass in staging.

## AI engine plan

The pipeline stores timestamped feature snapshots and model/feature/strategy/risk versions for every signal. Digit analysis calculates distributions, transitions, entropy, concentration, streaks, autocorrelation diagnostics, rolling windows, and out-of-sample calibration. Observed frequency is not predictive evidence; inadequate samples, drift, invalid calibration, conflicting models, or non-positive expected value produce `NO_TRADE`. Model candidates must be backtested, walk-forward validated, paper traded, approved, and versioned before live eligibility.

## Risk and security plan

Risk has absolute priority over models. The quality gate blocks stale data/signals, unavailable contracts, invalid or changed proposals, insufficient balance, rate/position/exposure limits, daily/session loss, loss streaks, cooldowns, weak model quality, low sample size, bad expected value, high execution latency, duplicate execution, missing authorization, and an emergency stop. Defaults are conservative: 0.5% risk per trade, 2% daily loss, 3 consecutive losses, and 10 trades/hour. Martingale is absent.

OAuth uses authorization code + PKCE with a server-held, HttpOnly, Secure, SameSite=Lax state cookie/session. The verifier and CSRF state expire rapidly and are single use. Access/refresh credentials are encrypted before persistence, redacted from structured logs, and never returned to frontend code or browser storage. Admin operations require RBAC, 2FA in deployment, and append-only audit events.

## UI/UX plan

A responsive graphite interface defaults to paper mode and shows data provenance. Empty, disconnected, stale, or unsupported states read `DATA UNAVAILABLE`, never a placeholder number. Real-money controls stay disabled until the staged safety release and explicit user confirmations. The referral link is disclosed simply as account creation, with no implied advantage. Emergency stop is visible once an authenticated trading session exists.

## Test plan and roadmap

1. Architecture, migrations, credential vault, audit base.
2. OAuth PKCE and secure server sessions.
3. Live market data, tick history, and contract discovery.
4. Proposal validation and isolated demo/paper settlement.
5. Risk gate, backtest, walk-forward, Monte Carlo, and drift controls.
6. Security, failure-recovery, WebSocket, responsive, and load tests.
7. Credentialed staging acceptance: authenticated account, proposals, paper flows, reconciliation, duplicate prevention, and audit trails.
8. Only after signed-off phases 1–11: separately reviewed real-money buy/sell implementation behind two-step activation.

No real-money order is enabled by this codebase.
