# Embeddable Widget & Lead-Capture Platform

This is my backend capstone project, built during a backend internship track. It's a small SaaS-style platform: a business signs up, builds a "widget" (a signup form, contact form, or popover) through a dashboard, and gets a two-line embed snippet to paste into their own website. Visitors to that website fill out the embedded form, and the submission gets captured, enriched, and stored - visible back in the business owner's dashboard.

Think of it as a tiny, self-hosted version of something like a Mailchimp popup form or Typeform embed, built from scratch to show real backend engineering: auth, multi-tenant data isolation, rate limiting, external API integration with graceful degradation, a safe async-style side effect, cached public delivery, and an aggregation-heavy dashboard API - all backed by hand-written SQL rather than an ORM hiding what's actually happening.

## Architecture

```
Visitor's website (embeds a <script> tag)
        |
        v
GET /widgets/{id}/config, /widgets/{id}/widget.v{n}.js   (public, cached)
        |
        v
Visitor fills out the rendered form
        |
        v
POST /submissions   (public, rate-limited, honeypot, idempotent)
        |
        v
SubmissionService
  1. honeypot + fill-time heuristic check (silently drop obvious bots)
  2. if the widget requires it, verify the solved proof-of-work challenge (silently drop if missing or invalid)
  3. validate fields against the widget's schema
  4. save submission to PostgreSQL (sync)
  5. geo-enrich: provider A -> provider B -> null (best effort)
  6. webhook notify: @Async, retries with backoff, ERROR alert on failure
  7. push a live event to the owner's open dashboard stream, if any
        |
        v
PostgreSQL (owners, widgets, submissions)


Business owner's browser
        |
        v
JWT-authenticated JSON API
  POST/GET/PUT/DELETE /widgets
  GET /dashboard  (aggregate stats via native SQL)
  GET /dashboard/stream  (live submission feed, Server-Sent Events)
        |
        v
PostgreSQL (same database, owner-scoped rows only)
```

Everything a visitor triggers (`POST /submissions`) is public and unauthenticated by design; everything an owner does (create/list/update/delete widgets, view the dashboard) requires a JWT. Widget delivery (`/widgets/{id}/config`, `/widgets/{id}/widget.js`, `/widgets/{id}/widget.v{n}.js`) is public and cached, but never exposes owner-only fields like the webhook URL.

## How it's built

- **Java 21 / Spring Boot 4.1.1** (Spring Framework 7, Hibernate 7)
- **PostgreSQL 16** via Docker Compose, with **Flyway** for versioned, hand-written SQL migrations - no auto-generated schema
- **Spring Security + JWT** (HMAC-SHA256) for stateless auth
- **Bucket4j** for per-IP rate limiting
- Plain **java.net.http.HttpClient** for outbound calls to the geo-lookup providers and webhook deliveries (deliberately not a Spring abstraction, to sidestep framework version churn)
- Server-Sent Events for the live dashboard feed (`GET /dashboard/stream`) - a per-owner in-memory map of open `SseEmitter`s in `SubmissionEventBroadcaster`, pushed to the instant a submission is actually persisted (never for honeypot or fill-time-heuristic drops). No WebSockets dependency needed for a one-directional server-to-client feed; the browser client uses `fetch()` with a manually parsed SSE stream instead of the native `EventSource` API, because `EventSource` can't send the `Authorization: Bearer` header this app's whole auth model depends on.
- Optional proof-of-work bot defense, opt-in per widget (`requireProofOfWork`): a SHA-256 challenge/response solved client-side with the Web Crypto API (`crypto.subtle`) before a protected widget's form will submit. Issued by `GET /submissions/challenge` and verified/consumed exactly once by `PowChallengeService`, so a solved challenge can't be replayed across submissions.
- Widget display targeting, driven entirely by the existing `displayOptions` JSONB field (no schema change needed): `targetPages` (exact paths or `prefix*` wildcards) restricts which customer pages the widget renders on, `delaySeconds` delays rendering after page load, and `oncePerVisitor` skips rendering for a browser that has already seen the widget, tracked in `localStorage`. All three are decided by `maybeRenderWidget` before the existing `renderWidget` ever runs.
- A single static HTML/vanilla-JS dashboard page - no frontend framework, no build step
- Native SQL for every aggregate query in the dashboard (`GROUP BY`, date truncation) rather than pulling rows into Java and counting in memory

