# Design - FlyRank Capstone: Embeddable Widget & Lead-Capture Platform

## Data model

### owners
- id (UUID, PK)
- email (unique, not null)
- password_hash (not null)
- created_at (timestamptz, default now())

### widgets
- id (UUID, PK)
- owner_id (UUID, FK -> owners.id, not null, indexed)
- type (varchar: signup | contact | popover, not null)
- title (varchar, not null)
- description (text, nullable)
- fields (jsonb, not null) -- array of {name, label, type, required}
- button_text (varchar, default Submit)
- display_options (jsonb, nullable) -- colors, position, delay, etc.
- version (int, default 1) -- bumped on material config change, used to cache-bust the config endpoint
- created_at, updated_at (timestamptz)
- Index: (owner_id)

### submissions
- id (UUID, PK)
- widget_id (UUID, FK -> widgets.id, not null, indexed)
- owner_id (UUID, denormalized from widget, indexed) -- avoids a join for dashboard aggregation queries
- payload (jsonb, not null) -- the validated field values submitted
- ip_address (varchar, not null)
- geo_country (varchar, nullable)
- geo_city (varchar, nullable)
- geo_provider_used (varchar, nullable) -- provider_a | provider_b | null if both failed
- created_at (timestamptz, default now(), indexed)
- Index: (widget_id), (owner_id, created_at) -- powers "counts over time" and "per-widget stats"

## Embed flow

1. Owner creates a widget via the authenticated Widget Management API.
2. API returns an embed snippet pointing at our widget.js endpoint for that widget id.
3. Customer pastes that script tag into their own site (a different origin than our API).
4. On page load, widget.js fetches GET /widgets/{id}/config (public, CORS-enabled, cached) and renders a form into the page.
5. Visitor submits the form. The script POSTs to /submissions (public, CORS-enabled) with widgetId, field values, and a honeypot field.
6. Server validates, rate-limits, checks the honeypot, stores the row, enriches with geo data (best-effort, fallback chain), fires a side effect (best-effort), and responds 201.
7. Owner views submissions and stats on the authenticated Dashboard API.

## API contracts (high level)

### Path 1 - Widget owner (authenticated, JWT Bearer token)
- POST /auth/signup, POST /auth/login -> returns JWT
- POST /widgets, GET /widgets, GET /widgets/{id}, PUT /widgets/{id}, DELETE /widgets/{id}
- GET /dashboard/widgets/{id}/stats
- GET /dashboard/summary

### Path 2 - Customer website (public, cached, CORS allowed for GET)
- GET /widgets/{id}/widget.js -> versioned JS bundle, long cache, cache-busted by version
- GET /widgets/{id}/config -> JSON config, short cache (about 60s)

### Path 3 - Website visitor (public, CORS allowed, POST only)
- POST /submissions -> validates, rate-limits, spam-checks, stores, enriches, side-effects; returns 201 or a clean 4xx, never a 500

## Non-goal

This capstone will not build a real-time dashboard (no WebSockets or Server-Sent Events for live-updating submissions). The dashboard is a polling/refresh-based read of stored data via REST endpoints only. Real-time updates are explicitly a stretch goal and are out of scope for the core build.
