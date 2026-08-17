# Integration Tests

This directory contains end-to-end integration tests for the Environment Service.

## EnvironmentLifecycleIntegrationTest

This test validates the complete lifecycle of an environment including:
- Environment registration
- Environment retrieval
- Creation of associated ACL entries and provider registrations
- Environment deletion
- Cascade deletion verification

### Running with Testcontainers (Recommended)

The test is designed to use Testcontainers with PostgreSQL for production-like testing.

**Prerequisites:**
- Docker must be installed and running
- Docker daemon must be accessible

**To run:**
```bash
mvn test -Dtest=EnvironmentLifecycleIntegrationTest
```

### Running without Docker

If Docker is not available, the test can be run using H2 in-memory database by using the `test` profile, which is configured in `application-test.yml`. However, note that H2 may not perfectly replicate PostgreSQL behavior, especially for cascade deletion constraints.

**To run with H2:**
The existing property tests already use H2 and validate cascade deletion behavior.

### Test Coverage

The integration test validates:
- **Requirement 1.1**: Environment registration
- **Requirement 2.1**: Environment retrieval  
- **Requirement 3.1**: Environment deletion
- **Requirement 3.2**: Cascade deletion of ACL entries
- **Requirement 3.3**: Cascade deletion of provider registrations

### Test Scenarios

1. **Complete lifecycle with provider environment**: Tests environment with both ACL entries and provider registrations
2. **Consumer environment lifecycle**: Tests environment with only ACL entries (no provider registrations)
3. **Empty environment lifecycle**: Tests environment with no associations
4. **Multiple environments**: Tests that deleting one environment doesn't affect others

### Notes

- The test uses `@Testcontainers` annotation to manage PostgreSQL container lifecycle
- Each test method is independent and creates its own test data
- Unique application names are generated using timestamps to avoid conflicts
- The test verifies both successful operations and proper error handling
