// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.repository.SolutionServiceRepository;
import org.sifunity.broker.environment.domain.SolutionService;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.XQueryParameter;
import org.sifunity.broker.environment.domain.XQueryStatus;
import org.sifunity.broker.environment.domain.XQueryTemplate;
import org.sifunity.broker.environment.domain.XQueryType;
import org.sifunity.broker.environment.dto.XQueryParameterDTO;
import org.sifunity.broker.environment.dto.XQueryParametersDTO;
import org.sifunity.broker.environment.dto.XQueryTemplateBody;
import org.sifunity.broker.environment.dto.XQueryTemplateCreateRequest;
import org.sifunity.broker.environment.dto.XQueryTemplateUpdateRequest;
import org.sifunity.broker.environment.dto.XQueryTemplateUpdateBody;
import org.sifunity.broker.environment.dto.XQueryTemplateDTO;
import org.sifunity.broker.environment.exception.DuplicateQueryTokenException;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.exception.XQueryTemplateNotFoundException;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.XQueryTemplateRepository;

import java.util.List;
import java.util.UUID;

/**
 * Service class for managing Named Query Templates (xQueryTemplates).
 *
 * <p>This service handles the business logic for Named Query Template lifecycle:
 * <ul>
 *   <li>Creating templates with validation</li>
 *   <li>Listing all templates for a solution</li>
 *   <li>Retrieving a template by query token</li>
 *   <li>Deleting a template</li>
 * </ul>
 *
 * @see XQueryTemplate
 * @see XQueryTemplateRepository
 */
@Service
@Transactional
public class XQueryTemplateService {

    private static final Logger log = LoggerFactory.getLogger(XQueryTemplateService.class);

    /** The solution_services type a Named Query's companion row carries. */
    private static final String XQUERYTEMPLATE_SERVICE_TYPE = "XQUERYTEMPLATE";

    private final XQueryTemplateRepository xQueryTemplateRepository;
    private final SolutionRepository solutionRepository;
    private final EnvironmentRepository environmentRepository;
    private final DomainRepository domainRepository;
    private final SolutionServiceRepository solutionServiceRepository;

    public XQueryTemplateService(XQueryTemplateRepository xQueryTemplateRepository,
                                 SolutionRepository solutionRepository,
                                 EnvironmentRepository environmentRepository,
                                 DomainRepository domainRepository,
                                 SolutionServiceRepository solutionServiceRepository) {
        this.xQueryTemplateRepository = xQueryTemplateRepository;
        this.solutionRepository = solutionRepository;
        this.environmentRepository = environmentRepository;
        this.domainRepository = domainRepository;
        this.solutionServiceRepository = solutionServiceRepository;
    }

    /**
     * Resolves a solution scoped to its owning domain. {@code solutionId} is unique only
     * within a domain, so the lookup is scoped by {@code domainId}.
     */
    private Solution resolveSolution(String domainId, String solutionId) {
        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .map(Domain::getId)
                .orElseThrow(() -> new SolutionNotFoundException(solutionId));
        return solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .orElseThrow(() -> new SolutionNotFoundException(solutionId));
    }

    /**
     * Creates a new Named Query Template.
     *
     * <p>Performs the following steps:
     * <ol>
     *   <li>Validates that the query token is non-empty</li>
     *   <li>Validates that type and status are valid enum values</li>
     *   <li>Validates uniqueness of query token within the solution</li>
     *   <li>Persists the XQueryTemplate entity</li>
     * </ol>
     *
     * @param solutionId    the solution ID string from the URL path
     * @param environmentId the UUID of the provider environment registering the template
     * @param request       the create request containing template data
     * @return DTO representing the created template
     * @throws IllegalArgumentException        if token is empty or type/status is invalid
     * @throws DuplicateQueryTokenException    if token already exists in the solution
     * @throws SolutionNotFoundException       if the solution does not exist
     * @throws EnvironmentNotFoundException    if the environment does not exist
     *
     * <p><strong>Requirements:</strong> 1.1, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5</p>
     */
    public XQueryTemplateDTO create(String domainId, String solutionId, UUID environmentId, XQueryTemplateCreateRequest request) {
        // D6 / requirement 2.12: the request is now wrapped under the "xquery" key.
        XQueryTemplateBody body = request.xquery();
        log.debug("Creating xQueryTemplate: token='{}', type='{}', status='{}', solution='{}', environment='{}'",
                body.id(), body.type(), body.status(), solutionId, environmentId);

        // Validate query token is non-empty (Requirement 2.5)
        if (body.id() == null || body.id().isBlank()) {
            throw new IllegalArgumentException("Query token must not be empty");
        }

        // Validate type enum (Requirement 2.3)
        XQueryType type = parseType(body.type());

        // Validate status enum (Requirement 2.4)
        XQueryStatus status = parseStatus(body.status());

        // Resolve solution UUID (scoped to domain)
        Solution solution = resolveSolution(domainId, solutionId);

        // Validate environment exists
        Environment environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new EnvironmentNotFoundException(environmentId));

