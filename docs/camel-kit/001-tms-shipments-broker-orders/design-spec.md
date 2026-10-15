---
staleness:
  stale: false
  since: null
  reason: null
generated:
  at: "2026-09-25T15:40:38.007234Z"
  by: "camel-brainstorm"
---
# TMS Shipments to Broker Orders — Design Spec

**Date:** 2026-09-25
**Camel Version:** 4.22.1
**Runtime:** Main
**Platform BOM Version:** 4.22.1 (`org.apache.camel:camel-catalog:4.22.1`)
**Pipeline:** `001-tms-shipments-broker-orders`

## Not Doing (and Why)

- **Containerisation (Dockerfile + Docker Compose)** — The sandbox already runs on `localhost:8000` and the integration runs via Camel JBang, so packaging adds an image build to every change without improving the feedback loop. Recorded as the agreed follow-up.
- **Propagating `order.eta` to the TMS** — `CreateTmsShipmentEvent` has no field capable of carrying it, so an `ETA_EVENT → ETA_CHANGED` necessarily arrives without the new ETA. Mapping around this would require a TMS schema change.
- **Metrics and health endpoints** — Structured logging at decision points was chosen instead; no dashboards are needed to validate a sandbox integration, and Micrometer would add dependencies and a scrape target to run.
- **Durable store for the rejected-shipment set** — Persistence was deliberately traded away: a restart re-logs each poison shipment exactly once, which is acceptable, and the alternative is a database purely for suppression bookkeeping.
- **Circuit breaker on outbound calls** — Flow 1 already degrades safely (an unsent shipment stays `new` and is retried next poll), so fail-fast behaviour would add resilience4j configuration without protecting anything that is currently at risk.
- **OAuth2 validation on our inbound listener** — The sandbox authenticates its push with an `X-API-KEY` header, so `platform-http`'s `oauthProfile` stays unset; supporting both would mean maintaining two inbound auth paths.
- **Tendering shipment updates and cancellations** — Only `messageFunction: 9` (NEW) is produced. The TMS exposes no update or cancel trigger for an already-tendered shipment, so `4` (UPDATE) and `1` (CANCEL) have no source event to carry them.
- **Deduplication and resequencing in Flow 2** — The broker-to-TMS spec states only one occurrence per event type per shipment is allowed and that out-of-order arrival is tolerated, so the TMS overwrites by design and ordering logic would be dead code.

---

## 1. Executive Summary

### Business Purpose

Outbound shipments planned in the TMS are today re-typed by hand into the broker's portal. This integration tenders each new TMS shipment to the broker automatically as a broker order, and feeds the broker's tracking events back into the TMS as shipment events — closing the loop without manual data entry.

### Business Value

- Eliminates manual re-keying of shipment data into the broker portal, and the transcription errors that come with it
- Removes the delay between a shipment being planned and being tendered
- Returns broker status back into the TMS automatically, so shipment state is visible where planners already work

### Stakeholders

- Everyone in the dispatch-to-tender process — planners and dispatchers who currently do the typing, and downstream consumers of shipment status in the TMS

---

## 2. Systems Landscape

| System | Type | Protocol | Role |
|--------|------|----------|------|
| TMS (Integration Sandbox, `/api/v1/tms/*`) | Transport Management System | REST / JSON, OAuth2 client credentials | Source (shipments) and Target (events) |
| Broker (Integration Sandbox, `/api/v1/broker/*`) | Visibility / broker platform | REST / JSON, OAuth2 client credentials | Target (orders) and Source (events, pushed) |
| Integration Sandbox trigger (`/api/v1/trigger/*`) | Test data generator | REST / JSON, pushes to `target_url` with `X-API-KEY` | Driver for Flow 2 |

Both systems are served by one sandbox process (`Integration Sandbox v1.0.5`) at `http://localhost:8000`. The OpenAPI document declares no `servers` block, so the base URL is configuration, not a spec-derived value.

---

## 3. Flow Designs

### Flow: tms-shipment-to-broker-order

| Field | Value |
|---|---|
| Target Module | _(project root — single-project setup)_ |

**Purpose:** Poll the TMS for new shipments and tender each one to the broker as a broker order.

**Source:**

