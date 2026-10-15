---
staleness:
  stale: false
  since: null
  reason: null
generated:
  at: "2026-09-25T15:57:10.568529Z"
  by: "camel-plan"
  from: "design-spec.md"
---
# TMS Shipments to Broker Orders — Implementation Plan

> **For agentic workers:** Use camel-execute to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tender new TMS shipments to the broker as broker orders, and record broker tracking events back into the TMS as shipment events.

**Architecture:** Two independent Camel routes on Camel Main with YAML DSL. Flow 1 polls the TMS every 60 s, splits the batch, filters shipments the broker has already rejected, maps each one in a Groovy bean, and POSTs it to the broker. Flow 2 exposes an authenticated `platform-http` listener that verifies an `X-API-KEY` header, acknowledges with 202, and asynchronously maps and delivers each broker event to the TMS via a dynamic endpoint. Both flows authenticate outbound calls with the `http` component's native OAuth2 client-credentials support; all three secrets resolve from OS environment variables.

**Tech Stack:** Apache Camel 4.22.1, runtime `main` (JBang CLI), YAML DSL, Groovy beans, components `timer`/`http`/`platform-http`/`seda`/`file`, EIPs `split`/`choice`/`filter`/`toD`, dataformat `jackson`, languages `groovy`/`simple`. Tests with Citrus 5.0.1.

**Design Spec:** `docs/camel-kit/001-tms-shipments-broker-orders/design-spec.md` (approved 2026-09-25)

**Path resolution:** Runtime is Main and `Target Module` is the project root, so `MODULE_PREFIX` is the empty string. `ROUTE_DIR`, `PROPS_DIR` and `MODULE_DIR` all resolve to the project root; `TEST_DIR` is `src/test/resources/`.

**Excluded by design — do not implement:** containerisation/Docker Compose, metrics and health endpoints, circuit breakers, a durable rejected-shipment store, inbound OAuth2 (`oauthProfile`), `messageFunction` 4/1, and any deduplication or resequencing in Flow 2. See the design spec's `## Not Doing (and Why)`.

---

```yaml plan-metadata
tasks:
  - id: 1
    title: Scaffold Project Structure
    files:
      creates:
        - schemas/
        - src/test/resources/test-data/
    provides:
      routeContracts:
        - project-tree-main
    consumes: {}
    dependsOn: []

  - id: 2
    title: Generate Groovy Bean — ShipmentToBrokerOrderMapper
    files:
      creates:
        - ShipmentToBrokerOrderMapper.groovy
    provides:
      beans:
        - shipmentToBrokerOrderMapper
    consumes:
      routeContracts:
        - project-tree-main
    dependsOn:
      - 1

  - id: 3
    title: Generate Groovy Bean — RejectedShipments
    files:
      creates:
        - RejectedShipments.groovy
    provides:
      beans:
        - rejectedShipments
    consumes:
      routeContracts:
        - project-tree-main
    dependsOn:
      - 1

  - id: 4
    title: Generate Route YAML — tms-shipment-to-broker-order
    files:
      creates:
        - tms-shipment-to-broker-order.camel.yaml
    provides:
      routes:
        - shipment-ingestion
        - shipment-tender
      endpoints:
        - timer:shipment-poll
        - direct:shipment-tender
      externalServices:
        - integration-sandbox-tms
        - integration-sandbox-broker
    consumes:
      beans:
        - shipmentToBrokerOrderMapper
        - rejectedShipments
    dependsOn:
      - 1
      - 2
      - 3

  - id: 5
    title: Generate Groovy Bean — BrokerEventToTmsEventMapper
    files:
      creates:
        - BrokerEventToTmsEventMapper.groovy
    provides:
      beans:
        - brokerEventToTmsEventMapper
    consumes:
      routeContracts:
        - project-tree-main
    dependsOn:
      - 1

  - id: 6
    title: Generate Route YAML — broker-event-to-tms-event
    files:
      creates:
        - broker-event-to-tms-event.camel.yaml
    provides:
      routes:
        - broker-event-ingestion
        - broker-event-delivery
      endpoints:
        - platform-http:/events
        - seda:broker-events
      externalServices:
        - integration-sandbox-tms
    consumes:
      beans:
        - brokerEventToTmsEventMapper
    dependsOn:
      - 1
      - 5

  - id: 7
    title: Generate Application Properties
    files:
      creates:
        - application.properties
    provides:
      properties:
        - sandbox.base.url
        - sandbox.token.endpoint
        - sandbox.client.id
        - sandbox.client.secret
        - sandbox.inbound.api.key
        - sandbox.oauth2.cache.tokens
        - sandbox.oauth2.body.authentication
        - tms.poll.period
        - tms.poll.limit
        - broker.sender.id
        - broker.message.function.new
        - inbound.http.port
        - inbound.http.path
        - error.maximum.redeliveries
        - error.redelivery.delay
        - error.backoff.multiplier
        - dlq.directory
        - camel.jbang.dependencies
    consumes:
      routes:
        - shipment-ingestion
        - shipment-tender
        - broker-event-ingestion
        - broker-event-delivery
    dependsOn:
      - 4
      - 6

  - id: 8
    title: Generate Main Run Script
    files:
      creates:
        - run.sh
      reads:
        - tms-shipment-to-broker-order.camel.yaml
        - broker-event-to-tms-event.camel.yaml
        - application.properties
    provides:
      routeContracts:
        - module-launch-command
    consumes:
      routes:
        - shipment-ingestion
        - shipment-tender
        - broker-event-ingestion
        - broker-event-delivery
      properties:
        - camel.jbang.dependencies
    dependsOn:
      - 4
      - 6
      - 7

  - id: 9
    title: Generate Integration Tests — tms-shipment-to-broker-order
    files:
      creates:
        - src/test/resources/tms-shipment-to-broker-order.camel.it.yaml
        - src/test/resources/test-data/tms-shipment-to-broker-order-input.json
        - src/test/resources/test-data/tms-shipment-to-broker-order-expected.json
      modifies:
        - src/test/resources/jbang.properties
      reads:
        - tms-shipment-to-broker-order.camel.yaml
    provides:
      testData:
        - tms-shipment-to-broker-order-input
        - tms-shipment-to-broker-order-expected
    consumes:
      routes:
        - shipment-ingestion
        - shipment-tender
      beans:
        - shipmentToBrokerOrderMapper
      externalServices:
        - integration-sandbox-tms
        - integration-sandbox-broker
    dependsOn:
      - 4
      - 7
      - 8

  - id: 10
    title: Generate Integration Tests — broker-event-to-tms-event
    files:
      creates:
        - src/test/resources/broker-event-to-tms-event.camel.it.yaml
        - src/test/resources/test-data/broker-event-to-tms-event-input.json
        - src/test/resources/test-data/broker-event-to-tms-event-expected.json
      modifies:
        - src/test/resources/jbang.properties
      reads:
        - broker-event-to-tms-event.camel.yaml
    provides:
      testData:
        - broker-event-to-tms-event-input
        - broker-event-to-tms-event-expected
    consumes:
      routes:
        - broker-event-ingestion
        - broker-event-delivery
      beans:
        - brokerEventToTmsEventMapper
      externalServices:
        - integration-sandbox-tms
    dependsOn:
      - 6
      - 7
      - 8
```