## What a widget owner can do

1. Sign up and log in (`/auth/signup`, `/auth/login`) - passwords hashed with BCrypt, sessions are stateless JWTs.
2. Create a widget (`POST /widgets`) with a custom set of fields (name, email, whatever), a button label, optionally a webhook URL to get notified of new submissions, optionally proof-of-work bot protection (`requireProofOfWork`), and optionally display targeting (`displayOptions.targetPages`, `delaySeconds`, `oncePerVisitor`).
3. Copy the embed snippet from the widget's response and paste it into any website. That's a `<script>` tag pointing at `/widgets/{id}/widget.v{version}.js`.
4. Watch submissions roll in live on the dashboard (`/dashboard-ui/index.html`) - total counts, per-widget breakdown, geo distribution by country, and a daily submission count from `GET /dashboard`, plus a live activity feed pushed over Server-Sent Events the instant a new submission is stored, with automatic reconnect if the connection drops.

## What a visitor sees

Nothing branded or platform-specific at all - just a small form rendered inline on the business's own page. The embed script is a single generic script shared by every widget; it fetches that widget's specific field configuration from `/widgets/{id}/config`, builds the form dynamically, and posts the result to `/submissions`. A hidden honeypot field silently drops obvious bot submissions without giving the bot any feedback that it failed.

## Running it locally

You'll need Docker, and a JDK (the project targets Java 21; I built it with JDK 25 using `--release 21`).

```powershell
docker compose up -d
.\mvnw.cmd clean compile
.\mvnw.cmd spring-boot:run
```

The app starts on port 3000 by default (`http://localhost:3000`). Flyway runs the schema migrations automatically on startup - there's no manual database setup step.

Config lives in `src/main/resources/application.properties`, with sane defaults for local development baked in (database credentials, JWT secret, geo provider URLs). See `.env.example` for the full list of variables it reads - set them as real environment variables if you want to override anything; the app does not auto-load a `.env` file (see the note on that below).

### Seeding demo data

Once the app is running, `seed.sh` signs up a demo owner, creates a demo widget, and submits two sample leads against it, so there's real data to look at immediately:

```bash
bash seed.sh
```

It prints the demo owner's email/password, the widget ID, and a ready-to-run `curl` command for `GET /dashboard` so you can see the seeded submissions without doing anything else by hand.

