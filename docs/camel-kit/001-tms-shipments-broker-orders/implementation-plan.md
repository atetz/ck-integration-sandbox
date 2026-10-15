---
staleness:
  stale: false
  since: null
  reason: null
generated:
  at: "2026-10-15T14:20:00Z"
  by: "camel-plan"
  from: "design-spec.md"
---
# TMS Shipments to Broker Orders — Implementation Plan (Amendment Delta)

> **For agentic workers:** Use camel-execute to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the already-implemented integration in line with the 2026-10-15 design amendment: `meta.senderId` is the fixed organisation id `KITT`, and `shipment.transportMode` is confirmed as the constant `"ROAD"`.

**Architecture:** Unchanged. Two Camel Main YAML flows (`tms-shipment-to-broker-order`, `broker-event-to-tms-event`) with Groovy mapping beans, OAuth2 on the `http` component, and environment-resolved secrets. This plan is a **delta** over the implementation produced by the previous plan revision (2026-09-25, 10/10 tasks executed and verified): only artifacts affected by the amendment are modified. Every other generated file, including the fixes applied during that run's reviews, is kept as-is and must not be regenerated.

**Tech Stack:** Apache Camel 4.22.1, runtime `main` (JBang CLI), YAML DSL, Groovy beans, components `timer`/`http`/`platform-http`/`seda`/`file`, EIPs `split`/`choice`/`filter`/`toD`, dataformat `jackson`, languages `groovy`/`simple`. Tests with Citrus 5.0.1.

**Design Spec:** `docs/camel-kit/001-tms-shipments-broker-orders/design-spec.md` (approved 2026-09-25, amended and re-approved 2026-10-15)

**Path resolution:** Runtime is Main and `Target Module` is the project root, so `MODULE_PREFIX` is the empty string. `ROUTE_DIR`, `PROPS_DIR` and `MODULE_DIR` all resolve to the project root; `TEST_DIR` is `src/test/resources/`.

**Amendment impact analysis:**

| Design change | Artifact | Impact |
|---|---|---|
| `broker.sender.id=KITT` (formerly OQ-2) | `application.properties` | **Change** — still `CHANGEME` with an "OQ-2 open" comment → Task 1 |
| `broker.sender.id=KITT` | `src/test/resources/tms-shipment-to-broker-order.citrus.it.yaml` | **Change** — pins `CHANGEME` and forces it with `--prop`, so the mapping assertion never checks the configured value → Task 2 |
| `broker.sender.id=KITT` | `ShipmentToBrokerOrderMapper.groovy` | None — already resolves `{{broker.sender.id}}` at runtime |
| `transportMode="ROAD"` confirmed (formerly OQ-3) | `ShipmentToBrokerOrderMapper.groovy`, `tms-shipment-to-broker-order-expected.json` | None — both already emit/expect `"ROAD"` |
| OQ-2/OQ-3 removed from Open Questions | Flow 2 artifacts, routes, `run.sh`, beans | None |

**Excluded by design — do not implement:** containerisation/Docker Compose, metrics and health endpoints, circuit breakers, a durable rejected-shipment store, inbound OAuth2 (`oauthProfile`), `messageFunction` 4/1, and any deduplication or resequencing in Flow 2. See the design spec's `## Not Doing (and Why)`.

**Out of scope for this delta:** the five open items in the previous execution report's cross-cutting review (Flow 2 non-transient retries, per-shipment `toD` token fetches, 422 body logging, unhandled mapper exceptions in Flow 1, `camel.jbang.dependencies` omitting `camel-direct`/`camel-bean`). They are not part of the amendment and need their own design decision.

---

```yaml plan-metadata
tasks:
  - id: 1
    title: Update Application Properties — broker.sender.id
    files:
      modifies:
        - application.properties
    provides:
      properties:
        - broker.sender.id
    consumes: {}
    dependsOn: []

  - id: 2
    title: Update Integration Tests — tms-shipment-to-broker-order
    files:
      modifies:
        - src/test/resources/tms-shipment-to-broker-order.citrus.it.yaml
      reads:
        - application.properties
        - src/test/resources/test-data/tms-shipment-to-broker-order-expected.json
        - tms-shipment-to-broker-order.camel.yaml
        - ShipmentToBrokerOrderMapper.groovy
    provides:
      testData:
        - tms-shipment-to-broker-order-sender-id
    consumes:
      properties:
        - broker.sender.id
      routes:
        - shipment-ingestion
        - shipment-tender
      beans:
        - shipmentToBrokerOrderMapper
      externalServices:
        - integration-sandbox-tms
        - integration-sandbox-broker
    dependsOn:
      - 1
```

---

### Task 1: Update Application Properties — broker.sender.id

**Agent:** implementation-engineer

**Files:**
- Modify: `application.properties`

**Guides to Load:**
- `camel-implement/guides/properties-generation.md`

**MCP Tools:**
- `camel_configuration_validate` — properties validation gate

**Design Spec Section:** Section 3, Flow: `tms-shipment-to-broker-order`, Configuration Properties; Section 3, Shared Configuration (note on `broker.sender.id`)

