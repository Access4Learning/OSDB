# SPOTLIGHT: Request Lifecycle

How an HTTP request flows through a typical OSDB module, from gateway ingress to database and back.

---

## The Layers

```
Client (Bruno / SIF App)
  │
  ▼
┌─────────────────────────────────────────────────┐
│  Gateway (Spring Cloud Gateway - reactive)      │
│  • Route matching by path prefix                │
│  • AuthFilter: HMAC-SHA256 validation           │
│  • Rate limiting (Redis-backed)                 │
│  • Injects: X-Environment-Id, X-Environment-Name│
│  • Forwards to backend on localhost:<port>       │
└─────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────┐
│  Controller (@RestController)                   │
│  • Declares HTTP verb + path (@GetMapping, etc) │
│  • Extracts path variables, headers, body       │
│  • Bean Validation on request DTOs (@Valid)      │
│  • Delegates to Service — no business logic here│
│  • Returns ResponseEntity with status code      │
└─────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────┐
│  Service (@Service, @Transactional)             │
│  • All business logic lives here                │
│  • Orchestrates repository calls                │
│  • Throws domain exceptions (e.g. NotFound)     │
│  • Maps entities ↔ DTOs                         │
│  • Calls other services or external clients     │
└─────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────┐
│  Repository (Spring Data JPA interface)         │
│  • Extends JpaRepository<Entity, UUID>          │
│  • Derived query methods (findByXxx)            │
│  • No implementation class — Spring generates it│
│  • Returns Optional<Entity> or List<Entity>     │
└─────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────┐
│  Entity (@Entity, @Table)                       │
│  • JPA-managed POJO mapping to a DB table       │
│  • UUID primary key (@GeneratedValue UUID)      │
│  • Flyway manages the schema                    │
│  • @PrePersist for audit timestamps             │
└─────────────────────────────────────────────────┘
  │
  ▼
  PostgreSQL
```

---

## Concrete Example: Create Zone

```
POST /GLOBAL_DOMAIN/GLOBAL_SOLUTION/zones
```

| Layer | Class | What Happens |
|-------|-------|--------------|
| Gateway | `application-local.yml` routes | Path prefix `/{domainId}/{solutionId}/zones` → `environment-service:8081` |
| Controller | `ZoneController.createZone()` | Extracts `@PathVariable domainId, solutionId`, validates `@RequestBody ZoneCreateRequest` |
| Service | `ZoneService.createZone()` | Checks solution exists, checks zone uniqueness, builds `Zone` entity, calls `save()` |
| Repository | `ZoneRepository.save(zone)` | Spring Data inserts row into `zones` table |
| Response | Controller returns | `201 Created` + `ZoneDTO` body |

---

## Key Conventions

| Convention | Rule |
|-----------|------|
| **DTOs vs Entities** | Entities never leak past the Service layer. Controllers only see DTOs/records. |
| **Validation** | Jakarta Bean Validation (`@NotBlank`, `@Size`) on request DTOs. Business rules in Service. |
| **Exceptions** | Service throws typed exceptions (`ZoneNotFoundException`). A global `@ControllerAdvice` maps them to HTTP status codes. |
| **Transactions** | `@Transactional` on Service methods that write. Read-only methods use `@Transactional(readOnly = true)` or no annotation. |
| **Naming** | `{Entity}Controller`, `{Entity}Service`, `{Entity}Repository`, `{Entity}CreateRequest`, `{Entity}DTO`. |
| **Path structure** | `/{domainId}/{solutionId}/...` — multi-tenancy scoping baked into every URL. |

---

## What Lives Where (quick reference)

| Concern | Location |
|---------|----------|
| HTTP routing, auth, rate limiting | `gateway/` |
| REST endpoint declaration | `controller/` package |
| Business logic, orchestration | `service/` package |
| Data access | `repository/` package |
| Table mapping | `domain/` package (entities) |
| Request/response shapes | `dto/` package (records) |
| Error mapping | `exception/` package + `@ControllerAdvice` |
| Schema evolution | `resources/db/migration/` (Flyway) |
| Shared utilities | `sif-broker-common` module |

---

## Anti-Patterns to Avoid

- **Logic in controllers** — controllers are thin dispatchers, nothing more.
- **Returning entities from services** — always map to a DTO before crossing the service boundary.
- **Repository calls in controllers** — always go through a service.
- **Catching exceptions in controllers** — let `@ControllerAdvice` handle it uniformly.
- **Hardcoded SQL in services** — use repository derived queries or `@Query` annotations.
