# SIF 3.7 Certification Tests

A standalone, portable Bruno API test collection that certifies any SIF 3.7 compliant broker. This suite tests the SIF 3.7 specification contract — not any particular broker's internals.

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| [Bruno CLI](https://docs.usebruno.com/bru-cli/overview) | Latest | Executes the test collection |
| [Node.js](https://nodejs.org/) | 16+ | Required for CryptoJS (HMAC-SHA256 auth) |

Install Bruno CLI:

```bash
npm install -g @usebruno/cli
```

## Setup

1. **Clone or copy** the `bruno-certification-tests/` folder to your machine.

2. **Configure credentials** — copy the example env file and populate your admin credentials:

   ```bash
   cp .env.example .env
   ```

   Edit `.env` and set:
   ```env
   cert-adminSessionToken=<your-admin-session-token>
   cert-adminSharedSecret=<your-admin-shared-secret>
   ```

   These are the admin credentials for the broker under test. They must have permission to create/delete domains and solutions.

3. **Install Node dependencies** (for CryptoJS used in HMAC auth):

   ```bash
   npm install
   ```

## Configuration

Environment variables are defined in `environments/local.bru`. Switch or create environment files to target different brokers.

| Variable | Purpose | Example |
|----------|---------|---------|
| `gatewayUrl` | Base URL of the SIF broker gateway | `http://localhost:8080` |
| `currentEnv` | Credential prefix for .env lookup | `cert` |
| `immediateProviderEndpointUrl` | External provider endpoint for request/response tests | `http://localhost:9090/sif/provider` |
| `immediateProviderServiceName` | SIF service name served by external provider | `TestObjects` |

To target a different broker, create a new environment file (e.g., `environments/staging.bru`) with the appropriate values.

## Execution

### Each Section is Self-Contained

Every test section (01–10) is fully independent. Each section:
1. Creates its own timestamped domain
2. Creates a solution within that domain
3. Retrieves admin credentials
4. Sets up section-specific infrastructure (zones, environments, ACLs, queues)
5. Runs SIF behavior tests
6. Tears down by deleting the domain (cascade-deletes all resources)

No shared provisioning or deprovisioning step is required.

### Test Sections

| Folder | Purpose | Category |
|--------|---------|----------|
| `01-environment-registration` | Verify environment GET and infrastructure services | SIF spec test |
| `02-subscription-management` | Create, list, delete subscriptions | SIF spec test |
| `03-event-publishing` | Publish events, verify delivery to subscribers | SIF spec test |
| `04-request-response` | All three choreographies (IMMEDIATE, DELAYED) | SIF spec test |
| `05-parameter-styles` | Header-based and matrix parameter routing | SIF spec test |
| `06-queue-operations` | Get Next and Pop pattern (§9.3.1) | SIF spec test |
| `07-service-operations` | Functional service CRUD via broker | SIF spec test |
| `08-header-passthrough` | Custom header forwarding verification | SIF spec test |
| `09-queue-count-validation` | Queue statistics and count accuracy | SIF spec test |
| `10-security-enforcement` | ACL enforcement and 403 responses | SIF spec test |

### Running the Full Suite

```bash
bru run --env local
```

### Running a Single Section

```bash
bru run 03-event-publishing --env local
```

Each section can run independently — no need to run other sections first.

### Re-Running Safely

The suite uses timestamp-suffixed identifiers, so consecutive runs never collide. Each section creates and deletes its own domain, ensuring complete isolation.

## Project Structure

```
bruno-certification-tests/
├── bruno.json              # Collection manifest
├── collection.bru          # Suite-level documentation
├── .env.example            # Credential template
├── .gitignore              # Excludes .env, node_modules
├── README.md               # This file
├── CONTRIBUTING.md         # How to add tests
├── LICENSE                 # MIT license
├── common/
│   └── auth.js             # HMAC-SHA256 auth module
├── environments/
│   └── local.bru           # Example environment config
└── 01–10-*/                # Self-contained SIF spec test sections
    ├── 01-create-domain.bru
    ├── 02-create-solution.bru
    ├── 03-get-admin-environment.bru
    ├── 04+ setup files
    ├── 10+ test files
    └── 99-teardown.bru
```

## Section Internal Structure

Each section follows a consistent numbering pattern:

| Range | Purpose |
|-------|---------|
| 01–03 | Domain/solution/admin provisioning (self-contained) |
| 04–09 | Section-specific setup (zones, environments, ACLs, queues) |
| 10–89 | SIF specification tests |
| 99 | Teardown (domain delete with cascade) |

## License

MIT — see [LICENSE](./LICENSE).