- System: TMS (Integration Sandbox)
- Component: `timer` (MCP-verified) driving `http` (MCP-verified)
- **Rationale:** `timer` was chosen over `scheduler` and over a polling consumer because the source is a plain request/response REST endpoint with no Camel polling consumer of its own; `timer` plus an `http` producer is the idiomatic pairing and keeps the poll interval a single externalised property. `http` was chosen over `vertx-http` and `netty-http` because it is the only one of the three exposing native OAuth2 client-credentials options (`oauth2ClientId`, `oauth2ClientSecret`, `oauth2TokenEndpoint`, `oauth2CacheTokens`), which removes an entire hand-written token-management route.
- **Constraints:** Polling must not overlap — a shipment clears from `/new` only once the broker validates it, so a second concurrent poll would re-read and re-tender shipments still in flight. `timer` is single-threaded per endpoint and the route runs synchronously to completion, which satisfies this by construction. The sandbox returns no `expires_in`, so token caching relies on `oauth2CachedTokensDefaultExpirySeconds`.
- Trigger: `timer:shipment-poll?period={{tms.poll.period}}` — every 60 000 ms
- Endpoint options: `period` (duration, default 1000 → set to 60000); `oauth2ClientId`, `oauth2ClientSecret`, `oauth2TokenEndpoint`, `oauth2BodyAuthentication=true`, `oauth2CacheTokens=true`
- Request: `GET {{sandbox.base.url}}/api/v1/tms/shipments/new?limit={{tms.poll.limit}}` → `TmsShipment[]`

**Transformations:**

1. Unmarshal the JSON array
   - Dataformat: `jackson` (MCP-verified)
   - **Rationale:** Chosen over `gson` and `johnzon` because it is the Camel default JSON binding, and `useList=true` maps the response array directly to a `List<Map>` that the splitter consumes without an intermediate type.
   - **Constraints:** The response is `TmsShipment[] | null` — a null body must be treated as an empty poll, not an error.
   - Details: `unmarshal: { json: { library: Jackson, useList: true } }`

2. Split the batch into one exchange per shipment
   - EIP: `split` (MCP-verified)
   - **Rationale:** Chosen over `loop` because the collection size is unknown per poll and `split` yields a per-shipment unit of work — so one poison shipment is dead-lettered on its own without aborting the batch.
   - **Constraints:** `stopOnException` must stay at its default `false`; a 422 on shipment 3 must not prevent shipments 4 and 5 from being tendered. `streaming` stays `false` — batches are bounded at 50.
   - Details: `split: { simple: "${body}" }`

3. Skip shipments already rejected by the broker
   - EIP: `filter` (MCP-verified)
   - **Rationale:** Chosen over `idempotentConsumer` because the suppression is keyed on *failures*, not successes. A successful tender needs no record — the sandbox clears it from `/new` — so an idempotent repository would accumulate keys that can never be consulted again. `filter` against a small rejected-id set expresses exactly the intent.
   - **Constraints:** The set is in-memory and rebuilt on restart, so each poison shipment is re-logged once per process lifetime. Accepted during discovery.
   - Details: `filter: { method: { beanType: RejectedShipments, method: isNotRejected } }`

4. Map `TmsShipment` → `CreateBrokerOrderMessage`
   - Language: `groovy` (MCP-verified), invoked as a bean
   - **Rationale:** Chosen over inline `jq` and over `datasonnet` because constitution rule 3 requires business logic in beans, and this mapping is business logic: it expands `total_packages` into repeated handling units, sums a weighted total, and applies an 8-value enum table. A 35-field mapping inline in the route YAML would be unreviewable. Groovy over a Java bean because it needs no POJO generation step and the toolkit already uses Groovy as a DataMapper engine.
   - **Constraints:** Dynamically typed — schema drift surfaces only in tests, which is why the documented example payloads become the mapping unit test.
   - Details: see **Mapping** below

**Mapping — `TmsShipment` → `CreateBrokerOrderMessage`**

Source of truth: `docs/integrations/tms-to-broker.md` in `atetz/integration-sandbox`.

