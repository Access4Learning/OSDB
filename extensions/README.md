# SIF Broker Extensions

Pluggable extension library for the SIF Unity Broker. Extensions are discovered automatically via Spring Boot auto-configuration when the JAR is added to a connector service's classpath.

## Architecture

Extensions follow a **drop-in** model:
- Add `extensions` as a Maven dependency to any connector service
- Spring Boot auto-configuration registers the extension beans
- Feature toggles (via entity tags) control per-solution activation at runtime
- Fail-open strategy: extension failures never block message processing

## Current Extensions

### Message Sequencing (`sif.extensions.sequencing`)

Injects a monotonically increasing `x-sif-sequenceId` header into incoming messages, providing consumers with a total ordering guarantee within a domain/solution.

**How it works:**
1. A Jakarta Servlet filter intercepts incoming HTTP requests
2. Extracts `domainId` and `solutionId` from the URL path
3. Checks the feature toggle (entity tag lookup with Caffeine cache)
4. If enabled, generates the next sequence number via Redis `INCR`
5. Injects the `x-sif-sequenceId` header into the request

**Components:**
- `MessageSequenceFilter` — Servlet filter (fail-open, intercepts all requests)
- `SequenceService` — Redis-backed atomic sequence generation (`INCR` on `seq:{domainId}:{solutionId}`)
- `ToggleService` — Entity-tag-based feature toggle with local Caffeine cache
- `SequencingAutoConfiguration` — Spring Boot auto-config (conditional on `sif.extensions.sequencing.enabled=true`)
- `SequencingProperties` — Externalized configuration

**Configuration:**
```yaml
sif:
  extensions:
    sequencing:
      enabled: true                              # Global kill switch (default: true)
      entity-tags-base-url: http://localhost:8081 # Environment-service URL for toggle lookups
      toggle-cache-ttl: 30s                      # Cache TTL for toggle state (default: 30s)
```

**Feature Toggle:**
Sequencing is activated per-solution by creating an entity tag:
- Entity type: `SOLUTION`
- Tag key: `SEQUENCING_ENABLED`
- Tag value: `true`

When the tag is absent or set to any other value, sequencing is disabled for that solution. The toggle is looked up via the environment-service entity-tags API (`GET {entity-tags-base-url}/{domainId}/{solutionId}/tags?entityType=SOLUTION&tagKey=SEQUENCING_ENABLED`) and cached in Caffeine for `toggle-cache-ttl`.

**Redis Key Pattern:**
```
seq:{domainId}:{solutionId}
```
Values are 64-bit integers, atomically incremented via `INCR`.

## Adding to a Connector Service

Add the dependency to the connector's `pom.xml`:

```xml
<dependency>
    <groupId>org.sifunity.broker</groupId>
    <artifactId>extensions</artifactId>
    <version>${project.version}</version>
</dependency>
```

The auto-configuration will register the filter automatically. No code changes needed in the connector.

## Developing New Extensions

1. Create a new package under `org.sifunity.broker.extensions.<name>/`
2. Implement the extension logic (filters, services, etc.)
3. Create an auto-configuration class in `config/`
4. Register it in `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
5. Add property-based tests validating correctness properties
6. Document the extension in this README

## Testing

```bash
# Run all extension tests
./mvnw test -pl extensions

# Run specific test class
./mvnw test -pl extensions -Dtest=MessageSequenceFilterTest
```

Tests include:
- **Property-based tests** (jqwik): URL path parsing, header injection, toggle interpretation, graceful degradation
- **Unit tests**: Filter behavior, toggle service caching, sequence generation
- **Integration tests**: Auto-configuration wiring with Spring context
