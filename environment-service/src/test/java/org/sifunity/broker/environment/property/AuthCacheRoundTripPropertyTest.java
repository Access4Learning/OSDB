// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.broker.environment.config.CacheProperties;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.dto.TokenValidationResponse;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EntityTagRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.service.AuthenticationService;
import org.sifunity.broker.environment.service.CascadeCleanupClient;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentResponseMapper;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.QueueServiceClient;
import org.sifunity.broker.environment.service.SolutionService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Property-based test for auth-environment cache round-trip.
 *
 * <p><strong>Property 2: Auth-environment cache round-trip</strong></p>
 *
 * <p>For any sessionToken string, calling {@code EnvironmentService.findBySessionToken(sessionToken)}
 * twice in succession (within TTL) SHALL return the same {@code Environment} entity, and the
 * underlying {@code EnvironmentRepository.findBySessionToken()} SHALL be invoked exactly once
 * (on the first call).</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2</strong></p>
 */
class AuthCacheRoundTripPropertyTest {

    private EnvironmentService environmentService;
    private SolutionService solutionService;
    private DomainService domainService;
    private AuthenticationService authenticationService;
    private EnvironmentRepository environmentRepository;
    private SolutionRepository solutionRepository;
    private DomainRepository domainRepository;
    private SecretsBackend secretsBackend;
    private CacheManager cacheManager;

