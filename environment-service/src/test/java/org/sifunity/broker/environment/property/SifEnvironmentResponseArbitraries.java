// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.ApplicationInfoDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.ProductIdentityDTO;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Shared jqwik arbitraries for SIF Environment Response property tests.
 *
 * <p>Provides reusable generators for registration requests, application info DTOs,
 * ACL entry sets, and environment entities used across all property tests in this feature.</p>
 */
public final class SifEnvironmentResponseArbitraries {

    private SifEnvironmentResponseArbitraries() {}

    /**
     * Generates valid {@link EnvironmentRegistrationRequest} instances with a specific solutionId.
     * Use this when the test has created a solution in the database.
     */
    public static Arbitrary<EnvironmentRegistrationRequest> validRegistrationRequests(String solutionId) {
        Arbitrary<String> appNames = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(20)
                .map(s -> "App-" + s + "-" + UUID.randomUUID().toString().substring(0, 8));
        Arbitrary<EnvironmentType> types = Arbitraries.of(EnvironmentType.DIRECT, EnvironmentType.BROKERED);
        // Use null for defaultZoneId since test solutions don't have zones created
        Arbitrary<String> zoneIds = Arbitraries.just((String) null);
        Arbitrary<String> solutionIds = Arbitraries.just(solutionId);
        Arbitrary<ApplicationInfoDTO> appInfos = validApplicationInfoDTOs().injectNull(0.3);

        return Combinators.combine(appNames, types, zoneIds, solutionIds, appInfos)
                .as((appName, type, zoneId, solId, appInfo) -> new EnvironmentRegistrationRequest(
                        new EnvironmentRegistrationBody(appName, type,
                                zoneId == null ? null : DefaultZoneDTO.of(zoneId),
                                solId, appInfo)));
    }

    /**
     * Generates valid {@link EnvironmentRegistrationRequest} instances with optional applicationInfo.
     * @deprecated Use {@link #validRegistrationRequests(String)} with a valid solutionId instead.
     */
    public static Arbitrary<EnvironmentRegistrationRequest> validRegistrationRequests() {
        Arbitrary<String> appNames = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(20)
                .map(s -> "App-" + s + "-" + UUID.randomUUID().toString().substring(0, 8));
        Arbitrary<EnvironmentType> types = Arbitraries.of(EnvironmentType.DIRECT, EnvironmentType.BROKERED);
        // Use null for defaultZoneId since test solutions don't have zones created
        Arbitrary<String> zoneIds = Arbitraries.just((String) null);
        Arbitrary<ApplicationInfoDTO> appInfos = validApplicationInfoDTOs().injectNull(0.3);

        // solutionId is required for environment registration; tests using this arbitrary
        // must call withSolutionId() to inject a valid solution created in the test DB.
        // Default: placeholder that MUST be replaced before use with the real service.
        Arbitrary<String> solutionIds = Arbitraries.just("test-solution-placeholder");

        return Combinators.combine(appNames, types, zoneIds, solutionIds, appInfos)
                .as((appName, type, zoneId, solId, appInfo) -> new EnvironmentRegistrationRequest(
                        new EnvironmentRegistrationBody(appName, type,
                                zoneId == null ? null : DefaultZoneDTO.of(zoneId),
                                solId, appInfo)));
    }

    /**
     * Generates valid {@link ApplicationInfoDTO} instances with optional nested ProductIdentity objects.
     */
    public static Arbitrary<ApplicationInfoDTO> validApplicationInfoDTOs() {
        Arbitrary<String> appKeys = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .injectNull(0.2);
        Arbitrary<String> infraVersions = Arbitraries.of("3.7", "3.6", "3.5")
                .injectNull(0.2);
        Arbitrary<String> namespaces = Arbitraries.of(
                        "http://www.sifassociation.org/datamodel/au/3.4",
                        "http://www.sifassociation.org/datamodel/us/3.5")
                .injectNull(0.3);
        Arbitrary<String> transports = Arbitraries.of("REST", "SOAP")
                .injectNull(0.3);
        Arbitrary<ProductIdentityDTO> appProducts = validProductIdentityDTOs().injectNull(0.3);
        Arbitrary<ProductIdentityDTO> adapterProducts = validProductIdentityDTOs().injectNull(0.3);

        return Combinators.combine(appKeys, infraVersions, namespaces, transports, appProducts, adapterProducts)
                .as(ApplicationInfoDTO::new);
    }

    /**
     * Generates valid {@link ProductIdentityDTO} instances.
     */
    public static Arbitrary<ProductIdentityDTO> validProductIdentityDTOs() {
        Arbitrary<String> vendors = Arbitraries.of("VendorA", "VendorB", "VendorC")
                .injectNull(0.2);
        Arbitrary<String> products = Arbitraries.of("ProductX", "ProductY", "ProductZ")
                .injectNull(0.2);
        Arbitrary<String> versions = Arbitraries.of("1.0", "2.0", "3.1.4")
                .injectNull(0.3);
        Arbitrary<String> icons = Arbitraries.of("http://example.com/icon.png", "http://example.com/logo.svg")
                .injectNull(0.5);

        return Combinators.combine(vendors, products, versions, icons)
                .as(ProductIdentityDTO::new);
    }

    /**
     * Generates valid sets of {@link AclEntry} instances with varying zone/service/context/accessType combinations.
     * The entries are not persisted — they are plain objects suitable for mapper-level testing.
     */
    public static Arbitrary<List<AclEntry>> validAclEntrySets() {
        Arbitrary<AclEntry> singleEntry = Combinators.combine(
                Arbitraries.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                Arbitraries.of(AccessType.values()),
                Arbitraries.of(AccessLevel.values())
        ).as((contextServiceUuid, accessType, accessLevel) -> {
            AclEntry entry = new AclEntry();
            entry.setId(UUID.randomUUID());
            entry.setContextServiceUuid(contextServiceUuid);
            entry.setAccessType(accessType);
            entry.setAccessLevel(accessLevel);
            return entry;
        });

        return singleEntry.list().ofMinSize(0).ofMaxSize(15);
    }

    /**
     * Generates valid {@link Environment} entities with all SIF 3.7 fields populated.
     * These are not persisted — suitable for mapper unit testing.
     */
    public static Arbitrary<Environment> validEnvironmentEntities() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15),
                Arbitraries.of(EnvironmentType.DIRECT, EnvironmentType.BROKERED),
                Arbitraries.of(true, false) // whether to include appinfo
        ).as((appName, type, includeAppInfo) -> {
            Environment env = new Environment();
            env.setId(UUID.randomUUID());
            env.setApplicationName("App-" + appName);
            env.setSessionToken(UUID.randomUUID().toString());
            env.setSharedSecret("dGVzdFNlY3JldA==");
            env.setCreatedAt(Instant.now());
            env.setDefaultZoneId("zone-default");
            env.setType(type);
            env.setFingerprint(UUID.randomUUID().toString());
            env.setAuthenticationMethod("SIF_HMACSHA256");
            env.setConsumerName("App-" + appName);

            if (includeAppInfo) {
                env.setAppinfoApplicationKey("key-" + UUID.randomUUID().toString().substring(0, 8));
                env.setAppinfoSupportedInfraVersion("3.7");
                env.setAppinfoDataModelNamespace("http://www.sifassociation.org/datamodel/au/3.4");
                env.setAppinfoTransport("REST");
                env.setAppinfoAppVendorName("TestVendor");
                env.setAppinfoAppProductName("TestProduct");
                env.setAppinfoAppProductVersion("1.0");
            }

            return env;
        });
    }
}
