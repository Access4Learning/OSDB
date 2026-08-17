// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.config.CacheProperties;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.PermissionCheckRequest;
import org.sifunity.broker.environment.dto.PermissionCheckResponse;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
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
import static org.mockito.Mockito.*;

/**
 * Property-based test for ACL-permissions cache round-trip.
 *
 * <p><strong>Property 7: ACL-permissions cache round-trip</strong></p>
 *
 * <p>For any {@code PermissionCheckRequest} with a valid (environmentId, serviceName, accessType)
 * composite key, calling {@code AuthorizationService.checkPermission(request)} twice in succession
 * (within TTL) SHALL return equivalent {@code PermissionCheckResponse} objects (whether granted or
 * denied), and the underlying repository queries SHALL be invoked exactly once. This holds for both
 * granted (true) and denied (false) results.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.2, 4.3, 4.6</strong></p>
 */
class AclCacheRoundTripPropertyTest {

    private AuthorizationService authorizationService;
    private EnvironmentRepository environmentRepository;
    private SolutionRepository solutionRepository;
    private DomainRepository domainRepository;
    private SolutionService solutionService;
    private DomainService domainService;
    private AclEntryRepository aclEntryRepository;
    private ContextRepository contextRepository;
    private ContextServiceRepository contextServiceRepository;
    private ZoneRepository zoneRepository;
    private CacheManager cacheManager;