| Target | Source | Rule |
|---|---|---|
| `meta.senderId` | _constant_ | `{{broker.sender.id}}` — fixed organisation id (`KITT`), not per customer |
| `meta.messageDate` | _generated_ | Transmission timestamp, ISO-8601 UTC with millis |
| `meta.messageReference` | `id` | Shipment id |
| `meta.messageFunction` | _constant_ | `9` (NEW) |
| `shipment.reference` | `id` | |
| `shipment.carrier` | `customer.carrier` | |
| `shipment.transportMode` | _constant_ | `"ROAD"` |
| `orders[0].reference` | `id` | One order per shipment |
| `orders[0].goodsDescription` | `line_items[].description` | Joined with `\|`, document order |
| `orders[0].quantity.grossWeight` | `line_items[]` | `SUM(package_weight × total_packages)` |
| `orders[0].quantity.loadingMeters` | `loading_meters` | |
| `orders[0].pickUp.*` | `stops[type=PICKUP].location.*` | `code→identification`, `address.address→address1`, `address.postal_code→postalCode`, `name`/`city`/`country`/`latitude`/`longitude` direct |
| `orders[0].consignee.*` | `stops[type=DELIVERY].location.*` | Same shape as `pickUp` |
| `…dates[]` | `planned_date` + time window | `planned_date` + `T` + `planned_time_window_start` + `Z` → `PERIOD_EARLIEST`; `…_end` → `PERIOD_LATEST` |
| `orders[0].handlingUnits[]` | `line_items[]` | **One handling unit per package** — each line item repeated `total_packages` times |
| `…handlingUnits[].grossWeight` | `line_items[].package_weight` | Per unit, not multiplied |
| `…handlingUnits[].width/length/height` | `line_items[].width/length/height` | Direct, CM |
| `address2`, `instructions` | — | Always `null` |

Package type enum: `BALE→BL`, `BOX→BX`, `COIL→CL`, `CRATE→CR`, `CYLINDER→CY`, `DRUM→DR`, `OTHER→OT`, `PLT→PL` (complete, 8 of 8).

Verified against the documented example: 3 line items with `total_packages` 4/1/3 produce **8 handling units**, and `7069.62×4 + 2336.24×1 + 890.43×3 = 33286.01` matches the documented `quantity.grossWeight` exactly.

**Sink:**

- System: Broker (Integration Sandbox)
- Component: `http` (MCP-verified)
- **Rationale:** Same OAuth2 reasoning as the source — one component and one credential configuration for every sandbox call.
- **Constraints:** Returns `202 Accepted`, not `200`; success handling must not assume 200. A 422 means the payload was rejected and is not retryable.
- Action: `POST {{sandbox.base.url}}/api/v1/broker/order` with `CreateBrokerOrderMessage`
- Endpoint options: `httpMethod=POST`, OAuth2 options as above

**Error Handling:**

- Strategy: Split by failure class
- `HttpOperationFailedException` with status 422 → `maximumRedeliveries: 0`, record the shipment id in the rejected set, dead-letter once with payload and reason
- Transient (5xx, `IOException`, timeout) → `maximumRedeliveries: 3`, `redeliveryDelay: 1000`, `backOffMultiplier: 2`, `useExponentialBackOff: true`, then dead-letter. The shipment remains `new`, so the next poll retries it regardless — redelivery only shortens the gap.
- Dead Letter: `file:{{dlq.directory}}/tms-shipment-to-broker-order`
- Circuit breaker: no — see Not Doing

**Configuration Properties:**

```properties
# tms-shipment-to-broker-order
tms.poll.period=60000
tms.poll.limit=50
broker.sender.id=KITT
broker.message.function.new=9
```

---

### Flow: broker-event-to-tms-event

| Field | Value |
|---|---|
| Target Module | _(project root — single-project setup)_ |

**Purpose:** Receive broker tracking events pushed by the sandbox and record them against the matching shipment in the TMS.

**Source:**

- System: Broker (via the sandbox `/api/v1/trigger/events` push)
- Component: `platform-http` (MCP-verified)
- **Rationale:** Chosen over `netty-http`, `jetty` and `undertow` because it binds to whichever HTTP server the runtime provides rather than embedding a specific one, keeping the route portable if this later moves to Spring Boot or Quarkus. The sandbox only needs a reachable `target_url`, so no server-specific feature is required.
- **Constraints:** Consumer-only, and for `runtime=main` it requires a server provider on the classpath (`camel-platform-http-main`). Must not bind port 8000, which the sandbox occupies.
- Trigger: `POST http://localhost:{{inbound.http.port}}{{inbound.http.path}}`, registered as `target_url` when calling `/api/v1/trigger/events`
- Endpoint options: `path` (required) = `{{inbound.http.path}}`, `httpMethodRestrict=POST`, `consumes=application/json`

