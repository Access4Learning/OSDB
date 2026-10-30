# Mock SIF Provider

Simple echo server for running Bruno certification tests locally. Simulates an external SIF provider that the requests-connector forwards IMMEDIATE requests to.

## Setup

```bash
cd test-tools/mock-provider
npm install
```

## Usage

Start the mock provider before running Bruno certification tests:

```bash
npm start
```

Runs on port 9091 by default (port 9090 is typically used by Prometheus). Override with `PORT` env var if needed.

Then in another terminal:

```bash
cd bruno-certification-tests
npx @usebruno/cli run --env local
```

## What it does

Listens on `http://localhost:9090` and responds to any request with a JSON echo containing:
- `receivedHeaders` — all headers the broker forwarded
- `receivedQuery` — query parameters
- `receivedBody` — parsed request body
- `method` — HTTP method
- `path` — request path

This is enough to satisfy sections 04 (request-response), 05 (parameter-styles), 07 (service-operations), and 08 (header-passthrough) of the certification tests.

## Not for deployment

This is a local test tool only. It's not part of the Maven build, not containerized, and not deployed anywhere.