    @BeforeProperty
    void setUp() {
        // Create mocks for all AuthorizationService dependencies
        environmentRepository = mock(EnvironmentRepository.class);
        solutionRepository = mock(SolutionRepository.class);
        domainRepository = mock(DomainRepository.class);
        solutionService = mock(SolutionService.class);
        domainService = mock(DomainService.class);
        aclEntryRepository = mock(AclEntryRepository.class);
        contextRepository = mock(ContextRepository.class);
        contextServiceRepository = mock(ContextServiceRepository.class);
        zoneRepository = mock(ZoneRepository.class);

        // Build real CaffeineCacheManager with acl-permissions cache
        CacheProperties props = new CacheProperties();
        SimpleCacheManager simpleCacheManager = new SimpleCacheManager();
        simpleCacheManager.setCaches(List.of(
                new CaffeineCache("acl-permissions", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAclPermissions().getTtlSeconds()))
                        .maximumSize(props.getAclPermissions().getMaxSize())
                        .recordStats()
                        .build()),
                new CaffeineCache("auth-solution", Caffeine.newBuilder().maximumSize(100).build()),
                new CaffeineCache("auth-domain", Caffeine.newBuilder().maximumSize(100).build())
        ));
        simpleCacheManager.afterPropertiesSet();
        cacheManager = simpleCacheManager;

        // Use Spring application context to get proxied service with caching
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(CacheManager.class, () -> cacheManager);
        ctx.registerBean(EnvironmentRepository.class, () -> environmentRepository);
        ctx.registerBean(SolutionRepository.class, () -> solutionRepository);
        ctx.registerBean(DomainRepository.class, () -> domainRepository);
        ctx.registerBean(AclEntryRepository.class, () -> aclEntryRepository);
        ctx.registerBean(ContextRepository.class, () -> contextRepository);
        ctx.registerBean(ContextServiceRepository.class, () -> contextServiceRepository);
        ctx.registerBean(ZoneRepository.class, () -> zoneRepository);
        ctx.register(CachingEnablerConfig.class);
        // Register AuthorizationService with explicit constructor args so SolutionService/DomainService
        // mocks are injected directly without Spring proxying them (they have @Cacheable methods that
        // would trigger proxy creation and interfere with Mockito stubbing).
        ctx.registerBean(AuthorizationService.class,
                () -> new AuthorizationService(aclEntryRepository, environmentRepository,
                        solutionService, domainService, contextRepository, contextServiceRepository, zoneRepository));
        ctx.refresh();

        authorizationService = ctx.getBean(AuthorizationService.class);
    }

    @Configuration
    @EnableCaching
    static class CachingEnablerConfig {
    }

    // ========== Property 7: Granted result cached ==========

    /**
     * Property 7: ACL-permissions cache round-trip (granted=true)
     *
     * <p><strong>Validates: Requirements 4.1, 4.2, 4.3, 4.6</strong></p>
     *
     * <p>Generates random PermissionCheckRequests where the ACL entry exists and is APPROVED,
     * calls checkPermission() twice with the same request within TTL, and verifies repository
     * queries are invoked exactly once while both calls return granted=true.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 7: ACL-permissions cache round-trip")
    void aclPermissionsCacheRoundTripGranted(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("accessTypes") AccessType accessType) {

        // Clear cache before each trial
        cacheManager.getCache("acl-permissions").clear();
        reset(environmentRepository, solutionRepository, domainRepository, aclEntryRepository, contextServiceRepository);
        reset(solutionService, domainService);

        // Build a non-admin environment (so it goes through ACL check path)
        UUID solutionUuid = UUID.randomUUID();
        UUID domainUuid = UUID.randomUUID();

        Environment environment = new Environment();
        environment.setId(environmentId);
        environment.setSessionToken("token-" + environmentId);
        environment.setApplicationName("test-app");
        environment.setType(EnvironmentType.DIRECT);
        environment.setSolutionUuid(solutionUuid);

        // Mock environment lookup
        when(environmentRepository.findById(environmentId))
                .thenReturn(Optional.of(environment));

        // Mock isAdmin() to return false — non-admin domain/solution via cached service methods
        org.sifunity.broker.environment.domain.Solution solution =
                new org.sifunity.broker.environment.domain.Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId("regular-solution");
        solution.setDomainUuid(domainUuid);

        org.sifunity.broker.environment.domain.Domain domain =
                new org.sifunity.broker.environment.domain.Domain();
        domain.setId(domainUuid);
        domain.setDomainId("regular-domain");

        when(solutionService.findByIdCached(solutionUuid))
                .thenReturn(Optional.of(solution));
        when(domainService.findByIdCached(domainUuid))
                .thenReturn(Optional.of(domain));

        // Mock ACL entry as APPROVED (granted=true)
        AclEntry aclEntry = new AclEntry();
        aclEntry.setId(UUID.randomUUID());
        aclEntry.setContextServiceUuid(java.util.UUID.randomUUID());
        aclEntry.setAccessType(accessType);
        aclEntry.setAccessLevel(AccessLevel.APPROVED);

        // Mock contextServiceRepository to resolve (null contextUuid, serviceName) → contextService
        org.sifunity.broker.environment.domain.ContextService contextService =
                new org.sifunity.broker.environment.domain.ContextService();
        contextService.setId(aclEntry.getContextServiceUuid());
        contextService.setContextUuid(null);
        contextService.setServiceName(serviceName);
        when(contextServiceRepository.findByContextUuidAndServiceName(null, serviceName))
                .thenReturn(Optional.of(contextService));

        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(environmentId, contextService.getId(), accessType))
                .thenReturn(Optional.of(aclEntry));

        // Build request with null contextId (wildcard — avoids zone/context resolution)
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, "DEFAULT", null, accessType);

        // First call — cache miss, hits repositories
        PermissionCheckResponse first = authorizationService.checkPermission(request);

        // Second call — cache hit, should NOT hit repositories
        PermissionCheckResponse second = authorizationService.checkPermission(request);

        // Repository queries invoked exactly once (first call only)
        verify(environmentRepository, times(1)).findById(eq(environmentId));
        verify(aclEntryRepository, times(1))
                .findByEnvironmentIdAndContextServiceUuidAndAccessType(eq(environmentId), any(), eq(accessType));

        // Both calls return granted=true
        assertThat(first.granted()).isTrue();
        assertThat(second.granted()).isTrue();
        assertThat(first).isEqualTo(second);
    }

    // ========== Property 7: Denied result cached ==========

    /**
     * Property 7: ACL-permissions cache round-trip (granted=false)
     *
     * <p><strong>Validates: Requirements 4.1, 4.2, 4.3, 4.6</strong></p>
     *
     * <p>Generates random PermissionCheckRequests where no ACL entry exists (denied),
     * calls checkPermission() twice with the same request within TTL, and verifies repository
     * queries are invoked exactly once while both calls return granted=false.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 7: ACL-permissions cache round-trip")
    void aclPermissionsCacheRoundTripDenied(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("accessTypes") AccessType accessType) {

        // Clear cache before each trial
        cacheManager.getCache("acl-permissions").clear();
        reset(environmentRepository, solutionRepository, domainRepository, aclEntryRepository, contextServiceRepository);
        reset(solutionService, domainService);

        // Build a non-admin environment
        UUID solutionUuid = UUID.randomUUID();
        UUID domainUuid = UUID.randomUUID();

        Environment environment = new Environment();
        environment.setId(environmentId);
        environment.setSessionToken("token-" + environmentId);
        environment.setApplicationName("test-app");
        environment.setType(EnvironmentType.DIRECT);
        environment.setSolutionUuid(solutionUuid);

        when(environmentRepository.findById(environmentId))
                .thenReturn(Optional.of(environment));

        // Mock isAdmin() to return false via cached service methods
        org.sifunity.broker.environment.domain.Solution solution =
                new org.sifunity.broker.environment.domain.Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId("regular-solution");
        solution.setDomainUuid(domainUuid);

        org.sifunity.broker.environment.domain.Domain domain =
                new org.sifunity.broker.environment.domain.Domain();
        domain.setId(domainUuid);
        domain.setDomainId("regular-domain");

        when(solutionService.findByIdCached(solutionUuid))
                .thenReturn(Optional.of(solution));
        when(domainService.findByIdCached(domainUuid))
                .thenReturn(Optional.of(domain));

        // Mock ACL entry as NOT found (denied)
        // Mock contextServiceRepository to resolve (null contextUuid, serviceName) → contextService
        org.sifunity.broker.environment.domain.ContextService contextService =
                new org.sifunity.broker.environment.domain.ContextService();
        contextService.setId(UUID.randomUUID());
        contextService.setContextUuid(null);
        contextService.setServiceName(serviceName);
        when(contextServiceRepository.findByContextUuidAndServiceName(null, serviceName))
                .thenReturn(Optional.of(contextService));

        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(environmentId, contextService.getId(), accessType))
                .thenReturn(Optional.empty());

        // Build request with null contextId
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, "DEFAULT", null, accessType);

        // First call — cache miss, hits repositories
        PermissionCheckResponse first = authorizationService.checkPermission(request);

        // Second call — cache hit, should NOT hit repositories
        PermissionCheckResponse second = authorizationService.checkPermission(request);

        // Repository queries invoked exactly once (first call only)
        verify(environmentRepository, times(1)).findById(eq(environmentId));
        verify(aclEntryRepository, times(1))
                .findByEnvironmentIdAndContextServiceUuidAndAccessType(eq(environmentId), any(), eq(accessType));

        // Both calls return granted=false
        assertThat(first.granted()).isFalse();
        assertThat(second.granted()).isFalse();
        assertThat(first).isEqualTo(second);
    }

    // ========== Arbitraries ==========

    /**
     * Generates random UUIDs for environmentId keys.
     */
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    /**
     * Generates random service name strings (alphanumeric, 3–30 chars).
     */
    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(30);
    }

    /**
     * Generates random AccessType enum values.
     */
    @Provide
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.values());
    }
}
