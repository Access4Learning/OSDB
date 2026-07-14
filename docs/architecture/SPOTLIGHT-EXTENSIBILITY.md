# SPOTLIGHT: Extensibility — Extending the Broker Without Forking

How implementors add custom behavior to OSDB by dropping a JAR on the classpath, using nothing but standard Spring, Servlet, and WebClient machinery.

---

## Contents

- [Core Design Principle](#core-design-principle)
- [The Three Extension Layers](#the-three-extension-layers)
- [The Drop-in Model (Layer 2)](#the-drop-in-model-layer-2)
- [Per-Tenant Activation via Entity Tags](#per-tenant-activation-via-entity-tags)
- [Worked Example 1 — Inbound: Message Sequencing](#worked-example-1--inbound-message-sequencing)
- [Worked Example 2 — Outbound: Alternate Transport Translation](#worked-example-2--outbound-alternate-transport-translation)
- [Inbound vs. Outbound at a Glance](#inbound-vs-outbound-at-a-glance)
- [Layer 1 — Gateway Filters](#layer-1--gateway-filters)
- [Layer 3 — Pluggable Backends](#layer-3--pluggable-backends)
- [Packaging Checklist](#packaging-checklist)
- [Extension Isolation](#extension-isolation)
- [Why This Design](#why-this-design)
- [Related Documents](#related-documents)

---

## Core Design Principle

**Extend through the container, not the code.**

OSDB is an open-source broker. Implementors will want to add proprietary value on top of it — payload validation, data enrichment, audit logging, transport translation, analytics — without maintaining a fork. The architecture makes this possible by exposing extension points that are wired through Spring's IoC container. An implementor packages their logic as a Spring Boot auto-configured JAR, adds it as a dependency, and Spring discovers the beans automatically. **Core broker code is never touched.**

There is deliberately **no custom `BrokerInterceptor` interface** to learn. Extensions hook into the standard machinery the broker already runs on:

- Gateway → Spring Cloud Gateway `GlobalFilter`
- Inbound message processing → Jakarta servlet `Filter`
- Outbound provider calls → WebClient `ExchangeFilterFunction`
- Infrastructure → an interface + `@ConditionalOnProperty`

If you know Spring Boot, you already know how to extend OSDB.

---

## The Three Extension Layers

Each layer targets a different point in the request lifecycle.

```
                External Client
                      │
                      ▼
        ┌─────────────────────────────┐
        │  API Gateway                │  ◄── Layer 1: GlobalFilter
        │  (Spring Cloud Gateway)     │      (auth, header enrichment, logging)
        └──────────────┬──────────────┘
                       │
                       ▼
        ┌─────────────────────────────┐
        │  Connector Service          │  ◄── Layer 2: Drop-in Extension Module
        │  (events/requests/...)      │      inbound servlet Filter  ┐
        │                             │      outbound WebClient       ├─ message level
        │                             │      ExchangeFilterFunction   ┘
        └──────────────┬──────────────┘
                       │
                       ▼
        ┌─────────────────────────────┐
        │  Pluggable Backends         │  ◄── Layer 3: interface + @ConditionalOnProperty
        │  (storage, secrets)         │      (S3/filesystem, Vault/AES/plaintext)
        └─────────────────────────────┘
```

| Layer | Mechanism | Ordering | Scope | Example |
|-------|-----------|----------|-------|---------|
| **1. Gateway filters** | Spring Cloud Gateway `GlobalFilter` (+ `Ordered`) | `@Order` / `Ordered` | Every request through the gateway | Custom auth, header enrichment, request logging |
| **2. Drop-in extension modules** | Auto-config JAR: inbound Jakarta servlet `Filter` **or** outbound WebClient `ExchangeFilterFunction`, activated per-solution via entity tags, fail-open | `@Order` / registration order | The connector service the JAR is added to | Message sequencing (inbound), alternate transport translation (outbound) |
| **3. Pluggable backends** | Interface + `@ConditionalOnProperty` | n/a (one active impl) | Whole service | `StorageProvider` (S3/filesystem), `SecretsBackend` (Vault/AES/plaintext) |

---

## The Drop-in Model (Layer 2)

Layer 2 is where most custom message logic lives, so it gets the fullest treatment. A drop-in extension is a self-contained Maven module that ships:

1. **The extension logic** — an inbound servlet `Filter` and/or an outbound `ExchangeFilterFunction`.
2. **A `@Configuration` auto-config class** gated by `@ConditionalOnProperty`.
3. **An `AutoConfiguration.imports` file** so Spring Boot discovers it.
4. **A `@ConfigurationProperties` record** for externalized config.

```
extension-myfeature.jar
├── MyFeatureFilter.java                    ← the logic (servlet Filter or WebClient filter)
├── config/
│   ├── MyFeatureAutoConfiguration.java      ← @Configuration + @ConditionalOnProperty
│   └── MyFeatureProperties.java             ← @ConfigurationProperties(prefix = "sif.extensions.myfeature")
└── META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
         (one line: org.sifunity.broker.extension.myfeature.config.MyFeatureAutoConfiguration)
```

Adding this JAR to a connector's `pom.xml` is all that's required. No code changes in the connector.

### Four properties every drop-in extension shares

| Property | What it means | Why |
|----------|---------------|-----|
| **Auto-discovery** | A `@Configuration` class listed in `AutoConfiguration.imports`, gated by `@ConditionalOnProperty(name = "sif.extensions.<name>.enabled", matchIfMissing = true)` | Drop the JAR in, it wires itself. A global kill switch turns it off without removing the JAR. |
| **Per-solution activation** | The extension checks an entity tag (on the solution or environment) looked up against the environment-service, cached in Caffeine | Behavior can be turned on for specific tenants at runtime — no redeploy. |
| **Fail-open** | On any error, log and pass the message through unchanged | An extension bug must never block message flow. |
| **Standard config prefix** | `sif.extensions.<name>.*` bound via a `@ConfigurationProperties` record | Consistent, discoverable configuration across all extensions. |

### Two toggle levels

An extension is only active when **both** switches are on:

```
sif.extensions.<name>.enabled = true      ← deployment-wide kill switch (config)
        AND
entity tag on solution/environment = true  ← per-tenant activation (runtime)
```

The config switch is a coarse on/off for the whole deployment. The entity tag is the fine-grained, per-solution control that lets you roll a feature out to one tenant at a time.

---

## Per-Tenant Activation via Entity Tags

Entity tags are the runtime control plane for extensions. An **entity tag** is a key/value pair the environment-service stores against an entity (a `SOLUTION`, `ENVIRONMENT`, `ZONE`, etc.). Extensions read these tags to decide whether — and how — to act for a given tenant. Because tags live in the environment-service and are looked up at runtime, an operator can turn an extension on or off for a specific solution or provider **without a redeploy**.

### The entity-tag REST API

Tags are managed through the environment-service, scoped under a solution at `/{domainId}/{solutionId}/tags`:

| Operation | Method + Path | Notes |
|-----------|---------------|-------|
| **Create** | `POST /{domainId}/{solutionId}/tags` | Body: `{ entityType, entityId, tagKey, tagValue }` → `201 Created` |
| **List** | `GET /{domainId}/{solutionId}/tags?entityType=&entityId=&tagKey=` | All query params optional; returns an array |
| **Get one** | `GET /{domainId}/{solutionId}/tags/{entityType}/{entityId}/{tagKey}` | |
| **Delete** | `DELETE /{domainId}/{solutionId}/tags/{entityType}/{entityId}/{tagKey}` | `204 No Content` |

The create body fields (`EntityTagCreateRequest`):

| Field | Type | Meaning |
|-------|------|---------|
| `entityType` | enum (`SOLUTION`, `ENVIRONMENT`, `ZONE`, …) | What kind of entity is being tagged |
| `entityId` | UUID | The tagged entity's ID |
| `tagKey` | string | The tag name (e.g., `SEQUENCING_ENABLED`) |
| `tagValue` | string (nullable) | The value; `null` represents a boolean flag |

### How an extension consumes a tag

Every extension follows the same read pattern:

```
1. Build the lookup key   → (domainId, solutionId, entityType, entityId?, tagKey)
2. Check the Caffeine cache (TTL from sif.extensions.<name>.*-cache-ttl)
3. On miss → call the environment-service tag API
4. Compare the tag value against the expected value
5. Cache the result (including "not present" / errors)
6. Fail-open: any 404 or error → treat as NOT activated, never block the message
```

The cache matters: without it, every message would trigger an HTTP call to the environment-service. TTL is configurable per extension (30s for sequencing, 5m for the transport example), trading activation latency against lookup traffic.

### What the examples look for

| Extension | `entityType` | `tagKey` | Activates when value is | Lookup query |
|-----------|--------------|----------|-------------------------|--------------|
| Message sequencing | `SOLUTION` | `SEQUENCING_ENABLED` | exactly `true` | `GET /{domainId}/{solutionId}/tags?entityType=SOLUTION&tagKey=SEQUENCING_ENABLED` |
| Alternate transport (example) | `ENVIRONMENT` | `ALTERNATE_TRANSPORT_SPEC` | a recognized transport id (e.g. `C00lNewEdTechSpec`) | tag value lookup for the provider environment UUID |

Note the difference in scope: sequencing is activated **per solution** (turn it on for a whole tenant), while the transport extension is activated **per provider environment** (only outbound calls to that specific provider get translated). The entity type on the tag is what determines the granularity.

### Turning sequencing on for a tenant — worked call

```http
POST /{domainId}/{solutionId}/tags
Content-Type: application/json

{
  "entityType": "SOLUTION",
  "entityId": "<the solution's UUID>",
  "tagKey": "SEQUENCING_ENABLED",
  "tagValue": "true"
}
```

Within one cache TTL (≤ 30s), the sequencing filter starts stamping `x-sif-sequenceId` on messages for that solution. To turn it off, `DELETE` the tag (or set the value to anything other than `true`) — the extension treats absent or non-`true` values as disabled.

---

## Worked Example 1 — Inbound: Message Sequencing

**Module:** `extensions`  ·  **Prefix:** `sif.extensions.sequencing`  ·  **Type:** Jakarta servlet `Filter`

**Goal:** give consumers a total ordering guarantee by stamping every incoming message with a monotonically increasing `x-sif-sequenceId` within a domain/solution.

```
Incoming request: POST /{domainId}/{solutionId}/events
        │
        ▼
┌──────────────────────────────────────────────────────────┐
│  MessageSequenceFilter (extends OncePerRequestFilter)     │
│                                                            │
│  1. Extract domainId, solutionId                          │
│       • normal path → URL segments [0] and [1]            │
│       • /internal/* path → domainId/solutionId headers    │
│  2. toggleService.isSequencingEnabled(domain, solution)   │
│       • entity-tag lookup, Caffeine-cached                │
│       • on failure → treat as disabled (fail-open)        │
│  3. if disabled → filterChain.doFilter(request) and RETURN│
│  4. sequenceService.nextSequence(domain, solution)        │
│       • Redis INCR on seq:{domainId}:{solutionId}         │
│  5. wrap request, inject x-sif-sequenceId header          │
│       • also set on response for IMMEDIATE consumers      │
│  6. continue filter chain with wrapped request            │
│                                                            │
│  Any exception at any step → pass original request through │
└──────────────────────────────────────────────────────────┘
```

Key implementation facts (from `MessageSequenceFilter`):

- Extends `OncePerRequestFilter` — standard Spring web machinery, nothing broker-specific.
- Sequence numbers come from Redis `INCR` on key `seq:{domainId}:{solutionId}` — atomic and monotonic.
- Fail-open is enforced at three levels: toggle evaluation, sequence generation, and a catch-all around the whole method. Every failure path calls `filterChain.doFilter` with the untouched request.
- The header is injected by wrapping the request in a `MutableHttpServletRequest`, and also mirrored onto the HTTP response so synchronous (IMMEDIATE-IMMEDIATE) consumers observe it.

**Activation:**
```yaml
sif:
  extensions:
    sequencing:
      enabled: true                                 # deployment kill switch (default true)
      entity-tags-base-url: http://localhost:8081   # environment-service for toggle lookups
      toggle-cache-ttl: 30s
```
Plus a per-solution entity tag: `entityType=SOLUTION`, `tagKey=SEQUENCING_ENABLED`, `tagValue=true` (see [Per-Tenant Activation via Entity Tags](#per-tenant-activation-via-entity-tags)).

---

## Worked Example 2 — Outbound: Alternate Transport Translation

> **Illustrative example.** This section describes a pattern, not a module that ships with the broker — there's no implementation of it in this repository to go find. The names below (`extension-transport`, `TransportExchangeFilter`, etc.) are placeholders to make the pattern concrete.

**Prefix:** `sif.extensions.transport`  ·  **Type:** WebClient `ExchangeFilterFunction`

**Goal:** let the requests-connector talk to a provider that speaks **something other than SIF** — another open-standard REST API, a vendor's proprietary API, or your own internal service — by translating auth, pagination, and headers on the outbound call, transparently, only for providers tagged for it. This is the general mechanism for making the broker interoperate with any REST endpoint: you teach the extension one provider "dialect" and tag the provider environments that speak it.

In this example the dialect is identified as `C00lNewEdTechSpec` — a hypothetical external spec that exposes a plain REST/JSON API using offset/limit pagination and Bearer-token auth.

```
requests-connector proxies a request to a tagged provider
        │
        │  routing layer sets TransportContext on the current thread
        │  (active flag, bearer token, navigation page/size), clears in finally
        ▼
┌──────────────────────────────────────────────────────────┐
│  TransportExchangeFilter (ExchangeFilterFunction)          │
│                                                            │
│  ctx = TransportContext.get()                              │
│  if ctx == null || !ctx.isActive()                         │
│        → next.exchange(request)  (pass through unchanged)  │
│                                                            │
│  OUTBOUND rewrite:                                         │
│    • strip SIF headers (Authorization, timestamp,          │
│      messageType, requestId, navigation*)                  │
│    • set Authorization: Bearer <provider shared secret>    │
│    • append offset/limit params from SIF navigation page   │
│                                                            │
│  INBOUND (response) rewrite:                               │
│    • read x-total-count from provider response             │
│    • remove x-total-count                                  │
│    • add SIF navigation response headers                   │
└──────────────────────────────────────────────────────────┘
```

Key implementation ideas:

- The filter is a plain `ExchangeFilterFunction`. It is wired into the connector's `WebClient.Builder` by a `WebClientCustomizer` bean — the auto-config registers `builder -> builder.filter(exchangeFilter)`. The connector's WebClient code is untouched.
- Whether translation runs is decided by a `TransportContext` carried on a `ThreadLocal`. The routing layer sets it before the outbound call and clears it in a `finally` block. When the context is absent or inactive, the filter is a pure pass-through.
- SIF-specific request headers are stripped and replaced with a `Bearer` token (the provider's shared secret); SIF navigation headers are translated to offset/limit query params.
- On the response path, the provider's `x-total-count` header is translated back into SIF navigation headers, so the consumer still receives a spec-compliant response.

**Activation:**
```yaml
sif:
  extensions:
    transport:
      enabled: true            # deployment kill switch (default true)
      default-page-size: 10     # limit when no navigationPageSize header present
      tag-cache-ttl: 5m         # Caffeine TTL for entity-tag lookups
```
Plus a per-provider entity tag: `entityType=ENVIRONMENT` on the provider environment, `tagKey=ALTERNATE_TRANSPORT_SPEC`, `tagValue=<transport id>` (e.g. `C00lNewEdTechSpec`) — see [Per-Tenant Activation via Entity Tags](#per-tenant-activation-via-entity-tags). Because the tag value names the dialect, a single extension can support several provider APIs at once — one open standard, another vendor's proprietary API, an internal service — dispatching on the value.

---

## Inbound vs. Outbound at a Glance

The two drop-in styles mirror each other on opposite sides of the connector:

| Aspect | Inbound (sequencing) | Outbound (transport example) |
|--------|----------------------|----------------------|
| Spring type | `OncePerRequestFilter` (Jakarta servlet) | `ExchangeFilterFunction` (WebClient) |
| Wired via | Auto-registered servlet filter bean | `WebClientCustomizer` → `builder.filter(...)` |
| Intercepts | Requests arriving at the connector | Calls the connector makes to a provider |
| Activation signal | Entity tag `SEQUENCING_ENABLED` on the solution | Entity tag `ALTERNATE_TRANSPORT_SPEC` on the provider environment, propagated via `ThreadLocal` context |
| Modifies | Injects a request header | Rewrites auth + pagination, both directions |
| Failure behavior | Fail-open — pass request through | Pass-through when context inactive |

---

## Layer 1 — Gateway Filters

The API Gateway is reactive (Spring Cloud Gateway, WebFlux). Custom cross-cutting logic that must run on **every** request — before it ever reaches a connector — belongs here.

- Implement `GlobalFilter` (optionally `Ordered`).
- Register it as a Spring bean; the gateway picks it up automatically.
- Ordering is by `@Order` / `Ordered` — lower runs first. The core filters (authentication, authorization, rate limiting) occupy well-defined order values, so an extension can position itself before or after a specific core filter.

Typical uses: custom authentication schemes, global header enrichment, centralized request logging, additional rate-limiting dimensions.

---

## Layer 3 — Pluggable Backends

Infrastructure concerns are swapped by implementing an interface and selecting the implementation with `@ConditionalOnProperty` — no per-request logic, just one active bean chosen at startup.

| Interface | Config | Implementations |
|-----------|--------|-----------------|
| `StorageProvider` | `STORAGE_TYPE` | `FileSystemStorageProvider` (dev/test), `S3StorageProvider` (prod) |
| `SecretsBackend` | secrets backend property | Vault, Multi-Key AES-256-GCM (DB), Plaintext (dev) |

To add a new backend: implement the interface, annotate the config with `@ConditionalOnProperty` on the selector value, and provide it as a bean. Selection is config-driven — no code changes elsewhere.

---

## Packaging Checklist

To ship a drop-in extension:

1. **New Maven module** (e.g., `extension-<name>`), depending on `sif-broker-common`.
2. **Implement the beans** — an inbound servlet `Filter`, an outbound `ExchangeFilterFunction`, or a pluggable-backend implementation.
3. **`@ConfigurationProperties` record** with prefix `sif.extensions.<name>`, sensible `@DefaultValue`s.
4. **Auto-config class** annotated `@ConditionalOnProperty(name = "sif.extensions.<name>.enabled", matchIfMissing = true)` and `@EnableConfigurationProperties(...)`.
5. **`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`** listing the auto-config class.
6. **Per-tenant toggle** via an entity tag, looked up against the environment-service and Caffeine-cached.
7. **Fail-open** everywhere — log and pass through on error.
8. **Property-based tests** (jqwik) for the correctness properties, plus an auto-configuration wiring test.
9. **README** documenting configuration, the toggle tag, and behavior.

Then add the JAR as a dependency to whichever connector needs it. Spring does the rest.

---

## Extension Isolation

Extensions run **in the same JVM** as the broker — there is no process-level isolation. That is a deliberate trade for flexibility, with consequences:

- Extensions have full access to the Spring context.
- A poorly written extension can affect broker performance or stability — fail-open limits blast radius but does not eliminate it.
- Test extensions thoroughly (property tests + wiring test) before deployment.
- Extension logging and metrics flow through the same observability pipeline as core broker telemetry.

---

## Why This Design

- **No fork required** — implementors add value as dependencies, not patches; broker upgrades stay clean.
- **Nothing new to learn** — extensions use the Spring/Servlet/WebClient machinery developers already know, not a bespoke interceptor API.
- **Safe by default** — fail-open plus two-level toggles mean an extension can be shipped dark, enabled per-tenant, and can never take message flow down.
- **Runtime control** — entity-tag activation rolls features out (or back) one solution at a time with no redeploy.

---

## Related Documents

- [System Architecture Overview](SYSTEM-ARCHITECTURE-OVERVIEW.md) — Extensibility summary table
- [System Architecture](SYSTEM-ARCHITECTURE.md) — full Extension Points section
- [`extensions/README.md`](../../extensions/README.md) — message-sequencing extension reference
- [SPOTLIGHT: Request Lifecycle](SPOTLIGHT-REQUEST-LIFECYCLE.md) — where inbound/outbound filters sit in a request
- [SPOTLIGHT: Queue Architecture](SPOTLIGHT-QUEUE-ARCHITECTURE.md) — the `StorageProvider` pluggable backend in action
