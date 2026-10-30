# Gatling Load Tests — SIF Unity Broker

Standalone Gatling Java DSL load testing module for the SIF Unity Broker. Exercises the full eventing pipeline — infrastructure setup, event publishing, and event consumption — at scale across hundreds of zones.

## Prerequisites

- Java 17+
- Maven 3.9+
- A running SIF Unity Broker gateway (default: `http://localhost:8080`)
- Valid admin session token and shared secret

## Configurable Parameters

All parameters can be set via Java system properties (`-D` flags) or environment variables. System properties take precedence over environment variables.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `ZONE_COUNT` | `300` | Number of zones created during setup |
| `DURATION_MINUTES` | `5` | How long the publish phase runs (minutes) |
| `RECORDS_PER_EVENT` | `1` | StudentDailyAttendance objects per event payload |
| `THREAD_COUNT` | `10` | Gatling injection concurrency (virtual users) |
| `CONNECTIONS_PER_CONSUMER` | `3` | Concurrent polling connections per consumer |
| `GATEWAY_URL` | `http://localhost:8080` | Target gateway base URL |
| `ADMIN_SESSION_TOKEN` | `admin-bootstrap-token` | Admin session token for setup phase |
| `ADMIN_SHARED_SECRET` | `admin-bootstrap-secret` | Admin shared secret for setup phase |

## Running the Load Tests

The load test runs in three sequential phases. Each phase is a separate Gatling simulation.

### 1. Setup Phase

Creates domain, solution, zones, environments, ACLs, queues, and subscriptions:

```bash
cd gatling-load-tests
mvn gatling:test -Dgatling.simulationClass=org.sifunity.loadtest.simulation.SetupSimulation
```

This writes `target/setup-state.json` which is consumed by the publish and consume phases.

### 2. Publish Phase

Emits StudentDailyAttendance events from all provider environments for `DURATION_MINUTES`:

```bash
mvn gatling:test -Dgatling.simulationClass=org.sifunity.loadtest.simulation.PublishSimulation
```

Requires `target/setup-state.json` from the setup phase.

### 3. Consume Phase

Drains all consumer queues with concurrent polling connections:

```bash
mvn gatling:test -Dgatling.simulationClass=org.sifunity.loadtest.simulation.ConsumeSimulation
```

Requires `target/setup-state.json` from the setup phase.

### Overriding Parameters

Pass `-D` flags to override any parameter:

```bash
mvn gatling:test \
    -Dgatling.simulationClass=org.sifunity.loadtest.simulation.SetupSimulation \
    -DZONE_COUNT=50 \
    -DGATEWAY_URL=http://staging:8080 \
    -DADMIN_SESSION_TOKEN=my-token \
    -DADMIN_SHARED_SECRET=my-secret
```

Or set environment variables:

```bash
export ZONE_COUNT=50
export GATEWAY_URL=http://staging:8080
export ADMIN_SESSION_TOKEN=my-token
export ADMIN_SHARED_SECRET=my-secret
mvn gatling:test -Dgatling.simulationClass=org.sifunity.loadtest.simulation.SetupSimulation
```

## Running Unit and Property-Based Tests

```bash
cd gatling-load-tests
mvn test
```

This runs all JUnit 5 unit tests and jqwik property-based tests for the helper classes (HmacAuthenticator, LoadTestConfig, AttendanceDataGenerator, StateManager).

## Reports

Gatling HTML reports are generated after each simulation run at:

```
gatling-load-tests/target/gatling/{simulation-name}-{timestamp}/index.html
```

Reports include:
- Request/response time distributions (p50, p75, p95, p99)
- Throughput (requests/second)
- Error rates and error breakdown
- Active users over time
- Response time percentiles over time

## Project Structure

```
gatling-load-tests/
├── pom.xml
├── README.md
└── src/
    └── test/
        ├── java/
        │   └── org/sifunity/loadtest/
        │       ├── config/          # LoadTestConfig — parameter management
        │       ├── auth/            # HmacAuthenticator — HMAC-SHA256 auth
        │       ├── data/            # AttendanceDataGenerator — SIF payload generation
        │       ├── state/           # SetupState, ZoneState, StateManager — inter-phase state
        │       └── simulation/      # SetupSimulation, PublishSimulation, ConsumeSimulation
        └── resources/
            └── gatling.conf         # Gatling configuration
```
