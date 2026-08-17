# Docker Deployment Testing Checklist

This checklist should be used to verify the Docker deployment of the Subscription Service.

## Pre-Deployment Validation

### Configuration Files
- [x] Dockerfile exists and follows multi-stage build pattern
- [x] docker-compose.yml exists with all required services
- [x] mockserver-init.json exists with Queue Service mock configuration
- [x] .dockerignore exists to optimize build context
- [x] DOCKER-DEPLOYMENT.md documentation exists

### File Validation
- [ ] Dockerfile syntax is valid (run: `docker build --no-cache --target build -t test .`)
- [ ] docker-compose.yml syntax is valid (run: `docker-compose config`)
- [ ] mockserver-init.json is valid JSON (run: `jq . docker/mockserver-init.json`)

### Port Availability
- [ ] Port 8083 is available (Subscription Service)
- [ ] Port 5433 is available (PostgreSQL)
- [ ] Port 6380 is available (Redis)
- [ ] Port 8082 is available (Mock Queue Service)

## Build Phase

### Docker Image Build
- [ ] Build completes without errors: `docker build -t subscription-service:latest .`
- [ ] Build stage downloads dependencies successfully
- [ ] Build stage compiles application successfully
- [ ] Runtime stage creates minimal image
- [ ] Image size is reasonable (< 500MB)
- [ ] Image includes health check configuration

### Verification Commands
```bash
# Build the image
docker build -t subscription-service:latest .

# Check image size
docker images subscription-service:latest

# Inspect image layers
docker history subscription-service:latest
```

## Deployment Phase

### Service Startup
- [ ] All services start: `docker-compose up -d`
- [ ] PostgreSQL becomes healthy within 30 seconds
- [ ] Redis becomes healthy within 30 seconds
- [ ] Mock Queue Service becomes healthy within 30 seconds
- [ ] Subscription Service becomes healthy within 60 seconds

### Verification Commands
```bash
# Start all services
docker-compose up -d

# Check service status
docker-compose ps

# Check health status
docker-compose ps | grep healthy

# View logs
docker-compose logs subscription-service
```

### Expected Output
All services should show status as "Up" and health as "healthy":
```
NAME                              STATUS              HEALTH
subscription-service              Up (healthy)        healthy
subscription-service-postgres     Up (healthy)        healthy
subscription-service-redis        Up (healthy)        healthy
subscription-service-mock-queue   Up (healthy)        healthy
```

## Functional Testing

### Health Endpoints
- [ ] Liveness probe responds: `curl http://localhost:8083/actuator/health/liveness`
- [ ] Readiness probe responds: `curl http://localhost:8083/actuator/health/readiness`
- [ ] General health endpoint responds: `curl http://localhost:8083/actuator/health`

### Expected Responses
```json
{
  "status": "UP"
}
```

### Database Connectivity
- [ ] Service connects to PostgreSQL successfully
- [ ] Flyway migrations execute successfully
- [ ] subscriptions table is created

### Verification Commands
```bash
# Check database connection
docker-compose exec postgres psql -U postgres -d sif_broker -c "\dt"

# Should show subscriptions table
```

### Redis Connectivity
- [ ] Service connects to Redis successfully
- [ ] Cache is operational

### Verification Commands
```bash
# Check Redis connection
docker-compose exec redis redis-cli ping

# Should return: PONG
```

### API Endpoints

#### Create Subscription
- [ ] POST /subscriptions returns 201 Created
- [ ] Response includes generated UUID
- [ ] Response includes createdAt timestamp

```bash
curl -X POST http://localhost:8083/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "queueId": "123e4567-e89b-12d3-a456-426614174000",
    "serviceName": "StudentPersonal",
    "zoneId": "district-123",
    "contextId": "school-456",
    "type": "EVENT"
  }'
```

#### Query EVENT Subscriptions
- [ ] GET /subscriptions/events returns 200 OK
- [ ] Response includes matching subscriptions

