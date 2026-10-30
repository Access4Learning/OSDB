# Troubleshooting Guide

Common issues and solutions for the Bruno API Test Suite.

## Table of Contents

- [Connection Issues](#connection-issues)
- [Authentication Issues](#authentication-issues)
- [Authorization Issues](#authorization-issues)
- [Test Failures](#test-failures)
- [Environment Configuration](#environment-configuration)
- [Bruno Application Issues](#bruno-application-issues)
- [Performance Issues](#performance-issues)

## Connection Issues

### Cannot Connect to Services

**Symptoms**:
- Health checks fail with "Connection refused"
- Requests timeout
- "ECONNREFUSED" errors

**Diagnosis**:
```bash
# Check if services are running
docker ps  # For Docker Compose
kubectl get pods -n sif-unity  # For Kubernetes

# Try accessing services directly
curl http://localhost:8080/actuator/health
```

**Solutions**:

1. **Verify Broker is Running**:
   ```bash
   # Docker Compose
   docker-compose ps
   docker-compose logs gateway
   
   # Kubernetes
   kubectl get pods -n sif-unity
   kubectl logs -l app=gateway -n sif-unity
   ```

2. **Check Service URLs**:
   - Open `environments/local.bru` (or your environment)
   - Verify URLs match your deployment
   - Update if services are on different ports

3. **Check Network Connectivity**:
   ```bash
   # Test connectivity
   telnet localhost 8080
   nc -zv localhost 8080
   ```

4. **Check Firewall Rules**:
   - Ensure ports 8080-8088 are not blocked
   - Check Docker network settings
   - Verify Kubernetes network policies

### Service Unavailable (503)

**Symptoms**:
- Services return 503 Service Unavailable
- Health checks show "DOWN" status

**Diagnosis**:
```bash
# Check service health
curl http://localhost:8081/actuator/health

# Check service logs
docker logs environment-service
kubectl logs -l app=environment-service
```

**Solutions**:

1. **Check Dependencies**:
   - PostgreSQL must be running and accessible
   - Redis must be running (for Queue Service)
   - S3/LocalStack must be running (for Queue Service)

2. **Verify Database Connection**:
   ```bash
   # Test PostgreSQL
   psql -h localhost -U sif_user -d sif_broker
   
   # Check connection in service logs
   docker logs environment-service | grep -i database
   ```

3. **Verify Redis Connection**:
   ```bash
   # Test Redis
   redis-cli ping
   
   # Check connection in service logs
   docker logs queue-service | grep -i redis
   ```

4. **Check Resource Limits**:
   - Services may be out of memory
   - Check CPU usage
   - Increase resource limits if needed

## Authentication Issues

### 401 Unauthorized - All Requests

**Symptoms**:
- All authenticated requests return 401
- Error: "Invalid token" or "Authentication failed"

**Diagnosis**:
```bash
# Verify credentials are set
cat .env | grep SESSION_TOKEN

# Check if environment exists in broker
curl -X GET http://localhost:8081/environments \
  -H "Authorization: SIF_HMACSHA256 admin-bootstrap-token:dummy"
```

**Solutions**:

1. **Verify Credentials in .env**:
   ```env
   # Check these are set correctly
   SELECTED_ENVIRONMENT=local
   LOCAL_SESSION_TOKEN=admin-bootstrap-token
   LOCAL_SHARED_SECRET=admin-bootstrap-secret
   ```

2. **Verify Session Token Exists**:
   - Session token must exist in broker database
   - Check `environments` table
   - Use bootstrap ADMIN credentials if available

3. **Check Environment Selection**:
   - Verify `SELECTED_ENVIRONMENT` matches your setup
   - Ensure corresponding credentials are set
   - Restart Bruno after changing `.env`

4. **Verify HMAC Signature**:
   - Enable verbose logging: `verboseLogging: true` in environment
   - Check Bruno console for signature details
   - Verify timestamp is current (within 5 minutes)

### 401 Unauthorized - Expired Timestamp

**Symptoms**:
- Requests fail with "Expired timestamp"
- Error mentions timestamp tolerance

**Solutions**:

1. **Check System Clock**:
   ```bash
   # Verify system time is correct
   date
   
   # Sync time if needed
   sudo ntpdate -s time.nist.gov  # Linux
   ```

2. **Check Timestamp Tolerance**:
   - Default tolerance is 5 minutes
   - Ensure request timestamp is within tolerance
   - Check broker configuration for custom tolerance

### 401 Unauthorized - Invalid HMAC

**Symptoms**:
- Requests fail with "Invalid HMAC signature"
- Signature doesn't match expected value

**Solutions**:

1. **Verify Shared Secret**:
   - Ensure shared secret in `.env` matches broker database
   - Check for typos or extra spaces
   - Shared secret is case-sensitive

2. **Check Signature Calculation**:
   - Enable verbose logging to see signature message
   - Verify message format: `SESSION_TOKEN\nTIMESTAMP`
   - Check for URL encoding issues

3. **Verify Request Body**:
   - Body must match exactly what's signed
   - Check for extra whitespace
   - Verify Content-Type header

## Authorization Issues

### 403 Forbidden - Insufficient Permissions

**Symptoms**:
- Authenticated requests return 403
- Error: "Insufficient permissions" or "Access denied"

**Diagnosis**:
```bash
# Check ACL entries for your environment
curl -X GET http://localhost:8080/acl-entries \
  -H "Authorization: SIF_HMACSHA256 your-token:signature" \
  -H "timestamp: 2024-01-15T10:30:00Z"
```

**Solutions**:

1. **Create ACL Entries**:
   - Use `02-infrastructure/acls` tests to create ACLs
   - Ensure `accessLevel` is `APPROVED`
   - Match service name, zone, and context

2. **Verify ACL Configuration**:
   ```json
   {
     "environmentId": "your-env-id",
     "serviceName": "StudentPersonal",
     "zoneId": "default",
     "contextId": "DEFAULT",
     "accessType": "CONSUME",
     "accessLevel": "APPROVED"
   }
   ```

3. **Check Access Type**:
   - `PROVIDE`: For providers offering services
   - `CONSUME`: For consumers requesting services
   - `SUBSCRIBE`: For subscribers receiving events

4. **Verify Zone and Context**:
   - Request zone/context must match ACL entry
   - Check default values in environment configuration
   - Ensure zone and context exist in broker

### 403 Forbidden - Zone Access Denied

**Symptoms**:
- Requests to specific zones return 403
- Error mentions zone access

**Solutions**:

1. **Create Zone-Specific ACL**:
   - ACL entries are scoped to zones
   - Create separate ACL for each zone
   - Use wildcard (`*`) for all zones (if supported)

2. **Verify Zone Exists**:
   - Check zone is registered in broker
   - Use `02-infrastructure/zones-contexts` tests

## Test Failures

### Test Assertions Fail

**Symptoms**:
- Tests run but assertions fail
- Response doesn't match expected values

**Diagnosis**:
- Review test output in Bruno
- Check response body and status code
- Review test documentation

**Solutions**:

1. **Check Prerequisites**:
   - Some tests require previous tests to run first
   - Example: "Get Environment" requires "Create Environment"
   - Run tests in sequence order

2. **Verify Test Data**:
   - Check stored variables (e.g., `createdEnvironmentId`)
   - Ensure test data wasn't manually deleted
   - Run cleanup and start fresh

3. **Check Broker State**:
   - Broker may have stale data
   - Run cleanup collection
   - Restart broker if needed

4. **Review Response**:
   - Check actual response vs. expected
   - Look for error messages in response body
   - Verify response format matches SIF specification

### Tests Timeout

**Symptoms**:
- Tests don't complete
- Bruno shows "Request timeout"

**Solutions**:

1. **Increase Timeout**:
   - Edit environment configuration
   - Increase `requestTimeout` value
   - Default is 30000ms (30 seconds)

2. **Check Service Performance**:
   - Services may be slow or overloaded
   - Check service logs for errors
   - Monitor resource usage

3. **Check Network Latency**:
   - Test network speed
   - Check for network congestion
   - Use local deployment for faster tests

### Variable Not Found Errors

**Symptoms**:
- Tests fail with "Variable not found"
- Variables show as `{{variableName}}`

**Solutions**:

1. **Run Prerequisites**:
   - Some variables are set by previous tests
   - Example: `createdEnvironmentId` set by "Create Environment"
   - Run tests in order

2. **Check Variable Scope**:
   - Environment variables: Persistent across tests
   - Test variables (`bru.setVar`): Temporary, cleared after test
   - Verify variable is set in correct scope

3. **Manually Set Variables**:
   - Click environment name in Bruno
   - Add missing variables manually
   - Or run the test that sets the variable

## Environment Configuration

### Wrong Environment Selected

**Symptoms**:
- Tests connect to wrong broker instance
- Unexpected data or errors

**Solutions**:

1. **Verify Environment Selection**:
   - Check dropdown in Bruno (top-right)
   - Should match your deployment (local, docker-compose, kubernetes)

2. **Verify .env Configuration**:
   ```env
   # Must match Bruno environment selection
   SELECTED_ENVIRONMENT=local
   ```

3. **Restart Bruno**:
   - Close and reopen Bruno
   - Reload collection
   - Verify environment is selected

### Environment Variables Not Loading

**Symptoms**:
- Variables show as `{{variableName}}`
- Credentials not applied

**Solutions**:

1. **Check .env File**:
   - Verify `.env` exists in `bruno-tests/` folder
   - Check file permissions (must be readable)
   - Verify no syntax errors

2. **Restart Bruno**:
   - Bruno loads `.env` on startup
   - Close and reopen after editing `.env`

3. **Check Variable Names**:
   - Variable names are case-sensitive
   - Match exactly: `LOCAL_SESSION_TOKEN` not `local_session_token`

4. **Check Environment File**:
   - Open `environments/local.bru`
   - Verify variables are defined
   - Check for typos

## Bruno Application Issues

### Bruno Won't Start

**Solutions**:
- Check system requirements
- Update to latest version
- Check for conflicting applications
- Review Bruno logs

### Collection Won't Load

**Solutions**:
- Verify `bruno.json` exists
- Check folder permissions
- Try opening parent folder
- Check for corrupted files

### Tests Won't Run

**Solutions**:
- Check Bruno console for errors
- Verify JavaScript syntax in scripts
- Check for missing dependencies
- Update Bruno to latest version

## Performance Issues

### Slow Test Execution

**Solutions**:

1. **Run Tests Selectively**:
   - Run individual collections instead of all tests
   - Skip optional tests
   - Focus on failing tests

2. **Optimize Broker**:
   - Increase broker resources
   - Check database performance
   - Monitor network latency

3. **Use Local Deployment**:
   - Local deployments are faster than remote
   - Avoid network latency
   - Use Docker Compose for testing

### High Memory Usage

**Solutions**:
- Close other applications
- Restart Bruno
- Run fewer tests concurrently
- Increase system memory

## Getting Additional Help

### Collect Diagnostic Information

Before seeking help, collect:

1. **Bruno Version**:
   ```
   Help → About in Bruno GUI
   ```

2. **Environment Configuration**:
   ```bash
   cat environments/local.bru
   ```

3. **Test Output**:
   - Screenshot of failed test
   - Response body and headers
   - Console errors

4. **Service Logs**:
   ```bash
   docker-compose logs
   kubectl logs -l app=gateway
   ```

5. **Health Check Results**:
   - Run `01-health-checks` collection
   - Capture all results

### Resources

- [Bruno Documentation](https://docs.usebruno.com/)
- [SIF Specification](https://specification.sifassociation.org/)
- [Setup Guide](SETUP.md)
- [Contributing Guide](CONTRIBUTING.md)

### Reporting Issues

When reporting issues:

1. Describe the problem clearly
2. Include steps to reproduce
3. Provide diagnostic information
4. Mention your environment (local, docker, k8s)
5. Include Bruno and broker versions

## Common Error Messages

### "ECONNREFUSED"
- **Meaning**: Cannot connect to service
- **Solution**: Verify service is running and URL is correct

### "ETIMEDOUT"
- **Meaning**: Request timed out
- **Solution**: Increase timeout or check service performance

### "Invalid token"
- **Meaning**: Session token not found or invalid
- **Solution**: Verify credentials in `.env`

### "Expired timestamp"
- **Meaning**: Request timestamp too old
- **Solution**: Check system clock, ensure within 5 minutes

### "Insufficient permissions"
- **Meaning**: Missing or denied ACL entry
- **Solution**: Create APPROVED ACL entry

### "Service unavailable"
- **Meaning**: Service or dependency is down
- **Solution**: Check service health and dependencies

### "Variable not found"
- **Meaning**: Required variable not set
- **Solution**: Run prerequisite tests or set variable manually
