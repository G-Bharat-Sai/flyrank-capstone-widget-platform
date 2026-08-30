# EVIDENCE.md
One pasted proof per Requirements checkbox in Section 6 of the capstone brief. All output below is real, captured directly from this system during development (dates/times are as they occurred).
---
## Widget management
### Authenticated CRUD endpoints for widgets; requests without valid auth are rejected.
Unauthenticated request to a protected endpoint:GET /widgets (no Authorization header)
Status: 403
Authenticated request by the widget's actual owner succeeds (see tenant isolation test below): `GET /widgets/{id}` with a valid owner token returns `200`.
### Multi-tenant isolation proven: tenant A cannot read or modify tenant B's widgets or submissions.
Two fresh owner accounts created (Owner A, Owner B). Owner A creates a widget. Owner B then attempts to read, update, and delete it:
Owner A signup -> token acquired: True
Owner B signup -> token acquired: True
Owner A creates widget -> id: 06fe14a4-ffd0-4c13-8bb3-0fc5e7f1e86d
Owner B GET /widgets/06fe14a4-... -> Status: 404
Owner B PUT /widgets/06fe14a4-... -> Status: 404
Owner B DELETE /widgets/06fe14a4-... -> Status: 404
Owner A GET /widgets/06fe14a4-... -> Status: 200 (owner can still read their own widget)
Owner B GET /dashboard -> totalSubmissions: 0 (no cross-tenant data leak in aggregates)
### Embed snippet generated per widget.
Response body from widget creation (Owner A's widget above):
Embed snippet: <script src="http://localhost:3000/widgets/06fe14a4-ffd0-4c13-8bb3-0fc5e7f1e86d/widget.v1.js" data-widget-id="06fe14a4-ffd0-4c13-8bb3-0fc5e7f1e86d"></script>
---
## Widget delivery
### Public config endpoint serves a small payload with correct HTTP cache headers.
GET /widgets/{id}/config
Status: 200
Cache-Control: max-age=300, public
ETag: "2"
Content-Length: 242 bytes
### Widget JavaScript is served as a versioned bundle (new version = new URL or cache-bust).
GET /widgets/{id}/widget.v1.js
Status: 200
ETag: "1"
Cache-Control: max-age=31536000, public
GET /widgets/{id}/widget.v2.js (version that does not exist yet)
Status: 404
GET /widgets/{id}/widget.js (unversioned convenience alias, kept for backward compatibility)
Status: 200
Cache-Control: max-age=86400, public
The URL genuinely changes when the version changes (`widget.v{version}.js`), and a version that hasn't been published 404s rather than silently serving stale or wrong content.
The versioned bundle is also minified: the live source was extracted, whitespace-collapsed (5020 -> 2940 bytes), and syntax-validated with `node --check` before being embedded, while the unversioned `widget.js` still serves the original readable source. Proven by the automated test `versionedWidgetScriptIsMinifiedAndSmallerThanTheUnversionedOne` in `WidgetDeliveryIntegrationTest`, which asserts the versioned response is smaller and contains no newlines.
### The widget renders on a page served from a different origin than your API.
A plain static HTML page ("Acme Bakery"), served via `python -m http.server 8080`, embeds the widget from the API running on port 3000 -- two genuinely different origins. Screenshot confirms the widget rendered (title "Webhook Test Widget", email field, Submit button) and, after submitting, displayed "Thank you!".
Submission count for that widget in the database, before and after the browser submission:
Before: count = 19
After: count = 20
---
## Public submission API
### Cross-origin submissions work: CORS headers correct, preflight (OPTIONS) handled.
OPTIONS /submissions (Origin: http://localhost:5500, Access-Control-Request-Method: POST)
Status: 200
Access-Control-Allow-Origin: http://localhost:5500
Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
POST /submissions (Origin: http://localhost:5500, real cross-origin request)
Status: 201
Access-Control-Allow-Origin: http://localhost:5500
### All incoming input validated; malformed and oversized payloads rejected with appropriate 4xx codes and JSON errors.
Missing required field:
POST /submissions (widgetId omitted)
Status: 400
{"error":"Validation failed","fields":{"widgetId":"must not be null"},"timestamp":"2026-08-28T20:24:30.703530600-05:00","status":400}
Broken JSON syntax:
POST /submissions (body: "{ this is not valid json")
Status: 400
{"error":"Malformed JSON request body","timestamp":"2026-08-28T20:32:23.445646700-05:00","status":400}
(Bug found and fixed during hardening: this previously returned a raw 500 because `GlobalExceptionHandler` had no specific handler for `HttpMessageNotReadableException` and fell through to the catch-all. See BUILDLOG.md.)
Invalid path parameter (malformed UUID):
GET /widgets/not-a-valid-uuid/config
Status: 400
{"error":"Invalid value for parameter 'id'","timestamp":"2026-08-28T20:32:23.487702-05:00","status":400}
Oversized single field (10,000 characters, cap is 5,000):
Status: 400
{"error":"Field 'oversized_field' exceeds maximum length of 5000 characters","timestamp":"2026-08-28T19:36:56.891684600-05:00","status":400}
Oversized aggregate payload (113,687 bytes total, cap is 100,000):
Status: 400
{"error":"Submission payload exceeds maximum size of 100000 bytes","timestamp":"2026-08-28T19:36:56.965357900-05:00","status":400}
### Valid submissions stored safely, linked to the right widget and tenant.
POST /submissions (valid payload)
Status: 201
{"id":"c7a3aa67-15c8-4049-a3fe-44d838aab0a9","widgetId":"a4681b47-3f7e-4290-ac87-1bc808fb3bec","createdAt":"2026-08-28T19:36:06.187397-05:00"}
Returned `widgetId` matches the submitted widget; `ownerId` on the stored row is derived server-side from the widget, never from client input (see `SubmissionService.submit`), which is what tenant isolation on submissions relies on.
---
## Abuse protection
### Rate limiting per IP and/or per widget returns 429 under a burst -- and the API keeps serving legitimate traffic.
8 rapid requests from one spoofed IP against a bucket of capacity 5:
Request 1 -> Status: 201
Request 2 -> Status: 201
Request 3 -> Status: 201
Request 4 -> Status: 201
Request 5 -> Status: 201
Request 6 -> Status: 429
Request 7 -> Status: 429
Request 8 -> Status: 429
Immediately afterward, a request from a *different* IP succeeds normally:
Status: 201
### At least one spam-prevention technique (honeypot field, token, or heuristic) demonstrably blocks a spam submission.
POST /submissions (honeypot field filled in, as a bot would)
Status: 201
{"status":"received"}
The response looks successful to the bot (so it doesn't learn to avoid the honeypot), but the submission was never actually stored:
SELECT count(*) FROM submissions WHERE payload::text LIKE '%spambot@example.com%';
count
0
A second, independent signal was added later: the embedded widget script records when the form actually rendered and reports it back on submit. A submission completed in under 1.5 seconds is treated the same way as a honeypot hit -- silently dropped, never stored. Proven by the automated tests `submissionSubmittedTooQuicklyAfterFormRenderIsSilentlyDropped` (dropped) and `submissionSubmittedAfterAReasonableFillTimeIsStored` (stored normally) in `SubmissionControllerIntegrationTest`, both passing as part of the 32-test `mvn test` run.
---
## Enrichment & safe side effects
### IP->geo enrichment uses a provider fallback chain: provider A down -> provider B answers -> submission enriched.
Provider A deliberately pointed at an unreachable address (`http://127.0.0.1:1/broken-provider-a`), provider B left on its real default. Submission from a real public IP (8.8.8.8):
SELECT id, ip_address, geo_country, geo_city, geo_provider_used FROM submissions WHERE id = 'b5b9f96c-afa7-4cc7-bf80-3a59e88e0ee4';
ip_address | geo_country | geo_city | geo_provider_used
8.8.8.8 | United States | Mountain View | ipapi.co
`geo_provider_used = ipapi.co` (provider B) proves B specifically covered for A's failure -- this can only happen if A was attempted and failed, since A's URL was unreachable by construction.
Reverted afterward and re-tested with both providers healthy: `geo_provider_used = ip-api` (provider A succeeds on its own), confirming the revert was clean.
### All providers down -> submission still succeeds (without geo). Degrade, never fail.
Both provider URLs deliberately broken:
POST /submissions
Status: 201
{"id":"29806335-11fa-4cd3-a2cf-02113aa31f20", ...}
SELECT id, geo_country, geo_city, geo_provider_used FROM submissions WHERE id = '29806335-11fa-4cd3-a2cf-02113aa31f20';
geo_country | geo_city | geo_provider_used
(all empty/NULL)
Submission succeeded and was stored despite total enrichment failure.
### A failing confirmation email / webhook does not prevent the submission from being stored.
Widget's `webhook_url` pointed at `http://127.0.0.1:1/unreachable` (guaranteed connection-refused). Submission still succeeds immediately:
POST /submissions
Status: 201
{"id":"a24f30ab-8680-479f-89ae-62933f27cc7c", ...}
Request completed in: 1803.8066 ms (dominated by synchronous geo lookup, not the webhook)
Webhook delivery runs as a background job (separate thread `webhook-async-1`) after the response was already sent, retries 3 times with increasing backoff, then logs a distinct failure alert:
19:45:45.128 WARN [webhook-async-1] Webhook attempt 1/3 failed ... ConnectException
19:45:46.135 WARN [webhook-async-1] Webhook attempt 2/3 failed ... ConnectException (1007ms later)
19:45:48.141 WARN [webhook-async-1] Webhook attempt 3/3 failed ... ConnectException (2006ms later)
19:45:48.141 ERROR [webhook-async-1] ALERT: webhook delivery permanently failed after 3 attempts for submission a24f30ab-...
---
## Stretch goal: real-time dashboard (Server-Sent Events)
### New submissions appear on the owner's dashboard live, without polling or a page refresh.
`GET /dashboard/stream` returns a Server-Sent Events stream, authenticated the same way as every other owner-only endpoint (`Authorization: Bearer <jwt>`) and scoped per owner via an in-memory map of open emitters in `SubmissionEventBroadcaster`. `SubmissionService` pushes to that owner's open connections immediately after a submission is actually persisted -- never for honeypot or fill-time-heuristic drops, and never for an idempotent duplicate that just returns the original record.
Proven by two automated tests in `DashboardStreamIntegrationTest`:
- `streamingWithoutAuthIsRejected` -- confirms the stream endpoint is owner-authenticated like the rest of `/dashboard`, not a hole in the auth model.
- `ownerReceivesLiveEventWhenANewSubmissionArrivesForTheirWidget` -- opens the stream as an authenticated owner, submits a real lead against that owner's widget, and asserts the emitted SSE bytes actually contain the new submission's widget ID, proving the push genuinely happens rather than just that the endpoint returns 200.
Both pass as part of the 35-test `mvn test` run.
One deliberate design choice: the browser's native `EventSource` API cannot send custom headers, and this app's entire auth model is `Authorization: Bearer <jwt>` -- there is no session cookie to fall back on. Rather than weaken auth by passing the token in a query string (which would leak it into server logs and browser history), the dashboard UI (`dashboard-ui/index.html`) opens the stream with `fetch()`, like every other authenticated call it makes, and manually parses the SSE wire format out of the streamed response body. The trade-off is losing `EventSource`'s built-in auto-reconnect, which is why the client implements its own 3-second reconnect loop instead.
---
## Documentation
### README with architecture diagram, setup instructions, and API documentation; the required files from Section 11 present.
See README.md, capstone.yaml, BUILDLOG.md, and .env.example in the repository root.
---
## Shared requirements (every capstone must show these)
- **Layered architecture** -- controller / service / repository separation throughout (see `controller/`, `service/`, `repository/` packages).
- **Validation at the boundary** -- see the malformed/oversized payload evidence above; every input path validated before touching business logic, no 500s on bad input.
- **>=1 background job, retries + failure alert** -- webhook delivery, see evidence above (`@Async`, 3 retries with backoff, `ERROR ... ALERT` on exhaustion).
- **Real persistence** -- PostgreSQL via Flyway migrations (`V1`-`V3`), indexed foreign keys, tenant-scoped queries throughout.
- **Idempotency where it matters** -- submission endpoint accepts an `Idempotency-Key` header; a repeated key returns the original submission instead of creating a duplicate:
Request 1 (key=test-idem-key-001) -> id: 34f7e620-1a4a-4e7e-b9c2-7c32cd46257b
Request 2 (same key) -> id: 34f7e620-1a4a-4e7e-b9c2-7c32cd46257b (identical)
Request 3 (different key) -> id: 1a0ca4a3-df37-4c6a-9439-646c5ce198de (new, distinct)
SELECT id, idempotency_key FROM submissions WHERE idempotency_key IN (...);
-- exactly 2 rows, not 3
- **Secrets clean** -- all secrets loaded via `${VAR:default}` from environment/`.env` (gitignored); `.env.example` ships with safe placeholders; nothing logged.
- **Cost tracked, if AI is used** -- not applicable; no paid AI APIs are called by the running application (AI was used only as a development aid, see BUILDLOG.md).
