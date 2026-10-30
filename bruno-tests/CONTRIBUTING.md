# Contributing Guide

Guidelines for contributing to the Bruno API Test Suite.

## Table of Contents

- [Getting Started](#getting-started)
- [Test Structure](#test-structure)
- [Naming Conventions](#naming-conventions)
- [Writing Tests](#writing-tests)
- [Documentation](#documentation)
- [Best Practices](#best-practices)
- [Submitting Changes](#submitting-changes)

## Getting Started

### Prerequisites

Before contributing:

1. ✅ Set up the test suite (see [SETUP.md](SETUP.md))
2. ✅ Run existing tests successfully
3. ✅ Understand Bruno basics
4. ✅ Understand SIF 3.7 specification
5. ✅ Review existing test examples

### Development Environment

- **Bruno**: Latest version
- **Text Editor**: VS Code, Sublime, or similar
- **Git**: For version control
- **SIF Unity Broker**: Running instance for testing

## Test Structure

### File Organization

Tests are organized into collections by functional area:

```
bruno-tests/
├── 01-health-checks/        # Service health checks
├── 02-infrastructure/       # Infrastructure APIs
├── 03-authentication/       # Auth tests
├── 04-authorization/        # Authz tests
├── 05-message-patterns/     # Message patterns
├── 06-error-handling/       # Error scenarios
└── 99-cleanup/              # Cleanup utilities
```

### File Naming

Use descriptive names with HTTP method prefix:

```
✅ Good:
- POST Create Environment - Success.bru
- GET List Environments - Success.bru
- DELETE Delete Environment - Success.bru
- POST Create Environment - Invalid Data.bru

❌ Bad:
- create-env.bru
- test1.bru
- environment.bru
```

### Sequence Numbers

Use `seq` in meta block to control execution order:

```
meta {
  name: POST Create Environment - Success
  type: http
  seq: 1
}
```

Lower numbers run first. Use increments of 1 for related tests.

## Naming Conventions

### Test Names

Format: `{METHOD} {Action} - {Scenario}`

Examples:
- `POST Create Environment - Success`
- `GET List Environments - Success`
- `POST Create Environment - Invalid Data`
- `GET Get Environment - Not Found`

### Variable Names

Use camelCase for variables:

```javascript
✅ Good:
- createdEnvironmentId
- testDataPrefix
- sessionToken

❌ Bad:
- created_environment_id
- test-data-prefix
- SessionToken
```

### Folder Names

Use kebab-case for folders:

```
✅ Good:
- health-checks
- message-patterns
- error-handling

❌ Bad:
- healthChecks
- message_patterns
- ErrorHandling
```

## Writing Tests

### Basic Test Structure

Every test should have:

1. **Meta Block**: Name, type, sequence
2. **Request Configuration**: Method, URL, body, headers
3. **Documentation**: Inline docs explaining the test
4. **Pre-Request Script**: Setup (auth, variables)
5. **Assertions**: Bruno assertions
6. **Post-Response Script**: Cleanup, variable storage
7. **Tests**: Additional test cases

### Example Test

```javascript
meta {
  name: POST Create Resource - Success
  type: http
  seq: 1
}

post {
  url: {{gatewayUrl}}/resources
  body: json
  auth: none
}

headers {
  Content-Type: application/json
}

body:json {
  {
    "name": "{{testDataPrefix}}-resource-{{$randomUUID}}",
    "type": "TEST"
  }
}

docs {
  # Create Resource - Success
  
  Creates a new resource.
  
  ## Expected Response
  
  - Status: 201 Created
  - Location header with resource URL
  - Body: Created resource with ID
  
  ## What This Tests
  
  - Resource creation with valid data
  - ID generation
  - Location header format
}

script:pre-request {
  const auth = require('../../common/auth.bru');
  auth.generateAuthHeaders(req, bru);
}

script:post-response {
  if (res.getStatus() === 201) {
    const data = res.getBody();
    bru.setVar('createdResourceId', data.id);
    console.log('Created resource:', data.id);
  }
}

assert {
  res.status: eq 201
  res.headers.location: isDefined
  res.body.id: isDefined
}

tests {
  test("Resource created successfully", function() {
    expect(res.getStatus()).to.equal(201);
  });
  
  test("Response contains resource data", function() {
    const data = res.getBody();
    expect(data).to.have.property('id');
    expect(data).to.have.property('name');
  });
}
```

### Authentication

Use the shared auth script for authenticated requests:

```javascript
script:pre-request {
  const auth = require('../../common/auth.bru');
  auth.generateAuthHeaders(req, bru);
}
```

### Variable Management

**Environment Variables** (persistent):
- Defined in `environments/*.bru`
- Used for configuration (URLs, credentials)
- Access with `{{variableName}}`

**Test Variables** (temporary):
- Set with `bru.setVar('name', value)`
- Cleared after test completes
- Used for test data (IDs, tokens)

```javascript
// Set test variable
bru.setVar('createdResourceId', data.id);

// Get test variable
const resourceId = bru.getVar('createdResourceId');

// Clear test variable
bru.setVar('createdResourceId', null);
```

### Assertions

Use Bruno built-in assertions for simple checks:

```javascript
assert {
  res.status: eq 200
  res.body.id: isDefined
  res.body.name: contains test
  res.headers.content-type: eq application/json
}
```

Use test scripts for complex assertions:

```javascript
tests {
  test("Response has correct structure", function() {
    const data = res.getBody();
    expect(data).to.have.property('id');
    expect(data.id).to.match(/^[0-9a-f-]{36}$/);
  });
}
```

### Error Handling

Test error scenarios explicitly:

```javascript
meta {
  name: POST Create Resource - Invalid Data
  type: http
  seq: 2
}

// ... request configuration with invalid data ...

assert {
  res.status: eq 400
  res.body.code: isDefined
  res.body.message: isDefined
}

tests {
  test("Returns SIF error format", function() {
    const data = res.getBody();
    expect(data).to.have.property('code');
    expect(data).to.have.property('message');
    expect(data).to.have.property('scope');
    expect(data).to.have.property('timestamp');
  });
}
```

## Documentation

### Folder Documentation

Every folder should have a `folder.bru` file with documentation:

```
meta {
  name: my-collection
}

docs {
  # My Collection

  Description of what this collection tests...
}
# Collection Name

Brief description of what this collection tests.

## Purpose

Explain why these tests exist.

## Tests Included

- List of test scenarios
- What each test validates

## Prerequisites

- What must be set up first
- Dependencies on other tests

## Important Notes

- Special considerations
- Known limitations
```

### Request Documentation

Every test should have inline documentation:

```javascript
docs {
  # Test Name
  
  Brief description of what this test does.
  
  ## Prerequisites
  
  - List any prerequisites
  - Dependencies on other tests
  
  ## Expected Response
  
  - Status code
  - Response body structure
  - Headers
  
  ## What This Tests
  
  - Specific behaviors validated
  - Edge cases covered
}
```

### Code Comments

Add comments for complex logic:

```javascript
script:pre-request {
  // Generate unique test data name
  const helpers = require('../../common/helpers.bru');
  const resourceName = helpers.getTestDataName('resource', bru);
  
  // Set in request body
  req.setBody(JSON.stringify({
    name: resourceName
  }));
}
```

## Best Practices

### Test Independence

- ✅ Each test should be runnable independently
- ✅ Don't rely on test execution order (except documented prerequisites)
- ✅ Clean up test data after each test
- ✅ Use unique names for test resources

### Test Data

- ✅ Use `testDataPrefix` for all test resources
- ✅ Include UUIDs in names for uniqueness
- ✅ Clean up after tests complete
- ✅ Don't hardcode IDs or names

### Error Messages

- ✅ Provide clear assertion messages
- ✅ Explain what failed and why
- ✅ Include expected vs. actual values
- ✅ Log helpful debugging information

### Performance

- ✅ Keep tests fast (< 5 seconds each)
- ✅ Use appropriate timeouts
- ✅ Don't add unnecessary delays
- ✅ Run tests in parallel when possible

### Security

- ✅ Never commit credentials
- ✅ Use `.env` for sensitive data
- ✅ Don't log credentials
- ✅ Use test-specific credentials

## Submitting Changes

### Before Submitting

1. ✅ Run all existing tests
2. ✅ Verify new tests pass
3. ✅ Add documentation
4. ✅ Follow naming conventions
5. ✅ Check for typos
6. ✅ Update CHANGELOG.md

### Pull Request Process

1. **Create Branch**:
   ```bash
   git checkout -b feature/add-new-tests
   ```

2. **Make Changes**:
   - Add new tests
   - Update documentation
   - Follow conventions

3. **Test Changes**:
   ```bash
   # Run all tests
   bru run --env local
   
   # Run new tests specifically
   bru run path/to/new/tests --env local
   ```

4. **Commit Changes**:
   ```bash
   git add .
   git commit -m "Add tests for XYZ feature"
   ```

5. **Push Branch**:
   ```bash
   git push origin feature/add-new-tests
   ```

6. **Create Pull Request**:
   - Describe changes clearly
   - Reference related issues
   - Include test results
   - Request review

### Pull Request Template

```markdown
## Description

Brief description of changes.

## Changes Made

- Added tests for XYZ
- Updated documentation
- Fixed issue #123

## Testing

- [ ] All existing tests pass
- [ ] New tests pass
- [ ] Documentation updated
- [ ] Follows conventions

## Screenshots

(If applicable)

## Related Issues

Closes #123
```

### Code Review

Reviewers will check:
- ✅ Tests follow conventions
- ✅ Documentation is complete
- ✅ Tests pass successfully
- ✅ Code is clean and readable
- ✅ No credentials committed

## Adding New Collections

To add a new test collection:

1. **Create Folder**:
   ```bash
   mkdir -p bruno-tests/07-new-collection
   ```

2. **Add Documentation**:
   ```bash
   touch bruno-tests/07-new-collection/folder.bru
   ```

3. **Add Tests**:
   - Follow naming conventions
   - Use sequence numbers
   - Include documentation

4. **Update README**:
   - Add collection to structure
   - Describe purpose
   - List test scenarios

5. **Test Collection**:
   - Run all tests
   - Verify assertions
   - Check documentation

## Questions?

- Review existing tests for examples
- Check [SETUP.md](SETUP.md) for configuration
- See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for common issues
- Open an issue for questions

## Thank You!

Thank you for contributing to the SIF Unity Broker test suite! Your contributions help ensure the broker works correctly for the entire community.
