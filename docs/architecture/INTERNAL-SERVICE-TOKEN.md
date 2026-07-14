# Internal Service Token — Operational Guide

## Overview

`INTERNAL_SERVICE_TOKEN` is a shared static secret that authenticates service-to-service calls
on `/internal/**` endpoints. It prevents direct-to-service credential exfiltration and header
forgery from any network position inside the perimeter.

**Participants:**

- **Enforcer:** environment-service (`AuthorizationFilter`)
- **Callers:** gateway, events-connector, requests-connector, responses-connector,
  services-connector, queue-service (any service invoking `/internal/` paths)

## Operational Requirements

| Requirement | Detail |
|---|---|
| Storage | Secrets backend (AWS Secrets Manager or HashiCorp Vault) |
| Config reference | `${INTERNAL_SERVICE_TOKEN}` placeholder only — never a literal value |
| Minimum length | 32 random characters (alphanumeric + symbols) |
| Version control | MUST NOT appear in `application.yml`, git, or log output |
| Startup validation | Service refuses to start if token is blank |

## Token Rotation Procedure

The token is symmetric — every participant shares the same value. Rotation therefore
requires coordinated updates across all services.

**Steps:**

1. Generate a new token (e.g., `openssl rand -base64 48`).
2. Store the new value in the secrets backend under the existing key.
3. Perform a rolling restart of all participating services (or trigger secret refresh).
4. Verify: internal calls succeed (HTTP 200); no 401 errors in logs.

**During the rotation window:**

- Callers still holding the old value will receive `401 Unauthorized` until they
  pick up the new secret.
- **Mitigation:** use blue/green deployment or schedule rotation within a maintenance
  window so all pods restart near-simultaneously.

## Migration Path — mTLS / Short-Lived Tokens

The static shared token is an **interim control**. It has a known limitation: any holder
is fully trusted, and compromise of one service exposes the secret for all.

### Preferred durable solution: mutual TLS (mTLS)

Each service presents a client certificate signed by an internal CA. The callee validates
the certificate chain — no shared secret required. This gives per-service identity and
revocation without restarting all services.

### Alternative: short-lived signed JWTs

An internal auth service issues short-lived JWTs (minutes). Each callee verifies the
signature against the issuer's public key. Token expiry limits blast radius; per-service
`sub` claims enable fine-grained authorization.

### Why migrate?

Either approach eliminates the "all services share one secret" problem, enables
per-service identity, and removes the coordinated-restart requirement for rotation.

### Target timeline

Post-production-launch hardening phase. The static token is sufficient for initial
deployment behind a private network; mTLS or signed tokens should be adopted before
the system handles sensitive PII at scale.
