# Design Interview — Q&A Transcript

**Pipeline:** `001-tms-shipments-broker-orders`
**Date:** 2026-09-25
**Skill chain:** `/camel-start` → `/camel-brainstorm` → `/camel-plan` → `/camel-execute`
**Project:** Camel 4.22.1, runtime `main`, greenfield

This is the complete question-and-answer record from the design interview, in the order it happened. Questions marked **[choice]** were presented as multiple-choice; the rest were open text.

---

## 1. Project name

**Q:** What is the name of this integration project?

**A:** TMS shipments to broker orders

> Pipeline `001-tms-shipments-broker-orders` was created from this.

---

## 2. Business purpose

**Q:** What is the business purpose of this integration? What problem does it solve, what value does it deliver, who benefits?

**A:** Outbound shipments from a TMS are now manually typed into the portal of a broker. By integrating the two, this manual work is eliminated. This benefits everyone in the process.

---

## 3. Systems landscape

**Q:** Which concrete systems are we connecting — which TMS product, which broker platform, and is there a third system in the picture?

**A:** To start, we are integrating API endpoints from a sandbox. The sandbox has TMS and broker endpoints. `http://localhost:8000/docs`

> At this point the OpenAPI document was read directly from the running sandbox (`Integration Sandbox v1.0.5`). That resolved several planned interview questions without asking them: systems, technologies, message formats (JSON), auth mechanism (OAuth2 client credentials), and the available endpoints.

---

## 4. Flow scope — asked three times

### 4a. First attempt **[choice]**

**Q:** Which flows are in scope for this iteration?

Options offered: both legs (round trip) · outbound leg only · status sync only.

**Sent back for clarification.**

**A:** Trigger endpoint can be used to send broker events to TMS.

> **Correction #1.** I had concluded from the OpenAPI spec that the sandbox was pull-only and that polling was the only option. Wrong. The `/api/v1/trigger/*` endpoints take a `target_url` and **POST the generated payload to it**, then report back `target_url_response_status`. The sandbox pushes.

### 4b. Second attempt **[choice]**

**Q:** Which flows are in scope for this iteration? (reformulated, now push-aware)

Options offered: both legs (round trip) · outbound leg only · outbound now, inbound next.

**Sent back for clarification.**

**A:** I want to pull shipments and push events.

> This settled both scope *and* trigger style in one sentence:
> - **Flow 1 (pull):** poll `GET /api/v1/tms/shipments/new` → map → `POST /api/v1/broker/order`
> - **Flow 2 (push):** sandbox pushes broker events to a Camel HTTP listener → map → `POST /api/v1/tms/event/{shipment_id}`

---

## 5. Poll semantics **[choice]**

**Q:** When Camel polls `GET /api/v1/tms/shipments/new` repeatedly, what comes back the second time?

| Option | Meaning |
|---|---|
| Self-clearing on read | Fetching marks it consumed; no idempotency needed |
| **Cleared by TMS event post** ← chosen | Stays new until we `POST /tms/event/{id}`; needs idempotent consumer |
| Always returns everything | No server state at all; de-dup entirely ours |
| Not sure — let's probe it | Drive the live sandbox to find out |

**A:** Cleared by TMS event post.

---

## 6. Acknowledgement strategy **[choice]**

**Q:** After Flow 1 gets `202` from `POST /broker/order`, what clears the shipment's `new` state?

| Option | Meaning |
|---|---|
| **Flow 2 closes the loop** ← chosen | Flow 1 only tenders; the broker's `ORDER_CREATED` becomes `BOOKED` via Flow 2, which clears it |
| Flow 1 acks immediately | Flow 1 posts its own `BOOKED`; risks a duplicate when the broker's event arrives |
| Flow 1 acks, distinct type | Flow 1 posts `DISPATCHED`, Flow 2 posts `BOOKED`; leaves `DRIVING_TO_LOAD` homeless |

**A:** Flow 2 closes the loop.

> Consequence: Flow 2 is the **only** writer of TMS events, and `ORDER_CREATED → BOOKED` is what closes the loop Flow 1 opens.

---

## 7. Idempotency — withdrawn after a correction

### 7a. First attempt **[choice]**

**Q:** Which idempotent repository should Flow 1 use to prevent duplicate broker tenders?

Options offered: file-based · in-memory · database (JDBC) · Caffeine with TTL.

**Sent back for clarification.**

**A:** Hold on, I made a mistake. As soon as the shipment is validated on the other side (immediately after receiving the correct payload) it will disappear from `/new`.

> **Correction #2** — and it invalidated two of my conclusions:
> - Flow 1 does **not** have two sinks. `POST /broker/order` is its only sink.
> - A **durable** idempotent repository buys nothing. Anything already accepted is gone from `/new`, so a restart simply re-reads what genuinely is still new. Persistence would protect against a duplicate that cannot happen.
>
> What survived: Flow 2 remains the sole writer of TMS events.

