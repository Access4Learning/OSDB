// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.config.CacheProperties;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Preservation Property Test — environment-service permission cache (Property 2).
 *
 * <p><strong>Property 2 — Preservation: Same-Scope Sharing and Unchanged Decisions.</strong>
 * For any pair of requests where the bug condition does NOT hold (identical
 * {@code environmentId}, {@code serviceName}, {@code accessType}, {@code zoneId}, and normalized
 * {@code contextId}), the env-service {@code @Cacheable("acl-permissions")} cache SHALL share a
 * single entry (repository queried once, second call served from cache) and return the same
 * granted/denied decision. TTL/eviction, admin-bypass, self-access, and ACL resolution rules
 * (null {@code contextId} as wildcard, missing zone/context denies, APPROVED-only grants) are
 * unchanged.</p>
 *
 * <p><strong>Observation-first methodology:</strong> these behaviors were observed on the UNFIXED
 * code (same-scope requests already share one entry; admin/self-access short-circuit; ACL rules
 * resolve as documented), then encoded here. They MUST PASS on unfixed code and MUST continue to
 * pass after the SpEL key is widened.</p>
 *
 * <p>The Spring {@code @Cacheable} proxy is exercised via an
 * {@link AnnotationConfigApplicationContext} with {@link EnableCaching} active, mirroring
 * {@code AclCacheRoundTripPropertyTest}.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5</strong></p>
 *
 * @see org.sifunity.broker.environment.service.AuthorizationService
 */
@Tag("acl-permission-cache-key-fix")
@Tag("preservation")
class AclPermissionCacheKeyPreservationTest {

    private static final String CACHE_NAME = "acl-permissions";
    private static final String CONTEXT_CURRENT = "CURRENT";

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
        environmentRepository = mock(EnvironmentRepository.class);
        solutionRepository = mock(SolutionRepository.class);
        domainRepository = mock(DomainRepository.class);
        solutionService = mock(SolutionService.class);
        domainService = mock(DomainService.class);
        aclEntryRepository = mock(AclEntryRepository.class);
        contextRepository = mock(ContextRepository.class);
        contextServiceRepository = mock(ContextServiceRepository.class);
        zoneRepository = mock(ZoneRepository.class);

