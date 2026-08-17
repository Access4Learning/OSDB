// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Bug Condition Exploration Test — environment-service permission cache (Property 1).
 *
 * <p><strong>Bug Description:</strong>
 * {@code AuthorizationService.checkPermission} is annotated
 * {@code @Cacheable(value = "acl-permissions", key = "#request.environmentId() + ':' +
 * #request.serviceName() + ':' + #request.accessType()")}. The SpEL key omits {@code zoneId}
 * and {@code contextId}, so a decision cached for one zone/context is incorrectly served for a
 * differently-scoped request that shares the same environment/service/accessType. When the first
 * decision was {@code granted=true}, this is a cross-zone / cross-context authorization bypass.</p>
 *
 * <p><strong>Expected Behavior (encoded here):</strong> after driving {@code checkPermission(A)}
 * to seed the {@code acl-permissions} cache, {@code checkPermission(B)} for a different
 * zone/context SHALL reflect B's OWN scope (a cache miss that re-evaluates its ACLs), i.e.
 * {@code cacheKey'(A) <> cacheKey'(B)}.</p>
 *
 * <p><strong>CRITICAL:</strong> These tests MUST FAIL on unfixed code — the stale first-scope
 * decision is served for the second scope. Failure confirms the bug exists. They will PASS once
 * the SpEL key is widened to include {@code zoneId} and the normalized {@code contextId}.</p>
 *
 * <p>The Spring {@code @Cacheable} proxy is exercised via an {@link AnnotationConfigApplicationContext}
 * with {@link EnableCaching} active, mirroring {@code AclCacheRoundTripPropertyTest}.</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5</strong></p>
 *
 * @see org.sifunity.broker.environment.service.AuthorizationService
 */
@Tag("acl-permission-cache-key-fix")
@Tag("bug-condition-exploration")
class AclPermissionCacheKeyBugConditionExplorationTest {

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
     * Bug Condition — cross-zone cache reuse.
     *
     * <p>Zone A resolves to a context with an APPROVED ACL ({@code granted=true}); zone B has no
     * matching zone ({@code granted=false}). After seeding the cache with zone A's decision, a
     * zone B request must be re-evaluated on its own scope and return {@code granted=false}.</p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — the cached zone A
     * {@code granted=true} is served for zone B because the key omits {@code zoneId}.</p>
     *
     * <p><strong>Validates: Requirements 2.1, 2.2, 2.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Bug Condition: env-service cross-zone request must not reuse another zone's cached decision")
    void crossZoneMustNotReuseCachedDecision(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("accessTypes") AccessType accessType) {

        // Two deterministically-distinct zones — the property only needs zoneA != zoneB.
        // Generating both from the same arbitrary + Assume(zoneA != zoneB) made the low-tries
        // property flaky: an unlucky seed could draw equal zones on every try and exhaust it.
        String zoneA = "zone-alpha";
        String zoneB = "zone-beta";

        cacheManager.getCache(CACHE_NAME).clear();
        reset(environmentRepository, solutionRepository, domainRepository,
                aclEntryRepository, contextRepository, contextServiceRepository, zoneRepository);

        UUID solutionUuid = UUID.randomUUID();
        Environment environment = nonAdminEnvironment(environmentId, solutionUuid);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        // Zone A: resolves context -> context_service -> APPROVED ACL (granted=true).
        UUID zoneAUuid = UUID.randomUUID();
        UUID contextAUuid = UUID.randomUUID();
        UUID contextServiceAUuid = UUID.randomUUID();
        Zone zoneAEntity = new Zone();
        zoneAEntity.setId(zoneAUuid);
        Context contextA = new Context();
        contextA.setId(contextAUuid);
        ContextService contextServiceA = new ContextService();
        contextServiceA.setId(contextServiceAUuid);
        contextServiceA.setContextUuid(contextAUuid);
        contextServiceA.setServiceName(serviceName);
        AclEntry approved = new AclEntry();
        approved.setId(UUID.randomUUID());
        approved.setContextServiceUuid(contextServiceAUuid);
        approved.setAccessType(accessType);
        approved.setAccessLevel(AccessLevel.APPROVED);

        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneA))
                .thenReturn(Optional.of(zoneAEntity));
        when(contextRepository.findByZoneUuidAndContextId(zoneAUuid, CONTEXT_CURRENT))
                .thenReturn(Optional.of(contextA));
        when(contextServiceRepository.findByContextUuidAndServiceName(contextAUuid, serviceName))
                .thenReturn(Optional.of(contextServiceA));
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                environmentId, contextServiceAUuid, accessType))
                .thenReturn(Optional.of(approved));

        // Zone B: no matching zone -> permission denied (granted=false).
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneB))
                .thenReturn(Optional.empty());

        PermissionCheckRequest requestA = new PermissionCheckRequest(
                environmentId, serviceName, zoneA, CONTEXT_CURRENT, accessType);
        PermissionCheckRequest requestB = new PermissionCheckRequest(
                environmentId, serviceName, zoneB, CONTEXT_CURRENT, accessType);

        // Seed cache with zone A's granted decision.
        PermissionCheckResponse seeded = authorizationService.checkPermission(requestA);
        assertThat(seeded.granted()).isTrue();

        // Zone B must be evaluated on its own scope: denied.
        PermissionCheckResponse resultB = authorizationService.checkPermission(requestB);

        assertThat(resultB.granted())
                .as("checkPermission(env=%s, svc=%s, zone=%s, %s) must reflect zone B's own scope "
                                + "(granted=false), not the cached zone A decision",
                        environmentId, serviceName, zoneB, accessType)
                .isFalse();
    }

    /**
     * Bug Condition — cross-context cache reuse (null vs "CURRENT").
     *
     * <p>The {@code CURRENT} context resolves to an APPROVED ACL ({@code granted=true}); the null
     * context resolves via the wildcard path to no context_service ({@code granted=false}). After
     * seeding the cache with the CURRENT-context decision, a null-context request must be
     * re-evaluated on its own scope and return {@code granted=false}.</p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — the cached CURRENT-context
     * {@code granted=true} is served for the null-context request because the key omits
     * {@code contextId}.</p>
     *
     * <p><strong>Validates: Requirements 2.3, 2.4, 2.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Bug Condition: env-service cross-context request (null vs CURRENT) must not reuse cached decision")
    void crossContextMustNotReuseCachedDecision(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("accessTypes") AccessType accessType) {

        cacheManager.getCache(CACHE_NAME).clear();
        reset(environmentRepository, solutionRepository, domainRepository,
                aclEntryRepository, contextRepository, contextServiceRepository, zoneRepository);

        UUID solutionUuid = UUID.randomUUID();
        Environment environment = nonAdminEnvironment(environmentId, solutionUuid);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        // CURRENT context: resolves context -> context_service -> APPROVED ACL (granted=true).
        UUID zoneUuid = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();
        UUID contextServiceUuid = UUID.randomUUID();
        Zone zone = new Zone();
        zone.setId(zoneUuid);
        Context context = new Context();
        context.setId(contextUuid);
        ContextService contextService = new ContextService();
        contextService.setId(contextServiceUuid);
        contextService.setContextUuid(contextUuid);
        contextService.setServiceName(serviceName);
        AclEntry approved = new AclEntry();
        approved.setId(UUID.randomUUID());
        approved.setContextServiceUuid(contextServiceUuid);
        approved.setAccessType(accessType);
        approved.setAccessLevel(AccessLevel.APPROVED);

        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId))
                .thenReturn(Optional.of(zone));
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, CONTEXT_CURRENT))
                .thenReturn(Optional.of(context));
        when(contextServiceRepository.findByContextUuidAndServiceName(contextUuid, serviceName))
                .thenReturn(Optional.of(contextService));
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                environmentId, contextServiceUuid, accessType))
                .thenReturn(Optional.of(approved));

        // Null context: wildcard contextUuid=null resolves to no context_service -> denied.
        when(contextServiceRepository.findByContextUuidAndServiceName(null, serviceName))
                .thenReturn(Optional.empty());

        PermissionCheckRequest currentContextRequest = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, CONTEXT_CURRENT, accessType);
        PermissionCheckRequest nullContextRequest = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, null, accessType);

        // Seed cache with the CURRENT-context granted decision.
        PermissionCheckResponse seeded = authorizationService.checkPermission(currentContextRequest);
        assertThat(seeded.granted()).isTrue();

        // Null-context request must be evaluated on its own scope: denied.
        PermissionCheckResponse nullResult = authorizationService.checkPermission(nullContextRequest);

        assertThat(nullResult.granted())
                .as("checkPermission(env=%s, svc=%s, zone=%s, ctx=null, %s) must reflect the null-context "
                                + "scope (granted=false), not the cached CURRENT-context decision",
                        environmentId, serviceName, zoneId, accessType)
                .isFalse();
    }

    // ========== Helpers ==========

    private Environment nonAdminEnvironment(UUID environmentId, UUID solutionUuid) {
        Environment environment = new Environment();
        environment.setId(environmentId);
        environment.setSessionToken("token-" + environmentId);
        environment.setApplicationName("test-app");
        environment.setType(EnvironmentType.DIRECT);
        environment.setSolutionUuid(solutionUuid);
        // isAdmin() -> not a global admin, and fingerprint is not "broker-services".
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
