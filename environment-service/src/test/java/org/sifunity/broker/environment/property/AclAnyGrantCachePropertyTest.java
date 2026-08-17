// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.config.CacheProperties;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.AnyGrantCheckRequest;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caching properties for the {@code acl-any-grant} cache behind the response destination gate.
 *
 * <h2>The divergence this file exists to pin</h2>
 *
 * <p>{@link AclCacheRoundTripPropertyTest} asserts that {@code acl-permissions} caches
 * <em>both</em> granted and denied results. {@code acl-any-grant} deliberately does <b>not</b>: it
 * caches positives only, via {@code unless = "#result == null || !#result.granted()"}.</p>
 *
 * <p>The reason is that {@code hasAnyGrant} fails closed — every failure path returns
 * not-granted — so a {@code false} is indistinguishable from a transient database or lookup
 * failure. Caching it would convert a momentary blip into a sticky 30-second denial for a consumer
 * that is in fact entitled, and this cache gates whether responses reach a consumer's queue at all.
 * A cache miss must mean <em>fetch</em>, never <em>deny</em>.</p>
 *
 * <p>Someone tidying these two caches into consistency would break exactly this. Hence a property
 * rather than a comment.</p>
 *
 * <p><strong>Validates: Requirement 2.9</strong></p>
 */
class AclAnyGrantCachePropertyTest {

    private AuthorizationService authorizationService;
    private EnvironmentRepository environmentRepository;
    private SolutionService solutionService;
    private DomainService domainService;
    private AclEntryRepository aclEntryRepository;
    private ContextServiceRepository contextServiceRepository;
    private CacheManager cacheManager;

    @BeforeProperty
    void setUp() {
        environmentRepository = mock(EnvironmentRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        solutionService = mock(SolutionService.class);
        domainService = mock(DomainService.class);
        aclEntryRepository = mock(AclEntryRepository.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        contextServiceRepository = mock(ContextServiceRepository.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);

        CacheProperties props = new CacheProperties();
        SimpleCacheManager simpleCacheManager = new SimpleCacheManager();
        simpleCacheManager.setCaches(List.of(
                new CaffeineCache("acl-any-grant", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getAclAnyGrant().getTtlSeconds()))
                        .maximumSize(props.getAclAnyGrant().getMaxSize())
                        .recordStats()
                        .build()),
                new CaffeineCache("acl-permissions", Caffeine.newBuilder().maximumSize(100).build()),
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
        ctx.registerBean(AuthorizationService.class,
                () -> new AuthorizationService(aclEntryRepository, environmentRepository,
                        solutionService, domainService, contextRepository, contextServiceRepository,
                        zoneRepository));
        ctx.refresh();

        authorizationService = ctx.getBean(AuthorizationService.class);
    }

    @Configuration
    @EnableCaching
    static class CachingEnablerConfig {
    }

    /** Wires a non-admin environment whose any-grant lookup yields {@code granted}. */
    private void stubNonAdminEnvironment(UUID environmentId, String serviceName, boolean granted) {
        UUID solutionUuid = UUID.randomUUID();
        UUID domainUuid = UUID.randomUUID();

        Environment environment = new Environment();
        environment.setId(environmentId);
        environment.setSessionToken("token-" + environmentId);
        environment.setApplicationName("test-app");
        environment.setType(EnvironmentType.DIRECT);
        environment.setSolutionUuid(solutionUuid);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId("regular-solution");
        solution.setDomainUuid(domainUuid);
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId("regular-domain");
        when(solutionService.findByIdCached(solutionUuid)).thenReturn(Optional.of(solution));
        when(domainService.findByIdCached(domainUuid)).thenReturn(Optional.of(domain));

        ContextService contextService = new ContextService();
        contextService.setId(UUID.randomUUID());
        contextService.setContextUuid(null);
        contextService.setServiceName(serviceName);
        when(contextServiceRepository.findByContextUuidAndServiceName(null, serviceName))
                .thenReturn(Optional.of(contextService));

        when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessLevel(
                environmentId, contextService.getId(), AccessLevel.APPROVED))
                .thenReturn(granted);
    }

    /** A positive is cached: the second call must not re-query. */
    @Property(tries = 10)
    @Label("A granted any-grant result is cached")
    void grantedResultIsCached(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("serviceNames") String serviceName) {

        cacheManager.getCache("acl-any-grant").clear();
        reset(environmentRepository, aclEntryRepository, contextServiceRepository,
                solutionService, domainService);
        stubNonAdminEnvironment(environmentId, serviceName, true);

        AnyGrantCheckRequest request =
                new AnyGrantCheckRequest(environmentId, serviceName, "DEFAULT", null);

        PermissionCheckResponse first = authorizationService.hasAnyGrant(request);
        PermissionCheckResponse second = authorizationService.hasAnyGrant(request);

        assertThat(first.granted()).isTrue();
        assertThat(second.granted()).isTrue();

        verify(aclEntryRepository, times(1))
                .existsByEnvironmentIdAndContextServiceUuidAndAccessLevel(
                        eq(environmentId), any(), eq(AccessLevel.APPROVED));
    }

    /**
     * A negative is NOT cached — the counterpart to {@code acl-permissions}, which does cache
     * denials. Every call must re-query, so a transient failure cannot become sticky.
     */
    @Property(tries = 10)
    @Label("A denied any-grant result is NOT cached (a miss means fetch, never deny)")
    void deniedResultIsNotCached(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("serviceNames") String serviceName) {

        cacheManager.getCache("acl-any-grant").clear();
        reset(environmentRepository, aclEntryRepository, contextServiceRepository,
                solutionService, domainService);
        stubNonAdminEnvironment(environmentId, serviceName, false);

        AnyGrantCheckRequest request =
                new AnyGrantCheckRequest(environmentId, serviceName, "DEFAULT", null);

        assertThat(authorizationService.hasAnyGrant(request).granted()).isFalse();
        assertThat(authorizationService.hasAnyGrant(request).granted()).isFalse();
        assertThat(authorizationService.hasAnyGrant(request).granted()).isFalse();

        verify(aclEntryRepository, times(3))
                .existsByEnvironmentIdAndContextServiceUuidAndAccessLevel(
                        eq(environmentId), any(), eq(AccessLevel.APPROVED));
    }

    /**
     * A grant appearing after a denial must be honoured immediately, not held off until the TTL
     * lapses. This is the practical consequence of not caching negatives.
     */
    @Property(tries = 10)
    @Label("A grant added after a denial takes effect without waiting for the TTL")
    void grantAfterDenialTakesEffectImmediately(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("serviceNames") String serviceName) {

        cacheManager.getCache("acl-any-grant").clear();
        reset(environmentRepository, aclEntryRepository, contextServiceRepository,
                solutionService, domainService);
        stubNonAdminEnvironment(environmentId, serviceName, false);

        AnyGrantCheckRequest request =
                new AnyGrantCheckRequest(environmentId, serviceName, "DEFAULT", null);

        assertThat(authorizationService.hasAnyGrant(request).granted()).isFalse();

        // The grant is now approved.
        when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessLevel(
                eq(environmentId), any(), eq(AccessLevel.APPROVED)))
                .thenReturn(true);

        assertThat(authorizationService.hasAnyGrant(request).granted())
                .as("A cached denial would keep this false for the whole TTL")
                .isTrue();
    }

    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30);
    }
}