---

### Task 1: Scaffold Project Structure

**Agent:** implementation-engineer

**Files:**
- Create: `schemas/` (directory)
- Create: `src/test/resources/test-data/` (directory)

**Guides to Load:**
- `camel-implement/guides/orchestrator.md` — file path table

**Design Spec Section:** Section 6 (Project Structure)

<HARD-RULE>
Runtime is Main. Do NOT load Maven/POM guides and do NOT create a `pom.xml`. Route YAML, Groovy beans, `application.properties` and `run.sh` all live at the project root because `Target Module` is empty.
</HARD-RULE>

- [ ] **Step 1:** Read design spec Section 6 and confirm the target tree for runtime `main` at the project root
- [ ] **Step 2:** Create `schemas/` and `src/test/resources/test-data/`
- [ ] **Step 3:** Confirm `docs/`, `.camel-kit/`, `mvnw`, `mvnw.cmd` and `.mvn/` already exist and are left untouched
- [ ] **Step 4:** Confirm no `pom.xml` exists at the project root and none is created
- [ ] **Step 5:** Verify: `test -d schemas && test -d src/test/resources/test-data && test ! -f pom.xml && echo OK` prints `OK`

**Review:**
- [ ] Spec compliance: tree matches design spec Section 6 for runtime Main at the project root
- [ ] Code quality: no POM created; no Spring Boot/Quarkus-only assets introduced

---

### Task 2: Generate Groovy Bean — ShipmentToBrokerOrderMapper

**Agent:** implementation-engineer

**Files:**
- Create: `ShipmentToBrokerOrderMapper.groovy`

**Guides to Load:**
- `camel-implement/guides/orchestrator.md` — file path table
- `camel-implement/guides/datamapper-groovy.md` — Groovy transformation conventions

**MCP Tools:**
- `camel_catalog_language_doc(language="groovy", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`

**Design Spec Section:** Section 3, Flow: `tms-shipment-to-broker-order`, "Mapping — `TmsShipment` → `CreateBrokerOrderMessage`"

