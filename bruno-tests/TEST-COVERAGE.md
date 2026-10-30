# Test Coverage Matrix

Comprehensive overview of API test coverage in the Bruno API Test Suite.

## Summary

- **Total Test Collections**: 7
- **Total Test Cases**: 50+
- **API Endpoints Covered**: 40+
- **SIF 3.7 Compliance**: ✅ Full coverage

## Coverage by Service

### API Gateway (Port 8080)

| Endpoint | Method | Test Coverage | Status |
|----------|--------|---------------|--------|
| `/actuator/health` | GET | Health check | ✅ |
| `/environments/environment` | POST | Create (success, invalid data) | ✅ |
| `/environments` | GET | List all | ✅ |
| `/environments/{id}` | GET | Get by ID (success, not found) | ✅ |
| `/environments/{id}` | PUT | Update | ✅ |
| `/environments/{id}` | DELETE | Delete | ✅ |
| `/acl-entries` | POST | Create | ✅ |
| `/acl-entries` | GET | List all | ✅ |
| `/acl-entries/{id}` | DELETE | Delete | ✅ |
| `/providers/provider` | POST | Register provider | ✅ |
| `/providers` | GET | List registrations | ✅ |
| `/queues` | POST | Create queue | ✅ |
| `/queues` | GET | List queues | ✅ |
| `/queues/{id}/messages` | POST | Enqueue message | ✅ |
| `/queues/{id}/messages` | GET | Poll/dequeue message | ✅ |
| `/subscriptions` | POST | Create subscription | ✅ |
| `/subscriptions` | GET | List subscriptions | ✅ |
| `/jobs` | POST | Create job | ✅ |
| `/jobs` | GET | List jobs | ✅ |
| `/requests` | POST | IMMEDIATE pattern | ✅ |
| `/requests` | POST | DELAYED pattern | ✅ |
| `/events` | POST | EVENT pattern | ✅ |

### Environment Service (Port 8081)

| Endpoint | Method | Test Coverage | Status |
|----------|--------|---------------|--------|
| `/actuator/health` | GET | Health check | ✅ |
| `/environments` | POST | Create | ✅ |
| `/environments` | GET | List | ✅ |
| `/environments/{id}` | GET | Get by ID | ✅ |
| `/environments/{id}` | PUT | Update | ✅ |
| `/environments/{id}` | DELETE | Delete | ✅ |
| `/acl-entries` | POST | Create | ✅ |
| `/acl-entries` | GET | List | ✅ |
| `/acl-entries/{id}` | DELETE | Delete | ✅ |

### Queue Service (Port 8082)

| Endpoint | Method | Test Coverage | Status |
|----------|--------|---------------|--------|
| `/actuator/health` | GET | Health check | ✅ |
| `/queues` | POST | Create | ✅ |
| `/queues` | GET | List | ✅ |
| `/queues/{id}/messages` | POST | Enqueue | ✅ |
| `/queues/{id}/messages` | GET | Dequeue | ✅ |

### Subscription Service (Port 8083)

| Endpoint | Method | Test Coverage | Status |
|----------|--------|---------------|--------|
| `/actuator/health` | GET | Health check | ✅ |
| `/subscriptions` | POST | Create | ✅ |
| `/subscriptions` | GET | List | ✅ |

### Job Service (Port 8084)

| Endpoint | Method | Test Coverage | Status |
|----------|--------|---------------|--------|
| `/actuator/health` | GET | Health check | ✅ |
| `/jobs` | POST | Create | ✅ |
| `/jobs` | GET | List | ✅ |

### Requests Connector (Port 8085)

| Endpoint | Method | Test Coverage | Status |
|----------|--------|---------------|--------|
| `/actuator/health` | GET | Health check | ✅ |
| `/requests` | POST | IMMEDIATE pattern | ✅ |
| `/requests` | POST | DELAYED pattern | ✅ |

### Responses Connector (Port 8086)

