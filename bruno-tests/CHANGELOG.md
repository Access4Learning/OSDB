# Changelog

All notable changes to the Bruno API Test Suite will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2024-01-15

### Added

#### Core Infrastructure
- Initial release of standalone Bruno API test suite
- Project structure with bruno.json configuration
- .gitignore excluding sensitive files (.env)
- VERSION file documenting compatibility
- .env.example with credential placeholders

#### Environment Configurations
- Local development environment (local.bru)
- Docker Compose environment (docker-compose.bru)
- Kubernetes environment (kubernetes.bru)
- Support for multiple credential sets via SELECTED_ENVIRONMENT

#### Shared Utilities
- HMAC-SHA256 authentication script (common/auth.bru)
- Helper functions for UUID, timestamps, test data (common/helpers.bru)
- Cleanup utilities for test resource management (common/cleanup.bru)

#### Test Collections

**01-health-checks** - Service Health Verification
- Gateway health check
- Environment Service health check
- Queue Service health check
- Subscription Service health check
- Job Service health check
- Requests Connector health check
- Responses Connector health check
- Events Connector health check
- Services Connector health check

**02-infrastructure** - Infrastructure API Tests
- Environment management (create, list, get, update, delete)
- ACL management (create, list, delete)
- Provider registry tests (registration and discovery)
- Queue management tests (create, operations, delete)
- Subscription management tests (create, list, delete)
- Job management tests (create, state updates, delete)
- Zones and contexts tests (create, list, get)
- Error scenario tests (400, 404 responses)

**03-authentication** - Authentication Tests
- Valid authentication scenarios
- Invalid session token tests
- Expired timestamp tests
- Invalid HMAC signature tests
- Malformed authorization header tests

**04-authorization** - Authorization Tests
- APPROVED ACL entry tests
- Missing ACL entry tests (403)
- DENIED ACL entry tests
- Zone access control tests
- Context access control tests

**05-message-patterns** - Message Exchange Pattern Tests
- IMMEDIATE pattern tests (synchronous request/response)
- DELAYED pattern tests (asynchronous via queues)
- EVENT pattern tests (publish/subscribe)
- Provider unavailability tests
- Message persistence validation

**06-error-handling** - Error Scenario Tests
- 400 Bad Request tests (validation errors)
- 404 Not Found tests
- 409 Conflict tests
- 503 Service Unavailable tests
- SIF error format validation

**99-cleanup** - Cleanup Utilities
- Delete all test environments
- Delete all test queues
- Delete all test subscriptions
- Delete all test ACLs
- Delete all test provider registrations

#### Documentation
- Comprehensive README.md with quick start guide
- Detailed SETUP.md with environment-specific instructions
- TROUBLESHOOTING.md with common issues and solutions
- CONTRIBUTING.md with guidelines for adding tests
- Folder-level documentation (folder.bru) for each collection
- Request-level documentation in each .bru file

#### Security Features
- Credentials stored in .env file (excluded from Git)
- Support for multiple credential sets per environment
- Secure HMAC-SHA256 authentication implementation
- No hardcoded credentials in test files

#### Best Practices
- Test data prefixing (bruno-test) for easy identification
- Unique resource names with UUIDs
- Proper variable scoping (environment vs. test variables)
- Bruno built-in assertions for simple checks
- Test scripts for complex validations
- Comprehensive error handling

### Features

- **Standalone Distribution**: Complete test suite independent of other project modules
- **Multiple Environments**: Support for local, Docker Compose, and Kubernetes deployments
- **Comprehensive Coverage**: Tests for all externally-facing APIs
- **SIF 3.7 Compliance**: Validates SIF specification requirements
- **Well-Documented**: Documentation at project, folder, and request levels
- **Secure**: Credentials managed via .env file
- **Maintainable**: Clear naming conventions and organization
- **Extensible**: Easy to add new tests following established patterns

### Compatible Versions

- **SIF Unity Broker**: 1.0.0+
- **SIF Specification**: 3.7
- **Bruno**: 1.0.0+

### Known Limitations

- Tests assume broker is already deployed and running
- No automated broker deployment included
- Some tests require manual setup of bootstrap credentials
- Property-based testing not included (focused on example-based tests)

### Notes

This is the initial release of the standalone Bruno API test suite. It provides comprehensive coverage of all SIF Unity Broker APIs and is ready for distribution to the open source community.

## [Unreleased]

### Planned

- Additional message pattern test scenarios
- Performance benchmarking tests
- Load testing scenarios
- CI/CD integration examples
- Docker Compose file for running tests in containers
- Additional error scenario coverage
- More comprehensive cleanup utilities

---

## Version History

- **1.0.0** (2024-01-15): Initial release

## Upgrade Guide

### From Pre-Release to 1.0.0

This is the first official release. No upgrade needed.

## Breaking Changes

None in this release.

## Deprecations

None in this release.

## Security

### Security Fixes

None in this release.

### Security Best Practices

- Always use `.env` for credentials
- Never commit `.env` to version control
- Rotate credentials regularly
- Use test-specific credentials (not production)
- Review `.gitignore` to ensure sensitive files are excluded

## Contributors

Thank you to all contributors who helped create this test suite!

## Support

For issues, questions, or contributions:
- Review documentation in this repository
- Check [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for common issues
- Open an issue in the repository
- See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.