This bean owns the entire Flow 1 transformation. It receives one `TmsShipment` as a `Map` and returns one `CreateBrokerOrderMessage` as a `Map`.

- [ ] **Step 1:** Read the design spec mapping table for Flow 1 in full, including the package-type table and the date rule
- [ ] **Step 2:** Implement the package-type lookup as a constant map covering all 8 values: `BALE→BL`, `BOX→BX`, `COIL→CL`, `CRATE→CR`, `CYLINDER→CY`, `DRUM→DR`, `OTHER→OT`, `PLT→PL`. An unmapped value must throw rather than silently emit null
- [ ] **Step 3:** Implement `meta`: `senderId` from the `broker.sender.id` property, `messageDate` as the current instant in ISO-8601 UTC with milliseconds, `messageReference` from the shipment `id`, `messageFunction` from the `broker.message.function.new` property
- [ ] **Step 4:** Implement `shipment`: `reference` from `id`, `carrier` from `customer.carrier`, `transportMode` as the constant `"ROAD"` (do NOT derive it from `mode` — see design spec OQ-3)
- [ ] **Step 5:** Implement `orders[0].goodsDescription` by joining every `line_items[].description` with a literal `|` in document order (see design spec OQ-4)
- [ ] **Step 6:** Implement `orders[0].quantity`: `grossWeight` as `SUM(package_weight × total_packages)` across all line items, `loadingMeters` from `loading_meters`
- [ ] **Step 7:** Implement `orders[0].handlingUnits[]` by emitting **one entry per package** — repeat each line item `total_packages` times, with `grossWeight` set to the per-unit `package_weight` (NOT multiplied), and `width`/`length`/`height` mapped directly
- [ ] **Step 8:** Implement `pickUp` from `stops[type=PICKUP]` and `consignee` from `stops[type=DELIVERY]`, mapping `location.code→identification`, `location.name→name`, `address.address→address1`, `address.city→city`, `address.postal_code→postalCode`, `address.country→country`, and `latitude`/`longitude` directly. Set `address2` and `instructions` to `null`
- [ ] **Step 9:** Implement both `dates[]` entries per location: `planned_date` + `T` + `planned_time_window_start` + `Z` as `PERIOD_EARLIEST`, and the same with `planned_time_window_end` as `PERIOD_LATEST`
- [ ] **Step 10:** Throw a clear exception when a shipment has no `PICKUP` stop or no `DELIVERY` stop — the broker schema requires both and a silent null would become a 422 with no explanation
- [ ] **Step 11:** Verify: `test -s ShipmentToBrokerOrderMapper.groovy && groovy -c ShipmentToBrokerOrderMapper.groovy && echo OK` prints `OK` (syntax check only; behaviour is asserted in Task 9)

**Review:**
- [ ] Spec compliance: every row of the Flow 1 mapping table is implemented; all 8 package types present; handling units expand per package; `grossWeight` uses the multiplied sum while handling-unit weights do not
- [ ] Code quality: constitution rule 3 (business logic in a bean, not the route); no hardcoded URLs or credentials; `senderId` and `messageFunction` read from properties rather than literals; missing-stop and unknown-enum cases fail loudly

---

### Task 3: Generate Groovy Bean — RejectedShipments

**Agent:** implementation-engineer

**Files:**
- Create: `RejectedShipments.groovy`

**Guides to Load:**
- `camel-implement/guides/orchestrator.md` — file path table

**Design Spec Section:** Section 3, Flow: `tms-shipment-to-broker-order`, transformation step 3 and Error Handling

This bean holds the in-memory set of shipment ids the broker rejected with 422, so a poison shipment is dead-lettered once per process lifetime instead of on every poll.

- [ ] **Step 1:** Implement a thread-safe set of shipment ids (the route body is split and the set is touched from both the filter and the error handler)
- [ ] **Step 2:** Expose `isNotRejected(Map shipment)` returning `true` when the shipment id is absent from the set — this is the predicate the `filter` EIP calls
- [ ] **Step 3:** Expose `markRejected(String shipmentId)` for the 422 error handler to call
- [ ] **Step 4:** Do NOT add persistence, eviction, or a TTL — the design explicitly excludes a durable store, and the set is intended to be lost on restart
- [ ] **Step 5:** Verify: `test -s RejectedShipments.groovy && groovy -c RejectedShipments.groovy && echo OK` prints `OK`

**Review:**
- [ ] Spec compliance: suppression is keyed on failures only; nothing is recorded on success
- [ ] Code quality: thread-safe collection; no persistence; no unbounded growth concern documented away — growth is bounded by the number of genuinely broken shipments

---

### Task 4: Generate Route YAML — tms-shipment-to-broker-order

**Agent:** implementation-engineer