**Transformations:**

1. Authenticate the push
   - EIP: `choice` (MCP-verified)
   - **Rationale:** The sandbox authenticates its push with an `X-API-KEY` header, and rejection must happen before anything is enqueued — a request that fails authentication is not a failed event and must not reach the DLQ. `choice` gives the explicit reject branch that `filter` (which silently drops) cannot.
   - **Constraints:** Compare in length-constant fashion rather than with `==`. The header must be masked in all logging so the key never reaches stdout. Mismatch or absence → HTTP 401, no enqueue.
   - Details: `when` header `X-API-KEY` equals `{{sandbox.inbound.api.key}}` → continue; `otherwise` → set `CamelHttpResponseCode: 401` and stop

2. Acknowledge and hand off asynchronously
   - Component: `seda` (MCP-verified)
   - **Rationale:** Required by the chosen accept-fast behaviour: the push returns 202 as soon as the payload is accepted, so the sandbox is never held open for the TMS round trip. `seda` over `direct` because `direct` is synchronous and would defeat exactly that. Constitution rule 3 nominates `seda` for asynchronous internal routing.
   - **Constraints:** In-memory queue — events accepted but not yet delivered are lost if the process dies. Bounded by design; the sandbox never re-sends, so the DLQ is the only durable record of a delivery failure.
   - Details: `seda:broker-events`, then set `CamelHttpResponseCode: 202`

3. Split the pushed batch
   - EIP: `split` (MCP-verified)
   - **Rationale:** `/trigger/events` accepts up to 1000 `shipment_ids`, so the push is expected to carry many events; splitting gives per-event error isolation and a per-event DLQ entry.
   - **Constraints:** Must tolerate a single JSON object as well as an array — see open question OQ-1.

4. Map `BrokerEventMessage` → `CreateTmsShipmentEvent`
   - Language: `groovy` (MCP-verified), invoked as a bean
   - **Rationale:** Consistent with Flow 1 and with constitution rule 3; the event-type table and the conditional location block are business rules.
   - **Constraints:** `location` is present only for the four location-based event types.

**Mapping — `BrokerEventMessage` → `CreateTmsShipmentEvent`**

Source of truth: `docs/integrations/broker-to-tms.md` in `atetz/integration-sandbox`.

| Target | Source | Rule |
|---|---|---|
| _path param_ `shipment_id` | `shipmentId` | Selects the TMS shipment |
| `created_at` | `situation.registrationDate` | |
| `occured_at` | `situation.actualDate` | |
| `event_type` | `situation.event` | Enum table below |
| `external_order_reference` | `order.reference` | |
| `source` | _constant_ | `"broker"` |
| `location.code` | `situation.position.locationReference` | Only when `position` is present |
| `location.latitude` / `longitude` | `situation.position.latitude` / `longitude` | Only when `position` is present |

Event type enum (complete, 6 of 6):

| Broker | TMS | Location |
|---|---|---|
| `ORDER_CREATED` | `BOOKED` | none |
| `CANCEL_ORDER` | `CANCELLED` | none |
| `DRIVING_TO_LOAD` | `DISPATCHED` | pickup |
| `ORDER_LOADED` | `PICKED_UP` | pickup |
| `ETA_EVENT` | `ETA_CHANGED` | delivery |
| `ORDER_DELIVERED` | `DELIVERED` | delivery |

`ORDER_CREATED → BOOKED` is what clears a tendered shipment's `new` state in the TMS, closing the loop opened by Flow 1. Flow 2 is the only writer of TMS events.

**Sink:**

- System: TMS (Integration Sandbox)
- Component: `http` (MCP-verified), addressed via `toD` (MCP-verified)
- **Rationale:** As Flow 1 — shared OAuth2 configuration across every sandbox call. `toD` rather than `to` because the shipment id is a path segment that varies per message, which a static endpoint URI cannot express.
- **Constraints:** Returns `202`. **The URI is built from `shipmentId`, which arrives in an externally pushed payload** — an unvalidated value interpolated into a dynamic endpoint is a path-injection risk. Two mitigations are required, not optional: validate `shipmentId` against a strict UUID pattern before it reaches `toD` and reject non-conforming events to the DLQ, and set `toD` `allowedSchemes=http,https` as defence in depth.
- Action: `POST {{sandbox.base.url}}/api/v1/tms/event/${shipmentId}` with `CreateTmsShipmentEvent`

