// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.service;

import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.common.exception.SubscriptionNotFoundException;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.domain.AclEntryRef;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.sifunity.broker.subscriptionservice.exception.AccessTypeMismatchException;
import org.sifunity.broker.subscriptionservice.exception.AclEntryNotFoundException;
import org.sifunity.broker.subscriptionservice.exception.AclEntryNotHeldException;
import org.sifunity.broker.subscriptionservice.exception.DuplicateSubscriptionException;
import org.sifunity.broker.subscriptionservice.repository.AclEntryLookupRepository;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for managing subscriptions in the SIF Unity Broker.
 * 
 * <p>This service provides business logic for subscription operations including:</p>
 * <ul>
 *   <li>Creating subscriptions with queue validation and duplicate checking</li>
 *   <li>Querying EVENT subscriptions with exact context matching</li>
 *   <li>Querying DELAYED_REQUEST subscriptions with exact matching</li>
 *   <li>Retrieving subscriptions by ID or queue</li>
 *   <li>Deleting subscriptions</li>
 * </ul>
 * 
 * <p>The service uses caching to optimize read-heavy workloads and invalidates
 * caches on write operations.</p>
 * 
 * @see SubscriptionRepository
 * @see QueueServiceClient
 */
@Service
public class SubscriptionService {
    
    private final SubscriptionRepository repository;
    private final QueueServiceClient queueServiceClient;
    private final AclEntryLookupRepository aclEntryLookupRepository;
    
    /**
     * Constructs a SubscriptionService with required dependencies.
     *
     * @param repository the subscription repository
     * @param queueServiceClient the queue service client for validation
     * @param aclEntryLookupRepository the ACL entry lookup repository for resolving ACL entries
     */
    public SubscriptionService(SubscriptionRepository repository,
                               QueueServiceClient queueServiceClient,
                               AclEntryLookupRepository aclEntryLookupRepository) {
        this.repository = repository;
        this.queueServiceClient = queueServiceClient;
        this.aclEntryLookupRepository = aclEntryLookupRepository;
    }
    
    /**
     * Creates a new subscription after resolving the ACL entry, validating access type,
     * checking the queue exists, and checking for duplicates.
     * 
     * <p>Validation steps:</p>
     * <ol>
     *   <li>Resolve the ACL entry (by direct ID or by serviceName/contextUuid lookup)</li>
     *   <li>Validate the ACL entry's access type matches the subscription type</li>
     *   <li>Verify the queue exists via Queue Service</li>
     *   <li>Check for duplicate subscription using aclEntryId/queueId/type</li>
     *   <li>Create and persist the subscription</li>
     * </ol>
     * 
     * @param domainId the domain identifier from the request path (tenant scope)
     * @param solutionId the solution identifier from the request path (tenant scope)
     * @param dto the subscription data transfer object
     * @return the created subscription with generated ID and timestamp
     * @throws AclEntryNotFoundException if the ACL entry cannot be resolved
     * @throws AccessTypeMismatchException if the ACL entry's access type doesn't match the subscription type
     * @throws QueueNotFoundException if the queue does not exist
     * @throws DuplicateSubscriptionException if a subscription already exists with the same parameters
     */
    @Transactional
    @CacheEvict(value = "subscriptions", allEntries = true, beforeInvocation = false)
    public SubscriptionDTO createSubscription(String domainId, String solutionId, SubscriptionDTO dto) {
        // Resolve ACL entry (scoped to the domain+solution tenant)
        AclEntryRef aclEntry = resolveAclEntry(domainId, solutionId, dto);
        
        // Validate access type
        AccessType expectedAccessType = getExpectedAccessType(dto.messageType());
        if (aclEntry.getAccessType() != expectedAccessType) {
            throw new AccessTypeMismatchException(
                "Cannot create " + dto.messageType() + " subscription: ACL entry has access_type=" 
                + aclEntry.getAccessType() + ", expected=" + expectedAccessType);
        }
        
        // Validate queue exists
        if (!queueServiceClient.queueExists(dto.queueId())) {
            throw new QueueNotFoundException("Queue not found: " + dto.queueId());
        }
        
        // Check for duplicate using aclEntryId/queueId/type
        if (repository.existsByAclEntryIdAndQueueIdAndType(
                aclEntry.getId(), dto.queueId(), dto.messageType())) {
            throw new DuplicateSubscriptionException(
                "Subscription already exists for this ACL entry, queue, and type");
        }
        
        // Create subscription with aclEntryId
        Subscription subscription = new Subscription();
        subscription.setAclEntryId(aclEntry.getId());
        subscription.setQueueId(dto.queueId());
        subscription.setType(dto.messageType());
        subscription = repository.save(subscription);
        repository.flush();
        
        // Build response DTO with serviceName/contextUuid from ACL entry
        return toDTO(subscription, aclEntry);
    }
    
