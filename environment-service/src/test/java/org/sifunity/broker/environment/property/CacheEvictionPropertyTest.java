// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.environment.config.CacheProperties;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.PermissionCheckResponse;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EntityTagRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.CascadeCleanupClient;
import org.sifunity.broker.environment.service.ContextServiceService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentResponseMapper;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.QueueServiceClient;
import org.sifunity.broker.environment.service.SolutionService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for cache eviction on write operations.
 *
 * <p>Verifies that delete operations correctly evict cached entries,
 * ensuring subsequent reads result in cache misses.</p>
 */
class CacheEvictionPropertyTest {

    private EnvironmentService environmentService;
    private SolutionService solutionService;
    private DomainService domainService;
    private AclService aclService;
    private EnvironmentRepository environmentRepository;
    private SolutionRepository solutionRepository;
    private DomainRepository domainRepository;
    private AclEntryRepository aclEntryRepository;
    private SecretsBackend secretsBackend;
    private EntityTagRepository entityTagRepository;
    private CacheManager cacheManager;

    @BeforeProperty
    void setUp() {
        // Create mocks
        environmentRepository = mock(EnvironmentRepository.class);
        solutionRepository = mock(SolutionRepository.class);
        domainRepository = mock(DomainRepository.class);
        aclEntryRepository = mock(AclEntryRepository.class);
        secretsBackend = mock(SecretsBackend.class);
        entityTagRepository = mock(EntityTagRepository.class);
        EnvironmentResponseMapper mapper = mock(EnvironmentResponseMapper.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        ProviderRegistrationRepository providerRegistrationRepository = mock(ProviderRegistrationRepository.class);

        // Build real Caffeine caches
        CacheProperties props = new CacheProperties();
        SimpleCacheManager simpleCacheManager = new SimpleCacheManager();
        simpleCacheManager.setCaches(List.of(
                new CaffeineCache("auth-environment", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAuthEnvironment().getTtlSeconds()))
                        .maximumSize(props.getAuthEnvironment().getMaxSize())
                        .recordStats().build()),
                new CaffeineCache("auth-shared-secret", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAuthSharedSecret().getTtlSeconds()))
                        .maximumSize(props.getAuthSharedSecret().getMaxSize())
                        .recordStats().build()),
                new CaffeineCache("auth-solution", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAuthSolution().getTtlSeconds()))
                        .maximumSize(props.getAuthSolution().getMaxSize())
                        .recordStats().build()),
                new CaffeineCache("auth-domain", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAuthDomain().getTtlSeconds()))
                        .maximumSize(props.getAuthDomain().getMaxSize())
                        .recordStats().build()),
                new CaffeineCache("acl-permissions", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAclPermissions().getTtlSeconds()))
                        .maximumSize(props.getAclPermissions().getMaxSize())
                        .recordStats().build()),
                // Required, not optional: AclService's @CacheEvict names both acl-permissions and
                // acl-any-grant, and SimpleCacheManager does NOT create caches on demand — an
                // unregistered name fails the evict with IllegalArgumentException rather than being
                // silently skipped. Any test that builds its own CacheManager and exercises an
                // AclService write must register every cache the evict names.
                new CaffeineCache("acl-any-grant", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAclAnyGrant().getTtlSeconds()))
                        .maximumSize(props.getAclAnyGrant().getMaxSize())
                        .recordStats().build())
        ));
        simpleCacheManager.afterPropertiesSet();
        cacheManager = simpleCacheManager;

        // Build Spring context with caching enabled
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

        // Register services with their full constructor dependencies
        ctx.registerBean(EnvironmentService.class,
                () -> new EnvironmentService(environmentRepository, solutionRepository,
                        domainRepository, aclEntryRepository, secretsBackend, mapper, cacheManager,
                        mock(QueueServiceClient.class), zoneRepository));
        ctx.registerBean(SolutionService.class,
                () -> new SolutionService(solutionRepository, domainRepository,
                        zoneRepository, contextRepository,
                        mock(org.sifunity.broker.environment.repository.ContextServiceRepository.class),
                        entityTagRepository,
                        providerRegistrationRepository, environmentRepository,
                        mock(ContextServiceService.class), cacheManager,
                        mock(CascadeCleanupClient.class)));
        ctx.registerBean(DomainService.class,
                () -> new DomainService(domainRepository, cacheManager,
                        mock(CascadeCleanupClient.class)));
        ctx.registerBean(AclService.class,
                () -> new AclService(aclEntryRepository, environmentRepository, contextRepository,
                        mock(ContextServiceService.class),
                        mock(org.sifunity.broker.environment.repository.ContextServiceRepository.class),
                        solutionRepository, zoneRepository, domainRepository));
        ctx.refresh();

        environmentService = ctx.getBean(EnvironmentService.class);
        solutionService = ctx.getBean(SolutionService.class);
        domainService = ctx.getBean(DomainService.class);
        aclService = ctx.getBean(AclService.class);
    }

    @Configuration
    @EnableCaching
    static class CachingEnablerConfig {
    }

    // ========== Property 10 ==========

    /**
     * Property 10: Environment deletion evicts auth caches
     *
     * <p><strong>Validates: Requirements 5.1</strong></p>
     *
     * <p>For any environment that has been cached in auth-environment (by sessionToken)
     * and auth-shared-secret (by environmentId), calling deleteEnvironment(id) SHALL
     * evict both cache entries. Subsequent findBySessionToken() and getSharedSecret()
     * SHALL result in cache misses (repository invoked again).</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 10: Environment deletion evicts auth caches")
    void environmentDeletionEvictsAuthCaches(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("uuids") UUID environmentId) {

        // Clear caches
        cacheManager.getCache("auth-environment").clear();
        cacheManager.getCache("auth-shared-secret").clear();
        reset(environmentRepository, secretsBackend);

        // Build environment entity
        Environment environment = new Environment();
        environment.setId(environmentId);
        environment.setSessionToken(sessionToken);
        environment.setApplicationName("test-app");

        String sharedSecret = "secret-" + environmentId;

        // Mock repository for findBySessionToken and getSharedSecret
        when(environmentRepository.findBySessionToken(sessionToken))
                .thenReturn(Optional.of(environment));
        when(secretsBackend.retrieve(environmentId))
                .thenReturn(sharedSecret);

        // Step 1: Populate caches via service calls
        Environment cached1 = environmentService.findBySessionToken(sessionToken);
        String cachedSecret1 = environmentService.getSharedSecret(environmentId);
        assertThat(cached1).isNotNull();
        assertThat(cachedSecret1).isEqualTo(sharedSecret);

        // Verify repository called once each (cache populated)
        verify(environmentRepository, times(1)).findBySessionToken(sessionToken);
        verify(secretsBackend, times(1)).retrieve(environmentId);

        // Step 2: Call deleteEnvironment — should evict both caches
        when(environmentRepository.findById(environmentId))
                .thenReturn(Optional.of(environment));
        environmentService.deleteEnvironment(environmentId);

        // Step 3: Reset mocks to track new invocations after eviction
        reset(environmentRepository, secretsBackend);
        when(environmentRepository.findBySessionToken(sessionToken))
                .thenReturn(Optional.of(environment));
        when(secretsBackend.retrieve(environmentId))
                .thenReturn(sharedSecret);

        // Step 4: Call again — should be cache misses, hitting repository
        Environment afterDelete1 = environmentService.findBySessionToken(sessionToken);
        String afterDeleteSecret = environmentService.getSharedSecret(environmentId);

        // Repository invoked again (cache was evicted)
        verify(environmentRepository, times(1)).findBySessionToken(sessionToken);
        verify(secretsBackend, times(1)).retrieve(environmentId);
        assertThat(afterDelete1).isNotNull();
        assertThat(afterDeleteSecret).isEqualTo(sharedSecret);
    }

    // ========== Property 11 ==========

    /**
     * Property 11: ACL write evicts entire permission cache
     *
     * <p><strong>Validates: Requirements 5.2</strong></p>
     *
     * <p>For any ACL create, update, or delete operation, the entire acl-permissions
     * cache SHALL be cleared. All previously cached PermissionCheckResponse entries
     * SHALL be evicted regardless of their composite key.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 11: ACL write evicts entire permission cache")
    void aclWriteEvictsEntirePermissionCache(
            @ForAll("uuids") UUID environmentId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("accessTypes") AccessType accessType) {

        // Clear caches
        Cache aclCache = cacheManager.getCache("acl-permissions");
        Cache anyGrantCache = cacheManager.getCache("acl-any-grant");
        aclCache.clear();
        anyGrantCache.clear();
        reset(aclEntryRepository, environmentRepository);

        // Step 1: Manually populate acl-permissions cache with multiple entries
        String key1 = environmentId + ":" + serviceName + ":" + accessType;
        String key2 = UUID.randomUUID() + ":otherService:" + AccessType.QUERY;
        String key3 = UUID.randomUUID() + ":anotherService:" + AccessType.CREATE;

        aclCache.put(key1, new PermissionCheckResponse(true));
        aclCache.put(key2, new PermissionCheckResponse(false));
        aclCache.put(key3, new PermissionCheckResponse(true));

        // ...and the any-grant cache, which the same @CacheEvict must also clear. A revoked grant
        // that stayed cached here would keep admitting responses onto a consumer's queue.
        String anyKey1 = "any:" + environmentId + ":DEFAULT:null:" + serviceName;
        String anyKey2 = "any:" + UUID.randomUUID() + ":DEFAULT:null:otherService";
        anyGrantCache.put(anyKey1, new PermissionCheckResponse(true));
        anyGrantCache.put(anyKey2, new PermissionCheckResponse(true));

        // Verify entries are cached
        assertThat(aclCache.get(key1)).isNotNull();
        assertThat(aclCache.get(key2)).isNotNull();
        assertThat(aclCache.get(key3)).isNotNull();
        assertThat(anyGrantCache.get(anyKey1)).isNotNull();
        assertThat(anyGrantCache.get(anyKey2)).isNotNull();

        // Step 2: Perform ACL delete — has @CacheEvict(value = "acl-permissions", allEntries = true)
        UUID aclEntryId = UUID.randomUUID();
        AclEntry aclEntry = new AclEntry();
        aclEntry.setId(aclEntryId);
        Environment env = new Environment();
        env.setId(environmentId);
        aclEntry.setEnvironment(env);
        aclEntry.setContextServiceUuid(UUID.randomUUID());
        aclEntry.setAccessType(accessType);
        aclEntry.setAccessLevel(AccessLevel.APPROVED);

        when(aclEntryRepository.findById(aclEntryId))
                .thenReturn(Optional.of(aclEntry));

        aclService.deleteAclEntry(aclEntryId);

        // Step 3: Verify ALL cached entries are evicted — from BOTH ACL caches
        assertThat(aclCache.get(key1)).isNull();
        assertThat(aclCache.get(key2)).isNull();
        assertThat(aclCache.get(key3)).isNull();
        assertThat(anyGrantCache.get(anyKey1)).isNull();
        assertThat(anyGrantCache.get(anyKey2)).isNull();
    }

    // ========== Property 14 ==========

    /**
     * Property 14: Solution deletion evicts solution cache
     *
     * <p><strong>Validates: Requirements 5.5</strong></p>
     *
     * <p>For any solution that has been cached in auth-solution, calling
     * deleteSolution() SHALL evict the cache entry. A subsequent call to
     * findByIdCached() SHALL result in a cache miss.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 14: Solution deletion evicts solution cache")
    void solutionDeletionEvictsSolutionCache(@ForAll("uuids") UUID solutionUuid) {

        // Clear cache
        cacheManager.getCache("auth-solution").clear();
        reset(solutionRepository, entityTagRepository);

        // Build solution entity scoped to a domain
        UUID domainUuid = UUID.randomUUID();
        String domainId = "dom-" + domainUuid.toString().substring(0, 8);
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(domainId);
        domain.setDescription("test-domain");

        String solutionId = "sol-" + solutionUuid.toString().substring(0, 8);
        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId(solutionId);
        solution.setDomainUuid(domainUuid);
        solution.setDescription("test-solution");

        when(solutionRepository.findById(solutionUuid))
                .thenReturn(Optional.of(solution));

        // Step 1: Populate cache via findByIdCached
        Optional<Solution> cached = solutionService.findByIdCached(solutionUuid);
        assertThat(cached).isPresent();
        verify(solutionRepository, times(1)).findById(solutionUuid);

        // Step 2: Call deleteSolution — should evict auth-solution cache
        when(domainRepository.findByDomainId(domainId))
                .thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId))
                .thenReturn(Optional.of(solution));
        when(entityTagRepository.findBySolutionId(solutionUuid))
                .thenReturn(List.of());

        solutionService.deleteSolution(domainId, solutionId);

        // Step 3: Reset mock and call findByIdCached again — should be cache miss
        reset(solutionRepository);
        when(solutionRepository.findById(solutionUuid))
                .thenReturn(Optional.of(solution));

        Optional<Solution> afterDelete = solutionService.findByIdCached(solutionUuid);
        assertThat(afterDelete).isPresent();

        // Repository invoked again (cache was evicted)
        verify(solutionRepository, times(1)).findById(solutionUuid);
    }

    // ========== Property 15 ==========

    /**
     * Property 15: Domain deletion evicts domain cache
     *
     * <p><strong>Validates: Requirements 5.6</strong></p>
     *
     * <p>For any domain that has been cached in auth-domain, calling
     * deleteDomain() SHALL evict the cache entry. A subsequent call to
     * findByIdCached() SHALL result in a cache miss.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 15: Domain deletion evicts domain cache")
    void domainDeletionEvictsDomainCache(@ForAll("uuids") UUID domainUuid) {

        // Clear cache
        cacheManager.getCache("auth-domain").clear();
        reset(domainRepository);

        // Build domain entity
        String domainId = "dom-" + domainUuid.toString().substring(0, 8);
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(domainId);
        domain.setDescription("test-domain");

        when(domainRepository.findById(domainUuid))
                .thenReturn(Optional.of(domain));

        // Step 1: Populate cache via findByIdCached
        Optional<Domain> cached = domainService.findByIdCached(domainUuid);
        assertThat(cached).isPresent();
        verify(domainRepository, times(1)).findById(domainUuid);

        // Step 2: Call deleteDomain — should evict auth-domain cache
        when(domainRepository.findByDomainId(domainId))
                .thenReturn(Optional.of(domain));

        domainService.deleteDomain(domainId);

        // Step 3: Reset mock and call findByIdCached again — should be cache miss
        reset(domainRepository);
        when(domainRepository.findById(domainUuid))
                .thenReturn(Optional.of(domain));

        Optional<Domain> afterDelete = domainService.findByIdCached(domainUuid);
        assertThat(afterDelete).isPresent();

        // Repository invoked again (cache was evicted)
        verify(domainRepository, times(1)).findById(domainUuid);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> sessionTokens() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(8)
                .ofMaxLength(64);
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of("students", "schools", "staff", "alerts", "codeSets",
                "namespaces", "enrollments", "sections", "courses");
    }

    @Provide
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.values());
    }
}