**Files:**
- Create: `tms-shipment-to-broker-order.camel.yaml`

**Guides to Load:**
- `camel-implement/guides/orchestrator.md` — file paths, execution order
- `camel-implement/guides/component-loading.md` — component documentation
- `camel-implement/guides/yaml-catalog-rules.md` — catalog verification rules
- `camel-implement/guides/yaml-structure.md` — YAML DSL structure
- `camel-implement/guides/route-validation.md` — validation loop
- `camel-implement/guides/sequential-http-calls.md` — HTTP→HTTP flow patterns

**MCP Tools:**
- `camel_catalog_component_doc(component="timer", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_component_doc(component="http", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_component_doc(component="file", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_eip_doc(eip="split", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_eip_doc(eip="filter", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_dataformat_doc(dataformat="jackson", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_language_doc(language="simple", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`

**Design Spec Section:** Section 3, Flow: `tms-shipment-to-broker-order`

- [ ] **Step 1:** Read design spec Section 3 for `tms-shipment-to-broker-order` in full, including Error Handling
- [ ] **Step 2:** Verify every component, EIP, dataformat and language above via MCP before writing any YAML
- [ ] **Step 3:** Generate route `shipment-ingestion` with a `description`, sourced from `timer:shipment-poll?period={{tms.poll.period}}`
- [ ] **Step 4:** Call `GET {{sandbox.base.url}}/api/v1/tms/shipments/new?limit={{tms.poll.limit}}` via the `http` component, configuring `oauth2ClientId`, `oauth2ClientSecret`, `oauth2TokenEndpoint`, `oauth2BodyAuthentication` and `oauth2CacheTokens` from properties. `oauth2CacheTokens` MUST be `true` — it defaults to `false`, and leaving it off re-fetches a token on every single call
- [ ] **Step 5:** Unmarshal with `jackson` using `useList: true`. Treat a null or empty body as an empty poll that completes normally — it is not an error
- [ ] **Step 6:** `split` the list with `stopOnException` left at its default `false`, so one rejected shipment does not abort the remaining shipments in the batch
- [ ] **Step 7:** Apply the `filter` EIP calling `RejectedShipments.isNotRejected` to skip shipments already rejected with 422
- [ ] **Step 8:** Route each surviving shipment to `direct:shipment-tender`
- [ ] **Step 9:** Generate route `shipment-tender` with a `description`, sourced from `direct:shipment-tender`: invoke `ShipmentToBrokerOrderMapper`, marshal with `jackson`, then `POST {{sandbox.base.url}}/api/v1/broker/order` with the same OAuth2 options. Treat **202** as success — do not assume 200
- [ ] **Step 10:** Add an `onException` for `org.apache.camel.http.base.HttpOperationFailedException` where the status code is 422: set `maximumRedeliveries` to `0`, call `RejectedShipments.markRejected` with the shipment id, log the shipment id and the broker's response body at WARN, and dead-letter to `file:{{dlq.directory}}/tms-shipment-to-broker-order`
- [ ] **Step 11:** Add a second `onException` for transient failures (`java.io.IOException` and 5xx `HttpOperationFailedException`) with `maximumRedeliveries` `{{error.maximum.redeliveries}}`, `redeliveryDelay` `{{error.redelivery.delay}}`, `backOffMultiplier` `{{error.backoff.multiplier}}`, `useExponentialBackOff` true, dead-lettering to the same directory after exhaustion
- [ ] **Step 12:** Add logging at decision points only, per constitution rule 5: shipment count per poll, each tender with its status, each 422 suppression. Do NOT log the OAuth2 client secret, the token, or any `Authorization` header
- [ ] **Step 13:** Self-validate: both routes have `routeId` and `description`, no hardcoded URLs or credentials, every environment value is a `{{placeholder}}`
- [ ] **Step 14:** Verify: `camel-kit validate route tms-shipment-to-broker-order.camel.yaml` exits 0 with no ERROR findings

**Review:**
- [ ] Spec compliance: `timer` period, poll limit, OAuth2 options, split/filter order, both `onException` blocks and the DLQ path all match the design spec
- [ ] Code quality: constitution rules 1–7; two routes each with a single responsibility; `direct:` used for the synchronous internal hop per rule 3; no secrets in logs

---

### Task 5: Generate Groovy Bean — BrokerEventToTmsEventMapper

**Agent:** implementation-engineer

**Files:**
- Create: `BrokerEventToTmsEventMapper.groovy`

**Guides to Load:**
- `camel-implement/guides/orchestrator.md` — file path table
- `camel-implement/guides/datamapper-groovy.md` — Groovy transformation conventions

**MCP Tools:**
- `camel_catalog_language_doc(language="groovy", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`

