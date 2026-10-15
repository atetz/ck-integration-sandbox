# ck-integration-sandbox

Integrations for my [integration sandbox](https://github.com/atetz/integration-sandbox), built with [camel-kit](https://github.com/luigidemasi/camel-kit) and Claude Code.

## Why does this exist?

I'm trying out tools for building integrations with AI, and this repo is what camel-kit generated when I asked it to build the flows of my integration sandbox. I only answered questions, reviewed and pushed back, so consider it an example of camel-kit output, not a reference implementation.

<small>A quick refresher: the integration sandbox is a REST API that lets users test two integration flows:
- Pull, transform and send outbound orders.
- Receive, transform and send inbound events.

The API endpoints validate if the user is authenticated and if the message was correctly transformed.</small>

## What's in here?

Two flows, each with a yaml containing the Camel routes, a data mapper in Groovy and a Citrus integration test:

| Flow | What it does | Files |
|---|---|---|
| `tms-shipment-to-broker-order` | Polls the TMS for new shipments every 60s, maps them to broker orders and sends them to the broker. | `tms-shipment-to-broker-order.camel.yaml`, `ShipmentToBrokerOrderMapper.groovy`, `RejectedShipments.groovy` |
| `broker-event-to-tms-event` | Listens on `http://localhost:8080/events` for pushed broker events, maps them to TMS events and sends them to the TMS. | `broker-event-to-tms-event.camel.yaml`, `BrokerEventToTmsEventMapper.groovy` |

Both flows authenticate against the sandbox with OAuth2 client credentials. Inbound pushes are checked for an `X-API-KEY` header. Failed messages end up in `data/dlq/`.

The paper trail camel-kit left behind is worth a look too:

- [`docs/interview-qa.md`](docs/interview-qa.md): the 14 questions of the design interview, with my answers.
- [`docs/camel-kit/001-tms-shipments-broker-orders/design-spec.md`](docs/camel-kit/001-tms-shipments-broker-orders/design-spec.md): the design spec I reviewed and approved.
- [`docs/camel-kit/001-tms-shipments-broker-orders/implementation-plan.md`](docs/camel-kit/001-tms-shipments-broker-orders/implementation-plan.md): the design broken down into tasks.
- [`docs/camel-kit/001-tms-shipments-broker-orders/execution-report.md`](docs/camel-kit/001-tms-shipments-broker-orders/execution-report.md): what was built, what deviated from the plan and what's still open.

## How to run it

### Prerequisites

- [JBang](https://www.jbang.dev/installation/) (it pulls in Camel 4.22.1 for you)
- A running [integration sandbox](https://github.com/atetz/integration-sandbox) on `http://localhost:8000`
- A client id, client secret and inbound API key for the sandbox

### 1. Set your credentials

Credentials are read from environment variables. They are never stored in `application.properties`.

```bash
export SANDBOX_CLIENT_ID=...
export SANDBOX_CLIENT_SECRET=...
export SANDBOX_INBOUND_API_KEY=...
```

### 2. Start both flows

```bash
./run.sh
```

The script checks that the three variables are set and then starts both flows with `jbang camel@apache/camel run`. The inbound listener binds port `8080`, so make sure that one's free.

### 3. Feed it some data

- **Flow 1:** seed some shipments in the TMS with `POST /api/v1/tms/shipments/seed`. Within a minute they get picked up and sent to the broker.
- **Flow 2:** trigger broker events with `POST /api/v1/trigger/events` and use `http://localhost:8080/events` as `target_url`. If the sandbox runs in Docker, use `http://host.docker.internal:8080/events` instead.

Use `DELETE /api/v1/nuke` to start fresh.

Tune the poll interval, batch size, ports and retry behaviour in [`application.properties`](application.properties).

## Running the tests

Each flow has a Citrus integration test. Scenario A runs against a Citrus mock of the sandbox, scenario B against the live sandbox on `localhost:8000`.

The tests read the sandbox credentials from a `.env` file in the project root (it's in `.gitignore`):

```
SANDBOX_CLIENT_ID=...
SANDBOX_CLIENT_SECRET=...
SANDBOX_INBOUND_API_KEY=...
```

Then run them from the project root:

```bash
camel test run src/test/resources/tms-shipment-to-broker-order.citrus.it.yaml
camel test run src/test/resources/broker-event-to-tms-event.citrus.it.yaml
```

Options:
- `-Dsandbox.env.file=...` points to a different credentials file.
- `-Dsandbox.callback.host=...` overrides the host the sandbox pushes events to. It defaults to `host.docker.internal`, because I run the sandbox in Docker.

## Known loose ends

Camel-kit was pretty honest about what it didn't finish. The [execution report](docs/camel-kit/001-tms-shipments-broker-orders/execution-report.md) lists the open items, for example:

- `broker.sender.id` is still `CHANGEME`. The sandbox accepts it, a real broker probably won't.
- Flow 2 retries non-transient failures (4xx, parse errors) before dead-lettering them.
- Flow 2 fetches an OAuth token per shipment, because the token cache is per endpoint.
- Rejected shipments are only remembered in memory, so a restart re-logs each one once. That was a deliberate choice.

This is a sandbox experiment, so I left them as is. **Don't put this in production!**
