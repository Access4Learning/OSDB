// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Property-based test for null contextServiceUuid rejection.
 *
 * <p><strong>Property:</strong> A provider registration request with a null contextServiceUuid
 * SHALL be rejected by bean validation, and no provider registration SHALL be created.</p>
 *
 * <p><strong>Validates: Requirements 2.2, 2.3</strong></p>
 */
public class BlankProviderNameRejectionPropertyTest {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    /**
     * A body that identifies the service by neither form must be rejected.
     *
     * <p>D1 changed <em>where</em> this violation lands, not whether it happens. The request used to
     * carry {@code contextServiceUuid} with {@code @NotNull}, so the violation was on that field.
     * A conforming SIF client cannot supply that value at all — it is an OSDB-internal foreign key —
     * so the body now accepts <b>either</b> {@code contextServiceUuid} <b>or</b> the
     * {@code (serviceName, zoneId, contextId)} triple, enforced by
     * {@code ProviderRegistrationBody.isServiceIdentifiable()}. A cross-field {@code @AssertTrue}
     * reports against that property rather than against any single field, because {@code @NotBlank} on
     * the triple would reject the UUID form and vice versa.</p>
     *
     * <p>So this asserts on the property that now carries the constraint. The complementary test below
     * is the control: without it, this one would still pass if validation rejected <em>everything</em>.</p>
     *
     * <p><strong>Validates: Requirements 2.2, 2.3</strong></p>
     */
    @Property(tries = 10)
    void neitherContextServiceUuidNorTripleIsRejected(
            @ForAll("validEndpoints") String endpoint) {

        ProviderRegistrationRequest request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                UUID.randomUUID(),
                null,               // serviceName
                ServiceType.OBJECT,
                null,               // zoneId
                null,               // contextId
                null,               // contextServiceUuid — so neither form is supplied
                ProtocolDTO.of(endpoint),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType( RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY)))))));

        Set<ConstraintViolation<ProviderRegistrationRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations)
                .as("A body identifying the service by neither form must be rejected")
                .isNotEmpty();

        boolean hasIdentifiabilityViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().endsWith("serviceIdentifiable"));

        assertThat(hasIdentifiabilityViolation)
                .as("Violation should come from the serviceIdentifiable cross-field check, "
                    + "not from a single field — actual paths: %s",
                    violations.stream().map(v -> v.getPropertyPath().toString()).toList())
                .isTrue();
    }

    /**
     * CONTROL for the property above: the schema triple alone is sufficient.
     *
     * <p>Without this, {@code neitherContextServiceUuidNorTripleIsRejected} would pass just as happily
     * against an implementation that rejected every provider body. It also pins the form a conforming
     * client actually uses, since {@code contextServiceUuid} is not available to one.</p>
     *
     * <p><strong>Validates: Requirements 2.2, 2.3</strong></p>
     */
    @Property(tries = 10)
    void serviceNameZoneContextTripleIsAccepted(
            @ForAll("validEndpoints") String endpoint) {

        ProviderRegistrationRequest request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                UUID.randomUUID(),
                "StudentPersonal",
                ServiceType.OBJECT,
                "district-zone",
                "DEFAULT",
                null,               // no contextServiceUuid — the triple stands alone
                ProtocolDTO.of(endpoint),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType( RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY)))))));

        Set<ConstraintViolation<ProviderRegistrationRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations)
                .as("The (serviceName, zoneId, contextId) triple alone must be accepted")
                .isEmpty();
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> validEndpoints() {
        return Arbitraries.of(
                "http://localhost:8080/api",
                "https://provider.example.com/sif",
                "http://192.168.1.1:9090/services"
        );
    }
}