**Error Handling:**

- Strategy: Retry then DLQ. The sandbox never re-sends a push, so a dropped event is unrecoverable — the DLQ is the only replay source.
- `maximumRedeliveries: 3`, `redeliveryDelay: 1000`, `backOffMultiplier: 2`, `useExponentialBackOff: true`
- Dead Letter: `file:{{dlq.directory}}/broker-event-to-tms-event`
- Failed authentication is **not** dead-lettered — it returns 401 and is discarded
- Circuit breaker: no — see Not Doing

**Configuration Properties:**

```properties
# broker-event-to-tms-event
inbound.http.port=8080
inbound.http.path=/events
# sandbox.inbound.api.key is environment-bound — see Shared Configuration
```

---

### Shared Configuration

```properties
# sandbox connection (both flows)
sandbox.base.url=http://localhost:8000
sandbox.token.endpoint=http://localhost:8000/token
sandbox.oauth2.cache.tokens=true
sandbox.oauth2.body.authentication=true

# secrets — resolved from the environment, never stored here
sandbox.client.id={{env:SANDBOX_CLIENT_ID}}
sandbox.client.secret={{env:SANDBOX_CLIENT_SECRET}}
sandbox.inbound.api.key={{env:SANDBOX_INBOUND_API_KEY}}

# error handling (both flows)
error.maximum.redeliveries=3
error.redelivery.delay=1000
error.backoff.multiplier=2
dlq.directory=data/dlq
```

Route YAML references every property as `{{property-name}}`, including the three secrets — the routes are unaware of where a value came from, so nothing in a route file changes if the delivery mechanism does.

**No secret has a value in `application.properties`.** Each is declared as `{{env:VAR}}`, which the properties component resolves from the OS environment (nested placeholders are enabled by default in 4.22.1). This is deliberate rather than decorative:

- A file containing `sandbox.client.secret=CHANGEME` invites someone to fill it in and commit it. A file containing `{{env:SANDBOX_CLIENT_SECRET}}` has nowhere to put a secret in the first place.
- An unset variable fails at **startup** with an unresolved-placeholder error, rather than surfacing as a confusing 401 on the first poll a minute later.
- Camel's `environment-variable-mode` defaults to `2` (override), so an environment variable would win over a file value anyway — making the file value pure risk with no benefit.

Required at runtime: `SANDBOX_CLIENT_ID`, `SANDBOX_CLIENT_SECRET`, `SANDBOX_INBOUND_API_KEY`.

`broker.sender.id` is deliberately *not* environment-bound — it is an organisation identifier, not a credential, and belongs in configuration where it can be reviewed. Its value is `KITT`, the sending organisation's id.

---

## 4. Cross-Cutting Concerns

### Performance

No throughput or latency targets were set. Flow 1 polls once per 60 s with `limit=50`; Flow 2 is push-driven and bounded by the trigger's 1000-id maximum. Deployment is local (Camel JBang), so no scaling design is required.

### Security

- **Outbound:** OAuth2 client credentials against `POST /token`. Credentials travel in the form body (`client_id`/`client_secret` are body fields), so `oauth2BodyAuthentication=true`. Token caching is enabled explicitly — `oauth2CacheTokens` defaults to `false`, and the `Token` response omits `expires_in`, so cache lifetime falls back to `oauth2CachedTokensDefaultExpirySeconds` (3600 s).
- **Inbound:** Every push to Flow 2 is authenticated by verifying the `X-API-KEY` header before any processing. Mismatch or absence → HTTP 401 with no enqueue and no DLQ entry. Comparison is length-constant; the header is masked in logs.
- **Dynamic endpoint hardening:** Flow 2's sink URI is computed from `shipmentId`, a field supplied by an external caller. It is validated against a strict UUID pattern before interpolation, and `toD` is restricted with `allowedSchemes=http,https`. Without both, a crafted `shipmentId` could redirect or reshape the outbound request.
- **Credentials:** The OAuth2 client id and secret and the inbound API key are resolved from the OS environment via `{{env:VAR}}` and have no value in any file in the repository. They are never logged, and a missing variable fails startup rather than degrading to a runtime 401. No PII and no compliance standard was identified — shipment and location data only.

