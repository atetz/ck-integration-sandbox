---
staleness:
  stale: false
  since: null
  reason: null
generated:
  at: "2026-10-15T15:05:00Z"
  by: camel-validate
  from: execution-report.md
---
# Validation Report

**Date:** 2026-10-15 17:05
**Project:** greenfield
**Runtime:** main
**Camel Version:** 4.22.1
**Pipeline:** `001-tms-shipments-broker-orders` (standalone, pipeline-scoped)
**Inputs:** design-spec.md (fresh, camel-brainstorm) → implementation-plan.md (fresh, camel-plan) → execution-report.md (fresh, camel-execute)

**Inventory (Main, project root):**
- `ROUTE_FILES`: `tms-shipment-to-broker-order.camel.yaml` (routeConfiguration + `shipment-ingestion`, `shipment-tender`), `broker-event-to-tms-event.camel.yaml` (routeConfiguration + `broker-event-ingestion`, `broker-event-delivery`)
- `PROPS_FILE`: `application.properties` (+ `src/test/resources/application-test.properties`)

**Catalog binding:** `camel_catalog_components(limit=0)`; runtime=main; platformBom=org.apache.camel:camel-catalog:4.22.1; returned camelVersion=4.22.1; binding=MATCHED

**Isolation:** all dimensions ran inline in the orchestrator session (no subagent dispatch).

## Summary

| Category | Pass | Fail | Warn | Total |
|----------|------|------|------|-------|
| Schema Validation | 0 | 2 | 1 | 3 |
| Endpoint Verification | 7 | 0 | 1 | 8 |
| Quality Checks | 6 | 0 | 3 | 9 |
| Security Analysis | 4 | 2 | 1 | 7 |
| Anti-Patterns | 4 | 0 | 3 | 7 |
| Constitution Compliance | 7 | 0 | 1 | 8 |
| **Total** | **28** | **4** | **10** | **42** |

**Result: FAIL** — 4 blocking findings (2 schema, 2 security rule 4). None were introduced by today's amendment delta; all exist in the routes generated on 2026-09-26.

## Findings

### Schema Validation

Tool: `camel_validate_yaml_dsl` (Camel YAML DSL JSON schema). The shipped Maven validator
(`camel-yaml-dsl-validator-maven-plugin:4.22.1:validate`) could not run: the Maven wrapper's `mktemp` is denied by the
Claude Code sandbox (`mkdtemp failed on /var/folders/...: Operation not permitted`).

- [FAIL] `tms-shipment-to-broker-order.camel.yaml` — 2 schema errors at
  `/0/routeConfiguration/onException/1/onException/redeliveryPolicy`:
  `maximumRedeliveries: "{{error.maximum.redeliveries}}"` and `backOffMultiplier: "{{error.backoff.multiplier}}"` —
  *string found, number expected*. (`redeliveryDelay` is a duration/string field and passes.)
- [FAIL] `broker-event-to-tms-event.camel.yaml` — the same 2 errors at the same path (transient/delivery-failure
  `onException`).
  - Context: Camel's runtime resolves these placeholders — Flow 1 loaded and ran in today's Citrus run, both flows in the
    2026-09-26 run — so this is schema non-conformance (Kaoto/tooling), not a runtime defect.
- [WARN] Maven schema validator NOT_RUN (sandbox); MCP schema validation used as the Stage 1 evidence.

### Endpoint Verification

Tool: `camel_validate_route` (uri + route, runtime=main, full BOM). Each result echoed the submitted URI with no `errors`.

- [PASS] `timer:shipment-poll?period=…` — valid; consumer-only component used as consumer
- [PASS] `{{sandbox.base.url}}/api/v1/tms/shipments/new` (http, GET + OAuth2 options) — valid; the POST
  `/api/v1/broker/order` endpoint uses the same option set minus `limit`
- [PASS] `direct:shipment-tender` — valid (uriValidations)
- [PASS] `file:{{dlq.directory}}/tms-shipment-to-broker-order` (fileName, allowNullBody) — valid
- [PASS] `platform-http:{{inbound.http.path}}` (httpMethodRestrict=POST, consumes=application/json) — valid
- [PASS] `seda:broker-events` (waitForTaskToComplete=Never) — valid
- [PASS] `toD` `{{sandbox.base.url}}/api/v1/tms/event/${…}` (http, POST + OAuth2 options, `allowedSchemes: http,https`) — valid
- [WARN] Stage 3 `camel run --check` NOT_RUN — Camel JBang cannot start inside the sandbox
  (`toRealPath(~/.camel/…)` → `/Users: Operation not permitted`). Runtime evidence instead: today's
  `camel test run src/test/resources/tms-shipment-to-broker-order.citrus.it.yaml` started `shipment-ingestion` and
  `shipment-tender` cleanly (Routes startup total:2) and passed 1/1.

