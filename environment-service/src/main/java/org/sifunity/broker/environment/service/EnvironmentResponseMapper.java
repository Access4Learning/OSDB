// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.config.BrokerInfrastructureConfig;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps {@link Environment} JPA entities to SIF 3.7 response DTOs.
 *
 * <p>Handles the conversion of flattened entity fields into the nested DTO
 * structure required by the SIF 3.7 specification, including applicationInfo,
 * infrastructureServices (from broker configuration), and provisionedZones
 * (grouped from ACL entries).</p>
 *
 * @see SifEnvironmentResponse
 * @see SifEnvironmentRegistrationResponse
 */
@Component
public class EnvironmentResponseMapper {

    private final BrokerInfrastructureConfig config;
    private final SolutionRepository solutionRepository;
    private final DomainRepository domainRepository;
    private final ContextRepository contextRepository;
    private final ContextServiceRepository contextServiceRepository;
    private final ZoneRepository zoneRepository;

    public EnvironmentResponseMapper(BrokerInfrastructureConfig config, SolutionRepository solutionRepository,
                                     DomainRepository domainRepository,
                                     ContextRepository contextRepository,
                                     ContextServiceRepository contextServiceRepository,
                                     ZoneRepository zoneRepository) {
        this.config = config;
        this.solutionRepository = solutionRepository;
        this.domainRepository = domainRepository;
        this.contextRepository = contextRepository;
        this.contextServiceRepository = contextServiceRepository;
        this.zoneRepository = zoneRepository;
    }

    /**
     * Maps an {@link Environment} entity to a full SIF 3.7 environment response.
     *
     * @param entity the environment entity (with ACL entries loaded)
     * @return the SIF 3.7 environment response DTO
     */
    public SifEnvironmentResponse toEnvironmentResponse(Environment entity) {
        return toEnvironmentResponse(entity, entity.getAclEntries());
    }

    /**
     * Maps an {@link Environment} entity to a full SIF 3.7 environment response
     * using externally provided ACL entries (for batch-loaded scenarios).
     *
     * @param entity     the environment entity
     * @param aclEntries the ACL entries for this environment
     * @return the SIF 3.7 environment response DTO
     */
    public SifEnvironmentResponse toEnvironmentResponse(Environment entity, List<AclEntry> aclEntries) {
        // Resolve solutionUuid back to solutionId string for the API response
        // Return null for the global admin solution (used as default when no solutionId is provided)
        String solutionId = null;
        String domainId = null;
        if (entity.getSolutionUuid() != null) {
            Solution solution = solutionRepository.findById(entity.getSolutionUuid()).orElse(null);
            if (solution != null) {
                solutionId = solution.getSolutionId();
                // Hide the internal global solution from API responses
                if (GlobalAdminConstants.GLOBAL_SOLUTION.equals(solutionId)) {
                    solutionId = null;
                } else if (solution.getDomainUuid() != null) {
                    domainId = domainRepository.findById(solution.getDomainUuid())
                            .map(Domain::getDomainId)
                            .orElse(null);
                }
            }
        }

        return new SifEnvironmentResponse(
                entity.getId(),
                entity.getType(),
                entity.getFingerprint(),
                entity.getSessionToken(),
                solutionId,
                // D3: omit defaultZone entirely when there is no default zone. The schema types
                // defaultZone.id as a string and does not declare it nullable, so emitting
                // { "defaultZone": { "id": null } } is non-conformant — and @JsonInclude(NON_NULL)
                // on DefaultZoneDTO cannot help, because the wrapper object itself is non-null.
                // A present zone must still be emitted with its id (asserted by
                // SifWireFormatConformancePreservationPropertyTest#defaultZoneWithAnIdIsStillEmitted).
                entity.getDefaultZoneId() != null ? new DefaultZoneDTO(entity.getDefaultZoneId()) : null,
                entity.getAuthenticationMethod(),
                entity.getInstanceId(),
                entity.getUserToken(),
                entity.getConsumerName(),
                buildApplicationInfo(entity),
                buildInfrastructureServices(domainId, solutionId, entity.getId()),
                buildProvisionedZones(aclEntries)
        );
    }

    /**
     * Wraps the environment response with a shared secret for registration responses.
     *
     * @param entity       the environment entity
     * @param sharedSecret the generated shared secret
     * @return the registration response DTO
     */
    public SifEnvironmentRegistrationResponse toRegistrationResponse(Environment entity, String sharedSecret) {
        return SifEnvironmentRegistrationResponse.of(
                toEnvironmentResponse(entity),
                sharedSecret
        );
    }

