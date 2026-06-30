# SIF Test Provider

Standalone Spring Boot application that acts as a SIF 3 provider for testing the OSDB broker. It handles both **IMMEDIATE** (synchronous proxy) and **DELAYED** (queue-based) request choreographies.

> **Not a Maven reactor module.** This app has its own `pom.xml` and is built independently of the main `sif-unity-broker` project.

> **Test harness only.** This provider exists to exercise the OSDB broker from the SIF certification
> suite (Bruno). It is **not** a real or shippable SIF provider. The intended flow is: start it with no
> credentials, let the Bruno suite register the provider environment + queue, then have the suite push
> credentials/context at runtime via the unauthenticated [`/test-control`](#test-control-endpoints-runtime-configuration)
> endpoints and drive DELAYED polling on demand — no restart, no manual config.

---

## What It Does

| Choreography | How It Works |
|---|---|
| **IMMEDIATE** | The broker proxies consumer requests to this provider's HTTP endpoints. The provider responds synchronously with test data. |
| **DELAYED** | The provider polls its queue **on demand** (one cycle per `POST /test-control/poll` call) for pending requests, generates a response, submits it via the Responses Connector, and acknowledges the message. There is no background polling timer. |

Every response contains the same test object shape:

```json
{
  "TESTOBJECT": {
    "RefId": "D3E34B359D75101A8C3D00AA001A1652",
    "Name": "2026-04-15T10:30:00.000Z-TESTOBJECT"
  }
}
```

### Paged Query Support

When a GET collection request includes `queryIntention: ALL` and `navigationPageSize` headers, the provider returns paginated responses with full SIF navigation headers. This exercises the broker's paging loop for DELAYED GET queries.

**Trigger conditions** (both required):
- `queryIntention: ALL` header
- `navigationPageSize: <N>` header (number of records per page)

**Behavior**: Returns 7 pages of data, each containing exactly N records. Each record has a unique sequential identifier:

```json
{
  "TESTOBJECTs": {
    "TESTOBJECT": [
      {
        "RefId": "D3E34B359D75101A8C3D00AA001A1652-P2R1-G6",
        "Name": "Page 2 Record 1 (Global #6 of 35)",
        "echo_PageNumber": 2,
        "echo_RecordIndexOnPage": 1,
        "echo_GlobalRecordIndex": 6,
        "echo_TotalPages": 7,
        "echo_Timestamp": "2026-04-20T..."
      }
    ]
  }
}
```

> The `echo_*` fields are **diagnostic test data only** — they are echoed into the record body so you
> can eyeball which page/record a response came from. They carry no SIF meaning and drive nothing. The
> real paging contract lives entirely in the SIF navigation **response headers** below.

**Response headers** (all 5 SIF navigation headers):

| Header | Value | Description |
|---|---|---|
| `navigationPage` | Current page (1-7) | Which page this response contains |
| `navigationLastPage` | `7` | Total number of pages |
| `navigationPageSize` | Echoed from request | Records per page |
| `navigationCount` | `7 × pageSize` | Total records across all pages |
| `navigationId` | Stable cursor ID | Carried forward from request, or generated on first page |

**Optional request headers**:
- `navigationPage: <N>` — request a specific page (default: 1)
- `navigationId: <id>` — carry forward the cursor from a previous response

---

## Prerequisites

**To build and start the provider:**

- Java 17+
- Maven 3.9+

The provider has **no startup dependency on the broker** — it boots with no credentials, exposes its
endpoints, and gets its broker URL and credentials injected at runtime via
[`POST /test-control/configure`](#test-control-endpoints-runtime-configuration).

**To run an end-to-end test flow**, you additionally need a reachable OSDB broker (default
`http://localhost:8080`): the broker proxies IMMEDIATE requests *into* the provider, and the provider
makes outbound DELAYED calls to the broker's `gatewayUrl`. The broker only needs to be up when you
actually exercise a request flow, not when the provider starts.

---

## Quick Start

The normal way to run this provider is **driven by the Bruno certification suite** — you start it
empty and the suite configures it at runtime. No credentials, no editing config files, no restart.

### 1. Build

```bash
cd test-provider
mvn clean package -DskipTests
```

### 2. Run it (no credentials needed)

```bash
java -jar target/test-provider-1.0.0-SNAPSHOT.jar
```

The provider starts on port **9091** by default, under the servlet context path `/sif/provider`
(base URL `http://localhost:9091/sif/provider`). IMMEDIATE endpoints are public and work immediately;
DELAYED polling stays disabled until it is configured at runtime.

### 3. Let the suite configure it at runtime

The Bruno suite registers the provider environment + provider queue, then pushes the resulting
`sessionToken`, `sharedSecret`, `domainId`, `solutionId`, and `queueId` to the provider via
[`POST /test-control/configure`](#test-control-endpoints-runtime-configuration). The change applies to
the next outbound broker call and the next triggered poll — no restart. Use
[`GET /test-control/status`](#test-control-endpoints-runtime-configuration) to confirm state at any time.

If you are driving the provider by hand instead of through Bruno, make the same `configure` call with
`curl` (see the [Test Control Endpoints](#test-control-endpoints-runtime-configuration) section).

### 4. Register as a Provider in the Broker

The suite registers this provider's service URL with the broker so it knows where to proxy IMMEDIATE
requests (done for you by Bruno; shown here for reference):

```
POST {{gatewayUrl}}/{{domainId}}/{{solutionId}}/services
{
  "serviceName": "StudentPersonals",
  "serviceType": "OBJECT",
  "providerEnvironmentId": "<provider-env-id>",
  "providerUrl": "http://localhost:9091/sif/provider"
}
```

### 5. Test IMMEDIATE Requests

From a consumer, send a request through the broker:

```
POST {{gatewayUrl}}/{{domainId}}/{{solutionId}}/requests/StudentPersonals
requestType: IMMEDIATE
```

The broker proxies to this provider, which returns test data. No provider credentials required.

### 6. Test DELAYED Requests

1. Configure a complete credential set **and** a `queueId` at runtime (step 3).
2. From a consumer, send a DELAYED request through the broker.
3. The broker enqueues the request to the provider's queue.
4. Trigger one poll cycle with `POST /test-control/poll`. The provider picks up the request and submits a response. (There is no background timer — each cycle runs only when you call the endpoint.)
5. The consumer can then poll their own queue for the response.

> **Optional: startup configuration (legacy / standalone use).** Startup binding still works for
> backward compatibility — you can supply `provider.*` values via environment variables
> (`PROVIDER_SESSION_TOKEN`, `PROVIDER_SHARED_SECRET`, `PROVIDER_DOMAIN_ID`, `PROVIDER_SOLUTION_ID`,
> `PROVIDER_QUEUE_ID`), CLI properties (`--provider.session-token=...`), or by editing the `provider:`
> section of `application.yml`. This is only useful when running the provider standalone without the
> suite; a later runtime `configure` call replaces whatever was set at startup. Either way, DELAYED
> cycles only run when you call `POST /test-control/poll` — there is never any background polling.

---

## Exposing the Provider with ngrok (remote broker)

When the broker runs **remotely** (e.g. the AWS demo environment) it cannot reach
`localhost:9091`. [ngrok](https://ngrok.com) tunnels your local provider to a public HTTPS URL the
broker can proxy IMMEDIATE requests to.

> **Only IMMEDIATE needs the tunnel.** IMMEDIATE is broker→provider (inbound), so the provider must be
> publicly reachable. DELAYED is provider→broker (outbound HMAC calls to `gatewayUrl`), so it works
> without any tunnel. If you only test DELAYED against a remote broker, you don't need ngrok at all —
> just point `gatewayUrl` at the remote broker.

### 1. Install and authenticate ngrok

```bash
# install (see https://ngrok.com/download), then register your authtoken once
ngrok config add-authtoken <your-ngrok-authtoken>
```

### 2. Start the provider, then the tunnel

```bash
# terminal 1 — provider on 9091
java -jar target/test-provider-1.0.0-SNAPSHOT.jar

# terminal 2 — tunnel to the provider's port
ngrok http 9091
```

ngrok prints a public forwarding URL, e.g.:

```
Forwarding   https://a1b2-203-0-113-7.ngrok-free.app -> http://localhost:9091
```

The provider's public base URL is that forwarding URL **plus the context path** `/sif/provider`:

```
https://a1b2-203-0-113-7.ngrok-free.app/sif/provider
```

### 3. Register that URL as the provider's service URL

Register the service with the broker using the ngrok base URL as `providerUrl` (the suite does this
for you — point the suite's provider-URL variable at the ngrok URL):

```
POST {{gatewayUrl}}/{{domainId}}/{{solutionId}}/services
{
  "serviceName": "StudentPersonals",
  "serviceType": "OBJECT",
  "providerEnvironmentId": "<provider-env-id>",
  "providerUrl": "https://a1b2-203-0-113-7.ngrok-free.app/sif/provider"
}
```

For the Bruno suite, set the provider endpoint variable in your environment file
(`bruno-certification-tests/environments/*.bru`) to the ngrok base URL, e.g.
`immediateProviderEndpointUrl: https://a1b2-203-0-113-7.ngrok-free.app/sif/provider`. The
`/test-control/*` calls go to the same base URL.

### 4. Verify the tunnel

```bash
curl https://a1b2-203-0-113-7.ngrok-free.app/sif/provider/test-control/status
```

> ⚠️ **ngrok makes the unauthenticated `/test-control/*` endpoints publicly reachable.** Anyone with
> the URL can set the provider's broker credentials or trigger a poll. The forwarding URL changes on
> each restart (on the free tier) which limits exposure, but you should still keep the tunnel up only
> while actively testing and tear it down afterward. To restrict access, prefer an ngrok **IP
> allowlist / traffic policy** (allow only the broker's egress IP). Avoid tunnel-level basic auth here —
> it would also reject the broker's inbound IMMEDIATE proxy calls, since the broker sends SIF HMAC, not
> basic-auth credentials. Never leave a tunnel to this provider running unattended.

---


> **Test-only scaffolding.** These endpoints let the Bruno certification suite push
> credentials/context to a running provider and drive DELAYED polling on demand, removing the
> configure-and-restart cycle. They are **unauthenticated** by design.

All three endpoints are mounted at `/test-control` under the configured servlet context path. With
the defaults from `application.yml` (`server.port: 9091`, `server.servlet.context-path: /sif/provider`),
the full URLs are:

- `POST http://localhost:9091/sif/provider/test-control/configure`
- `GET  http://localhost:9091/sif/provider/test-control/status`
- `POST http://localhost:9091/sif/provider/test-control/poll`

> ⚠️ **Localhost-only security note.** These endpoints have **no authentication** and can set the
> broker credentials the provider signs outbound calls with. Bind the provider to `127.0.0.1`/loopback
> or a trusted, isolated test network only. Never run it on a shared or internet-reachable host, and
> **never ship this endpoint in a real or production provider** — it is test scaffolding. Secrets are
> masked on every read, and the broker still enforces real HMAC-SHA256 auth on all outbound calls, so
> broker security coverage is preserved. Configuration is in-memory only and resets to
> `application.yml`/env values on restart.

### `POST /test-control/configure`

Stores the runtime configuration the provider uses for the DELAYED choreography.

**Request body** (`sessionToken`, `sharedSecret`, `domainId`, `solutionId` are **required**;
`gatewayUrl` and `queueId` are optional; unknown fields are ignored):

```json
{
  "gatewayUrl": "http://localhost:8080",
  "sessionToken": "NEW_TOKEN",
  "sharedSecret": "NEW_SECRET",
  "domainId": "d-1",
  "solutionId": "s-1",
  "queueId": "q-123"
}
```

- When `gatewayUrl` is omitted, the existing value is retained.
- A blank or absent `queueId` disables delayed polling.

**Success response** — `200 OK` (secrets masked, never raw):

```json
{
  "configured": true,
  "gatewayUrl": "http://localhost:8080",
  "sessionTokenMasked": "NEW_****OKEN",
  "sharedSecretMasked": "NEW_****CRET",
  "domainId": "d-1",
  "solutionId": "s-1",
  "queueIdConfigured": true,
  "port": 9091,
  "delayedPollingEnabled": true
}
```

**Validation failure** — `400 Bad Request` identifying the offending field(s):

```json
{ "errors": { "solutionId": "is required" } }
```

```bash
# Configure credentials + queue (after Bruno registers the env and creates the queue)
curl -X POST http://localhost:9091/sif/provider/test-control/configure \
  -H 'Content-Type: application/json' \
  -d '{
        "gatewayUrl": "http://localhost:8080",
        "sessionToken": "NEW_TOKEN",
        "sharedSecret": "NEW_SECRET",
        "domainId": "d-1",
        "solutionId": "s-1",
        "queueId": "q-123"
      }'

# Missing a required field -> 400
curl -X POST http://localhost:9091/sif/provider/test-control/configure \
  -H 'Content-Type: application/json' \
  -d '{ "sessionToken": "T", "sharedSecret": "S", "domainId": "d-1" }'
```

### `GET /test-control/status`

Inspects the live provider state without exposing secrets.

**Response** — `200 OK`:

```json
{
  "configured": false,
  "delayedPollingEnabled": false,
  "gatewayUrl": "http://localhost:8080",
  "sessionTokenMasked": "(not set)",
  "sharedSecretMasked": "(not set)",
  "domainId": null,
  "solutionId": null,
  "queueIdConfigured": false
}
```

```bash
curl http://localhost:9091/sif/provider/test-control/status
```

### `POST /test-control/poll`

Runs exactly one DELAYED poll cycle synchronously. No request body.

**Response** — `200 OK`:

```json
{ "polled": true, "messageProcessed": true }
```

- `polled` is `true` only when a complete credential set is present **and** `queueId` is non-blank.
- `messageProcessed` is `true` when a message was found and a response was submitted this cycle.
- When the provider is not fully configured (or `queueId` is blank), it returns
  `{ "polled": false, "messageProcessed": false }` with no error and no queue interaction.

```bash
# Trigger one poll cycle (deterministic — driven by the suite)
curl -X POST http://localhost:9091/sif/provider/test-control/poll
# { "polled": true,  "messageProcessed": true  }   # a message was found and answered
# { "polled": true,  "messageProcessed": false }   # queue was empty this cycle
# { "polled": false, "messageProcessed": false }   # not fully configured / queueId blank
```

---

## Configuration Reference

> These are **startup** properties (legacy / standalone use). The suite-driven path sets credentials at
> runtime via [`POST /test-control/configure`](#test-control-endpoints-runtime-configuration) instead,
> and a runtime `configure` call overrides anything set here.

| Property | Env Variable | Default | Description |
|---|---|---|---|
| `server.port` | `SERVER_PORT` | `9091` | Port this provider listens on |
| `server.servlet.context-path` | `SERVER_SERVLET_CONTEXT_PATH` | `/sif/provider` | Servlet context path prefixed to every endpoint |
| `provider.gateway-url` | `PROVIDER_GATEWAY_URL` | `http://localhost:8080` | Broker gateway URL |
| `provider.session-token` | `PROVIDER_SESSION_TOKEN` | _(required)_ | Provider's session token |
| `provider.shared-secret` | `PROVIDER_SHARED_SECRET` | _(required)_ | Provider's shared secret |
| `provider.domain-id` | `PROVIDER_DOMAIN_ID` | _(required)_ | Domain ID |
| `provider.solution-id` | `PROVIDER_SOLUTION_ID` | _(required)_ | Solution ID |
| `provider.queue-id` | `PROVIDER_QUEUE_ID` | _(empty)_ | Queue ID for delayed polling (leave empty to disable) |

---

## Endpoints Exposed

These are the endpoints the broker proxies IMMEDIATE requests to:

| Method | Path | Description |
|---|---|---|
| `POST` | `/{serviceName}` | Handle create/update request |
| `GET` | `/{serviceName}` | Handle query (returns collection) |
| `GET` | `/{serviceName}/{id}` | Handle query by ID (returns single object) |
| `GET` | `/actuator/health` | Health check |

---

## Service Name Convention

The broker uses the service name to determine choreography:

- **`DELAYED_*`** prefix → delayed choreography (queued)
- **Everything else** → immediate choreography (proxied)

This convention is enforced by the broker, not by this provider. This provider handles both — it responds to proxied HTTP requests (immediate) and polls its queue (delayed).

---

## Authentication

All outbound requests from this provider to the broker use **HMAC-SHA256** authentication:

```
Authorization: SIF_HMACSHA256 Base64(sessionToken:Base64(HMAC-SHA256(sessionToken:timestamp, sharedSecret)))
timestamp: <ISO 8601 timestamp>
```

The `HmacAuthUtil` class handles signature generation, matching the same algorithm used in the Bruno `common/auth.js` helper.

---

## Architecture

```
┌─────────────┐     IMMEDIATE      ┌──────────────┐
│   Consumer   │ ──── request ────▶ │    Broker     │
│              │ ◀── response ──── │   (Gateway)   │
└─────────────┘                    └──────┬───────┘
                                          │ proxy
                                          ▼
                                   ┌──────────────┐
                                   │ Test Provider │ ← this app (port 9091, /sif/provider)
                                   └──────────────┘

┌─────────────┐     DELAYED        ┌──────────────┐
│   Consumer   │ ──── request ────▶ │    Broker     │
│              │                    │   (Gateway)   │
└──────┬──────┘                    └──────┬───────┘
       │                                  │ enqueue
       │                                  ▼
       │                           ┌──────────────┐
       │                           │ Provider Queue│
       │                           └──────┬───────┘
       │                                  │ poll
       │                                  ▼
       │                           ┌──────────────┐
       │                           │ Test Provider │ ← generates response
       │                           └──────┬───────┘
       │                                  │ POST /responses
       │                                  ▼
       │                           ┌──────────────┐
       └──── poll consumer queue ◀ │    Broker     │
                                   └──────────────┘
```