### 7b. Revised question **[choice]**

**Q:** A poll can fire while the previous batch is still being tendered, re-reading shipments that aren't cleared yet. How should Flow 1 handle that?

| Option | Meaning |
|---|---|
| **Non-overlapping poll only** ← chosen | Single-threaded scheduler; no idempotency state at all |
| Overlap guard + in-memory dedup | Plus a `MemoryIdempotentRepository` as belt-and-braces |
| Claim by paging instead | Drain with `skip`/`limit` paging per run |

**A:** Non-overlapping poll only.

---

## 8. Line-item mapping — answered by documentation

**Q [choice]:** How should `TmsLineItem[]` map to `handlingUnits[]`, and what is `quantity.grossWeight`? (`BrokerHandlingUnit` has no package-count field, and it was ambiguous whether `package_weight` meant one package or the whole line.)

Options offered: one HU per line with per-package weight · one HU per package · one HU per line with line-total weight.

**Sent back for clarification.**

**A:** For mapping specs pull:
- `https://github.com/atetz/integration-sandbox/blob/main/docs/integrations/tms-to-broker.md`
- `https://github.com/atetz/integration-sandbox/blob/main/docs/integrations/broker-to-tms.md`

> Both documents were fetched and resolved the entire mapping — no further mapping questions were needed. The answer was **one handling unit per package** (expand `total_packages`), with per-unit `grossWeight`, and `quantity.grossWeight = SUM(package_weight × total_packages)`.
>
> Verified arithmetically against the documented example: `7069.62×4 + 2336.24×1 + 890.43×3 = 33286.01`, matching the documented value exactly, and 4+1+3 = **8 handling units**.

### Discrepancies found in the mapping docs

These were recorded as flagged open questions rather than silently decided:

| # | Issue | Resolution taken |
|---|---|---|
| OQ-2 | `meta.senderId` documented as a fixed org id, but the example's value equals `customer.id` | Externalised as config |
| OQ-3 | `transportMode`: Source column says `mode`, Notes say fixed `"road"`, example emits `"ROAD"` | Constant `"ROAD"` |
| OQ-4 | `goodsDescription` example concatenates line items in **reverse** order | Document order |
| — | `external_order_refere` in the broker-to-tms example | Truncated typo; schema name is authoritative |

Also noted: `order.eta` has no target field in `CreateTmsShipmentEvent`, so an `ETA_EVENT → ETA_CHANGED` necessarily arrives without its new ETA.

---

## 9. Error handling — explained, then re-asked

### 9a. First attempt **[choice]**

**Q:** How should the flows handle failures when posting to the broker or the TMS?

Options offered: split by failure class · uniform retry + DLQ · add a circuit breaker.

**Sent back for clarification.**

**A:** Explain the options.

> Explaining it surfaced something that changed the question itself: because a shipment clears only when the broker *validates* it, a **422 becomes an infinite loop** — the shipment is re-read, re-mapped and re-rejected on every poll forever. Retry settings don't fix that; only suppressing the shipment id does. Meanwhile Flow 2 has **no** retry at all, because the sandbox never re-sends a push.

### 9b. Revised, two questions **[choice]**

**Q1:** A shipment the broker rejects with 422 stays `new` and is re-tendered on every poll, forever. How should Flow 1 break that loop?

| Option | Meaning |
|---|---|
| **Suppress after first 422** ← chosen | Record the id, skip it later; DLQ once instead of every cycle |
| Let it loop, log once per cycle | No state; fix the mapping and it self-heals |
| Uniform retry, no special 422 | One handler for everything |

**A1:** Suppress after first 422.

**Q2:** Flow 2 receives pushed events with no re-delivery. What should it return to the sandbox, and where do failed events go?

| Option | Meaning |
|---|---|
| **Accept fast, DLQ on failure** ← chosen | Return 202 immediately, then map and deliver; DLQ on failure |
| Process, then mirror the outcome | Return 202/500 reflecting the real result |

**A2:** Accept fast, DLQ on failure.

---

## 10. Transformation and cadence **[choice]**

**Q1:** How should the `TmsShipment → CreateBrokerOrderMessage` mapping be implemented?

| Option | Meaning |
|---|---|
| DataSonnet | Purpose-built JSON-to-JSON mapping language |
| **Groovy bean** ← chosen | Business logic in a bean per constitution rule 3; house-consistent |
| Java bean + Jackson POJOs | Fully typed, compiler-checked, most ceremony |
| JQ expression | Compact and inline, but dense at 35 fields |

**A1:** Groovy bean.