### Quality Checks

- [PASS] Completeness — all 4 routes have `id`, `from`, a sink (`to`/`toD`/internal hop), and a `description` ≥ 10 chars
- [WARN] `broker-event-ingestion` declares no `routeConfigurationId`/error handler; it relies on the default error
  handler (a failed enqueue returns 500 with no log/DLQ step). Authentication failures are correctly not dead-lettered.
- [PASS] Component catalog (5.2) — `timer`, `http`, `direct`, `file`, `platform-http`, `seda`: all present, `deprecated: false`, `supportLevel: Stable`, required path parameter supplied
- [PASS] Expressions (5.3) — Simple (`${exchangeProperty.*}`, `${body[id]}`, `${exception.statusCode}`), Groovy split/auth expressions; all referenced properties (`shipment-id`, `broker-event-id`) are set before use
- [PASS] Configuration (7.1) — `camel_configuration_validate`: `application.properties` 23/23 valid, `application-test.properties` 3/3 valid; no duplicate keys
- [PASS] Placeholders (7.2) — every `{{…}}` in the routes and Groovy beans (incl. `broker.sender.id`, `broker.message.function.new`, `sandbox.inbound.api.key`) is defined; no `${…}` in either properties file; secrets are `{{env:…}}`
- [PASS] Beans / Forage (7.3, 7.4) — no `camel.beans.*`, no YAML `beans`, no `forage.*` keys; `beanType` classes are application Groovy beans; no component requires an object option (`dataSource`, `connectionFactory`)
- [WARN] Flow 1 error-handling gap — the routeConfiguration only handles `HttpOperationFailedException` 422 and
  `IOException`/5xx. An `IllegalArgumentException` from `ShipmentToBrokerOrderMapper` (missing stop, unmapped package
  type) or a non-422 4xx matches no handler: the exchange fails with no DLQ entry, and the shipment stays `new` and fails
  on every poll.
- [WARN] Flow 2 delivery handler catches `java.lang.Exception` and retries non-transient failures (4xx, parse errors)
  3× before dead-lettering; under a burst this holds the seda consumer and can fill the 1000-slot queue.

### Security Analysis

Canonical checklist (`shared/camel-security-checklist.md`) applied to both routes and `application.properties`;
`camel_route_harden_context` used as supplemental evidence.

- [PASS] Rule 1 — No hardcoded credentials: OAuth2 id/secret and inbound API key are `{{env:…}}`; routes reference placeholders only (`RAW(…)` wrapping); no secret literals
- [PASS] Rule 2 — TLS: the only HTTP targets are `sandbox.base.url`/`sandbox.token.endpoint` = `http://localhost:8000` (localhost exemption). MCP's "HTTP instead of HTTPS" concern was corroborated as localhost-only; it becomes a FAIL the moment the base URL leaves localhost without `https://`
- [WARN] Rule 3 — Logs: `camel.main.logMask=true`; the API key header is removed before any log and never logged; no `${body}` logging. However the Flow 1 422 handler logs `${exception.responseBody}` in full; the design records no PII in shipment data and the canonical PII fields (email, phone, ssn, creditCard) are not in the schema, so this is a WARN — a broker validation error that echoes request content would turn it into a FAIL
- [FAIL] Rule 4 — Flow 2 external ingress (`platform-http`): size limit is enforced (`camel.server.maxBodySize=5242880`), but there is no schema validation (`json-validator:` / `bean-validator:`). Validation happens only field-by-field inside `BrokerEventToTmsEventMapper` after unmarshalling
- [FAIL] Rule 4 — Flow 1 TMS response: the polled `TmsShipment[]` is not validated, and the TMS-supplied `id`
  (`setProperty shipment-id = ${body[id]}`) is put unsanitized into the DLQ file name
  (`rejected-422-…-${exchangeProperty.shipment-id}.json`, `transient-…-${exchangeProperty.shipment-id}.json`). An id
  containing `../` or `/` writes outside `{{dlq.directory}}`. This corroborates MCP's medium "Path Traversal" concern
  for this route; Flow 2's DLQ names use `${exchangeId}` and are safe
- [PASS] Rule 4 — Dynamic endpoint: Flow 2 `toD` interpolates only `shipment-id`, which `BrokerEventToTmsEventMapper.shipmentId` validates against a strict UUID regex (throws `IllegalArgumentException` → DLQ, no outbound call), plus `allowedSchemes: http,https`
- [PASS] Rule 5 — Authentication: `broker-event-ingestion` checks `X-API-KEY` as its first step with a SHA-256 digest + `MessageDigest.isEqual` (constant-time) comparison; 401 + `stop` on failure; header removed on both branches; `removeHeaders: "*"` at the start of `broker-event-delivery` and after the TMS poll; no CORS configured