**Design Spec Section:** Section 3, Flow: `broker-event-to-tms-event`, "Mapping — `BrokerEventMessage` → `CreateTmsShipmentEvent`"

- [ ] **Step 1:** Read the design spec mapping table and event-type table for Flow 2 in full
- [ ] **Step 2:** Implement the event-type lookup covering all 6 values: `ORDER_CREATED→BOOKED`, `CANCEL_ORDER→CANCELLED`, `DRIVING_TO_LOAD→DISPATCHED`, `ORDER_LOADED→PICKED_UP`, `ETA_EVENT→ETA_CHANGED`, `ORDER_DELIVERED→DELIVERED`. An unknown broker event type must throw rather than emit null
- [ ] **Step 3:** Map `created_at` from `situation.registrationDate`, `occured_at` from `situation.actualDate`, `external_order_reference` from `order.reference`, and `source` to the constant `"broker"`
- [ ] **Step 4:** Map `location` **only when `situation.position` is present** — `position.locationReference→location.code`, plus `latitude` and `longitude`. When `position` is absent (`ORDER_CREATED`, `CANCEL_ORDER`), omit `location` entirely rather than emitting an object of nulls
- [ ] **Step 5:** Use the target field name `external_order_reference`. The published example document shows `external_order_refere`, which is a truncated typo — the schema name is authoritative
- [ ] **Step 6:** Expose the validated `shipmentId` for the route to use as a path segment. Validate it against a strict UUID pattern and throw when it does not match — this value arrives from an external caller and is interpolated into a dynamic endpoint URI
- [ ] **Step 7:** Do NOT map `order.eta`. `CreateTmsShipmentEvent` has no field for it; the design records this as an accepted limitation
- [ ] **Step 8:** Verify: `test -s BrokerEventToTmsEventMapper.groovy && groovy -c BrokerEventToTmsEventMapper.groovy && echo OK` prints `OK`

**Review:**
- [ ] Spec compliance: all 6 event types mapped; `location` is conditional; `source` is the constant `"broker"`; `order.eta` deliberately unmapped
- [ ] Code quality: UUID validation present and failing closed; unknown enum values throw; no hardcoded endpoints

---

### Task 6: Generate Route YAML — broker-event-to-tms-event

**Agent:** implementation-engineer

**Files:**
- Create: `broker-event-to-tms-event.camel.yaml`

**Guides to Load:**
- `camel-implement/guides/orchestrator.md` — file paths, execution order
- `camel-implement/guides/component-loading.md` — component documentation
- `camel-implement/guides/yaml-catalog-rules.md` — catalog verification rules
- `camel-implement/guides/yaml-structure.md` — YAML DSL structure
- `camel-implement/guides/route-validation.md` — validation loop
- `camel-implement/guides/sequential-http-calls.md` — HTTP→HTTP flow patterns

**MCP Tools:**
- `camel_catalog_component_doc(component="platform-http", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_component_doc(component="seda", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_component_doc(component="http", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_component_doc(component="file", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_eip_doc(eip="choice", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_eip_doc(eip="split", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_eip_doc(eip="toD", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_dataformat_doc(dataformat="jackson", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`

**Design Spec Section:** Section 3, Flow: `broker-event-to-tms-event`

- [ ] **Step 1:** Read design spec Section 3 for `broker-event-to-tms-event` in full, including Security and Error Handling
- [ ] **Step 2:** Verify every component, EIP and dataformat above via MCP before writing any YAML
- [ ] **Step 3:** Generate route `broker-event-ingestion` with a `description`, sourced from `platform-http:{{inbound.http.path}}` with `httpMethodRestrict: POST` and `consumes: application/json`. Do NOT set `oauthProfile` — inbound auth is the API key
- [ ] **Step 4:** As the **first** step in the route, apply a `choice` comparing header `X-API-KEY` against `{{sandbox.inbound.api.key}}`. On mismatch or absence set `CamelHttpResponseCode` to `401` and stop the route without enqueueing anything and without writing to the DLQ — a failed authentication is not a failed event
- [ ] **Step 5:** Perform the comparison in a length-constant manner rather than plain string equality, and ensure the `X-API-KEY` header is never written to a log
- [ ] **Step 6:** On success, send the payload to `seda:broker-events` and set `CamelHttpResponseCode` to `202`. The HTTP response must return without waiting for the TMS call
- [ ] **Step 7:** Generate route `broker-event-delivery` with a `description`, sourced from `seda:broker-events`: unmarshal with `jackson`, then `split` the payload. The body may be a JSON array or a single object — handle both (see design spec OQ-1)
- [ ] **Step 8:** Invoke `BrokerEventToTmsEventMapper` per event, marshal with `jackson`, and deliver with `toD` to `{{sandbox.base.url}}/api/v1/tms/event/${shipmentId}` using the same OAuth2 options as Flow 1, with `oauth2CacheTokens` set to `true`
- [ ] **Step 9:** Set `allowedSchemes=http,https` on the `toD` endpoint. Combined with the UUID validation in Task 5, this prevents a crafted `shipmentId` from reshaping the outbound request. Both mitigations are required, not optional
- [ ] **Step 10:** Treat **202** as success. Add an `onException` with `maximumRedeliveries` `{{error.maximum.redeliveries}}`, `redeliveryDelay` `{{error.redelivery.delay}}`, `backOffMultiplier` `{{error.backoff.multiplier}}`, `useExponentialBackOff` true, dead-lettering to `file:{{dlq.directory}}/broker-event-to-tms-event`. The sandbox never re-sends a push, so the DLQ is the only replay source
- [ ] **Step 11:** Add logging at decision points: each authenticated push accepted, each event mapped with its resulting TMS event type, each dead-letter with its reason. Never log the API key, the client secret, or the bearer token
- [ ] **Step 12:** Self-validate: both routes have `routeId` and `description`, no hardcoded values, `seda:` used for the asynchronous hop per constitution rule 3
- [ ] **Step 13:** Verify: `camel-kit validate route broker-event-to-tms-event.camel.yaml` exits 0 with no ERROR findings

