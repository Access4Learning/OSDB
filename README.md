# SIF Unity Broker

An open-source, SIF 3.7 compliant message broker that facilitates CEDS adoption for education data interoperability.

## Project Status

This software is provided under the MIT License on an "as-is" basis. It was developed by [LevelData](https://leveldata.com) under contract for the Maine Department of Education. See [GOVERNANCE.md](GOVERNANCE.md) for the project's governance model and maintenance responsibilities.

<!-- CI/CD: Bitbucket Pipelines enabled -->

## Overview

The SIF Unity Broker is a **microservices** implementation of the Schools Interoperability Framework (SIF) Infrastructure specification. It provides message brokering services between SIF Consumers and Providers, supporting synchronous (IMMEDIATE), asynchronous (DELAYED), and publish/subscribe (EVENT) message exchange patterns.

### Key Features

- **SIF 3.7 Compliant** - Full support for SIF Infrastructure 3.7 specification
- **Multi-format Support** - Both XML and JSON payloads
- **Flexible Authentication** - Basic Auth, SIF HMAC-SHA256, and Bearer token support
- **Role-Based Access Control** - Fine-grained permissions per zone/context/service
- **Message Queuing** - Redis Streams-backed reliable message delivery
- **Event Distribution** - Pub/sub pattern with fan-out to subscribers
- **Functional Services** - Job state tracking with phases
- **Microservices Architecture** - Independently deployable services with clear boundaries
- **Cloud-Native** - Docker containers, Kubernetes ready, observability built-in

## Architecture

### Microservices Design

The broker is built as a collection of independently deployable Spring Boot microservices, each owning its domain and data:

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway                              │
│                    (Spring Cloud Gateway)                       │
├─────────┬─────────┬─────────┬─────────┬─────────┬───────────────┤
│ environ │ requests│  queue  │ subscrip│   job   │   responses   │
│  ment   │ connect │ service │  tion   │ service │  connector    │
│ service │   or    │         │ service │         │               │
├─────────┼─────────┼─────────┼─────────┼─────────┼───────────────┤
│ events  │ services│         │         │         │               │
│connect  │connect  │         │         │         │               │
│   or    │   or    │         │         │         │               │
├─────────┴─────────┴─────────┴─────────┴─────────┴───────────────┤
│                       sif-broker-common                         │
│                    (Shared Library)                             │
└─────────────────────────────────────────────────────────────────┘
```

### Service Overview

| Service | Port | Description |
|---------|------|-------------|
| `gateway` | 8080 | API Gateway - Routes requests to appropriate services |
| `environment-service` | 8081 | Environment registration, zones, contexts, and admin services |
| `queue-service` | 8082 | Message queue management with Redis Streams backend |
| `subscription-service` | 8083 | Event and delayed request subscriptions |
| `job-service` | 8084 | Functional Services job state tracking |
| `requests-connector` | 8085 | Request routing to providers (Object Services) |
| `responses-connector` | 8086 | Response handling for asynchronous requests |
| `events-connector` | 8087 | Event publishing and distribution |
| `services-connector` | 8088 | Functional Services routing |

### Shared Components

| Component | Description |
|-----------|-------------|
| `sif-broker-common` | Shared DTOs, exceptions, utilities, and observability configuration |
| `integration-tests` | End-to-end integration tests across all services |
| `observability` | Monitoring stack (Prometheus, Grafana, Jaeger, ELK) |

### Service Dependencies

```
gateway
└── All services (routes traffic)

environment-service
└── sif-broker-common

queue-service
└── sif-broker-common

subscription-service  
└── sif-broker-common

job-service
└── sif-broker-common

requests-connector
└── sif-broker-common, environment-service, queue-service

responses-connector
└── sif-broker-common, queue-service

events-connector
└── sif-broker-common, subscription-service

services-connector
└── sif-broker-common, environment-service
```

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 17 LTS |
| Framework | Spring Boot | 3.2.0 |
| Gateway | Spring Cloud Gateway | 4.x |
| Database | PostgreSQL | 15+ |
| Message Queue | Redis (Streams) | 7+ |
| Build Tool | Maven | 3.8+ |
| ORM | Hibernate/JPA | 6.x |
| Migrations | Flyway | 9.x |
| API Docs | SpringDoc OpenAPI | 2.x |
| Observability | Micrometer, OpenTelemetry | Latest |
| Containerization | Docker | Latest |

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8 or higher  
- Docker and Docker Compose (recommended for local development)
- PostgreSQL 15+ (or use Docker)
- Redis 7+ (or use Docker)

### Quick Start with Docker (Recommended)

1. **Clone the repository**
```bash
git clone <repository-url>
cd sif-unity-broker
```

2. **Start infrastructure services**
```bash
cd queue-service
docker-compose up -d postgres redis
cd ..
```

3. **Build all services**
```bash
# Build all services with a single command (recommended)
mvn clean install

# Or skip tests for faster builds
mvn clean install -DskipTests
```

4. **Start all services**
```bash
# Windows
.\start-broker.ps1

# Linux/macOS  
./start-broker.sh
```

5. **Verify services are running**
```bash
# Windows
.\check-services.ps1

# Linux/macOS
./check-services.sh
```

### Manual Service Startup

If you prefer to start services individually:

```bash
# 1. Start Environment Service (FIRST - required by others)
cd environment-service
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 2. Start Queue Service
cd queue-service  
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 3. Start Subscription Service
cd subscription-service
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. Start Job Service
cd job-service
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 5. Start Connector Services
cd requests-connector
mvn spring-boot:run -Dspring-boot.run.profiles=local

cd responses-connector
mvn spring-boot:run -Dspring-boot.run.profiles=local

cd events-connector
mvn spring-boot:run -Dspring-boot.run.profiles=local

cd services-connector
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 6. Start Gateway (LAST)
cd gateway
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Multi-Module Maven Build

The SIF Unity Broker uses a Maven aggregator/parent POM structure that enables unified builds across all services. The root `pom.xml` serves as both an aggregator (building all modules together) and a parent (providing centralized dependency and plugin management).

#### Build Structure

```
sif-unity-broker/
├── pom.xml                      # Root aggregator/parent POM
├── sif-broker-common/           # Built first (dependency for all services)
│   └── pom.xml
├── environment-service/         # Service modules (built in parallel)
│   └── pom.xml
├── queue-service/
│   └── pom.xml
├── [other services...]
└── integration-tests/           # Built last (depends on all services)
    └── pom.xml
```

Maven automatically determines the correct build order based on inter-module dependencies:
1. **sif-broker-common** - Built first as all services depend on it
2. **Service modules** - Built in parallel (no interdependencies)
3. **integration-tests** - Built last (requires all service artifacts)

#### Common Maven Commands

**Build all services:**
```bash
# Full build with tests
mvn clean install

# Skip tests for faster builds
mvn clean install -DskipTests

# Package without installing to local repository
mvn clean package
```

**Run tests:**
```bash
# Run all tests across all modules
mvn test

# Run tests for a specific module
mvn test -pl environment-service

# Run integration tests only
cd integration-tests
mvn verify
```

**Clean build artifacts:**
```bash
# Clean all modules
mvn clean

# Clean specific module
mvn clean -pl queue-service
```

#### Module-Specific Build Commands

**Build a specific module and its dependencies:**
```bash
# Build queue-service and sif-broker-common (its dependency)
mvn install -pl queue-service -am

# -pl: project list (specify modules)
# -am: also make (build dependencies)
```

**Build a module and modules that depend on it:**
```bash
# Build sif-broker-common and all services that depend on it
mvn install -pl sif-broker-common -amd

# -amd: also make dependents
```

**Build multiple specific modules:**
```bash
# Build environment-service and queue-service
mvn install -pl environment-service,queue-service -am
```

**Resume build from a specific module:**
```bash
# Resume build from queue-service (useful after fixing a failed build)
mvn install -rf :queue-service

# -rf: resume from
```

#### Profile Usage

The parent POM supports Maven profiles for different environments:

**Available profiles:**
- `dev` - Development environment (active by default)
- `staging` - Staging environment
- `production` - Production environment
- `skip-integration-tests` - Skip integration tests

**Using profiles:**
```bash
# Build with production profile
mvn install -P production

# Build with multiple profiles
mvn install -P production,skip-integration-tests

# Skip integration tests
mvn install -DskipITs
```

#### Selective Module Builds

**Build only services (exclude integration tests):**
```bash
mvn install -pl '!integration-tests'
```

**Build all except specific modules:**
```bash
# Build everything except observability
mvn install -pl '!observability'
```

**Build specific services for development:**
```bash
# Build only the services you're working on
mvn install -pl sif-broker-common,environment-service,gateway -am
```

#### Build Order and Dependency Management

The Maven Reactor automatically handles build ordering:

**View the reactor build order:**
```bash
mvn validate
```

**Parallel builds:**
```bash
# Build modules in parallel (faster on multi-core systems)
mvn install -T 4

# Use one thread per CPU core
mvn install -T 1C
```

**Incremental builds:**
```bash
# Only rebuild changed modules and their dependents
mvn install -amd -pl $(git diff --name-only HEAD~1 | grep pom.xml | xargs dirname | tr '\n' ',')
```

#### Dependency Management

All shared dependency versions are managed in the root POM's `<dependencyManagement>` section. Child modules inherit these versions automatically:

**Viewing effective dependencies:**
```bash
# See resolved dependencies for a module
cd environment-service
mvn dependency:tree

# See all dependencies across all modules
mvn dependency:tree
```

**Updating dependency versions:**
```bash
# Update all modules to a new version
mvn versions:set -DnewVersion=2.0.0-SNAPSHOT

# Update a specific dependency version
mvn versions:use-latest-versions -Dincludes=org.springframework.boot:*
```

#### Troubleshooting Maven Builds

**Clear local repository cache:**
```bash
# Remove all project artifacts from local Maven repository
mvn dependency:purge-local-repository

# Force update of snapshots
mvn clean install -U
```

**Debug build issues:**
```bash
# Run with debug output
mvn install -X

# Show version information
mvn --version

# Validate POM files
mvn validate
```

**Common build issues:**
- **"Could not resolve dependencies"** - Run `mvn clean install -U` to force update
- **"Non-resolvable parent POM"** - Ensure you're in the repository root or the parent POM exists
- **Build order issues** - Maven Reactor handles this automatically; check for circular dependencies
- **Out of memory** - Increase Maven memory: `export MAVEN_OPTS="-Xmx2g"`

### Configuration

The services use Spring profiles for environment-specific configuration:

| Profile | Description |
|---------|-------------|
| `local` | Local development with debug logging |
| `test` | Testing configuration |
| `prod` | Production settings |

Key configuration properties in each service's `application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sif_broker
    username: postgres
    password: postgres
  
  redis:
    host: localhost
    port: 6379

server:
  port: 808X  # Service-specific port
```

Environment variables:
- `DB_USERNAME` - Database username (defaults to postgres)
- `DB_PASSWORD` - Database password (defaults to postgres)
- `REDIS_HOST` - Redis host (defaults to localhost)
- `REDIS_PORT` - Redis port (defaults to 6379)

## API Reference

### SIF Headers

All SIF API requests should include these headers:

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes | Authentication credentials |
| `X-SIF-ZONE` | Yes | Target zone ID |
| `X-SIF-CONTEXT` | No | Context ID (defaults to DEFAULT) |
| `X-SIF-REQUESTID` | No | Unique request identifier |
| `X-SIF-TIMESTAMP` | No | Request timestamp (ISO 8601) |
| `X-SIF-MESSAGEID` | No | Message correlation ID |
| `X-SIF-QUEUEID` | No | Queue ID for DELAYED mode |

### Infrastructure Endpoints

#### Environment Service (Port 8081)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/environments/environment` | Register a new environment |
| `GET` | `/environments/{id}` | Get environment details |
| `DELETE` | `/environments/{id}` | Deregister environment |
| `POST` | `/zones` | Create zone |
| `GET` | `/zones` | List zones |

#### Queue Service (Port 8082)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/queues` | Create a queue |
| `GET` | `/queues` | List queues |
| `GET` | `/queues/{id}` | Get queue details |
| `DELETE` | `/queues/{id}` | Delete queue |
| `GET` | `/queues/{id}/messages` | Get next message |
| `DELETE` | `/queues/{id}/messages/{msgId}` | Acknowledge message |

#### Subscription Service (Port 8083)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/subscriptions` | Create subscription |
| `GET` | `/subscriptions` | List subscriptions |
| `GET` | `/subscriptions/{id}` | Get subscription |
| `DELETE` | `/subscriptions/{id}` | Delete subscription |

#### Job Service (Port 8084)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/jobs` | Create a job |
| `GET` | `/jobs` | List jobs |
| `GET` | `/jobs/{id}` | Get job status |
| `PUT` | `/jobs/{id}` | Update job |
| `DELETE` | `/jobs/{id}` | Cancel job |

### Connector Endpoints (via Gateway - Port 8080)

#### Requests Connector (Object Services)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/requests/{serviceName}s` | Create object(s) |
| `GET` | `/requests/{serviceName}s` | Query all objects |
| `GET` | `/requests/{serviceName}s/{id}` | Get single object |
| `PUT` | `/requests/{serviceName}s/{id}` | Update object |
| `DELETE` | `/requests/{serviceName}s/{id}` | Delete object |

#### Responses Connector
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/responses` | Submit async response |

#### Events Connector  
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/events` | Publish event |
| `POST` | `/events/{serviceName}` | Publish event for service |

#### Services Connector (Functional Services)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/services/{serviceName}` | Execute service |
| `POST` | `/services/{serviceName}/{operation}` | Execute named operation |
| `GET` | `/services/{serviceName}` | Query service |

### Health Endpoints

Each service exposes health endpoints:

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Overall health status |
| `/actuator/health/liveness` | Liveness probe |
| `/actuator/health/readiness` | Readiness probe |
| `/actuator/info` | Service information |
| `/actuator/metrics` | Prometheus metrics |

## Message Exchange Patterns

### IMMEDIATE (Synchronous)
```
Consumer                    Gateway                     Provider
|                         |                           |
|-- Request ------------->|                           |
|                         |-- Forward Request ------->|
|                         |<-- Response --------------|
|<-- Response ------------|                           |
```

### DELAYED (Asynchronous)
```
Consumer                    Gateway                     Provider
|                         |                           |
|-- Request + QueueId --->|                           |
|<-- 202 Accepted --------|                           |
|                         |-- Queue Request --------->|
|                         |                           |
|                         |<-- Poll Queue ------------|
|                         |-- Deliver Request ------->|
|                         |                           |
|                         |<-- POST /responses -------|
|                         |-- Queue Response -------->|
|                         |                           |
|-- Poll Queue ---------->|                           |
|<-- Deliver Response ----|                           |
```

### EVENT (Pub/Sub)
```
Provider                    Gateway                    Subscribers
|                         |                           |
|-- POST /events -------->|                           |
|                         |-- Lookup Subscribers      |
|                         |-- Queue to Sub 1 -------->|
|                         |-- Queue to Sub 2 -------->|
|                         |-- Queue to Sub N -------->|
|<-- 202 Accepted --------|                           |
```

## Database Schema

The broker uses PostgreSQL with separate databases per service:

### Environment Service
- `environments` - Registered consumer/provider sessions
- `zones` - Logical groupings within solutions
- `contexts` - Sub-divisions within zones
- `providers` - Registered service providers
- `provisioned_rights` - RBAC permissions per environment

### Queue Service
- `queues` - Message queues (backed by Redis Streams)
- `queue_messages` - Message metadata

### Subscription Service  
- `subscriptions` - Event and request subscriptions
- `subscription_filters` - Filtering criteria

### Job Service
- `jobs` - Functional service job tracking
- `job_phases` - Job execution phases

## Testing

### Unit Tests
```bash
# Test all services
mvn test

# Test specific service
cd environment-service
mvn test
```

### Integration Tests
Integration tests use Testcontainers for PostgreSQL and Redis:

```bash
# Run integration tests
cd integration-tests
mvn verify
```

### End-to-End Testing
```bash
# Start all services first
.\start-broker.ps1

# Run E2E tests
cd integration-tests
mvn test -Dtest=**/*E2ETest
```

### API Testing
Use the provided PowerShell scripts for API testing:

```bash
# Test authentication
.\test-api-authentication.ps1

# Test zone initialization  
.\test-initFullTestZone.ps1
```

## Deployment

### Docker Compose (Development)

Each service includes a `docker-compose.yml` for local development:

```bash
# Start infrastructure
cd queue-service
docker-compose up -d postgres redis

# Start individual service
cd environment-service
docker-compose up -d
```

### Production Deployment

For production deployments:

1. **Database**: Use managed PostgreSQL service or dedicated instances per service
2. **Redis**: Use Redis Cluster or managed Redis service  
3. **Services**: Deploy as containers with appropriate resource limits

Recommended JVM options for each service:
```bash
java -Xms256m -Xmx1g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar service.jar
```

### Kubernetes

Kubernetes manifests are available in each service's `k8s/` directory:

```bash
# Deploy environment service
cd environment-service/k8s
kubectl apply -f .

# Deploy all services
for service in */k8s; do
  kubectl apply -f $service/
done
```

## Observability

### Monitoring Stack

The `observability/` directory contains a complete monitoring setup:

```bash
cd observability/docker
docker-compose up -d
```

Services included:
- **Prometheus** (port 9090) - Metrics collection
- **Grafana** (port 3000) - Dashboards and visualization  
- **Jaeger** (port 16686) - Distributed tracing
- **Elasticsearch** (port 9200) - Log aggregation
- **Kibana** (port 5601) - Log visualization

### Metrics

Each service exposes Prometheus metrics at `/actuator/prometheus`:

Key metrics:
- HTTP request duration and count
- JVM memory and GC stats  
- Database connection pool stats
- Redis connection stats
- Queue message counts
- Custom business metrics

### Distributed Tracing

Services are instrumented with OpenTelemetry for distributed tracing. Traces are sent to Jaeger.

### Logging

Structured JSON logging with correlation IDs for request tracing across services.

## Security

### Authentication Methods

1. **Basic Auth**: `Authorization: Basic base64(applicationKey:password)`
2. **SIF HMAC-SHA256**: `Authorization: SIF_HMACSHA256 base64(signature)`  
3. **Bearer Token**: `Authorization: Bearer <token>`

### Authorization

Role-Based Access Control (RBAC) with the following right types:

| Right | Description |
|-------|-------------|
| `QUERY` | Read/query objects |
| `CREATE` | Create new objects |
| `UPDATE` | Modify existing objects |
| `DELETE` | Remove objects |
| `SUBSCRIBE` | Subscribe to events |
| `PROVIDE` | Act as a provider |

Rights can have these values:
- `APPROVED` - Permission granted
- `SUPPORTED` - Provider supports this operation  
- `REJECTED` - Permission denied
- `UNSUPPORTED` - Provider doesn't support this

### Admin Credentials

Default admin credentials for local development:
- **Username**: `admin`
- **Password**: `admin123`

⚠️ **Change these in production!**

## Development

### Project Structure

```
sif-unity-broker/
├── sif-broker-common/          # Shared library
├── gateway/                    # API Gateway
├── environment-service/        # Environment management
├── queue-service/             # Message queuing
├── subscription-service/      # Event subscriptions  
├── job-service/              # Job tracking
├── requests-connector/       # Request routing
├── responses-connector/      # Response handling
├── events-connector/         # Event publishing
├── services-connector/       # Functional services
├── integration-tests/        # E2E tests
├── observability/           # Monitoring stack
├── start-broker.ps1         # Start all services (Windows)
├── start-broker.sh          # Start all services (Linux/macOS)
├── stop-broker.ps1          # Stop all services (Windows)
├── stop-broker.sh           # Stop all services (Linux/macOS)
└── check-services.ps1       # Health check (Windows)
```

### Development Workflow

1. **Make changes** to service code
2. **Build service**: `mvn clean package -DskipTests`
3. **Restart service** (close PowerShell window, script will restart)
4. **Test changes** using health endpoints or API tests
5. **Run tests**: `mvn test`

### Adding a New Service

1. Create new Spring Boot module
2. Add dependency on `sif-broker-common`
3. Implement health endpoints
4. Add service to gateway routing
5. Update startup scripts
6. Add integration tests

## Troubleshooting

### Common Issues

**Services won't start:**
- Check PostgreSQL and Redis are running: `docker ps`
- Verify database credentials in `application-local.yml`
- Check port conflicts: `netstat -an | findstr :8080`

**Database connection errors:**
- Ensure PostgreSQL container is running
- Verify database exists: `docker exec -it queue-service-postgres psql -U postgres -l`
- Check credentials match configuration

**Redis connection errors:**  
- Verify Redis container: `docker exec -it queue-service-redis redis-cli ping`
- Check Redis host/port in configuration

**Gateway routing issues:**
- Verify all services are healthy
- Check gateway logs for routing errors
- Ensure service URLs are correct in gateway config

### Useful Commands

```bash
# Check all service health
.\check-services.ps1

# View service logs (Docker)
docker logs queue-service-postgres
docker logs queue-service-redis

# Reset database
.\reset-database.ps1

# Clean build all services
mvn clean package -DskipTests
```

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes
4. Add tests for new functionality  
5. Ensure all tests pass: `mvn test`
6. Submit a pull request

### Code Style

- Follow Java naming conventions
- Use Spring Boot best practices
- Add comprehensive tests
- Document public APIs
- Include health checks for new services

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## References

- [SIF Association](https://www.sifassociation.org/)
- [SIF 3.7 Infrastructure Specification](https://www.sifassociation.org/Specification)
- [CEDS (Common Education Data Standards)](https://ceds.ed.gov/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway)

## Support

For issues and questions:

- **GitHub Issues**: [Report a bug](../../issues)
- **Documentation**: See `LOCAL-DEVELOPMENT-SETUP-GUIDE.md` for detailed setup
- **API Documentation**: Available at each service's `/swagger-ui.html` endpoint
- **Monitoring**: Access Grafana dashboards at http://localhost:3000

---

**Quick Links:**
- 📚 [Local Development Setup](LOCAL-DEVELOPMENT-SETUP-GUIDE.md)
- 🚀 [Deployment Guide](SIF-UNITY-BROKER-DEPLOYMENT-READINESS.md)  
- 🔍 [API Authentication Guide](API-AUTHENTICATION-GUIDE.md)
- 📊 [Observability Setup](observability/docs/README.md)
- 🧪 [Integration Tests](integration-tests/README.md)