### Dead Letter Channel

Both flows dead-letter to `file` (MCP-verified) under `{{dlq.directory}}`. Chosen over a broker queue because no messaging infrastructure exists in this deployment and the local filesystem gives a durable, inspectable record with zero setup; the failed payload and its reason can be read directly and replayed by hand. If this integration later moves off a single local process, the DLQ is the first thing that must change.

### Monitoring

Structured logging at decision points per constitution rule 5: shipments polled per cycle, each tender and its status, each 422 suppression, each event mapped and posted, and every dead-letter with its reason. Correlation via shipment id. Every route carries `routeId` and `description`. No metrics or health endpoints — see Not Doing.

### Constraints

Standard Apache Camel practice; no additional constraints were identified. YAML DSL on `runtime=main`, with Groovy beans for the two mappings.

---

## 5. Constitution Compliance

All flows in this spec are designed to comply with the 8 constitution rules:

- [x] **Route Structure** — every externally-facing route has a source and a final sink; `direct:`/`seda:` sub-routes are exempt
- [x] **Single Responsibility** — each route states one purpose in one sentence; Flow 1 and Flow 2 are each split into ingestion and delivery routes
- [x] **Separation of Concerns** — Ingestion → Processing → Delivery, with `seda:` for the asynchronous handoff in Flow 2 and mapping logic held in Groovy beans
- [x] **Naming Conventions** — route IDs follow `<domain>-<action>`: `shipment-ingestion`, `shipment-tender`, `broker-event-ingestion`, `broker-event-delivery`
- [x] **Observability** — `routeId` and `description` on every route; logging at decision points; shipment id as correlation key
- [x] **External Configuration** — no hardcoded URLs or credentials; every environment value is a `{{placeholder}}`
- [x] **Component Support** — all components, EIPs, dataformats and languages MCP-verified below
- [x] **Infrastructure via Forage** — no infrastructure beans required; the two Groovy mapping beans are application logic, not infrastructure, and have no Forage equivalent

### Catalog Verification Evidence

Catalog binding: camel_catalog_components(limit=0); runtime=main; platformBom=org.apache.camel:camel-catalog:4.22.1; returned camelVersion=4.22.1; binding=MATCHED

| Artifact identity | Result | Verification provenance |
|---|---|---|
| component:timer | VERIFIED | camel_catalog_component_doc, camel_catalog_component_maven; requested component:timer; catalog binding=MATCHED |
| component:http | VERIFIED | camel_catalog_component_doc, camel_catalog_component_maven; requested component:http; catalog binding=MATCHED |
| component:platform-http | VERIFIED | camel_catalog_component_doc, camel_catalog_component_maven; requested component:platform-http; catalog binding=MATCHED |
| component:seda | VERIFIED | camel_catalog_component_doc, camel_catalog_component_maven; requested component:seda; catalog binding=MATCHED |
| component:file | VERIFIED | camel_catalog_component_doc, camel_catalog_component_maven; requested component:file; catalog binding=MATCHED |
| eip:split | VERIFIED | camel_catalog_eip_doc; requested eip:split; catalog binding=MATCHED |
| eip:choice | VERIFIED | camel_catalog_eip_doc; requested eip:choice; catalog binding=MATCHED |
| eip:filter | VERIFIED | camel_catalog_eip_doc; requested eip:filter; catalog binding=MATCHED |
| eip:toD | VERIFIED | camel_catalog_eip_doc; requested eip:toD; catalog binding=MATCHED |
| dataformat:jackson | VERIFIED | camel_catalog_dataformat_doc; requested dataformat:jackson; catalog binding=MATCHED |
| language:groovy | VERIFIED | camel_catalog_language_doc; requested language:groovy; catalog binding=MATCHED |
| language:simple | VERIFIED | camel_catalog_language_doc; requested language:simple; catalog binding=MATCHED |