```bash
curl "http://localhost:8083/subscriptions/events?serviceName=StudentPersonal&zoneId=district-123&contextId=school-456"
```

#### Get Subscription by ID
- [ ] GET /subscriptions/{id} returns 200 OK for existing subscription
- [ ] GET /subscriptions/{id} returns 404 Not Found for non-existent subscription

```bash
# Replace {id} with actual subscription ID
curl http://localhost:8083/subscriptions/{id}
```

#### List Subscriptions by Queue
- [ ] GET /subscriptions?queueId={queueId} returns 200 OK
- [ ] Response includes all subscriptions for the queue

```bash
curl "http://localhost:8083/subscriptions?queueId=123e4567-e89b-12d3-a456-426614174000"
```

#### Delete Subscription
- [ ] DELETE /subscriptions/{id} returns 204 No Content
- [ ] Subsequent GET returns 404 Not Found

```bash
# Replace {id} with actual subscription ID
curl -X DELETE http://localhost:8083/subscriptions/{id}
```

### Error Handling
- [ ] Invalid request returns 400 Bad Request
- [ ] Non-existent resource returns 404 Not Found
- [ ] Duplicate subscription returns 409 Conflict

```bash
# Test invalid request
curl -X POST http://localhost:8083/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "StudentPersonal"
  }'

# Should return 400 Bad Request
```

## Performance Testing

### Response Times
- [ ] Health check responds within 100ms
- [ ] Create subscription responds within 500ms
- [ ] Query subscriptions responds within 200ms
- [ ] Delete subscription responds within 500ms

### Load Testing (Optional)
- [ ] Service handles 100 concurrent requests
- [ ] Service handles 1000 requests per minute
- [ ] No memory leaks under sustained load

## Security Testing

### Container Security
- [ ] Service runs as non-root user (spring:spring)
- [ ] No sensitive data in environment variables (in production)
- [ ] Image uses minimal base (Alpine Linux)
- [ ] No unnecessary packages installed

### Verification Commands
```bash
# Check running user
docker-compose exec subscription-service whoami

# Should return: spring
```

### Network Security
- [ ] Services communicate on internal Docker network
- [ ] Only necessary ports are exposed
- [ ] No direct database access from host (except mapped port)

## Monitoring and Logging

### Logs
- [ ] Application logs are visible: `docker-compose logs subscription-service`
- [ ] Logs include correlation IDs
- [ ] Error logs include stack traces
- [ ] No sensitive data in logs

### Metrics
- [ ] Actuator metrics endpoint is accessible: `curl http://localhost:8083/actuator/metrics`
- [ ] JVM metrics are available
- [ ] Database connection pool metrics are available

## Cleanup and Teardown

### Stop Services
- [ ] Services stop gracefully: `docker-compose down`
- [ ] No orphaned containers remain
- [ ] Volumes persist data (if not using -v flag)

### Verification Commands
```bash
# Stop services
docker-compose down

# Check no containers are running
docker ps | grep subscription-service

# Remove volumes (optional)
docker-compose down -v
```

## Production Readiness

### Configuration
- [ ] Environment variables are externalized
- [ ] Database credentials use secrets
- [ ] Queue Service URL points to actual service (not mock)
- [ ] Resource limits are configured
- [ ] Logging level is appropriate (INFO or WARN)

### High Availability
- [ ] Multiple replicas can be deployed
- [ ] Service is stateless
- [ ] Database supports connection pooling
- [ ] Cache is shared across instances

### Disaster Recovery
- [ ] Database backups are configured
- [ ] Volume data can be restored
- [ ] Service can recover from crashes
- [ ] Health checks trigger automatic restarts

## Sign-Off

### Tested By
- Name: _______________
- Date: _______________
- Environment: _______________

### Issues Found
1. _______________
2. _______________
3. _______________

### Approval
- [ ] All critical tests passed
- [ ] All blockers resolved
- [ ] Ready for production deployment

### Notes
_______________________________________________
_______________________________________________
_______________________________________________