**Q2:** How often should Flow 1 poll `GET /api/v1/tms/shipments/new`?

Options: every 10 seconds · **every 60 seconds** ← chosen · every 5 seconds.

**A2:** Every 60 seconds.

---

## 11. Deployment, observability, testing **[choice]**

**Q1:** What is the deployment target?

Options: local JBang · Docker Compose · **local now, container later** ← chosen.

**A1:** Local now, container later.

**Q2:** What observability do you want built in?

Options: **structured logging only** ← chosen · logging + health and metrics.

**A2:** Structured logging only.

**Q3:** What integration testing approach should the plan include?

Options: **Citrus against the live sandbox** ← chosen · Citrus with mocked endpoints · unit-test the mapping first.

**A3:** Citrus against the live sandbox.

---

## 12. Constraints, scope boundaries, and assumptions

**Q:** Any other constraints? Anything explicitly out of scope? Plus six assumptions listed for correction:

1. Flow 2 listens on `platform-http` at `localhost:8080/events`
2. The trigger pushes a JSON array; the route splits it
3. **Our inbound listener is unauthenticated**
4. Credentials externalised as placeholders, supplied by env var
5. `oauth2CacheTokens=true` with the 3600s default expiry
6. Flow 1's rejected-shipment set is in-memory, rebuilt on restart

**A:** 3. The sandbox will in fact send a header that we must verify. `X-API-KEY`

> **Correction #3.** Assumption 3 was wrong in the most important direction — it assumed an absence of security where there was a requirement. Revised: Flow 2 authenticates **every** push by verifying `X-API-KEY` before any processing. A mismatched or missing key returns **401**, enqueues nothing, and writes no DLQ entry (a failed authentication is not a failed event). The check lands *ahead* of the "accept fast, return 202" step, the key is masked in logs, and the comparison is length-constant.

**Q (re-asked):** Any other constraints? Anything else out of scope?

**A:** None, assemble the spec.

---

## 13. Approval

**Q:** Do you approve this design?

**A:** Should credentials not be in env vars?

> A fair challenge. The spec *said* env vars in prose, but the properties block still listed `sandbox.client.id=CHANGEME` — which invites exactly the mistake of filling it in and committing it. Made structural instead:
>
> ```properties
> # secrets — resolved from the environment, never stored here
> sandbox.client.id={{env:SANDBOX_CLIENT_ID}}
> sandbox.client.secret={{env:SANDBOX_CLIENT_SECRET}}
> sandbox.inbound.api.key={{env:SANDBOX_INBOUND_API_KEY}}
> ```
>
> Three reasons, verified against the 4.22.1 `properties-component` docs: there is no slot to fill; an unset variable fails at **startup** rather than as an opaque 401 a minute later; and `environment-variable-mode` defaults to `2` (override), so an env var already beats the file value — keeping a secret in the file buys nothing and leaks something.

**Q:** Do you approve this design?

**A:** Yes.

---

## Summary

**14 question rounds. 4 sent back for clarification. 3 corrections that changed the design.**

The three corrections, in order of how much they mattered:

1. **`X-API-KEY`** — I assumed no inbound authentication. The opposite was true. Assuming absence of security is the worst direction to be wrong in.
2. **Shipment clearing** — I had the trigger mechanism wrong, which had cascaded into a two-sink Flow 1 and a mandatory durable idempotent repository. Both evaporated.
3. **Push vs pull** — I read the OpenAPI spec and concluded "no webhook, polling only", missing that `/trigger/*` takes a `target_url`.

What the documentation resolved without any questions: the entire field mapping (35+ fields, two enum tables), which would otherwise have been many rounds of interview — plus four internal discrepancies in those same documents that became tracked open questions rather than silent guesses.

### Final design

| | Flow 1 — `tms-shipment-to-broker-order` | Flow 2 — `broker-event-to-tms-event` |
|---|---|---|
| Trigger | `timer` @ 60s (non-overlapping) | `platform-http` push, `X-API-KEY` verified |
| Source | `GET /api/v1/tms/shipments/new?limit=50` | `POST /events` → 202 → `seda` |
| Mapping | Groovy bean, 1 HU per package | Groovy bean, 6-value enum table |
| Sink | `POST /api/v1/broker/order` | `toD POST /api/v1/tms/event/{shipmentId}` |
| 422 | No retry, suppress id, DLQ once | n/a |
| Transient | Retry ×3, backoff ×2, then DLQ | Retry ×3, backoff ×2, then DLQ |

Plus two hardening items added without being asked: `shipmentId` is UUID-validated before reaching `toD` (it arrives in an externally pushed payload and is interpolated into a dynamic URI), and `oauth2CacheTokens` is set explicitly because it defaults to `false`.