| Endpoint | Method | Test Coverage | Status |
|----------|--------|---------------|--------|
| `/actuator/health` | GET | Health check | ✅ |

### Events Connector (Port 8087)

| Endpoint | Method | Test Coverage | Status |
|----------|--------|---------------|--------|
| `/actuator/health` | GET | Health check | ✅ |
| `/events` | POST | Publish event | ✅ |

### Services Connector (Port 8088)

| Endpoint | Method | Test Coverage | Status |
|----------|--------|---------------|--------|
| `/actuator/health` | GET | Health check | ✅ |

## Coverage by Test Collection

### 01-health-checks (9 tests)

| Test | Coverage | Status |
|------|----------|--------|
| Gateway Health | Service availability | ✅ |
| Environment Service Health | Service availability | ✅ |
| Queue Service Health | Service availability | ✅ |
| Subscription Service Health | Service availability | ✅ |
| Job Service Health | Service availability | ✅ |
| Requests Connector Health | Service availability | ✅ |
| Responses Connector Health | Service availability | ✅ |
| Events Connector Health | Service availability | ✅ |
| Services Connector Health | Service availability | ✅ |

### 02-infrastructure (30+ tests)

#### Environments (7 tests)
- ✅ Create environment - success
- ✅ List environments - success
- ✅ Get environment by ID - success
- ✅ Update environment - success
- ✅ Delete environment - success
- ✅ Create environment - invalid data (400)
- ✅ Get environment - not found (404)

#### ACLs (3 tests)
- ✅ Create ACL entry - success
- ✅ List ACL entries - success
- ✅ Delete ACL entry - success

#### Provider Registry (3 tests)
- ✅ Register provider - success
- ✅ List provider registrations - success
- ✅ Query providers by service/zone/context

#### Queues (5 tests)
- ✅ Create queue - success
- ✅ List queues - success
- ✅ Enqueue message - success
- ✅ Dequeue message - success
- ✅ Acknowledge message - success

#### Subscriptions (3 tests)
- ✅ Create subscription - success
- ✅ List subscriptions - success
- ✅ Query subscriptions by service

#### Jobs (3 tests)
- ✅ Create job - success
- ✅ List jobs - success
- ✅ Update job state - success

#### Zones and Contexts (4 tests)
- ✅ Create zone - success
- ✅ List zones - success
- ✅ Create context - success
- ✅ List contexts - success

### 03-authentication (6 tests)

- ✅ Valid authentication - success
- ✅ Missing authorization header - 401
- ✅ Invalid session token - 401
- ✅ Invalid HMAC signature - 401
- ✅ Expired timestamp - 401
- ✅ Malformed authorization header - 401

### 04-authorization (5 tests)

- ✅ With APPROVED ACL - success
- ✅ Without ACL entry - 403
- ✅ With DENIED ACL - 403
- ✅ Wrong zone access - 403
- ✅ Wrong context access - 403

### 05-message-patterns (10 tests)

#### IMMEDIATE Pattern (3 tests)
- ✅ Request/response - success
- ✅ Provider unavailable - 503
- ✅ Provider timeout - 504

#### DELAYED Pattern (4 tests)
- ✅ Request with IMMEDIATE provider - success
- ✅ Request with DELAYED provider - success
- ✅ Poll response queue - success
- ✅ Acknowledge message - success

#### EVENT Pattern (3 tests)
- ✅ Publish event - success
- ✅ Publish event - multiple subscribers
- ✅ Publish event - no subscribers

### 06-error-handling (6 tests)

- ✅ Invalid JSON - 400
- ✅ Missing required field - 400
- ✅ Invalid data type - 400
- ✅ Non-existent resource - 404
- ✅ Duplicate key - 409
- ✅ Service unavailable - 503

### 99-cleanup (5 tests)

- ✅ Delete all test environments
- ✅ Delete all test queues
- ✅ Delete all test subscriptions
- ✅ Delete all test ACLs
- ✅ Delete all test provider registrations

## SIF 3.7 Compliance Coverage