Maven coordinates consumed: `org.apache.camel:camel-timer:4.22.1`, `camel-http:4.22.1`, `camel-platform-http:4.22.1`, `camel-seda:4.22.1`, `camel-file:4.22.1`, `camel-jackson:4.22.1`, `camel-groovy:4.22.1`, `camel-core-languages:4.22.1`.

**Catalog note:** the `http` component's `oauth2TokenEndpoint` and `oauth2ResourceIndicator` options carry each other's descriptions in the 4.22.1 catalog. Configuration follows the option *names*, not the swapped description text.

---

## 6. Project Structure

```
greenfield/
├── .camel-kit/
│   ├── config.properties
│   └── pipeline.json
├── docs/
│   ├── constitution.md
│   └── camel-kit/
│       └── 001-tms-shipments-broker-orders/
│           ├── design-spec.md                    ← this file
│           ├── implementation-plan.md            [created by camel-plan]
│           ├── execution-report.md               [created by camel-execute]
│           └── validation-report.md              [created by camel-validate]
├── tms-shipment-to-broker-order.camel.yaml
├── broker-event-to-tms-event.camel.yaml
├── ShipmentToBrokerOrderMapper.groovy
├── BrokerEventToTmsEventMapper.groovy
├── RejectedShipments.groovy
├── application.properties
├── run.sh
└── src/test/resources/
    ├── tms-shipment-to-broker-order.camel.it.yaml
    ├── broker-event-to-tms-event.camel.it.yaml
    ├── test-data/
    ├── application-test.properties
    └── jbang.properties
```

---

## 7. Acceptance Criteria

1. A shipment created via `POST /api/v1/tms/shipments/seed` is tendered within one poll cycle and the broker returns `202`.
2. The emitted `CreateBrokerOrderMessage` matches the documented example for the documented input — specifically 8 handling units and `quantity.grossWeight = 33286.01`.
3. After a successful tender the shipment no longer appears in `GET /api/v1/tms/shipments/new`.
4. A broker event pushed to `{{inbound.http.path}}` with a valid `X-API-KEY` returns `202` and produces a matching TMS event with the correct mapped `event_type`.
5. A push with a missing or wrong `X-API-KEY` returns `401`, creates no TMS event, and writes nothing to the DLQ.
6. An `ORDER_CREATED` event for a tendered shipment records `BOOKED` in the TMS.
7. A shipment rejected with 422 is dead-lettered exactly once and is not re-tendered on subsequent polls within the same process lifetime.
8. No credential or API key appears in any log output.
9. A pushed event whose `shipmentId` is not a valid UUID is rejected to the DLQ without any outbound call being made.

### Test Approach

Citrus 5.0.1 (pinned in `.camel-kit/config.properties`) driving the live sandbox: seed via `POST /api/v1/tms/shipments/seed`, drive Flow 2 via `POST /api/v1/trigger/events`, assert against the sandbox's own state endpoints, and reset with `DELETE /api/v1/nuke` between runs. The two Groovy mappers additionally get unit tests against the documented example payloads, since the mapping is the highest-risk component and needs no running sandbox to verify.

---

## 8. Open Design Questions

| # | Question | Impact | Resolution path |
|---|---|---|---|
| OQ-1 | Does `/api/v1/trigger/events` push a JSON **array** of `BrokerEventMessage`, or one object per request? | Determines whether Flow 2's `split` is load-bearing | Design tolerates both: split an array, treat a lone object as a single-element batch. Confirm by observing a real push during execution. |
| OQ-4 | `goodsDescription` in the documented example concatenates line items in **reverse** order. | Cosmetic unless the broker relies on ordering | Using document order. Flag if a payload comparison fails on this alone. |
| OQ-5 | The expected `X-API-KEY` value and the OAuth2 client id/secret are not yet supplied. | Blocks any live run | Exported as `SANDBOX_INBOUND_API_KEY`, `SANDBOX_CLIENT_ID` and `SANDBOX_CLIENT_SECRET` before startup. Values are never pasted into this repository, including into this spec. |

Resolved by amendment (2026-10-15): `meta.senderId` is the fixed organisation id `KITT` (formerly OQ-2); `shipment.transportMode` is the constant `"ROAD"`, confirmed (formerly OQ-3).

`external_order_refere` in the broker-to-tms example document is a truncated typo for `external_order_reference`; the schema name is authoritative and no question arises.