### Anti-Patterns

- [PASS] God routes — largest routes: `shipment-ingestion` 4 top-level / 9 total processors, `broker-event-delivery` 3 top-level / 10 total (≤ default threshold 10)
- [PASS] Hardcoded endpoints — none in route YAML
- [PASS] Circuit breakers / idempotency — deliberately excluded by the approved design (`Not Doing`: no circuit breaker; no Flow 2 dedup because the TMS overwrites by design); HTTP client is pooled by default (Apache HC5)
- [PASS] Correlation — shipment id carried as `shipment-id` / `broker-event-id` exchange properties and logged at every decision point (design-chosen correlation key)
- [WARN] No timeouts configured for outbound `http` calls (no `connectTimeout`/`responseTimeout`/`soTimeout` options); a hung TMS/broker call blocks the single timer thread (Flow 1) or the seda consumer (Flow 2)
- [WARN] `toD` builds one `http` endpoint per shipment id; the OAuth2 token cache is per endpoint, so a token is fetched per shipment and the endpoint cache churns. A static `to` with `CamelHttpPath` would keep one endpoint and one token
- [WARN] `camel.jbang.dependencies` omits `camel-direct` and `camel-bean` (resolved automatically by JBang at runtime, but an export/`camel run` without auto-resolution would miss them)

### Constitution Compliance

- Rule 1 (Route Structure): PASS — `shipment-ingestion` (timer → http + direct), `shipment-tender` (direct → http), `broker-event-ingestion` (platform-http → seda), `broker-event-delivery` (seda → http)
- Rule 2 (Single Responsibility): PASS — each description is one purpose; top-level step counts 3–4 (≤ 7)
- Rule 3 (Separation of Concerns): WARN — ingestion → processing → delivery via `direct:`/`seda:`, mapping in Groovy beans; but the API-key check is inline Groovy in `broker-event-ingestion` rather than a bean
- Rule 4 (Naming Conventions): PASS — route IDs match `^[a-z][a-z0-9]*(-[a-z][a-z0-9]*)+$`; `direct:shipment-tender` = `direct:<route-id>`; `seda:broker-events` = `seda:<domain>-<purpose>`; custom properties kebab-case
- Rule 5 (Observability): PASS — every route has `id` + `description`; logging at decision points only
- Rule 6 (External Configuration): PASS — no hosts, ports, credentials or queue names hardcoded in routes; all `{{…}}`
- Rule 7 (Component Verification): PASS — all 6 components Available, none deprecated (validated detail fields, binding MATCHED)
- Rule 8 (Infrastructure via Forage): PASS — no infrastructure beans; Groovy beans are application logic

Project Customizations: all fenced blocks are commented out — no custom rules applied.

## Recommendations

**Priority fixes (FAIL) — route changes belong to `/camel-brainstorm` (amend) → `/camel-plan` → `/camel-execute`; this report modifies nothing:**

1. **Flow 1 DLQ path traversal (security rule 4).** Validate the TMS shipment `id` (e.g. the same strict UUID check Flow 2 uses) before it is stored in `shipment-id`, or build the DLQ `fileName` from `${exchangeId}` and keep the shipment id in the file body / log only.
2. **Schema validation at both ingresses (security rule 4).** Add a JSON Schema check (`json-validator:`) for the pushed `BrokerEventMessage` payload in Flow 2, and for the polled `TmsShipment[]` in Flow 1, ahead of mapping. Schemas can be derived from the sandbox OpenAPI document; `schemas/` already exists (empty).
3. **YAML DSL schema conformance.** `maximumRedeliveries` and `backOffMultiplier` are typed `number`; placeholder strings break strict schema/tooling validation in both route files. Options: use literal numbers, or move redelivery settings to a properties-configured error handler. This trades away part of constitution rule 6's externalisation, so it needs a design decision rather than a mechanical edit.

**Suggested improvements (WARN):**

- Give `broker-event-ingestion` an explicit error strategy for enqueue failures.
- Close the Flow 1 error-handling gap (mapper `IllegalArgumentException`, non-422 4xx → DLQ once, like 422).
- Restrict Flow 2 retries to transient failures; dead-letter 4xx/parse errors immediately.
- Configure connect/response timeouts on the outbound `http` endpoints.
- Replace the per-shipment `toD` endpoint with a static `to` + `CamelHttpPath` (one endpoint, one cached token).
- Log the broker's 422 reason selectively instead of the full `responseBody`.
- Move the inline API-key Groovy expression into a bean (constitution rule 3).
- Add `camel-direct` and `camel-bean` to `camel.jbang.dependencies`.
- Re-run Stage 1 with the Maven validator and Stage 3 `camel run --check` outside the sandbox to replace the NOT_RUN entries.
