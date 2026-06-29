# Contributing to SIF 3.7 Certification Tests

Thank you for contributing to the SIF certification suite! This document explains how to add tests, naming conventions, and documentation requirements.

## Adding a New Test Section

Each test section lives in its own numbered folder (e.g., `06-queue-operations/`). Follow these steps:

### 1. Create the Folder

Use the next available number in the sequence (01–98). Folder names use kebab-case:

```
NN-descriptive-name/
```

### 2. Create folder.bru

Every test folder **must** have a `folder.bru` with a `docs` section containing:

```
meta {
  name: Section Name
  seq: <number>
}

docs {
  # Section Title

  ## Concept Overview
  Explain the SIF concept being tested. Reference SIF spec sections where applicable.

  ## Test Purpose
  What compliance behavior is being verified.

  ## Prerequisites
  Prior folders or state that must exist before running this section.

  ## Expected Behavior
  Observable outcomes of a successful run.

  ## SIF Specification Reference
  Section numbers from the SIF 3.x specification (if applicable).
}
```

### 3. Create Test Files

Name test files with a numeric prefix indicating execution order:

| Prefix Range | Purpose |
|-------------|---------|
| `01–09` | Setup (zone, environments, ACLs, queues, subscriptions) |
| `10–89` | Test operations |
| `99` | Teardown |

Example:
```
01-setup-zone.bru
02-create-provider-env.bru
03-create-consumer-env.bru
04-create-acls.bru
10-publish-event.bru
11-verify-delivery.bru
99-teardown.bru
```

### 4. Document Each Test File

Every `.bru` test file **must** include a `docs` section:

```
docs {
  ## Summary
  One-sentence description of what this test verifies.

  ## Rationale
  Why this matters for SIF compliance.

  ## Expected Result
  Exact status code and key response characteristics.
}
```

## Naming Conventions

| Item | Convention | Example |
|------|-----------|---------|
| Folder names | `NN-kebab-case` | `03-event-publishing` |
| Test files | `NN-kebab-case.bru` | `10-publish-event.bru` |
| Bruno variables | camelCase with section prefix | `sec01DomainId`, `sec03SolutionId` |
| Section-local variables | camelCase with section prefix | `sec01ZoneId`, `sec03ConsumerEnvId` |

## Setup/Teardown Pattern

Every test section is **self-contained** and follows the same lifecycle:

1. **Provisioning** (files 01–03): Create domain, solution, get admin credentials
2. **Setup** (files 04–09): Create zone, environments, ACLs, queues, subscriptions
3. **Test** (files 10+): Exercise SIF behavior, assert compliance
4. **Teardown** (file 99): Delete the domain (cascade-deletes all resources)

The teardown is a simple domain deletion — the broker cascade-deletes the solution
and all resources within it. No need to manually delete individual resources.

```javascript
// Teardown just deletes the domain:
// DELETE {{gatewayUrl}}/domains/{{sec0XDomainId}}
// This cascade-deletes solution, zones, environments, ACLs, queues, subscriptions
```

## Authentication

Use the shared `auth.js` module for all authenticated requests:

```javascript
// In pre-request script:
const auth = require('./common/auth');

// Using section admin credentials (from Bruno vars, set by 03-get-admin-environment):
auth.setAuthHeaders(req, bru, { varPrefix: 'sec01Admin' });

// Using .env credentials directly (for domain/solution creation and teardown):
auth.setAuthHeaders(req, bru, 'admin');
```

## Portability Rules

- **Zero external references**: No imports, requires, or paths pointing outside `bruno-certification-tests/`
- **Self-contained**: The suite must run when cloned alone without the parent repository
- **No hardcoded URLs**: All broker URLs come from environment files
- **No hardcoded credentials**: All credentials come from `.env` or Bruno variables

## Pull Request Checklist

- [ ] New folder has `folder.bru` with all required docs sections
- [ ] All test files have `docs` section with summary, rationale, expected result
- [ ] Setup creates only what the section needs
- [ ] Teardown deletes everything the section created (handles 404 gracefully)
- [ ] No file references outside `bruno-certification-tests/`
- [ ] Variables use appropriate naming convention
- [ ] Auth uses `common/auth.js` module