**Review:**
- [ ] Spec compliance: API key verified before any enqueue; 401 on failure with no DLQ entry; 202 returned before the TMS call; `toD` used with `allowedSchemes`; retry and DLQ settings match the spec
- [ ] Code quality: constitution rules 1–7; no secret reaches a log; authentication cannot be bypassed by an alternate path through the route

---

### Task 7: Generate Application Properties

**Agent:** implementation-engineer

**Files:**
- Create: `application.properties`

**Guides to Load:**
- `camel-implement/guides/properties-generation.md`

**MCP Tools:**
- `camel_configuration_validate` — properties validation gate

**Design Spec Section:** Section 3 (Configuration Properties for both flows) and "Shared Configuration"

<HARD-RULE>
No secret may be given a literal value in this file. `sandbox.client.id`, `sandbox.client.secret` and `sandbox.inbound.api.key` MUST be written as `{{env:VAR}}` references so there is no slot for a credential to be committed into. A missing variable must fail at startup rather than surface later as a 401.
</HARD-RULE>

- [ ] **Step 1:** Read the design spec Configuration Properties blocks for both flows and the Shared Configuration section
- [ ] **Step 2:** Write the non-secret values: `sandbox.base.url`, `sandbox.token.endpoint`, `sandbox.oauth2.cache.tokens=true`, `sandbox.oauth2.body.authentication=true`, `tms.poll.period=60000`, `tms.poll.limit=50`, `broker.message.function.new=9`, `inbound.http.port=8080`, `inbound.http.path=/events`, `error.maximum.redeliveries=3`, `error.redelivery.delay=1000`, `error.backoff.multiplier=2`, `dlq.directory=data/dlq`
- [ ] **Step 3:** Write the three secrets as `sandbox.client.id={{env:SANDBOX_CLIENT_ID}}`, `sandbox.client.secret={{env:SANDBOX_CLIENT_SECRET}}`, `sandbox.inbound.api.key={{env:SANDBOX_INBOUND_API_KEY}}`
- [ ] **Step 4:** Write `broker.sender.id` as a reviewable configuration value, not an environment reference — it is an organisation identifier, not a credential. Its value is still open under design spec OQ-2
- [ ] **Step 5:** Configure the HTTP server port for `platform-http` so the listener binds `{{inbound.http.port}}` (8080) and never port 8000, which the sandbox occupies
- [ ] **Step 6:** Add `camel.jbang.dependencies` listing every required artifact: `camel-timer`, `camel-http`, `camel-platform-http`, the Main platform-http server provider, `camel-seda`, `camel-file`, `camel-jackson`, `camel-groovy`. Do NOT create a `pom.xml`
- [ ] **Step 7:** Group properties by flow with comments, and mark the secrets block explicitly as environment-resolved
- [ ] **Step 8:** Verify: `camel_configuration_validate` passes, and `grep -c '=CHANGEME' application.properties` returns `0` for every secret key (only `broker.sender.id` may carry a placeholder value)
- [ ] **Step 9:** Verify: every `{{...}}` placeholder referenced by either route YAML has a matching key in this file

**Review:**
- [ ] Spec compliance: every property named in the design spec is present with the specified value
- [ ] Code quality: constitution rule 6 — no hardcoded credentials, no secret literal anywhere; `camel.jbang.dependencies` complete so the app starts without a missing-component error

---

### Task 8: Generate Main Run Script

