// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.EntityTag;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.TagDefinition;
import org.sifunity.broker.environment.dto.EntityTagCreateRequest;
import org.sifunity.broker.environment.dto.EntityTagDTO;
import org.sifunity.broker.environment.exception.DuplicateEntityTagException;
import org.sifunity.broker.environment.exception.EntityTagNotFoundException;
import org.sifunity.broker.environment.exception.EntityTypeNotApplicableException;
import org.sifunity.broker.environment.exception.InvalidTagValueException;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.exception.TagDefinitionNotFoundForKeyException;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EntityTagRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EntityTagService {

    private static final Logger log = LoggerFactory.getLogger(EntityTagService.class);

    private final EntityTagRepository entityTagRepository;
    private final SolutionRepository solutionRepository;
    private final TagDefinitionService tagDefinitionService;
    private final DomainRepository domainRepository;

    public EntityTagService(EntityTagRepository entityTagRepository,
                            SolutionRepository solutionRepository,
                            TagDefinitionService tagDefinitionService,
                            DomainRepository domainRepository) {
        this.entityTagRepository = entityTagRepository;
        this.solutionRepository = solutionRepository;
        this.tagDefinitionService = tagDefinitionService;
        this.domainRepository = domainRepository;
    }

    @Transactional
    public EntityTagDTO createTag(String domainId, String solutionId, EntityTagCreateRequest request) {
        Solution solution = resolveSolution(domainId, solutionId);
        UUID solUuid = solution.getId();

        validateAgainstTagDefinition(request);

        if (entityTagRepository.existsBySolutionIdAndEntityTypeAndEntityIdAndTagKey(
                solUuid, request.entityType(), request.entityId(), request.tagKey())) {
            throw new DuplicateEntityTagException(
                    solutionId, request.entityType(), request.entityId(), request.tagKey());
        }

        EntityTag tag = new EntityTag();
        tag.setSolutionId(solUuid);
        tag.setEntityType(request.entityType());
        tag.setEntityId(request.entityId());
        tag.setTagKey(request.tagKey());
        tag.setTagValue(request.tagValue());

        try {
            EntityTag saved = entityTagRepository.save(tag);
            return mapToDTO(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEntityTagException(
                    solutionId, request.entityType(), request.entityId(), request.tagKey());
        }
    }

    /**
     * Validates an entity tag create request against the tag definitions governance layer.
     *
     * <p>Checks that:
     * <ol>
     *   <li>A TagDefinition exists for the given tagKey (case-insensitive)</li>
     *   <li>If the definition has allowed_values, the tagValue is in that list</li>
     *   <li>The request's entityType is in the definition's applicableEntityTypes</li>
     * </ol>
     *
     * @param request the entity tag create request to validate
     * @throws TagDefinitionNotFoundForKeyException if no definition matches the tagKey
     * @throws InvalidTagValueException if tagValue is not in allowed_values
     * @throws EntityTypeNotApplicableException if entityType is not in applicableEntityTypes
     */
    private void validateAgainstTagDefinition(EntityTagCreateRequest request) {
        TagDefinition definition = tagDefinitionService.findByTagName(request.tagKey())
                .orElseThrow(() -> new TagDefinitionNotFoundForKeyException(request.tagKey()));

        // Validate tagValue against allowed_values if constrained
        if (definition.getAllowedValues() != null && !definition.getAllowedValues().isEmpty()) {
            if (request.tagValue() == null || !definition.getAllowedValues().contains(request.tagValue())) {
                throw new InvalidTagValueException(
                        request.tagValue(), definition.getTagName(), definition.getAllowedValues());
            }
        }

        // Validate entityType against applicableEntityTypes
        String entityTypeName = request.entityType().name();
        if (!definition.getApplicableEntityTypes().contains(entityTypeName)) {
            throw new EntityTypeNotApplicableException(definition.getTagName(), entityTypeName);
        }
    }

    public EntityTagDTO getTag(String domainId, String solutionId, EntityType entityType,
                               UUID entityId, String tagKey) {
        Solution solution = resolveSolution(domainId, solutionId);
        EntityTag tag = entityTagRepository
                .findBySolutionIdAndEntityTypeAndEntityIdAndTagKey(
                        solution.getId(), entityType, entityId, tagKey)
                .orElseThrow(() -> new EntityTagNotFoundException(
                        solutionId, entityType, entityId, tagKey));
        return mapToDTO(tag);
    }

    public List<EntityTagDTO> listTags(String domainId, String solutionId, EntityType entityType,
                                       UUID entityId, String tagKey) {
        Solution solution = resolveSolution(domainId, solutionId);
        UUID solUuid = solution.getId();

        List<EntityTag> tags;
        if (entityType != null && entityId != null) {
            tags = entityTagRepository
                    .findBySolutionIdAndEntityTypeAndEntityId(solUuid, entityType, entityId);
        } else if (tagKey != null) {
            tags = entityTagRepository.findBySolutionIdAndTagKey(solUuid, tagKey);
        } else {
            tags = entityTagRepository.findBySolutionId(solUuid);
        }

        return tags.stream().map(this::mapToDTO).toList();
    }

    @Transactional
    public void deleteTag(String domainId, String solutionId, EntityType entityType,
                          UUID entityId, String tagKey) {
        Solution solution = resolveSolution(domainId, solutionId);
        EntityTag tag = entityTagRepository
                .findBySolutionIdAndEntityTypeAndEntityIdAndTagKey(
                        solution.getId(), entityType, entityId, tagKey)
                .orElseThrow(() -> new EntityTagNotFoundException(
                        solutionId, entityType, entityId, tagKey));
        entityTagRepository.delete(tag);
    }

    private Solution resolveSolution(String domainId, String solutionId) {
        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .map(Domain::getId)
                .orElseThrow(() -> new SolutionNotFoundException(solutionId));
        return solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .orElseThrow(() -> new SolutionNotFoundException(solutionId));
    }

    private EntityTagDTO mapToDTO(EntityTag tag) {
        return new EntityTagDTO(
                tag.getId(), tag.getSolutionId(), tag.getEntityType(),
                tag.getEntityId(), tag.getTagKey(), tag.getTagValue(),
                tag.getCreatedAt());
    }
}