### Infrastructure Services

| Feature | Coverage | Status |
|---------|----------|--------|
| Environment registration | Full CRUD | ✅ |
| ACL management | Create, list, delete | ✅ |
| Provider registry | Register, discover | ✅ |
| Queue management | Full operations | ✅ |
| Subscription management | Full CRUD | ✅ |
| Zone/context management | Create, list | ✅ |

### Message Exchange Patterns

| Pattern | Coverage | Status |
|---------|----------|--------|
| IMMEDIATE | Success, errors | ✅ |
| DELAYED (IMMEDIATE provider) | Full flow | ✅ |
| DELAYED (DELAYED provider) | Full flow | ✅ |
| EVENT | Publish, distribute | ✅ |

### Authentication

| Feature | Coverage | Status |
|---------|----------|--------|
| SIFHMACSHA256 scheme | Full implementation | ✅ |
| Session token validation | Valid, invalid | ✅ |
| HMAC signature validation | Valid, invalid | ✅ |
| Timestamp validation | Valid, expired | ✅ |
| Header format validation | Valid, malformed | ✅ |

### Authorization

| Feature | Coverage | Status |
|---------|----------|--------|
| ACL enforcement | APPROVED, DENIED | ✅ |
| Access type validation | PROVIDE, CONSUME, SUBSCRIBE | ✅ |
| Zone access control | Valid, invalid | ✅ |
| Context access control | Valid, invalid | ✅ |

### Error Handling

| Error Type | Coverage | Status |
|------------|----------|--------|
| 400 Bad Request | Validation errors | ✅ |
| 401 Unauthorized | Auth failures | ✅ |
| 403 Forbidden | Authz failures | ✅ |
| 404 Not Found | Missing resources | ✅ |
| 409 Conflict | Duplicate keys | ✅ |
| 503 Service Unavailable | Service down | ✅ |
| SIF error format | All errors | ✅ |

## Coverage Gaps

### Planned for Future Releases

- **Performance Testing**: Load and stress tests
- **Concurrency Testing**: Parallel request handling
- **Data Validation**: More comprehensive validation scenarios
- **Edge Cases**: Additional boundary condition tests
- **Recovery Testing**: Service restart and recovery
- **Security Testing**: Penetration and vulnerability tests

### Out of Scope

- **Unit Testing**: Covered by service-specific tests
- **UI Testing**: Broker is API-only
- **Database Testing**: Internal implementation detail
- **Infrastructure Testing**: Deployment-specific

## Test Execution Statistics

### Estimated Execution Times

| Collection | Tests | Estimated Time |
|------------|-------|----------------|
| 01-health-checks | 9 | < 10 seconds |
| 02-infrastructure | 30+ | 2-3 minutes |
| 03-authentication | 6 | 30 seconds |
| 04-authorization | 5 | 30 seconds |
| 05-message-patterns | 10 | 1-2 minutes |
| 06-error-handling | 6 | 30 seconds |
| 99-cleanup | 5 | 30 seconds |
| **Total** | **70+** | **5-7 minutes** |

### Success Criteria

- ✅ All health checks pass
- ✅ All infrastructure tests pass
- ✅ All authentication tests pass
- ✅ All authorization tests pass
- ✅ All message pattern tests pass
- ✅ All error handling tests pass
- ✅ Cleanup completes successfully

## Maintenance

### Updating Coverage

When adding new tests:

1. Update this document
2. Add test to appropriate collection
3. Update test count
4. Update execution time estimates
5. Document any new coverage gaps

### Reviewing Coverage

Review coverage quarterly:

- Identify gaps in API coverage
- Add tests for new features
- Update existing tests for API changes
- Remove obsolete tests

## References

- [SIF 3.7 Specification](https://specification.sifassociation.org/)
- [README.md](README.md) - Test suite overview
- [CONTRIBUTING.md](CONTRIBUTING.md) - Adding new tests
- [CHANGELOG.md](CHANGELOG.md) - Version history