**Agent:** implementation-engineer

**Files:**
- Create: `run.sh`

**Guides to Load:**
- `camel-implement/guides/run-script.md`

**Design Spec Section:** Section 3 (all flows), Section 6 (Project Structure)

- [ ] **Step 1:** Inventory every route file: `tms-shipment-to-broker-order.camel.yaml` and `broker-event-to-tms-event.camel.yaml`
- [ ] **Step 2:** Inventory XSLT DataMapper files — there are none in this project, so the list is empty
- [ ] **Step 3:** Generate a single `run.sh` whose one Camel command launches **both** route files together with `application.properties`. Do not generate a per-flow script
- [ ] **Step 4:** Include the Groovy bean files so they are loaded by the running context: `ShipmentToBrokerOrderMapper.groovy`, `RejectedShipments.groovy`, `BrokerEventToTmsEventMapper.groovy`
- [ ] **Step 5:** Add strict shell flags per the run-script guide
- [ ] **Step 6:** Fail fast with a clear message when `SANDBOX_CLIENT_ID`, `SANDBOX_CLIENT_SECRET` or `SANDBOX_INBOUND_API_KEY` is unset, so the operator sees the cause rather than an unresolved-placeholder stack trace
- [ ] **Step 7:** `chmod +x run.sh`
- [ ] **Step 8:** Verify: `test -x run.sh` exits 0 and `grep -c 'camel.yaml' run.sh` returns `2`

**Review:**
- [ ] Spec compliance: the script launches the whole module — both flows and all three beans — not one flow
- [ ] Code quality: strict shell flags, properties argument present, required environment variables checked before launch

---

### Task 9: Generate Integration Tests — tms-shipment-to-broker-order

**Agent:** test-engineer

`TEST_DIR` resolves to `src/test/resources/` because the target module is the project root.

**Files:**
- Create: `src/test/resources/tms-shipment-to-broker-order.camel.it.yaml`
- Create: `src/test/resources/test-data/tms-shipment-to-broker-order-input.json`
- Create: `src/test/resources/test-data/tms-shipment-to-broker-order-expected.json`
- Create/Modify: `src/test/resources/jbang.properties`

**Guides to Load:**
- `camel-test/guides/route-analysis.md` — identify testable behaviors
- `camel-test/guides/test-generation.md` — test patterns and templates
- `camel-test/guides/test-configuration.md` — test environment setup
- `camel-test/guides/test-runner.md` — execution and verification

