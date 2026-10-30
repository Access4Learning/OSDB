# Integration Test Cache Issue

## Summary
Integration tests are failing because provider registrations are being cached in Redis with stale WireMock ports. When tests run sequentially, the second and third tests fail because they try to connect to the WireMock port from the first test.

## Current Status
- ✅ **Test 1 (when run individually)**: `testImmediateRequest_ReturnsProviderResponseSynchronously` - **PASSING**
- ❌ **Test 2**: `testImmediateRequest_WithProviderError_ReturnsErrorResponse` - **FAILING** (tries to connect to port 62359 instead of current WireMock port)
- ❌ **Test 3**: `testImmediateRequest_WithProviderTimeout_ReturnsTimeoutError` - **FAILING** (same issue)

## Root Cause
1. Each test creates a new WireMock instance on a random port
2. Provider registration is created with the WireMock URL (e.g., `http://localhost:63906/StudentPersonals`)
3. Requests Connector caches the provider registration in Redis
4. Test teardown deletes the provider registration from the database
5. **BUT**: The cached entry in Redis is NOT cleared
6. Next test starts with a new WireMock on a different port (e.g., `http://localhost:62359/StudentPersonals`)
7. Requests Connector finds the CACHED registration with the OLD port
8. Connection fails because the old WireMock instance is stopped

## Evidence
From test logs:
```
Connection refused: getsockopt: localhost/127.0.0.1:62359
```

But the current WireMock is on a different port. The Requests Connector is using a stale cached endpoint.

## Solution Options

### Option 1: Clear Redis Cache Between Tests (Recommended)
Add Redis cache clearing to the test teardown:
- Clear the provider registry cache key after deleting the provider registration
- This ensures each test starts with a clean cache state

### Option 2: Disable Caching in Tests
Configure Requests Connector to disable Redis caching during integration tests:
- Set cache TTL to 0 or disable caching entirely
- This ensures provider registrations are always fetched fresh from the database

### Option 3: Use Fixed WireMock Port
Use a fixed port for WireMock across all tests:
- Less realistic (production uses dynamic ports)
- Doesn't solve the general cache invalidation problem

## Recommended Fix
Implement Option 1: Add Redis cache clearing to test teardown.

The test base class should clear Redis after each test to ensure test isolation.

## Files Involved
- `integration-tests/src/test/java/org/sifunity/integration/pattern/ImmediatePatternTest.java` - Test file
- `integration-tests/src/test/java/org/sifunity/integration/base/IntegrationTestBase.java` - Base test class (needs Redis clearing)
- `requests-connector/src/main/java/org/sifunity/requestsconnector/cache/ProviderRegistryCache.java` - Cache implementation
- `requests-connector/src/main/java/org/sifunity/requestsconnector/config/RedisConfig.java` - Redis configuration