    /**
     * Builds an {@link ApplicationInfoDTO} from the entity's flattened appinfo fields.
     * Returns {@code null} if all appinfo fields are null.
     *
     * @param entity the environment entity
     * @return the application info DTO, or null if no appinfo data exists
     */
    ApplicationInfoDTO buildApplicationInfo(Environment entity) {
        boolean allNull = entity.getAppinfoApplicationKey() == null
                && entity.getAppinfoSupportedInfraVersion() == null
                && entity.getAppinfoDataModelNamespace() == null
                && entity.getAppinfoTransport() == null
                && entity.getAppinfoAppVendorName() == null
                && entity.getAppinfoAppProductName() == null
                && entity.getAppinfoAppProductVersion() == null
                && entity.getAppinfoAppIconUri() == null
                && entity.getAppinfoAdapterVendorName() == null
                && entity.getAppinfoAdapterProductName() == null
                && entity.getAppinfoAdapterProductVersion() == null
                && entity.getAppinfoAdapterIconUri() == null;

        if (allNull) {
            return null;
        }

        ProductIdentityDTO applicationProduct = buildProductIdentity(
                entity.getAppinfoAppVendorName(),
                entity.getAppinfoAppProductName(),
                entity.getAppinfoAppProductVersion(),
                entity.getAppinfoAppIconUri()
        );

        ProductIdentityDTO adapterProduct = buildProductIdentity(
                entity.getAppinfoAdapterVendorName(),
                entity.getAppinfoAdapterProductName(),
                entity.getAppinfoAdapterProductVersion(),
                entity.getAppinfoAdapterIconUri()
        );

        return new ApplicationInfoDTO(
                entity.getAppinfoApplicationKey(),
                entity.getAppinfoSupportedInfraVersion(),
                entity.getAppinfoDataModelNamespace(),
                entity.getAppinfoTransport(),
                applicationProduct,
                adapterProduct
        );
    }

    /**
     * Builds an {@link InfrastructureServicesDTO} with gateway-routed URLs.
     *
     * <p>URLs are constructed using the gateway base URL with the domain/solution prefix
     * so that clients route all traffic through the API gateway.</p>
     *
     * @param domainId      the domain ID for URL construction (may be null for global admin)
     * @param solutionId    the solution ID for URL construction (may be null for global admin)
     * @param environmentId the environment UUID for the environment-specific URL
     * @return the infrastructure services DTO
     */
    InfrastructureServicesDTO buildInfrastructureServices(String domainId, String solutionId, UUID environmentId) {
        String base = config.gatewayBaseUrl();
        // Strip trailing slash if present
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        List<InfrastructureServiceDTO> services;
        if (domainId != null && solutionId != null) {
            String prefix = base + "/" + domainId + "/" + solutionId;
            services = List.of(
                    new InfrastructureServiceDTO("environment", prefix + "/environments/" + environmentId),
                    new InfrastructureServiceDTO("provisionRequests", prefix + "/environments/" + environmentId),
                    new InfrastructureServiceDTO("requestsConnector", prefix + "/requests"),
                    new InfrastructureServiceDTO("eventsConnector", prefix + "/events"),
                    new InfrastructureServiceDTO("queues", prefix + "/queues"),
                    new InfrastructureServiceDTO("subscriptions", prefix + "/subscriptions"),
                    new InfrastructureServiceDTO("servicesConnector", prefix + "/services"),
                    new InfrastructureServiceDTO("responsesConnector", prefix + "/responses")
            );
        } else {
            // Fallback for global admin or unresolvable domain/solution
            services = List.of(
                    new InfrastructureServiceDTO("environment", config.environment()),
                    new InfrastructureServiceDTO("provisionRequests", config.provisionRequests()),
                    new InfrastructureServiceDTO("requestsConnector", config.requestsConnector()),
                    new InfrastructureServiceDTO("eventsConnector", config.eventsConnector()),
                    new InfrastructureServiceDTO("queues", config.queues()),
                    new InfrastructureServiceDTO("subscriptions", config.subscriptions()),
                    new InfrastructureServiceDTO("servicesConnector", config.servicesConnector()),
                    new InfrastructureServiceDTO("responsesConnector", config.responsesConnector())
            );
        }
        return new InfrastructureServicesDTO(services);
    }