    /**
     * Resolves the ACL entry for a subscription creation request.
     * 
     * <p>If the DTO contains an {@code aclEntryId}, looks up the ACL entry by ID directly.
     * Otherwise, determines the expected access type from the subscription type and looks up
     * the ACL entry by {@code serviceName}, {@code zoneId}/{@code contextId}, and {@code accessType}
     * by joining through the contexts and zones tables — scoped to the {@code domainId}/{@code solutionId}
     * tenant so that zoneId/contextId (which are unique only within a solution/zone) cannot collide
     * across tenants.</p>
     *
     * @param domainId the domain identifier from the request path (tenant scope)
     * @param solutionId the solution identifier from the request path (tenant scope)
     * @param dto the subscription DTO containing either aclEntryId or serviceName/zoneId/contextId
     * @return the resolved ACL entry
     * @throws AclEntryNotFoundException if no matching ACL entry is found
     */
    private AclEntryRef resolveAclEntry(String domainId, String solutionId, SubscriptionDTO dto) {
        if (dto.aclEntryId() != null) {
            return aclEntryLookupRepository.findById(dto.aclEntryId())
                .orElseThrow(() -> new AclEntryNotFoundException(
                    "ACL entry not found: " + dto.aclEntryId()));
        }

        AccessType expectedAccessType = getExpectedAccessType(dto.messageType());
        // D7 (2.16): the caller holds no matching grant — an authorization outcome (403), not a
        // missing resource. POST /subscriptions/subscription does not declare 404.
        return aclEntryLookupRepository.findByServiceNameAndZoneContextAndAccessType(
                dto.serviceName(), domainId, solutionId, dto.zoneId(), dto.contextId(), expectedAccessType)
            .orElseThrow(() -> new AclEntryNotHeldException(
                "No matching ACL entry found for service=" + dto.serviceName()
                + ", domainId=" + domainId
                + ", solutionId=" + solutionId
                + ", zoneId=" + dto.zoneId()
                + ", contextId=" + dto.contextId()
                + ", accessType=" + expectedAccessType));
    }
    
    /**
     * Returns the expected ACL access type for a given subscription type.
     * 
     * @param subscriptionType the subscription type
     * @return SUBSCRIBE for EVENT, PROVIDE for DELAYED_REQUEST
     */
    private AccessType getExpectedAccessType(SubscriptionType subscriptionType) {
        return switch (subscriptionType) {
            case EVENT -> AccessType.SUBSCRIBE;
            case DELAYED_REQUEST -> AccessType.PROVIDE;
        };
    }
    
    /**
     * Finds all EVENT subscriptions matching the given service name and context UUID.
     * 
     * <p>Returns subscriptions where the context_service matches the serviceName
     * and contextUuid exactly.</p>
     * 
     * @param serviceName the service name to match
     * @param contextUuid the context UUID to match
     * @return list of matching EVENT subscriptions (empty if none found)
     */
    @Cacheable(value = "subscriptions", key = "'events:' + #serviceName + ':' + #contextUuid")
    public List<SubscriptionDTO> findEventSubscriptions(
            String serviceName, UUID contextUuid) {
        if (contextUuid == null) {
            throw new IllegalArgumentException("contextUuid must not be null — context is never optional");
        }

        List<Object[]> results = repository.findEventSubscriptions(serviceName, contextUuid);

        return results.stream()
            .map(row -> toDTO((Subscription) row[0], (AclEntryRef) row[1]))
            .collect(Collectors.toList());
    }

    /**
     * Finds all EVENT subscriptions matching the given service name, regardless of context.
     *
     * <p>Used by the alert publisher (and similar broadcast scenarios) where events must
     * reach all subscribers for a service, not just those in a specific context.</p>
     *
     * @param serviceName the service name to match
     * @return list of matching EVENT subscriptions across all contexts (empty if none found)
     */
    @Cacheable(value = "subscriptions", key = "'events:' + #serviceName + ':ALL'")
    public List<SubscriptionDTO> findEventSubscriptionsByServiceName(String serviceName) {
        List<Object[]> results = repository.findEventSubscriptionsByServiceName(serviceName);

        return results.stream()
            .map(row -> toDTO((Subscription) row[0], (AclEntryRef) row[1]))
            .collect(Collectors.toList());
    }
    