        // Check uniqueness of queryToken in solution (Requirement 1.5)
        if (xQueryTemplateRepository.existsByQueryTokenAndSolutionUuid(body.id(), solution.getId())) {
            throw new DuplicateQueryTokenException(body.id(), solutionId);
        }

        // Build and persist the template entity
        // D6 / requirement 2.13: parameters arrive nested as { "parameter": [ ... ] }, so unwrap one
        // level. Both the wrapper and its inner list are null-guarded — validation requires the
        // wrapper, but this mapper is also reached from paths that do not go through @Valid.
        List<XQueryParameter> parameters =
                (body.parameters() != null && body.parameters().parameter() != null)
                ? body.parameters().parameter().stream()
                    .map(p -> new XQueryParameter(p.name(), p.type(), p.defaultValue(), p.description(), p.required()))
                    .toList()
                : List.of();

        XQueryTemplate template = XQueryTemplate.builder()
                .queryToken(body.id())
                .type(type)
                .status(status)
                .qualifier(body.qualifier())
                .description(body.description())
                .script(body.script())
                .parameters(parameters)
                .returnType(body.returnType())
                .environment(environment)
                .solutionUuid(solution.getId())
                .build();

        XQueryTemplate saved = xQueryTemplateRepository.save(template);
        log.info("Created xQueryTemplate: id={}, token='{}', solution='{}'",
                saved.getId(), saved.getQueryToken(), solutionId);

        ensureCompanionSolutionService(solution.getId(), saved.getQueryToken(), solutionId);