    /**
     * Groups ACL entries by zone (resolved from contextUuid), then by (serviceName, contextId),
     * mapping rights. Sets type="OBJECT" for all services. Returns empty list if no ACL entries.
     *
     * <p>For ACL entries with a non-null contextUuid, the zone and context string IDs are resolved
     * via the contexts and zones tables. For wildcard ACL entries (contextUuid is null), the zone
     * and context are represented as null.</p>
     *
     * @param aclEntries the ACL entries to group
     * @return the provisioned zones DTO
     */
    ProvisionedZonesDTO buildProvisionedZones(List<AclEntry> aclEntries) {
        if (aclEntries == null || aclEntries.isEmpty()) {
            return new ProvisionedZonesDTO(List.of());
        }

        // Batch-load all referenced context_services
        Set<UUID> contextServiceUuids = aclEntries.stream()
                .map(AclEntry::getContextServiceUuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, ContextService> contextServiceMap = contextServiceUuids.isEmpty()
                ? Map.of()
                : contextServiceRepository.findAllById(contextServiceUuids).stream()
                        .collect(Collectors.toMap(ContextService::getId, Function.identity()));

        // Batch-load all referenced contexts (from context_services)
        Set<UUID> contextUuids = contextServiceMap.values().stream()
                .map(ContextService::getContextUuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Context> contextMap = contextUuids.isEmpty()
                ? Map.of()
                : contextRepository.findAllById(contextUuids).stream()
                        .collect(Collectors.toMap(Context::getId, Function.identity()));

        // Batch-load all referenced zones
        Set<UUID> zoneUuids = contextMap.values().stream()
                .map(Context::getZoneUuid)
                .collect(Collectors.toSet());

        Map<UUID, Zone> zoneMap = zoneUuids.isEmpty()
                ? Map.of()
                : zoneRepository.findAllById(zoneUuids).stream()
                        .collect(Collectors.toMap(Zone::getId, Function.identity()));

        // Resolve each ACL entry to (zoneId, contextId, serviceName) strings
        record ResolvedAcl(String zoneId, String contextId, String serviceName, AclEntry entry) {}

        List<ResolvedAcl> resolved = aclEntries.stream().map(acl -> {
            ContextService cs = contextServiceMap.get(acl.getContextServiceUuid());
            if (cs == null) {
                return new ResolvedAcl(null, null, null, acl);
            }
            String serviceName = cs.getServiceName();
            Context ctx = contextMap.get(cs.getContextUuid());
            if (ctx == null) {
                return new ResolvedAcl(null, null, serviceName, acl);
            }
            Zone zone = zoneMap.get(ctx.getZoneUuid());
            String zoneId = zone != null ? zone.getZoneId() : null;
            return new ResolvedAcl(zoneId, ctx.getContextId(), serviceName, acl);
        }).toList();

        // Group by zoneId, preserving insertion order
        Map<String, List<ResolvedAcl>> byZone = resolved.stream()
                .collect(Collectors.groupingBy(
                        r -> r.zoneId() != null ? r.zoneId() : "",
                        LinkedHashMap::new, Collectors.toList()));

        List<ProvisionedZoneDTO> zones = new ArrayList<>();
        for (Map.Entry<String, List<ResolvedAcl>> zoneEntry : byZone.entrySet()) {
            String zoneId = zoneEntry.getKey();
            List<ResolvedAcl> zoneAcls = zoneEntry.getValue();

            // Group by (serviceName, contextId) within the zone
            Map<ServiceKey, List<ResolvedAcl>> byService = zoneAcls.stream()
                    .collect(Collectors.groupingBy(
                            r -> new ServiceKey(r.serviceName(), r.contextId()),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            List<ProvisionedServiceDTO> services = new ArrayList<>();
            for (Map.Entry<ServiceKey, List<ResolvedAcl>> serviceEntry : byService.entrySet()) {
                ServiceKey key = serviceEntry.getKey();
                List<RightDTO> rights = serviceEntry.getValue().stream()
                        .map(r -> new RightDTO(r.entry().getAccessType().name(), r.entry().getAccessLevel().name()))
                        .toList();

                services.add(new ProvisionedServiceDTO(
                        "OBJECT",
                        key.serviceName(),
                        key.contextId(),
                        new ProvisionedServiceRightsDTO(rights)
                ));
            }

            zones.add(new ProvisionedZoneDTO(
                    zoneId.isEmpty() ? null : zoneId,
                    new ProvisionedZoneServicesDTO(services)));
        }

        return new ProvisionedZonesDTO(zones);
    }

    /**
     * Builds a {@link ProductIdentityDTO} from individual fields.
     * Returns null if all fields are null.
     */
    private ProductIdentityDTO buildProductIdentity(String vendorName, String productName,
                                                     String productVersion, String iconUri) {
        if (vendorName == null && productName == null && productVersion == null && iconUri == null) {
            return null;
        }
        return new ProductIdentityDTO(vendorName, productName, productVersion, iconUri);
    }

    /**
     * Composite key for grouping ACL entries by (serviceName, contextId).
     */
    private record ServiceKey(String serviceName, String contextId) {}
}
