// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AclEntryDTO.
 */
class AclEntryDTOTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void testEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();

        AclEntryDTO dto1 = new AclEntryDTO(id, envId, "StudentPersonal", "DEFAULT", "DEFAULT", AccessType.PROVIDE, AccessLevel.APPROVED);
        AclEntryDTO dto2 = new AclEntryDTO(id, envId, "StudentPersonal", "DEFAULT", "DEFAULT", AccessType.PROVIDE, AccessLevel.APPROVED);

        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }

    @Test
    void testToString() {
        UUID id = UUID.randomUUID();
        AclEntryDTO dto = new AclEntryDTO(id, UUID.randomUUID(), "StudentPersonal", "DEFAULT", "DEFAULT", AccessType.PROVIDE, AccessLevel.APPROVED);
        String toString = dto.toString();

        assertThat(toString).contains("AclEntryDTO");
        assertThat(toString).contains(id.toString());
        assertThat(toString).contains("StudentPersonal");
        assertThat(toString).contains("DEFAULT");
        assertThat(toString).contains("PROVIDE");
        assertThat(toString).contains("APPROVED");
    }

    @Test
    void testValidation_valid() {
        AclEntryDTO dto = new AclEntryDTO(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "StudentPersonal",
            "DEFAULT",
            "DEFAULT",
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );

        Set<ConstraintViolation<AclEntryDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void testValidation_nullId() {
        AclEntryDTO dto = new AclEntryDTO(null, UUID.randomUUID(), "StudentPersonal", "DEFAULT", "DEFAULT", AccessType.PROVIDE, AccessLevel.APPROVED);

        Set<ConstraintViolation<AclEntryDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("id");
    }

    @Test
    void testValidation_nullEnvironmentId() {
        AclEntryDTO dto = new AclEntryDTO(UUID.randomUUID(), null, "StudentPersonal", "DEFAULT", "DEFAULT", AccessType.PROVIDE, AccessLevel.APPROVED);

        Set<ConstraintViolation<AclEntryDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("environmentId");
    }

    @Test
    void testValidation_blankServiceName() {
        AclEntryDTO dto = new AclEntryDTO(UUID.randomUUID(), UUID.randomUUID(), "", "DEFAULT", "DEFAULT", AccessType.PROVIDE, AccessLevel.APPROVED);

        Set<ConstraintViolation<AclEntryDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("serviceName");
    }

    @Test
    void testValidation_nullAccessType() {
        AclEntryDTO dto = new AclEntryDTO(UUID.randomUUID(), UUID.randomUUID(), "StudentPersonal", "DEFAULT", "DEFAULT", null, AccessLevel.APPROVED);

        Set<ConstraintViolation<AclEntryDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("accessType");
    }

    @Test
    void testValidation_nullAccessLevel() {
        AclEntryDTO dto = new AclEntryDTO(UUID.randomUUID(), UUID.randomUUID(), "StudentPersonal", "DEFAULT", "DEFAULT", AccessType.PROVIDE, null);

        Set<ConstraintViolation<AclEntryDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("accessLevel");
    }

    @Test
    void testValidation_contextUuidCanBeNull() {
        AclEntryDTO dto = new AclEntryDTO(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "StudentPersonal", null, null, // contextUuid is optional (wildcard)
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );

        Set<ConstraintViolation<AclEntryDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }
}