        return mapToDTO(saved);
    }

    /**
     * Creates the companion {@code solution_services} row for a template, if it is not already there.
     *
     * <p>A template on its own is not reachable: the zone/context binding and provider registrations
     * hang off a service <em>name</em> in {@code solution_services}, and the two are coupled by name
     * with no foreign key. Creating the companion here means one action in the console produces
     * something that can actually be wired up, instead of a catalog entry that silently goes nowhere.</p>
     *
     * <p>Deliberately forgiving. It is idempotent — a row may already exist if one was added by hand
     * before the template — and a failure here is logged rather than thrown, because the template is
     * the user's actual intent and the companion is a convenience. The coupling is intentionally loose;
     * the {@code UNIQUE (query_token, solution_uuid)} and
     * {@code UNIQUE (solution_id, service_name, service_type)} constraints are what keep name-matching
     * unambiguous within a solution.</p>
     */
    private void ensureCompanionSolutionService(UUID solutionUuid, String queryToken, String solutionId) {
        try {
            if (solutionServiceRepository.existsBySolutionIdAndServiceNameAndServiceType(
                    solutionUuid, queryToken, XQUERYTEMPLATE_SERVICE_TYPE)) {
                log.debug("Companion solution service already present: name='{}', solution='{}'",
                        queryToken, solutionId);
                return;
            }
            SolutionService companion = new SolutionService();
            companion.setSolutionId(solutionUuid);
            companion.setServiceName(queryToken);
            companion.setServiceType(XQUERYTEMPLATE_SERVICE_TYPE);
            solutionServiceRepository.save(companion);
            log.info("Created companion solution service: name='{}', type={}, solution='{}'",
                    queryToken, XQUERYTEMPLATE_SERVICE_TYPE, solutionId);
        } catch (RuntimeException e) {
            log.warn("Could not create the companion solution service for token='{}' in solution='{}'. "
                     + "The template exists; add the service manually to make it routable. Cause: {}",
                    queryToken, solutionId, e.toString());
        }
    }

    /**
     * Lists all Named Query Templates for a solution.
     *
     * @param solutionId the solution ID string from the URL path
     * @return list of template DTOs (may be empty)
     * @throws SolutionNotFoundException if the solution does not exist
     *
     * <p><strong>Requirements:</strong> 1.2</p>
     */
    @Transactional(readOnly = true)
    public List<XQueryTemplateDTO> list(String domainId, String solutionId) {
        log.debug("Listing xQueryTemplates for solution: '{}'", solutionId);

        Solution solution = resolveSolution(domainId, solutionId);

        List<XQueryTemplate> templates = xQueryTemplateRepository.findAllBySolutionUuid(solution.getId());
        log.debug("Found {} xQueryTemplates for solution '{}'", templates.size(), solutionId);

        return templates.stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Retrieves a single Named Query Template by its query token within a solution.
     *
     * @param solutionId the solution ID string from the URL path
     * @param queryToken the query token to look up
     * @return the template DTO
     * @throws SolutionNotFoundException        if the solution does not exist
     * @throws XQueryTemplateNotFoundException  if no template found with the given token
     *
     * <p><strong>Requirements:</strong> 1.3</p>
     */
    @Transactional(readOnly = true)
    public XQueryTemplateDTO getByToken(String domainId, String solutionId, String queryToken) {
        log.debug("Getting xQueryTemplate: token='{}', solution='{}'", queryToken, solutionId);

        Solution solution = resolveSolution(domainId, solutionId);

        XQueryTemplate template = xQueryTemplateRepository
                .findByQueryTokenAndSolutionUuid(queryToken, solution.getId())
                .orElseThrow(() -> new XQueryTemplateNotFoundException(queryToken, solutionId));

        return mapToDTO(template);
    }

    /**
     * Applies a partial update to a Named Query Template.
     *
     * <p>Only the fields present on the body are changed; a {@code null} field means "leave alone".
     * That distinction is why this compares against {@code null} rather than blank-checking: sending
     * {@code "qualifier": ""} deliberately clears the qualifier, while omitting it preserves what is
     * there. Approving a template is this method with a body carrying only {@code status}.</p>
     *
     * <p>The query token is not updatable. It identifies the template, and {@code context_services}
     * rows, provider registrations and consumer requests are all coupled to it <em>by name</em> rather
     * than by a foreign key — renaming here would silently strand every one of them.</p>
     *
     * @param domainId   the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param queryToken the token identifying the template to update
     * @param request    the wrapped, all-optional update body
     * @return the updated template
     * @throws SolutionNotFoundException       if the solution does not exist
     * @throws XQueryTemplateNotFoundException if no template has that token in the solution
     * @throws IllegalArgumentException        if a supplied type or status is not a valid value
     *
     * <p><strong>Requirements:</strong> 1.1, 2.3, 2.4</p>
     */
    public XQueryTemplateDTO update(String domainId, String solutionId, String queryToken,
                                    XQueryTemplateUpdateRequest request) {
        XQueryTemplateUpdateBody body = request.xquery();
        log.debug("Updating xQueryTemplate: token='{}', solution='{}'", queryToken, solutionId);

        Solution solution = resolveSolution(domainId, solutionId);

        XQueryTemplate template = xQueryTemplateRepository
                .findByQueryTokenAndSolutionUuid(queryToken, solution.getId())
                .orElseThrow(() -> new XQueryTemplateNotFoundException(queryToken, solutionId));

        // Parse the enums before mutating anything, so an invalid value leaves the row untouched
        // rather than applying a partial change and then failing.
        XQueryType type = body.type() != null ? parseType(body.type()) : null;
        XQueryStatus status = body.status() != null ? parseStatus(body.status()) : null;

        if (type != null) {
            template.setType(type);
        }
        if (status != null) {
            template.setStatus(status);
        }
        if (body.qualifier() != null) {
            template.setQualifier(body.qualifier());
        }
        if (body.description() != null) {
            template.setDescription(body.description());
        }
        if (body.script() != null) {
            template.setScript(body.script());
        }
        if (body.returnType() != null) {
            template.setReturnType(body.returnType());
        }
        if (body.parameters() != null) {
            // Same unwrapping as create: parameters arrive nested as { "parameter": [ ... ] }.
            // An explicitly supplied wrapper with a null or empty array clears the parameters.
            List<XQueryParameter> parameters =
                    body.parameters().parameter() == null
                    ? List.of()
                    : body.parameters().parameter().stream()
                        .map(p -> new XQueryParameter(p.name(), p.type(), p.defaultValue(),
                                                      p.description(), p.required()))
                        .toList();
            template.setParameters(parameters);
        }

        XQueryTemplate saved = xQueryTemplateRepository.save(template);
        log.info("Updated xQueryTemplate: token='{}', solution='{}', status='{}'",
                saved.getQueryToken(), solutionId, saved.getStatus());

        return mapToDTO(saved);
    }

    /**
     * Deletes the template with this token if one exists, and reports whether it did.
     *
     * <p>For the companion direction: deleting an {@code XQUERYTEMPLATE} row from
     * {@code solution_services} should take its template with it, but a service row may legitimately
     * have no template behind it — the two are coupled by name with no foreign key, and rows created
     * before that coupling existed are exactly this case. Throwing
     * {@link XQueryTemplateNotFoundException} there would make deleting a harmless orphan fail, so
     * this returns {@code false} instead.</p>
     *
     * <p>Does <b>not</b> delete the companion service row, unlike {@link #delete}: the caller is
     * already deleting it, and re-entering that path would be circular.</p>
     *
     * @param domainId   the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param queryToken the token to delete
     * @return true when a template was found and deleted, false when there was nothing to delete
     */
    public boolean deleteTemplateIfPresent(String domainId, String solutionId, String queryToken) {
        Solution solution = resolveSolution(domainId, solutionId);

        return xQueryTemplateRepository
                .findByQueryTokenAndSolutionUuid(queryToken, solution.getId())
                .map(template -> {
                    xQueryTemplateRepository.delete(template);
                    log.info("Deleted xQueryTemplate alongside its solution service: token='{}', solution='{}'",
                            queryToken, solutionId);
                    return true;
                })
                .orElseGet(() -> {
                    log.debug("No xQueryTemplate named '{}' in solution '{}' to delete", queryToken, solutionId);
                    return false;
                });
    }

    /**
     * Deletes a Named Query Template.
     *
     * @param solutionId the solution ID string from the URL path
     * @param queryToken the query token to delete
     * @throws SolutionNotFoundException        if the solution does not exist
     * @throws XQueryTemplateNotFoundException  if no template found with the given token
     *
     * <p><strong>Requirements:</strong> 1.4</p>
     */
    public void delete(String domainId, String solutionId, String queryToken) {
        log.debug("Deleting xQueryTemplate: token='{}', solution='{}'", queryToken, solutionId);

        Solution solution = resolveSolution(domainId, solutionId);

        XQueryTemplate template = xQueryTemplateRepository
                .findByQueryTokenAndSolutionUuid(queryToken, solution.getId())
                .orElseThrow(() -> new XQueryTemplateNotFoundException(queryToken, solutionId));

        // Remove the template itself
        xQueryTemplateRepository.delete(template);

        // ...and its companion service row, so the pair stays consistent. Any context_services or
        // provider registrations bound to this name are deliberately left in place: they are coupled
        // by name with no foreign key, and cascading into them is a bigger decision than this method
        // should be making. They become orphans pointing at a name nothing serves, which is an
        // accepted trade-off until there are real requirements for the cleanup.
        try {
            solutionServiceRepository.deleteBySolutionIdAndServiceNameAndServiceType(
                    solution.getId(), queryToken, XQUERYTEMPLATE_SERVICE_TYPE);
            log.info("Deleted companion solution service: name='{}', solution='{}'", queryToken, solutionId);
        } catch (RuntimeException e) {
            log.warn("Deleted the template but could not delete its companion solution service "
                     + "name='{}' in solution='{}'. Cause: {}", queryToken, solutionId, e.toString());
        }

        log.info("Deleted xQueryTemplate: token='{}', solution='{}'", queryToken, solutionId);
    }

    // ========== Private helpers ==========

    private XQueryType parseType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Type must not be empty. Valid values: SINGULAR, FORMULA, EXTENDED");
        }
        try {
            return XQueryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid type: '" + type + "'. Valid values: SINGULAR, FORMULA, EXTENDED");
        }
    }

    private XQueryStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status must not be empty. Valid values: PENDING, APPROVED, DISALLOWED");
        }
        try {
            return XQueryStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid status: '" + status + "'. Valid values: PENDING, APPROVED, DISALLOWED");
        }
    }

    private XQueryTemplateDTO mapToDTO(XQueryTemplate template) {
        List<XQueryParameterDTO> paramDTOs = template.getParameters() != null
                ? template.getParameters().stream()
                    .map(p -> new XQueryParameterDTO(p.name(), p.type(), p.defaultValue(), p.description(), p.required()))
                    .toList()
                : List.of();

        // D6 / requirement 2.13: emit parameters nested as { "parameter": [ ... ] }. The empty case is
        // an empty ARRAY, not an omitted key — "takes no parameters" is a different statement from
        // "parameters unknown", and XQueryParametersDTO's javadoc records why that distinction matters.
        XQueryParametersDTO parametersDTO = new XQueryParametersDTO(paramDTOs);

        return new XQueryTemplateDTO(
                template.getQueryToken(),
                template.getType().name(),
                template.getStatus().name(),
                template.getQualifier(),
                template.getDescription(),
                template.getScript(),
                parametersDTO,
                template.getReturnType()
        );
    }
}