    /**
     * Finds the DELAYED_REQUEST subscription matching the given service name and context UUID.
     * 
     * <p>DELAYED_REQUEST subscriptions require exact matching on both serviceName and contextUuid.</p>
     * 
     * @param serviceName the service name to match
     * @param contextUuid the context UUID to match
     * @return the matching DELAYED_REQUEST subscription
     * @throws SubscriptionNotFoundException if no matching subscription exists
     */
    @Cacheable(value = "subscriptions", key = "'delayed:' + #serviceName + ':' + #contextUuid")
    public SubscriptionDTO findDelayedRequestSubscription(
            String serviceName, UUID contextUuid) {
        List<Object[]> results = repository.findDelayedRequestSubscription(serviceName, contextUuid);
        
        Object[] row = results.stream()
            .findFirst()
            .orElseThrow(() -> new SubscriptionNotFoundException(
                "No DELAYED_REQUEST subscription found for " + serviceName + 
                " with contextUuid " + contextUuid));
        
        return toDTO((Subscription) row[0], (AclEntryRef) row[1]);
    }
    
    /**
     * Retrieves a subscription by its unique identifier, joining with the ACL entry
     * to populate serviceName and contextUuid in the response DTO.
     * 
     * @param id the subscription ID
     * @return the subscription details with ACL-derived fields populated
     * @throws SubscriptionNotFoundException if the subscription does not exist
     */
    public SubscriptionDTO getSubscription(UUID id) {
        List<Object[]> results = repository.findByIdWithAcl(id);
        if (results.isEmpty()) {
            throw new SubscriptionNotFoundException("Subscription not found: " + id);
        }
        Object[] row = results.get(0);
        return toDTO((Subscription) row[0], (AclEntryRef) row[1]);
    }
    
    /**
     * Finds all subscriptions associated with a specific queue, joining with ACL entries
     * to populate serviceName and contextUuid in the response DTOs.
     * 
     * @param queueId the queue ID
     * @return list of subscriptions for the queue with ACL-derived fields populated (empty if none exist)
     */
    public List<SubscriptionDTO> findSubscriptionsByQueue(UUID queueId) {
        List<Object[]> results = repository.findByQueueIdWithAcl(queueId);
        return results.stream()
            .map(row -> toDTO((Subscription) row[0], (AclEntryRef) row[1]))
            .collect(Collectors.toList());
    }

    /**
     * Finds all subscriptions accessible to a specific environment by joining through
     * the acl_entries table on environment_id.
     *
     * @param environmentId the environment ID from the X-Environment-Id header
     * @return list of subscriptions for the environment with ACL-derived fields populated (empty if none exist)
     */
    public List<SubscriptionDTO> findSubscriptionsByEnvironment(UUID environmentId) {
        List<Object[]> results = repository.findByEnvironmentIdWithAcl(environmentId);
        return results.stream()
            .map(row -> toDTO((Subscription) row[0], (AclEntryRef) row[1]))
            .collect(Collectors.toList());
    }
    
    /**
     * Deletes a subscription by its unique identifier.
     * 
     * @param id the subscription ID
     * @throws SubscriptionNotFoundException if the subscription does not exist
     */
    @Transactional
    @CacheEvict(value = "subscriptions", allEntries = true)
    public void deleteSubscription(UUID id) {
        if (!repository.existsById(id)) {
            throw new SubscriptionNotFoundException("Subscription not found: " + id);
        }
        repository.deleteById(id);
    }
    
    /**
     * Converts a Subscription entity and its associated AclEntryRef to a SubscriptionDTO.
     * Populates serviceName, zoneId, and contextId from the ACL entry's context/zone chain.
     * 
     * @param entity the subscription entity
     * @param aclEntry the associated ACL entry
     * @return the subscription DTO with ACL-derived fields populated
     */
    private SubscriptionDTO toDTO(Subscription entity, AclEntryRef aclEntry) {
        return new SubscriptionDTO(
            entity.getId(),
            entity.getQueueId(),
            aclEntry.getId(),
            aclEntry.getServiceName(),
            aclEntry.resolveZoneId(),
            aclEntry.resolveContextId(),
            entity.getType(),
            entity.getCreatedAt()  // populated internally; suppressed from JSON by @JsonIgnore on accessor
        );
    }
    
    /**
     * Converts a SubscriptionDTO to a Subscription entity.
     * 
     * @param dto the subscription DTO
     * @param aclEntryId the resolved ACL entry ID
     * @return the subscription entity
     */
    private Subscription toEntity(SubscriptionDTO dto, UUID aclEntryId) {
        Subscription subscription = new Subscription();
        subscription.setAclEntryId(aclEntryId);
        subscription.setQueueId(dto.queueId());
        subscription.setType(dto.messageType());
        return subscription;
    }
}
