# SIF Unity Broker - Local Development Setup Guide

**Complete step-by-step instructions for running the entire broker on your local machine**

---

> ## ⚡ Fastest Path
>
> Install the [prerequisites](#prerequisites), make sure Docker is running, then from the repo root:
>
> - **Windows:** `.\start-broker.ps1` — builds all modules, starts PostgreSQL + Redis in Docker, and
>   launches all 9 broker services in order. Stop with `.\stop-broker.ps1`.
> - **Linux/macOS:** start PostgreSQL + Redis first (see [Install Infrastructure](#install-infrastructure)),
>   then `./start-broker.sh` (builds all modules and launches the services). Stop with `./stop-broker.sh`.
>
> The scripts build the project for you, so you can skip the manual [Build Services](#build-services)
> and [Start Services](#start-services) steps. Those sections remain below to explain what the scripts
> do and for running individual services or troubleshooting.
>
> The scripts start the **9 broker services only**. The standalone **test-provider** used by the
> certification suite is started separately — see
> [Run the Test Provider](#run-the-test-provider-for-certification-testing).

---

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Install Infrastructure](#install-infrastructure)
3. [Configure Services](#configure-services)
4. [Build Services](#build-services)
5. [Start Services](#start-services)
6. [Verify Deployment](#verify-deployment)
7. [Run the Test Provider (for certification testing)](#run-the-test-provider-for-certification-testing)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software
- **Java 17 or higher** - [Download](https://adoptium.net/)
- **Maven 3.8+** - [Download](https://maven.apache.org/download.cgi)
- **PostgreSQL 15+** - [Download](https://www.postgresql.org/download/)
- **Redis 7+** - [Download](https://redis.io/download/)
- **Git** - [Download](https://git-scm.com/downloads)

### Optional (for full observability)
- **Elasticsearch 8.x** - [Download](https://www.elastic.co/downloads/elasticsearch)
- **Logstash 8.x** - [Download](https://www.elastic.co/downloads/logstash)
- **Kibana 8.x** - [Download](https://www.elastic.co/downloads/kibana)
- **Jaeger** - [Download](https://www.jaegertracing.io/download/)
- **Prometheus** - [Download](https://prometheus.io/download/)
- **Grafana** - [Download](https://grafana.com/grafana/download)

---

## Install Infrastructure

### Option 1: Docker Setup (Recommended for Local Development)

The easiest way to get PostgreSQL and Redis running locally is using the provided Docker Compose configuration:

```bash
# Navigate to queue-service directory
cd queue-service

# Start PostgreSQL and Redis containers
docker-compose up -d postgres redis

# Verify containers are running
docker ps

# Test connections
docker exec -it queue-service-postgres psql -U postgres -d sif_broker -c "SELECT 1;"
docker exec -it queue-service-redis redis-cli ping
```

This approach:
- ✅ **No local installation required** - Just needs Docker
- ✅ **Consistent environment** - Same versions for everyone
- ✅ **Easy cleanup** - `docker-compose down` removes everything
- ✅ **Proper permissions** - No permission issues with database schemas

### Option 2: Native Installation

If you prefer to install PostgreSQL and Redis natively:

### 1. Install PostgreSQL

#### Windows
```powershell
# Download and install PostgreSQL from https://www.postgresql.org/download/windows/
# Or use Chocolatey:
choco install postgresql

# Start PostgreSQL service
net start postgresql-x64-15
```

#### macOS
```bash
# Using Homebrew
brew install postgresql@15
brew services start postgresql@15
```

#### Linux
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install postgresql-15 postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

### 2. Create Databases

**For Docker Setup:**
The database is automatically created by the Docker container. No additional setup needed!

**For Native Installation:**
```sql
-- Connect to PostgreSQL
psql -U postgres

-- Create database (user postgres already exists with full permissions)
CREATE DATABASE sif_broker;

-- Exit psql
\q
```

**Note:** We use the default `postgres` superuser for local development, which simplifies setup and avoids permission issues.

### 3. Install Redis

#### Windows
```powershell
# Download from https://github.com/microsoftarchive/redis/releases
# Or use Chocolatey:
choco install redis-64

# Start Redis
redis-server
```

#### macOS
```bash
# Using Homebrew
brew install redis
brew services start redis
```

#### Linux
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install redis-server
sudo systemctl start redis-server
sudo systemctl enable redis-server
```

### 4. Verify Infrastructure

**For Docker Setup:**
```bash
# Test PostgreSQL
docker exec -it queue-service-postgres psql -U postgres -d sif_broker -c "SELECT version();"

# Test Redis
docker exec -it queue-service-redis redis-cli ping
# Should return: PONG
```

**For Native Installation:**
```bash
# Test PostgreSQL
psql -U postgres -d sif_broker -h localhost -c "SELECT version();"

# Test Redis
redis-cli ping
# Should return: PONG
```

---

## Configure Services

### 1. Build Common Library

The `sif-broker-common` library must be built first as all services depend on it.

```bash
cd sif-broker-common
mvn clean install
cd ..
```

### 2. Local Configuration Already Exists — Do NOT Re-Create It

> **Important:** Every service already ships a working `src/main/resources/application-local.yml`
> in the repository. You do **not** need to create these files. Running a service with the
> `local` Spring profile (shown in [Start Services](#start-services)) picks them up automatically.
> Hand-creating or overwriting them will likely break things (ports and property names have moved
> over time). Treat the checked-in `application-local.yml` files as the source of truth.

The local profile assumes the defaults below. Override via environment variables only if your
setup differs.

**Database & Redis (all services):**
- PostgreSQL: `jdbc:postgresql://localhost:5432/sif_broker`, user `postgres`, password `postgres`
- Redis: `localhost:6379`, no password

**Admin bootstrap (environment-service):**
The admin environment is bootstrapped on first startup using HMAC credentials (not a username/password).
Defaults (overridable):
```yaml
sif:
  broker:
    bootstrap:
      enabled: true
      admin:
        sessionToken: ${ADMIN_SESSION_TOKEN:admin-bootstrap-token}
        sharedSecret: ${ADMIN_SHARED_SECRET:admin-bootstrap-secret}
```
All authenticated calls use **HMAC-SHA256** signing with the session token + shared secret — there
is no basic username/password auth.

> If you need to inspect or change a service's local config, edit its existing
> `*/src/main/resources/application-local.yml` rather than adding a new file.

---

## Build Services

Build all services in the correct order:

```bash
# 1. Build common library (already done above)
cd sif-broker-common
mvn clean install
cd ..

# 2. Build all services
cd environment-service
mvn clean package -DskipTests
cd ..

cd gateway
mvn clean package -DskipTests
cd ..

cd queue-service
mvn clean package -DskipTests
cd ..

cd subscription-service
mvn clean package -DskipTests
cd ..

cd job-service
mvn clean package -DskipTests
cd ..

cd requests-connector
mvn clean package -DskipTests
cd ..

cd responses-connector
mvn clean package -DskipTests
cd ..

cd events-connector
mvn clean package -DskipTests
cd ..

cd services-connector
mvn clean package -DskipTests
cd ..
```

---

## Start Services

Start services in the correct order (dependencies first). Each runs in its own terminal with the
`local` profile.

### 1. Start Environment Service (FIRST)

```bash
cd environment-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Wait for startup message:** `Started EnvironmentServiceApplication`

**Verify:** http://localhost:8081/actuator/health

### 2. Start Queue Service

```bash
cd queue-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Verify:** http://localhost:8082/actuator/health

### 3. Start Subscription Service

```bash
cd subscription-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Verify:** http://localhost:8083/actuator/health

### 4. Start Job Service

```bash
cd job-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Verify:** http://localhost:8086/actuator/health

### 5. Start Connector Services

#### Events Connector

```bash
cd events-connector
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Verify:** http://localhost:8084/actuator/health

#### Requests Connector

```bash
cd requests-connector
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Verify:** http://localhost:8085/actuator/health

#### Services Connector

```bash
cd services-connector
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Verify:** http://localhost:8087/actuator/health

#### Responses Connector

```bash
cd responses-connector
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Verify:** http://localhost:8088/actuator/health

### 6. Start Gateway (LAST)

```bash
cd gateway
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Verify:** http://localhost:8080/actuator/health

---

## Verify Deployment

### 1. Check All Services

```bash
# Gateway
curl http://localhost:8080/actuator/health

# Environment Service
curl http://localhost:8081/actuator/health

# Queue Service
curl http://localhost:8082/actuator/health

# Subscription Service
curl http://localhost:8083/actuator/health

# Events Connector
curl http://localhost:8084/actuator/health

# Requests Connector
curl http://localhost:8085/actuator/health

# Job Service
curl http://localhost:8086/actuator/health

# Services Connector
curl http://localhost:8087/actuator/health

# Responses Connector
curl http://localhost:8088/actuator/health
```

All should return `{"status":"UP"}`

### 2. Confirm Admin Bootstrap

The admin environment is created automatically on first startup of the environment-service.
Check its logs for a bootstrap confirmation, then authenticate using HMAC-SHA256 with the
configured `sessionToken`/`sharedSecret` (defaults `admin-bootstrap-token` /
`admin-bootstrap-secret`). See `bruno-certification-tests/common/auth.js` or
`API-AUTHENTICATION-GUIDE.md` for how the signature is built.

```bash
# Verify the admin environment exists in the database
psql -U postgres -d sif_broker -h localhost -c "SELECT application_name, type FROM environments WHERE type='ADMIN';"
```

---

## Run the Test Provider (for certification testing)

The broker's request/response certification flow (the Bruno suite under
`bruno-certification-tests/`) needs the standalone **test-provider** app running. It is a test
harness, not one of the nine broker services, and is built/run separately.

```bash
cd test-provider
mvn clean package -DskipTests
java -jar target/test-provider-1.0.0-SNAPSHOT.jar
```

- Listens on **port 9091** under context path **`/sif/provider`** (base URL
  `http://localhost:9091/sif/provider`).
- Start it with **no credentials** — the Bruno suite registers the provider environment/queue and
  pushes credentials to it at runtime via the unauthenticated `/test-control/configure` endpoint,
  then drives DELAYED polling with `/test-control/poll`. No restart needed.
- Full details (endpoints, IMMEDIATE vs DELAYED behavior, ngrok for a remote broker) are in
  `test-provider/README.md`.

---

## Troubleshooting

### PostgreSQL Connection Issues

**Problem:** `Connection refused` or `authentication failed`

**Solution:**

**For Docker Setup (Recommended for Local Development):**
```bash
# Start PostgreSQL and Redis using Docker (from queue-service directory)
cd queue-service
docker-compose up -d postgres redis

# Verify connection
docker exec -it queue-service-postgres psql -U postgres -d sif_broker -c "SELECT 1;"
```

**For Native PostgreSQL Installation:**
```bash
# Check PostgreSQL is running
# Windows:
net start postgresql-x64-15

# macOS/Linux:
sudo systemctl status postgresql

# Verify connection
psql -U postgres -d sif_broker -h localhost

# Check pg_hba.conf allows local connections
# Location: /etc/postgresql/15/main/pg_hba.conf (Linux)
# Add line: host all all 127.0.0.1/32 md5
```

### Redis Connection Issues

**Problem:** `Connection refused`

**Solution:**
```bash
# Check Redis is running
# Windows:
redis-server

# macOS/Linux:
sudo systemctl status redis-server

# Test connection
redis-cli ping
```

### Port Already in Use

**Problem:** `Port 8080 is already in use`

**Solution:**
```bash
# Find process using port
# Windows:
netstat -ano | findstr :8080
taskkill /PID <process-id> /F

# macOS/Linux:
lsof -i :8080
kill -9 <process-id>

# Or override the port via SERVER_PORT when starting the service
```

### Flyway Migration Errors

**Problem:** `Flyway migration failed`

**Solution:**
```bash
# Reset database
psql -U postgres -d sif_broker -h localhost

DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
\q

# Restart service
```

### Service Won't Start

**Problem:** Service fails to start with errors

**Solution:**
1. Check logs for specific error
2. Verify all dependencies are running (PostgreSQL, Redis)
3. Verify you started it with `-Dspring-boot.run.profiles=local`
4. Check Java version: `java -version` (should be 17+)
5. Check Maven version: `mvn -version` (should be 3.8+)
6. Rebuild: `mvn clean package -DskipTests`

### Admin Bootstrap Not Working

**Problem:** Admin environment not created

**Solution:**
```bash
# Check environment-service logs for bootstrap messages
# Verify sif.broker.bootstrap.enabled=true (it is, in application-local.yml)
# Check database for admin environment:
psql -U postgres -d sif_broker -h localhost -c "SELECT application_name, type FROM environments WHERE type='ADMIN';"
```

### DELAYED→DELAYED request returns 500 / alert not delivered

**Problem:** A connector can't reach a core service it depends on.

**Solution:** Each connector and the environment-service must point at the other services. Locally
these are wired by the checked-in `application-local.yml` files. If you overrode any service URL via
environment variables, confirm they match the [Service Ports Reference](#service-ports-reference)
below (e.g. requests-connector needs subscription-service on 8083 and queue-service on 8082;
environment-service's alert delivery needs `SIF_INFRA_SUBSCRIPTIONS_URL` and `SIF_INFRA_QUEUES_URL`).

---

## Quick Start Scripts

Automated scripts are provided to start, stop, and check all services. **They build all modules
first** (so `sif-broker-common` is installed to your local `.m2`), then start the services in the
correct order — you do not need to run the manual build/start steps separately.

**For Docker Setup:**
```powershell
# From the repo root — the script starts PostgreSQL + Redis in Docker automatically,
# builds all modules, and launches the 9 services.
.\start-broker.ps1
```

> On **Windows**, `start-broker.ps1` starts PostgreSQL + Redis in Docker for you. On **Linux/macOS**,
> `start-broker.sh` expects PostgreSQL + Redis to already be running (start them via Docker or
> natively first — see [Install Infrastructure](#install-infrastructure)).

### Start All Services

**Windows (PowerShell):**
```powershell
.\start-broker.ps1
```

**Linux/macOS (Bash):**
```bash
./start-broker.sh
```

The start script will:
- Build all modules (`mvn install -DskipTests`)
- Check/start PostgreSQL and Redis (Windows: via Docker; Linux/macOS: expects them running)
- Start all 9 services in the correct order
- Wait for Environment Service to be ready before starting dependent services
- Open separate terminal windows (PowerShell) or log to `logs/` (Bash)

### Stop All Services

**Windows (PowerShell):**
```powershell
.\stop-broker.ps1
```
Or close each service's PowerShell window / press Ctrl+C in each.

**Linux/macOS (Bash):**
```bash
./stop-broker.sh
```

### Check Service Health

**Windows (PowerShell):**
```powershell
.\check-services.ps1
```

**Linux/macOS (Bash):**
```bash
./check-services.sh
```

---

## Service Ports Reference

| Service | Port | Health Check |
|---------|------|--------------|
| Gateway | 8080 | http://localhost:8080/actuator/health |
| Environment Service | 8081 | http://localhost:8081/actuator/health |
| Queue Service | 8082 | http://localhost:8082/actuator/health |
| Subscription Service | 8083 | http://localhost:8083/actuator/health |
| Events Connector | 8084 | http://localhost:8084/actuator/health |
| Requests Connector | 8085 | http://localhost:8085/actuator/health |
| Job Service | 8086 | http://localhost:8086/actuator/health |
| Services Connector | 8087 | http://localhost:8087/actuator/health |
| Responses Connector | 8088 | http://localhost:8088/actuator/health |
| Test Provider (harness) | 9091 | http://localhost:9091/sif/provider/actuator/health |

---

## Next Steps

1. **Read Service Documentation:** Each service has a README.md with API documentation
2. **Test API Endpoints:** Use the Bruno certification suite under `bruno-certification-tests/` or curl
3. **Set Up Observability:** Follow `observability/docker/README.md` for full observability stack
4. **Run Integration Tests:** See `integration-tests/README.md`

---

## Additional Resources

- **SIF 3.7 Specification:** https://specification.sifassociation.org/
- **Service READMEs:** Each service directory has detailed documentation
- **Test Provider:** `test-provider/README.md`
- **API Authentication:** `API-AUTHENTICATION-GUIDE.md`

---

**Last Updated:** July 1, 2026  
**Version:** 1.2