**MCP Tools:**
- `camel_catalog_component_doc(component="timer", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_component_doc(component="http", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `citrus_catalog_actions` — confirm the Citrus 5.0.1 action vocabulary
- `citrus_catalog_endpoint_schema` — confirm HTTP endpoint configuration

**Design Spec Section:** Section 3, Flow: `tms-shipment-to-broker-order`; Section 7, Acceptance Criteria 1, 2, 3, 7, 8

**Input:** Generated route at `tms-shipment-to-broker-order.camel.yaml`

- [ ] **Step 1:** Read the generated route YAML for `tms-shipment-to-broker-order`
- [ ] **Step 2:** Use the design spec's documented example `TmsShipment` as the input fixture — 3 line items with `total_packages` 4/1/3
- [ ] **Step 3:** Use the design spec's documented `CreateBrokerOrderMessage` as the expected fixture. It MUST contain exactly **8 handling units** and `quantity.grossWeight` of **33286.01**. This single assertion covers the package expansion, the per-unit weight rule and the weighted sum at once
- [ ] **Step 4:** Generate the happy-path scenario: seed via `POST /api/v1/tms/shipments/seed`, wait for the tender, assert the broker received a correctly mapped order (Acceptance Criteria 1 and 2)
- [ ] **Step 5:** Generate a scenario asserting a tendered shipment no longer appears in `GET /api/v1/tms/shipments/new` (Acceptance Criterion 3)
- [ ] **Step 6:** Generate the poison-shipment scenario: a shipment the broker rejects with 422 is dead-lettered exactly once and is NOT re-tendered on the following poll (Acceptance Criterion 7)
- [ ] **Step 7:** Generate an empty-poll scenario: when `/new` returns an empty list or null, the route completes without error and writes nothing to the DLQ
- [ ] **Step 8:** Assert no credential, bearer token or `Authorization` header value appears in captured log output (Acceptance Criterion 8)
- [ ] **Step 9:** Reset sandbox state with `DELETE /api/v1/nuke` between scenarios so runs are independent
- [ ] **Step 10:** Add Citrus 5.0.1 test dependencies to `src/test/resources/jbang.properties` via `run.deps`. Do NOT create a `pom.xml`. The version MUST match `citrus.version` in `.camel-kit/config.properties`
- [ ] **Step 11:** Verify: `camel test run src/test/resources/tms-shipment-to-broker-order.camel.it.yaml` exits 0 with a passing summary

**Review:**
- [ ] Spec compliance: scenarios cover Acceptance Criteria 1, 2, 3, 7 and 8; the expected fixture matches the design spec's documented payload exactly
- [ ] Code quality: sandbox state reset between scenarios; assertions on payload content rather than only status codes; no fixed sleeps used where a wait-for-condition is available

---

### Task 10: Generate Integration Tests — broker-event-to-tms-event

**Agent:** test-engineer

`TEST_DIR` resolves to `src/test/resources/` because the target module is the project root.

**Files:**
- Create: `src/test/resources/broker-event-to-tms-event.camel.it.yaml`
- Create: `src/test/resources/test-data/broker-event-to-tms-event-input.json`
- Create: `src/test/resources/test-data/broker-event-to-tms-event-expected.json`
- Create/Modify: `src/test/resources/jbang.properties`

**Guides to Load:**
- `camel-test/guides/route-analysis.md` — identify testable behaviors
- `camel-test/guides/test-generation.md` — test patterns and templates
- `camel-test/guides/test-configuration.md` — test environment setup
- `camel-test/guides/test-runner.md` — execution and verification

**MCP Tools:**
- `camel_catalog_component_doc(component="platform-http", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `camel_catalog_component_doc(component="http", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")`
- `citrus_catalog_actions` — confirm the Citrus 5.0.1 action vocabulary
- `citrus_catalog_endpoint_schema` — confirm HTTP endpoint configuration

**Design Spec Section:** Section 3, Flow: `broker-event-to-tms-event`; Section 7, Acceptance Criteria 4, 5, 6, 8, 9

**Input:** Generated route at `broker-event-to-tms-event.camel.yaml`

- [ ] **Step 1:** Read the generated route YAML for `broker-event-to-tms-event`
- [ ] **Step 2:** Use the design spec's documented `BrokerEventMessage` (a `DRIVING_TO_LOAD` event with a `position`) as the input fixture, and the documented `CreateTmsShipmentEvent` as the expected fixture, corrected to the schema field name `external_order_reference`
- [ ] **Step 3:** Generate the happy-path scenario: push with a valid `X-API-KEY`, assert a 202 response and a matching TMS event with `event_type` `DISPATCHED` (Acceptance Criterion 4)
- [ ] **Step 4:** Generate a scenario per remaining event type asserting the mapping: `ORDER_CREATED→BOOKED`, `CANCEL_ORDER→CANCELLED`, `ORDER_LOADED→PICKED_UP`, `ETA_EVENT→ETA_CHANGED`, `ORDER_DELIVERED→DELIVERED`. Drive these through `POST /api/v1/trigger/events`
- [ ] **Step 5:** Assert `ORDER_CREATED` produces `BOOKED` against a previously tendered shipment, closing the loop from Flow 1 (Acceptance Criterion 6)
- [ ] **Step 6:** Assert that `ORDER_CREATED` and `CANCEL_ORDER` produce an event with **no** `location` object, while the four location-based types produce one populated from `situation.position`
- [ ] **Step 7:** Generate the authentication-failure scenarios: a push with a wrong `X-API-KEY` and a push with the header absent. Both MUST return **401**, create no TMS event, and write nothing to the DLQ (Acceptance Criterion 5)
- [ ] **Step 8:** Generate the path-injection scenario: a pushed event whose `shipmentId` is not a valid UUID is rejected to the DLQ with no outbound call made (Acceptance Criterion 9)
- [ ] **Step 9:** Generate a payload-shape scenario covering both a JSON array of events and a single JSON object, confirming the design spec's OQ-1 assumption. Record the observed real shape from `/trigger/events` so OQ-1 can be closed
- [ ] **Step 10:** Assert no API key, credential or bearer token appears in captured log output (Acceptance Criterion 8)
- [ ] **Step 11:** Reset sandbox state with `DELETE /api/v1/nuke` between scenarios
- [ ] **Step 12:** Add Citrus 5.0.1 test dependencies to `src/test/resources/jbang.properties` via `run.deps` if not already present from Task 9. Do NOT create a `pom.xml`
- [ ] **Step 13:** Verify: `camel test run src/test/resources/broker-event-to-tms-event.camel.it.yaml` exits 0 with a passing summary

**Review:**
- [ ] Spec compliance: all 6 event types asserted; conditional `location` verified both ways; Acceptance Criteria 4, 5, 6, 8 and 9 covered
- [ ] Code quality: authentication failures asserted for both wrong-key and missing-key cases; no fixed sleeps where a wait-for-condition is available; sandbox state reset between scenarios