    @BeforeProperty
    void setUp() {
        // Create mocks for all EnvironmentService dependencies
        environmentRepository = mock(EnvironmentRepository.class);
        solutionRepository = mock(SolutionRepository.class);
        domainRepository = mock(DomainRepository.class);
        AclEntryRepository aclEntryRepository = mock(AclEntryRepository.class);
        secretsBackend = mock(SecretsBackend.class);
        EnvironmentResponseMapper mapper = mock(EnvironmentResponseMapper.class);

        // Create mocks for SolutionService dependencies (beyond solutionRepository/domainRepository)
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        EntityTagRepository entityTagRepository = mock(EntityTagRepository.class);
        ProviderRegistrationRepository providerRegistrationRepository = mock(ProviderRegistrationRepository.class);

        // Build real CaffeineCacheManager with all auth caches
        CacheProperties props = new CacheProperties();
        SimpleCacheManager simpleCacheManager = new SimpleCacheManager();
        simpleCacheManager.setCaches(List.of(
                new CaffeineCache("auth-environment", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAuthEnvironment().getTtlSeconds()))
                        .maximumSize(props.getAuthEnvironment().getMaxSize())
                        .recordStats()
                        .build()),
                new CaffeineCache("auth-shared-secret", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAuthSharedSecret().getTtlSeconds()))
                        .maximumSize(props.getAuthSharedSecret().getMaxSize())
                        .recordStats()
                        .build()),
                new CaffeineCache("auth-solution", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAuthSolution().getTtlSeconds()))
                        .maximumSize(props.getAuthSolution().getMaxSize())
                        .recordStats()
                        .build()),
                new CaffeineCache("auth-domain", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAuthDomain().getTtlSeconds()))
                        .maximumSize(props.getAuthDomain().getMaxSize())
                        .recordStats()
                        .build())
        ));
        simpleCacheManager.afterPropertiesSet();
        cacheManager = simpleCacheManager;

        // Use Spring application context to get proxied services with caching
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(CacheManager.class, () -> cacheManager);
        ctx.registerBean(EnvironmentRepository.class, () -> environmentRepository);
        ctx.registerBean(SolutionRepository.class, () -> solutionRepository);
        ctx.registerBean(DomainRepository.class, () -> domainRepository);
        ctx.registerBean(AclEntryRepository.class, () -> aclEntryRepository);
        ctx.registerBean(SecretsBackend.class, () -> secretsBackend);
        ctx.registerBean(EnvironmentResponseMapper.class, () -> mapper);
        ctx.registerBean(ZoneRepository.class, () -> zoneRepository);
        ctx.registerBean(ContextRepository.class, () -> contextRepository);
        ctx.registerBean(EntityTagRepository.class, () -> entityTagRepository);
        ctx.registerBean(ProviderRegistrationRepository.class, () -> providerRegistrationRepository);
        ctx.register(CachingEnablerConfig.class);
        ctx.registerBean(EnvironmentService.class,
                () -> new EnvironmentService(environmentRepository, solutionRepository,
                        domainRepository, aclEntryRepository, secretsBackend, mapper, cacheManager,
                        mock(QueueServiceClient.class), zoneRepository));
        ctx.registerBean(SolutionService.class,
                () -> new SolutionService(solutionRepository, domainRepository,
                        zoneRepository, contextRepository, mock(org.sifunity.broker.environment.repository.ContextServiceRepository.class), entityTagRepository,
                        providerRegistrationRepository, environmentRepository, mock(org.sifunity.broker.environment.service.ContextServiceService.class), cacheManager,
                        mock(CascadeCleanupClient.class)));
        ctx.registerBean(DomainService.class,
                () -> new DomainService(domainRepository, cacheManager,
                        mock(CascadeCleanupClient.class)));
        ctx.registerBean(AuthenticationService.class);
        ctx.refresh();

        environmentService = ctx.getBean(EnvironmentService.class);
        solutionService = ctx.getBean(SolutionService.class);
        domainService = ctx.getBean(DomainService.class);
        authenticationService = ctx.getBean(AuthenticationService.class);
    }

    @Configuration
    @EnableCaching
    static class CachingEnablerConfig {
    }

    /**
     * Property 2: Auth-environment cache round-trip
     *
     * <p><strong>Validates: Requirements 2.1, 2.2</strong></p>
     *
     * <p>Generates random sessionToken strings, mocks the repository to return
     * an Environment entity, calls findBySessionToken twice within TTL, and
     * verifies the repository is invoked exactly once while both calls return
     * the same entity.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 2: Auth-environment cache round-trip")
    void authEnvironmentCacheRoundTrip(@ForAll("sessionTokens") String sessionToken) {
        // Clear cache before each trial to isolate property runs
        cacheManager.getCache("auth-environment").clear();
        reset(environmentRepository);

        // Build a minimal Environment entity for the mock to return
        Environment environment = new Environment();
        environment.setId(UUID.randomUUID());
        environment.setSessionToken(sessionToken);
        environment.setApplicationName("test-app-" + sessionToken.hashCode());

        when(environmentRepository.findBySessionToken(sessionToken))
                .thenReturn(Optional.of(environment));

        // First call — should be a cache miss, hitting the repository
        Environment first = environmentService.findBySessionToken(sessionToken);

        // Second call — should be a cache hit, NOT hitting the repository
        Environment second = environmentService.findBySessionToken(sessionToken);

        // Repository invoked exactly once (first call only)
        verify(environmentRepository, times(1)).findBySessionToken(sessionToken);

        // Both calls return the same entity
        assertThat(first).isSameAs(second);
        assertThat(first).isNotNull();
        assertThat(first.getSessionToken()).isEqualTo(sessionToken);
    }

    // ========== Arbitraries ==========

    /**
     * Generates random non-empty session token strings.
     * Session tokens are alphanumeric strings of length 8–64.
     */
    @Provide
    Arbitrary<String> sessionTokens() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(8)
                .ofMaxLength(64);
    }

    /**
     * Generates random UUIDs for environmentId keys.
     */
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    /**
     * Generates random UUIDs for solutionUuid keys.
     */
    @Provide
    Arbitrary<UUID> solutionUuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    /**
     * Generates random UUIDs for domainUuid keys.
     */
    @Provide
    Arbitrary<UUID> domainUuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    // ========== Property 3 ==========

    /**
     * Property 3: Auth-shared-secret cache round-trip
     *
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     *
     * <p>For any environmentId UUID, calling {@code EnvironmentService.getSharedSecret(environmentId)}
     * twice in succession (within TTL) SHALL return the same shared secret string, and the
     * underlying {@code SecretsBackend.retrieve()} SHALL be invoked exactly once (on the first call).</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 3: Auth-shared-secret cache round-trip")
    void authSharedSecretCacheRoundTrip(@ForAll("environmentIds") UUID environmentId) {
        // Clear cache before each trial to isolate property runs
        cacheManager.getCache("auth-shared-secret").clear();
        reset(secretsBackend);

        // Generate a deterministic secret for this environmentId
        String expectedSecret = "secret-" + environmentId;

        when(secretsBackend.retrieve(environmentId))
                .thenReturn(expectedSecret);

        // First call — cache miss, hits SecretsBackend
        String first = environmentService.getSharedSecret(environmentId);

        // Second call — cache hit, should NOT hit SecretsBackend
        String second = environmentService.getSharedSecret(environmentId);

        // SecretsBackend invoked exactly once (first call only)
        verify(secretsBackend, times(1)).retrieve(environmentId);

        // Both calls return same secret
        assertThat(first).isEqualTo(expectedSecret);
        assertThat(second).isEqualTo(expectedSecret);
        assertThat(first).isSameAs(second);
    }

    // ========== Property 4 ==========

    /**
     * Property 4: Auth-solution cache round-trip
     *
     * <p><strong>Validates: Requirements 2.5, 2.6</strong></p>
     *
     * <p>For any solutionUuid, calling {@code SolutionService.findByIdCached(solutionUuid)}
     * twice in succession (within TTL) SHALL return the same {@code Optional<Solution>}, and the
     * underlying {@code SolutionRepository.findById()} SHALL be invoked exactly once.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 4: Auth-solution cache round-trip")
    void authSolutionCacheRoundTrip(@ForAll("solutionUuids") UUID solutionUuid) {
        // Clear cache before each trial to isolate property runs
        cacheManager.getCache("auth-solution").clear();
        reset(solutionRepository);

        // Build a minimal Solution entity for the mock to return
        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId("sol-" + solutionUuid.toString().substring(0, 8));
        solution.setDomainUuid(UUID.randomUUID());
        solution.setDescription("test-solution");

        when(solutionRepository.findById(solutionUuid))
                .thenReturn(Optional.of(solution));

        // First call — cache miss, hits repository
        Optional<Solution> first = solutionService.findByIdCached(solutionUuid);

        // Second call — cache hit, should NOT hit repository
        Optional<Solution> second = solutionService.findByIdCached(solutionUuid);

        // Repository invoked exactly once (first call only)
        verify(solutionRepository, times(1)).findById(solutionUuid);

        // Both calls return same Optional with same entity
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get()).isSameAs(second.get());
        assertThat(first.get().getId()).isEqualTo(solutionUuid);
    }

    // ========== Property 5 ==========

    /**
     * Property 5: Auth-domain cache round-trip
     *
     * <p><strong>Validates: Requirements 2.7, 2.8</strong></p>
     *
     * <p>For any domainUuid, calling {@code DomainService.findByIdCached(domainUuid)}
     * twice in succession (within TTL) SHALL return the same {@code Optional<Domain>}, and the
     * underlying {@code DomainRepository.findById()} SHALL be invoked exactly once.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 5: Auth-domain cache round-trip")
    void authDomainCacheRoundTrip(@ForAll("domainUuids") UUID domainUuid) {
        // Clear cache before each trial to isolate property runs
        cacheManager.getCache("auth-domain").clear();
        reset(domainRepository);

        // Build a minimal Domain entity for the mock to return
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId("dom-" + domainUuid.toString().substring(0, 8));
        domain.setDescription("test-domain");

        when(domainRepository.findById(domainUuid))
                .thenReturn(Optional.of(domain));

        // First call — cache miss, hits repository
        Optional<Domain> first = domainService.findByIdCached(domainUuid);

        // Second call — cache hit, should NOT hit repository
        Optional<Domain> second = domainService.findByIdCached(domainUuid);

        // Repository invoked exactly once (first call only)
        verify(domainRepository, times(1)).findById(domainUuid);

        // Both calls return same Optional with same entity
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get()).isSameAs(second.get());
        assertThat(first.get().getId()).isEqualTo(domainUuid);
    }

    // ========== Property 8 ==========

    /**
     * Property 8: HMAC always executes regardless of cache state
     *
     * <p><strong>Validates: Requirements 2.9</strong></p>
     *
     * <p>For any TokenValidationRequest where entity lookups are served from cache,
     * {@code AuthenticationService.validateToken()} SHALL still compute and verify
     * the HMAC signature on every call. The HMAC computation count SHALL equal the
     * {@code validateToken()} invocation count.</p>
     *
     * <p>This is verified by:
     * <ol>
     *   <li>Pre-populating all 4 entity caches so no DB calls are needed</li>
     *   <li>Calling validateToken() N times with a correct HMAC — all must succeed</li>
     *   <li>Calling validateToken() once with an incorrect HMAC — must fail with InvalidTokenException</li>
     * </ol>
     * This proves HMAC runs every time: correct hashes pass, wrong hashes fail,
     * regardless of cache state.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 8: HMAC always executes regardless of cache state")
    void hmacAlwaysExecutesRegardlessOfCacheState(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("sharedSecrets") String sharedSecret,
            @ForAll @IntRange(min = 2, max = 5) int callCount) {

        // Clear all caches before each trial
        cacheManager.getCache("auth-environment").clear();
        cacheManager.getCache("auth-shared-secret").clear();
        cacheManager.getCache("auth-solution").clear();
        cacheManager.getCache("auth-domain").clear();
        reset(environmentRepository, secretsBackend, solutionRepository, domainRepository);

        // Build entities
        UUID envId = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        UUID domainUuid = UUID.randomUUID();

        Environment environment = new Environment();
        environment.setId(envId);
        environment.setSessionToken(sessionToken);
        environment.setApplicationName("test-app");
        environment.setType(EnvironmentType.DIRECT);
        environment.setDefaultZoneId("DEFAULT");
        environment.setSolutionUuid(solutionUuid);

        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId("sol-test");
        solution.setDomainUuid(domainUuid);

        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId("dom-test");

        // Mock repository/backend calls
        when(environmentRepository.findBySessionToken(sessionToken))
                .thenReturn(Optional.of(environment));
        when(secretsBackend.retrieve(envId))
                .thenReturn(sharedSecret);
        when(solutionRepository.findById(solutionUuid))
                .thenReturn(Optional.of(solution));
        when(domainRepository.findById(domainUuid))
                .thenReturn(Optional.of(domain));

        // Use a current timestamp so validateTimestamp() passes
        String timestamp = Instant.now().toString();

        // Compute the correct HMAC
        String correctHash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        // Call validateToken() N times with correct HMAC — all should succeed
        TokenValidationRequest validRequest = new TokenValidationRequest(sessionToken, timestamp, correctHash);
        for (int i = 0; i < callCount; i++) {
            TokenValidationResponse response = authenticationService.validateToken(validRequest);
            assertThat(response).isNotNull();
            assertThat(response.environmentId()).isEqualTo(envId);
        }

        // After first call, entities are cached. Verify repository was called only once
        // (proving caching works), but all N calls succeeded (proving HMAC ran each time)
        verify(environmentRepository, times(1)).findBySessionToken(sessionToken);
        verify(secretsBackend, times(1)).retrieve(envId);

        // Now call with an INCORRECT hash — must fail even though entities are cached
        TokenValidationRequest invalidRequest = new TokenValidationRequest(
                sessionToken, timestamp, "definitely-wrong-hash");
        assertThatThrownBy(() -> authenticationService.validateToken(invalidRequest))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("HMAC signature validation failed");

        // No additional repository calls — entities still served from cache
        verify(environmentRepository, times(1)).findBySessionToken(sessionToken);
        verify(secretsBackend, times(1)).retrieve(envId);
    }

    /**
     * Generates random shared secret strings for HMAC computation.
     * Shared secrets are alphanumeric strings of length 16–64.
     */
    @Provide
    Arbitrary<String> sharedSecrets() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(16)
                .ofMaxLength(64);
    }
}
