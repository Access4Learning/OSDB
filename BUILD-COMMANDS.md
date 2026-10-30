# Build Commands Reference

This document provides quick reference commands for building the SIF Unity Broker project with different module configurations.

## Full Build (All Modules)

Build all modules in the project:

```bash
mvn clean install
```

## Events Connector Focus Build

Build only the events-connector and its required dependencies, skipping job-service, requests-connector, responses-connector, and integration-tests.

### Option 1: Using Helper Scripts (Recommended)

**Windows (PowerShell):**
```powershell
.\build-events-connector.ps1
```

**Linux/Mac (Bash):**
```bash
./build-events-connector.sh
```

### Option 2: Using Maven -pl Flag Directly

```bash
mvn clean install -pl sif-broker-common,environment-service,queue-service,subscription-service,gateway,events-connector,services-connector
```

### Option 3: Using Maven Profile

```bash
mvn clean install -Pevents-connector-focus
```

**Note:** If the profile doesn't work as expected, use Option 1 or 2 instead.

## Modules Included in Events Connector Focus Build

- ✅ sif-broker-common (shared library)
- ✅ environment-service (required dependency)
- ✅ queue-service (required dependency)
- ✅ subscription-service (required dependency)
- ✅ gateway (required dependency)
- ✅ events-connector (target module)
- ✅ services-connector (required dependency)

## Modules Skipped in Events Connector Focus Build

- ❌ job-service
- ❌ requests-connector
- ❌ responses-connector
- ❌ integration-tests

## Build Individual Module Only

To build just one module without dependencies:

```bash
mvn clean install -pl events-connector
```

## Build Module with Dependencies

To build a module and all its dependencies:

```bash
mvn clean install -pl events-connector -am
```

## Skip Tests

Add `-DskipTests` to any command to skip test execution:

```bash
mvn clean install -pl events-connector -DskipTests
```

## Additional Build Options

### Skip Integration Tests Only

```bash
mvn clean install -Pskip-integration-tests
```

### Run Build Verification Tests

The `build/` package tests spawn nested Maven builds to verify artifacts, build order, and executable JARs. Excluded by default (adds 10+ minutes). Run explicitly:

```bash
mvn clean install -pl integration-tests -Pbuild-verification
```

### Run Smoke / Pattern Tests

The `smoke/` and `pattern/` tests require a full running broker stack (all services up via `startBrokerCurrentWork.ps1`). Excluded from `mvn clean install` by default. Run via Bruno tests instead.

### Build with Specific Profile

```bash
mvn clean install -Pdev          # Development profile (default)
mvn clean install -Pstaging      # Staging profile
mvn clean install -Pproduction   # Production profile
```

## Troubleshooting

If the profile-based build (`-Pevents-connector-focus`) still builds all modules:

1. Use the helper scripts (Option 1) or direct `-pl` flag (Option 2) instead
2. Verify the profile is being activated: `mvn help:active-profiles -Pevents-connector-focus`
3. Check for any parent POM overrides or settings.xml configurations