## API surface

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/signup` | none | Create an owner account |
| POST | `/auth/login` | none | Get a JWT |
| POST | `/widgets` | JWT | Create a widget |
| GET | `/widgets` | JWT | List your widgets |
| GET | `/widgets/{id}` | JWT | Get one widget (owner-scoped) |
| PUT | `/widgets/{id}` | JWT | Update a widget |
| DELETE | `/widgets/{id}` | JWT | Delete a widget |
| GET | `/widgets/{id}/config` | none | Public, cached widget config (no secrets) |
| GET | `/widgets/{id}/widget.js` | none | Public, cached embeddable script (unversioned, kept for backward compatibility) |
| GET | `/widgets/{id}/widget.v{version}.js` | none | Public, cached, versioned embeddable script (404 on unknown version) |
| GET | `/submissions/challenge` | none | Issue a proof-of-work challenge (shares the `/submissions` rate-limit bucket) |
| POST | `/submissions` | none | Public submission endpoint (rate-limited, honeypot-protected, supports `Idempotency-Key` header) |
| GET | `/dashboard` | JWT | Aggregate stats for the current owner |
| GET | `/dashboard/stream` | JWT | Live submission feed for the current owner (Server-Sent Events) |

## A few things worth knowing

**The `.env` file isn't actually auto-loaded.** `application.properties` reads from real OS environment variables first and falls back to safe local-dev defaults (`${DB_USERNAME:postgres}` style), which is why nothing has ever broken - but a `.env` file sitting in the project root does nothing on its own. I tried wiring up a library for this (`spring-dotenv`) and found it's not compatible with this Spring Boot version, so I backed it out rather than fight it. `.env.example` documents the variables; set them as real environment variables if you want non-default values.

**Public endpoints never leak owner-only data.** `/widgets/{id}/config`, the only public read of widget data, deliberately excludes the webhook URL and owner ID - those only ever appear in the authenticated `/widgets` responses.

**Nothing external can break a submission.** Geo lookup and webhook delivery both run with short timeouts, catch every exception, and log failures - a submission always succeeds or fails based only on its own validity, never on whether a third-party service happens to be reachable.

## Limitations

Being upfront about what this project doesn't do, since honesty matters more than polish:

- Automated coverage is solid but not total: 41 MockMvc integration tests (`mvn test`) run against a real Postgres instance and cover auth, widget CRUD and tenant isolation, widget delivery and caching, submission validation, idempotency, the honeypot and fill-time heuristic, proof-of-work bot defense, rate limiting, CORS preflight handling, the live dashboard stream, and the geo-provider fallback chain (using a deterministic embedded HTTP stub in place of real network calls). The full webhook retry-with-alert timing proof is still verified manually only (see `EVIDENCE.md`), since automating the exact backoff timing cleanly would add real wall-clock delay to every test run.
- The versioned bundle (`widget.v{version}.js`) is served minified via Terser, using only its default compact printer (no `-c` compress, no `-m` mangle): whitespace, comments, and newlines are stripped, but the code's structure and identifier names (like `renderWidget`) are left untouched, syntax-checked with `node --check` before being embedded (5791 -> 3797 bytes for the current script, which grew once proof-of-work challenge-solving was added). Full compression (`-c`) was tried and rejected: its function-inlining pass rewrote the single-call `renderWidget` function into an anonymous IIFE -- behaviorally identical, but it erases a readable name that's genuinely useful when debugging a live production bundle. The unversioned `widget.js` still serves the original readable source for backward compatibility and debugging. There's still no CDN or build pipeline; both versions are static content embedded directly in the application.
- The dashboard's live feed (`GET /dashboard/stream`, Server-Sent Events) pushes new submissions the instant they're stored, scoped per owner via an in-memory emitter map -- but it's in-memory only, so it doesn't survive an app restart or work across multiple app instances (no message broker or pub/sub), and each open dashboard tab holds its own independent connection with a simple fixed 3-second reconnect on drop rather than true exponential backoff.
- Rate limiting is per-IP only (Bucket4j, in-memory), so it resets if the app restarts and won't help against a distributed botnet - it's meant to stop naive abuse, not a determined attacker.
- Spam protection layers three signals: a honeypot field, a client-reported fill-time heuristic (dropped if completed in under 1.5 seconds), and an opt-in-per-widget proof-of-work challenge (`requireProofOfWork`) -- a SHA-256 puzzle solved in the browser via `crypto.subtle` before the form will submit, verified server-side and single-use. It's opt-in rather than mandatory specifically so it never breaks a widget owner's existing embed; solving it cost a real browser about 198ms of CPU time in testing at the current difficulty (4 leading hex zeros), which is cheap for one legitimate visitor but adds up fast in a scripted attack. `GET /submissions/challenge` deliberately shares the same per-IP rate-limit bucket as `POST /submissions` rather than getting its own bucket, so a PoW-enabled widget's real submissions now cost two rate-limit tokens instead of one -- a conscious trade-off to avoid opening a second unlimited endpoint. There's still no ML-based bot filtering or device fingerprinting.
- Geo enrichment is best-effort: if both providers are down, the submission is still saved with the country left null, exactly as intended, but there's no retry queue for enrichment specifically (only webhook delivery gets retried).
- Display targeting (`displayOptions.targetPages`/`delaySeconds`/`oncePerVisitor`) is real but client-side only: `targetPages` supports exact paths and `prefix*` wildcards (no regex), and `oncePerVisitor` is tracked per-browser via `localStorage`, not a true cross-device visitor identity. The decision logic is proven by an automated, deterministic Node script (`scripts/test-targeting-logic.js`); the live `setTimeout`/`localStorage` behavior in an actual browser was not separately captured as evidence this pass.
- No double opt-in / GDPR consent flow (confirmation flow, consent record, export/delete endpoints) - a stretch goal not attempted here.

## Project docs

- `DESIGN.md` - the original design doc (data model, embed flow, API contracts) written before implementation started
- `EVIDENCE.md` - proof that each requirement in the brief actually works, with real request/response evidence
- `BUILDLOG.md` - an honest log of how this was built, including the AI pair-programming process and the real bugs hit along the way