<HARD-RULE>
Modify only the `broker.sender.id` entry and its comment. Every other line of `application.properties`, including `camel.server.maxBodySize`, `camel.main.logMask`, `camel.main.autoConfigurationLogSummary` and the `{{env:...}}` secret references, MUST remain byte-for-byte unchanged.
</HARD-RULE>

- [ ] **Step 1:** Read the Flow 1 Configuration Properties block and the Shared Configuration note in the design spec. Confirm the approved value is `KITT` and that it is a reviewable organisation identifier, not an environment-bound credential
- [ ] **Step 2:** Change the line `broker.sender.id=CHANGEME` to `broker.sender.id=KITT`. Write the literal value — do NOT turn it into an `{{env:...}}` reference
- [ ] **Step 3:** Replace the comment above it (`# Organisation identifier, not a credential - value still open (design OQ-2)`) with one that no longer references an open question, e.g. stating it is the sending organisation's fixed id
- [ ] **Step 4:** Run `camel_configuration_validate` against `application.properties` and confirm no new findings
- [ ] **Step 5:** Verify: `grep -x 'broker.sender.id=KITT' application.properties && ! grep -q 'CHANGEME\|OQ-2' application.properties && echo OK` prints the matching line followed by `OK`

**Review:**
- [ ] Spec compliance: `broker.sender.id` equals `KITT` exactly; it is a literal configuration value, not environment-bound; no other property changed (`git diff --stat application.properties` shows a two-line change at most)
- [ ] Code quality: constitution rule 6 — no secret literals introduced; comment no longer points at a resolved design question

---

### Task 2: Update Integration Tests — tms-shipment-to-broker-order

**Agent:** test-engineer

`TEST_DIR` resolves to `src/test/resources/` because the target module is the project root. The existing suite is named `*.citrus.it.yaml` (the previous execution recorded that `camel test run` ignores `*.camel.it.yaml`); keep that file name.

**Files:**
- Modify: `src/test/resources/tms-shipment-to-broker-order.citrus.it.yaml`

**Guides to Load:**
- `camel-test/guides/route-analysis.md` — identify testable behaviors
- `camel-test/guides/test-generation.md` — test patterns and templates
- `camel-test/guides/test-configuration.md` — test environment setup
- `camel-test/guides/test-runner.md` — execution and verification

**MCP Tools:**
- `camel_catalog_component_doc(component="http", runtime="main", platformBom="org.apache.camel:camel-catalog:4.22.1")` — understand the tender assertion point
- `citrus_catalog_actions` — confirm the Citrus 5.0.1 action vocabulary for the `camel.cli.run` args being edited

**Design Spec Section:** Section 3, Flow: `tms-shipment-to-broker-order`, Mapping row `meta.senderId`; Section 7, Acceptance Criterion 2

**Input:** Generated route at `tms-shipment-to-broker-order.camel.yaml` (unchanged) and `application.properties` as updated by Task 1

**Why this task exists:** Scenario A currently declares the Citrus variable `broker.sender.id` as `CHANGEME` and passes `--prop=broker.sender.id=${broker.sender.id}` to the integration. That override makes the route emit whatever the test chose, so the AC 2 mapping assertion cannot detect a wrong value in `application.properties`. The test must instead assert that the value coming from the application's own configuration is `KITT`.

- [ ] **Step 1:** Read the existing suite in full, plus the expected fixture `src/test/resources/test-data/tms-shipment-to-broker-order-expected.json` (its `meta.senderId` is `"${broker.sender.id}"`, resolved from the Citrus variable)
- [ ] **Step 2:** Change the Citrus variable `broker.sender.id` from `CHANGEME` to `KITT`
- [ ] **Step 3:** Remove the `- "--prop=broker.sender.id=${broker.sender.id}"` argument from Scenario A's `flow1-mock` run, so the route resolves `broker.sender.id` from `application.properties`. Leave every other `--prop` argument (mock URLs, DLQ directory, inbound port) unchanged
- [ ] **Step 4:** Confirm `src/test/resources/application-test.properties` does not override `broker.sender.id` (it currently does not); do not add an override there
- [ ] **Step 5:** Leave the expected fixture unchanged: it already expects `"transportMode": "ROAD"`, 8 handling units and `quantity.grossWeight` `33286.01`
- [ ] **Step 6:** Leave Scenario B (live sandbox) unchanged; it runs with the application's properties and now tenders with `senderId` `KITT`
- [ ] **Step 7:** Verify the negative case once by hand: temporarily set `broker.sender.id=WRONG` in a scratch copy of the properties (not in the repository file) or reason through the edited args to confirm the AC 2 body assertion would now fail on a mismatched sender id. Do not commit any temporary change
- [ ] **Step 8:** Verify: `camel test run src/test/resources/tms-shipment-to-broker-order.citrus.it.yaml` exits 0 with a passing summary (requires the sandbox on `localhost:8000` and credentials in `../.env`, as in the previous run)

**Review:**
- [ ] Spec compliance: the AC 2 assertion now checks `meta.senderId` = `KITT` sourced from `application.properties`; `transportMode` `"ROAD"` still asserted; Acceptance Criteria 1, 2, 3, 7 and 8 remain covered
- [ ] Code quality: no test-only override masks the production value; no other scenario or fixture changed; no credential introduced into the suite