        CacheProperties props = new CacheProperties();
        SimpleCacheManager simpleCacheManager = new SimpleCacheManager();
        simpleCacheManager.setCaches(List.of(
                new CaffeineCache(CACHE_NAME, Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAclPermissions().getTtlSeconds()))
                        .maximumSize(props.getAclPermissions().getMaxSize())
                        .recordStats()
                        .build()),
                new CaffeineCache("auth-solution", Caffeine.newBuilder().maximumSize(100).build()),
                new CaffeineCache("auth-domain", Caffeine.newBuilder().maximumSize(100).build())
        ));
        simpleCacheManager.afterPropertiesSet();
        cacheManager = simpleCacheManager;

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

    /**
     * Preservation — same-scope requests share one cache entry and return the same decision.
     *
     * <p>Two requests identical across all five key dimensions must query the repositories exactly
     * once (second served from cache) and both must reflect the same decision. Uses the null-context
     * wildcard resolution path.</p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: PASSES.</strong></p>
     *
     * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
     */
    @Property(tries = 2)
    @Label("Preservation: env-service same-scope requests share one cache entry and same decision")
    void sameScopeSharesOneCacheEntryAndSameDecision(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("accessTypes") AccessType accessType,
            @ForAll boolean granted) {

        cacheManager.getCache(CACHE_NAME).clear();
        reset(environmentRepository, solutionRepository, domainRepository,
                aclEntryRepository, contextRepository, contextServiceRepository, zoneRepository);

        UUID solutionUuid = UUID.randomUUID();
        Environment environment = nonAdminEnvironment(environmentId, solutionUuid);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        // Null-context wildcard path: contextUuid=null resolves directly to a context_service.
        UUID contextServiceUuid = UUID.randomUUID();
        ContextService contextService = new ContextService();
        contextService.setId(contextServiceUuid);
        contextService.setContextUuid(null);
        contextService.setServiceName(serviceName);
        when(contextServiceRepository.findByContextUuidAndServiceName(null, serviceName))
                .thenReturn(Optional.of(contextService));

        if (granted) {
            AclEntry approved = new AclEntry();
            approved.setId(UUID.randomUUID());
            approved.setContextServiceUuid(contextServiceUuid);
            approved.setAccessType(accessType);
            approved.setAccessLevel(AccessLevel.APPROVED);
            when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                    environmentId, contextServiceUuid, accessType)).thenReturn(Optional.of(approved));
        } else {
            when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                    environmentId, contextServiceUuid, accessType)).thenReturn(Optional.empty());
        }

        // Two scope-identical (but distinct-object) requests, null contextId.
        PermissionCheckRequest requestA = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, null, accessType);
        PermissionCheckRequest requestB = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, null, accessType);

        PermissionCheckResponse first = authorizationService.checkPermission(requestA);
        PermissionCheckResponse second = authorizationService.checkPermission(requestB);

        // Same-scope pair shares one entry: repositories queried exactly once.
        verify(environmentRepository, times(1)).findById(eq(environmentId));
        verify(aclEntryRepository, times(1))
                .findByEnvironmentIdAndContextServiceUuidAndAccessType(eq(environmentId), any(), eq(accessType));

        assertThat(first.granted()).isEqualTo(granted);
        assertThat(second.granted()).isEqualTo(granted);
        assertThat(second).isEqualTo(first);
    }

    /**
     * Preservation — null {@code contextId} resolves via the wildcard path (contextUuid=null).
     *
     * <p><strong>Validates: Requirement 3.5</strong></p>
     */
    @Example
    @Label("Preservation: null contextId wildcard grants when an APPROVED ACL exists")
    void nullContextWildcardGrantsWhenApproved() {
        cacheManager.getCache(CACHE_NAME).clear();
        reset(environmentRepository, solutionRepository, domainRepository,
                aclEntryRepository, contextRepository, contextServiceRepository, zoneRepository);

        UUID environmentId = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        String serviceName = "StudentPersonal";
        AccessType accessType = AccessType.QUERY;

        Environment environment = nonAdminEnvironment(environmentId, solutionUuid);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        UUID contextServiceUuid = UUID.randomUUID();
        ContextService contextService = new ContextService();
        contextService.setId(contextServiceUuid);
        contextService.setContextUuid(null);
        contextService.setServiceName(serviceName);
        when(contextServiceRepository.findByContextUuidAndServiceName(null, serviceName))
                .thenReturn(Optional.of(contextService));

        AclEntry approved = new AclEntry();
        approved.setId(UUID.randomUUID());
        approved.setContextServiceUuid(contextServiceUuid);
        approved.setAccessType(accessType);
        approved.setAccessLevel(AccessLevel.APPROVED);
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                environmentId, contextServiceUuid, accessType)).thenReturn(Optional.of(approved));

        PermissionCheckResponse result = authorizationService.checkPermission(
                new PermissionCheckRequest(environmentId, serviceName, "district-1", null, accessType));

        assertThat(result.granted()).isTrue();
        // Wildcard path bypasses zone/context resolution.
        verify(zoneRepository, never()).findBySolutionUuidAndZoneId(any(), any());
    }

    /**
     * Preservation — a non-null context whose zone cannot be resolved denies permission.
     *
     * <p><strong>Validates: Requirement 3.5</strong></p>
     */
    @Example
    @Label("Preservation: missing zone denies permission")
    void missingZoneDeniesPermission() {
        cacheManager.getCache(CACHE_NAME).clear();
        reset(environmentRepository, solutionRepository, domainRepository,
                aclEntryRepository, contextRepository, contextServiceRepository, zoneRepository);

        UUID environmentId = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        String serviceName = "StudentPersonal";

        Environment environment = nonAdminEnvironment(environmentId, solutionUuid);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        // Non-null context, but the zone does not resolve -> denied.
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, "unknown-zone"))
                .thenReturn(Optional.empty());

        PermissionCheckResponse result = authorizationService.checkPermission(
                new PermissionCheckRequest(
                        environmentId, serviceName, "unknown-zone", CONTEXT_CURRENT, AccessType.QUERY));

        assertThat(result.granted()).isFalse();
    }

    /**
     * Preservation — only APPROVED ACL entries grant; a non-APPROVED level denies.
     *
     * <p><strong>Validates: Requirement 3.5</strong></p>
     */
    @Example
    @Label("Preservation: non-APPROVED ACL level denies permission")
    void nonApprovedAclLevelDenies() {
        cacheManager.getCache(CACHE_NAME).clear();
        reset(environmentRepository, solutionRepository, domainRepository,
                aclEntryRepository, contextRepository, contextServiceRepository, zoneRepository);

        UUID environmentId = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        String serviceName = "StudentPersonal";
        AccessType accessType = AccessType.QUERY;

        Environment environment = nonAdminEnvironment(environmentId, solutionUuid);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        UUID contextServiceUuid = UUID.randomUUID();
        ContextService contextService = new ContextService();
        contextService.setId(contextServiceUuid);
        contextService.setContextUuid(null);
        contextService.setServiceName(serviceName);
        when(contextServiceRepository.findByContextUuidAndServiceName(null, serviceName))
                .thenReturn(Optional.of(contextService));

        AclEntry pending = new AclEntry();
        pending.setId(UUID.randomUUID());
        pending.setContextServiceUuid(contextServiceUuid);
        pending.setAccessType(accessType);
        pending.setAccessLevel(AccessLevel.PENDING);
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                environmentId, contextServiceUuid, accessType)).thenReturn(Optional.of(pending));

        PermissionCheckResponse result = authorizationService.checkPermission(
                new PermissionCheckRequest(environmentId, serviceName, "district-1", null, accessType));

        assertThat(result.granted()).isFalse();
    }

    /**
     * Preservation — admin environments short-circuit to granted without ACL evaluation.
     *
     * <p><strong>Validates: Requirement 3.4</strong></p>
     */
    @Example
    @Label("Preservation: admin bypass grants without consulting ACLs")
    void adminBypassShortCircuits() {
        cacheManager.getCache(CACHE_NAME).clear();
        reset(environmentRepository, solutionRepository, domainRepository,
                aclEntryRepository, contextRepository, contextServiceRepository, zoneRepository);

        UUID environmentId = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();

        Environment admin = new Environment();
        admin.setId(environmentId);
        admin.setSessionToken("token-" + environmentId);
        admin.setApplicationName("admin-app");
        admin.setType(EnvironmentType.DIRECT);
        admin.setSolutionUuid(solutionUuid);
        admin.setFingerprint("broker-services"); // solution-level admin short-circuit
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(admin));

        PermissionCheckResponse result = authorizationService.checkPermission(
                new PermissionCheckRequest(
                        environmentId, "StudentPersonal", "district-1", CONTEXT_CURRENT, AccessType.QUERY));

        assertThat(result.granted()).isTrue();
        verify(aclEntryRepository, never())
                .findByEnvironmentIdAndContextServiceUuidAndAccessType(any(), any(), any());
    }

    /**
     * Preservation — self-access (serviceName equals the caller's own environment UUID)
     * short-circuits to granted without ACL evaluation.
     *
     * <p><strong>Validates: Requirement 3.4</strong></p>
     */
    @Example
    @Label("Preservation: self-access grants without consulting ACLs")
    void selfAccessShortCircuits() {
        cacheManager.getCache(CACHE_NAME).clear();
        reset(environmentRepository, solutionRepository, domainRepository,
                aclEntryRepository, contextRepository, contextServiceRepository, zoneRepository);

        UUID environmentId = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();

        Environment environment = nonAdminEnvironment(environmentId, solutionUuid);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        // serviceName equals the caller's own environment UUID -> self-access.
        PermissionCheckResponse result = authorizationService.checkPermission(
                new PermissionCheckRequest(
                        environmentId, environmentId.toString(), "district-1", CONTEXT_CURRENT, AccessType.QUERY));

        assertThat(result.granted()).isTrue();
        verify(aclEntryRepository, never())
                .findByEnvironmentIdAndContextServiceUuidAndAccessType(any(), any(), any());
    }

    /**
     * Preservation — evicting the cache (as {@code @CacheEvict(allEntries=true)} does) forces
     * re-evaluation on the next check.
     *
     * <p><strong>Validates: Requirement 3.3</strong></p>
     */
    @Example
    @Label("Preservation: clearing acl-permissions cache forces re-evaluation")
    void evictionForcesReEvaluation() {
        cacheManager.getCache(CACHE_NAME).clear();
        reset(environmentRepository, solutionRepository, domainRepository,
                aclEntryRepository, contextRepository, contextServiceRepository, zoneRepository);

        UUID environmentId = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        String serviceName = "StudentPersonal";
        AccessType accessType = AccessType.QUERY;

        Environment environment = nonAdminEnvironment(environmentId, solutionUuid);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        UUID contextServiceUuid = UUID.randomUUID();
        ContextService contextService = new ContextService();
        contextService.setId(contextServiceUuid);
        contextService.setContextUuid(null);
        contextService.setServiceName(serviceName);
        when(contextServiceRepository.findByContextUuidAndServiceName(null, serviceName))
                .thenReturn(Optional.of(contextService));

        AclEntry approved = new AclEntry();
        approved.setId(UUID.randomUUID());
        approved.setContextServiceUuid(contextServiceUuid);
        approved.setAccessType(accessType);
        approved.setAccessLevel(AccessLevel.APPROVED);
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                environmentId, contextServiceUuid, accessType)).thenReturn(Optional.of(approved));

        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, "district-1", null, accessType);

        authorizationService.checkPermission(request); // miss -> query (1)
        authorizationService.checkPermission(request); // hit -> no query
        verify(aclEntryRepository, times(1))
                .findByEnvironmentIdAndContextServiceUuidAndAccessType(eq(environmentId), any(), eq(accessType));

        // Simulate @CacheEvict(allEntries = true)
        cacheManager.getCache(CACHE_NAME).clear();

        authorizationService.checkPermission(request); // miss again -> query (2)
        verify(aclEntryRepository, times(2))
                .findByEnvironmentIdAndContextServiceUuidAndAccessType(eq(environmentId), any(), eq(accessType));
    }

    /**
     * Preservation — the {@code acl-permissions} cache TTL and max-size defaults are unchanged.
     *
     * <p><strong>Validates: Requirement 3.3</strong></p>
     */
    @Example
    @Label("Preservation: acl-permissions cache TTL (60s) and max-size (5000) unchanged")
    void aclPermissionsCacheTtlAndMaxSizeUnchanged() {
        CacheProperties props = new CacheProperties();
        assertThat(props.getAclPermissions().getTtlSeconds())
                .as("acl-permissions cache TTL must remain 60s")
                .isEqualTo(60);
        assertThat(props.getAclPermissions().getMaxSize())
                .as("acl-permissions cache max-size must remain 5000")
                .isEqualTo(5_000);
    }

    // ========== Helpers ==========

    private Environment nonAdminEnvironment(UUID environmentId, UUID solutionUuid) {
        Environment environment = new Environment();
        environment.setId(environmentId);
        environment.setSessionToken("token-" + environmentId);
        environment.setApplicationName("test-app");
        environment.setType(EnvironmentType.DIRECT);
        environment.setSolutionUuid(solutionUuid);
        UUID domainUuid = UUID.randomUUID();
        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId("regular-solution");
        solution.setDomainUuid(domainUuid);
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId("regular-domain");
        when(solutionService.findByIdCached(solutionUuid)).thenReturn(Optional.of(solution));
        when(domainService.findByIdCached(domainUuid)).thenReturn(Optional.of(domain));
        return environment;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(
                "StudentPersonal", "DataExport", "DataImport", "StudentLocator",
                "GradeSync", "AttendanceReporter", "RosterSync", "AssessmentResults");
    }

    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.of(
                "district-1", "district-2", "district-3", "school-a", "school-b", "state-zone");
    }

    @Provide
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.values());
    }
}
