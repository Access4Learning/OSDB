# Docker Deployment Guide

This guide explains how to run the Environment Service using Docker and Docker Compose for local development.

## Prerequisites

- Docker 20.10 or later
- Docker Compose 2.0 or later
- Maven 3.8+ (for building the JAR)

## Quick Start

### 1. Build the Application

First, build the Spring Boot application JAR:

```bash
./mvnw clean package -DskipTests
```

This creates `target/environment-service-1.0.0-SNAPSHOT.jar`.

### 2. Start the Services

Start both PostgreSQL and the Environment Service:

```bash
docker-compose up -d
```

This will:
- Start PostgreSQL on port 5432
- Run Flyway migrations to create the database schema
- Start the Environment Service on port 8081

### 3. Verify the Services

Check that both services are running:

```bash
docker-compose ps
```

Check the health endpoint:

```bash
curl http://localhost:8081/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

### 4. View Logs

View logs from both services:

```bash
docker-compose logs -f
```

View logs from a specific service:

```bash
docker-compose logs -f environment-service
docker-compose logs -f postgres
```

### 5. Stop the Services

Stop and remove containers:

```bash
docker-compose down
```

Stop and remove containers with volumes (deletes database data):

```bash
docker-compose down -v
```

## Development Mode

For development with enhanced logging and debugging capabilities:

```bash
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

Development mode includes:
- SQL query logging
- Debug-level application logging
- Java remote debugging on port 5005
- Source code volume mounts for hot reload

### Remote Debugging

Connect your IDE debugger to `localhost:5005`:

**IntelliJ IDEA:**
1. Run → Edit Configurations
2. Add New Configuration → Remote JVM Debug
3. Host: localhost, Port: 5005
4. Click Debug

**VS Code:**
Add to `.vscode/launch.json`:
```json
{
  "type": "java",
  "name": "Debug Environment Service",
  "request": "attach",
  "hostName": "localhost",
  "port": 5005
}
```

## Configuration

### Environment Variables

Copy `.env.example` to `.env` and customize:

```bash
cp .env.example .env
```

Available variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_DB` | `sif_broker` | PostgreSQL database name |
| `POSTGRES_USER` | `sif_broker` | PostgreSQL username |
| `POSTGRES_PASSWORD` | `changeme` | PostgreSQL password |
| `SERVER_PORT` | `8081` | Environment Service port |
| `AUTHENTICATION_TIMESTAMP_TOLERANCE_MINUTES` | `5` | HMAC timestamp tolerance |
| `LOGGING_LEVEL` | `INFO` | Application logging level |

### Custom Configuration

Override any Spring Boot property using environment variables:

```bash
docker-compose run -e SPRING_JPA_SHOW_SQL=true environment-service
```

## Database Management

### Access PostgreSQL

Connect to the PostgreSQL container:

```bash
docker-compose exec postgres psql -U sif_broker -d sif_broker
```

### Run SQL Queries

```bash
docker-compose exec postgres psql -U sif_broker -d sif_broker -c "SELECT * FROM environments;"
```

### Backup Database

```bash
docker-compose exec postgres pg_dump -U sif_broker sif_broker > backup.sql
```

### Restore Database

```bash
docker-compose exec -T postgres psql -U sif_broker -d sif_broker < backup.sql
```

### Reset Database

To completely reset the database:

```bash
docker-compose down -v
docker-compose up -d
```

This removes all data and re-runs Flyway migrations.

## Networking

The services communicate over a custom bridge network `environment-service-network`:

- **postgres**: Accessible at `postgres:5432` from environment-service
- **environment-service**: Accessible at `environment-service:8081` from other containers

### Connect from Host

- PostgreSQL: `localhost:5432`
- Environment Service: `http://localhost:8081`

### Connect from Other Containers

Add your service to the same network in docker-compose.yml:

```yaml
services:
  my-service:
    networks:
      - environment-service-network

networks:
  environment-service-network:
    external: true
```

## Volumes

### PostgreSQL Data

Database data persists in the `postgres_data` named volume:

```bash
# List volumes
docker volume ls

# Inspect volume
docker volume inspect environment-service_postgres_data

# Remove volume (deletes all data)
docker volume rm environment-service_postgres_data
```

## Troubleshooting

### Service Won't Start

Check logs for errors:

```bash
docker-compose logs environment-service
```

Common issues:
- **Database connection failed**: Ensure PostgreSQL is healthy (`docker-compose ps`)
- **Port already in use**: Change ports in docker-compose.yml
- **JAR not found**: Run `./mvnw clean package` first

### Database Connection Issues

Test PostgreSQL connectivity:

```bash
docker-compose exec postgres pg_isready -U sif_broker
```

### Flyway Migration Errors

If migrations fail, check Flyway logs:

```bash
docker-compose logs environment-service | grep -i flyway
```

To reset and retry:

```bash
docker-compose down -v
docker-compose up -d
```

### Health Check Failing

Check the health endpoint directly:

```bash
docker-compose exec environment-service wget -O- http://localhost:8081/actuator/health
```

### Out of Memory

Increase JVM memory in docker-compose.yml:

```yaml
environment:
  JAVA_OPTS: "-Xms512m -Xmx1024m"
```

## Production Considerations

For production deployment:

1. **Use secrets management** instead of plain-text passwords
2. **Configure resource limits**:
   ```yaml
   deploy:
     resources:
       limits:
         cpus: '1.0'
         memory: 1G
       reservations:
         cpus: '0.5'
         memory: 512M
   ```
3. **Use external PostgreSQL** (managed database service)
4. **Enable TLS/SSL** for database connections
5. **Configure proper logging** (centralized logging system)
6. **Set up monitoring** (Prometheus, Grafana)
7. **Use production-grade secrets** for database passwords
8. **Configure backup strategy** for PostgreSQL data

## API Testing

### Register an Environment

```bash
curl -X POST http://localhost:8081/environments \
  -H "Content-Type: application/json" \
  -d '{
    "applicationName": "TestApp",
    "type": "CONSUMER",
    "defaultZoneId": "default",
    "defaultContextId": null
  }'
```

### Get Environment

```bash
curl http://localhost:8081/environments/{id}
```

### Health Check

```bash
curl http://localhost:8081/actuator/health
```

### Metrics

```bash
curl http://localhost:8081/actuator/metrics
```

## Additional Resources

- [Spring Boot Docker Documentation](https://spring.io/guides/gs/spring-boot-docker/)
- [PostgreSQL Docker Hub](https://hub.docker.com/_/postgres)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Environment Service README](./README.md)
