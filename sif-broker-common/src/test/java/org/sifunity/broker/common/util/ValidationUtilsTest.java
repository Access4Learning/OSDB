// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.exception.InvalidParameterException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ValidationUtils.
 * Tests validation methods for UUIDs, service names, zone IDs, message exchange patterns, and access types.
 */
class ValidationUtilsTest {

    // ========== validateUuid() Tests ==========

    @Test
    void validateUuid_withValidUuid_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateUuid("550e8400-e29b-41d4-a716-446655440000", "testField"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateUuid_withValidUuidLowercase_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateUuid("550e8400-e29b-41d4-a716-446655440000", "testField"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateUuid_withValidUuidUppercase_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateUuid("550E8400-E29B-41D4-A716-446655440000", "testField"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateUuid_withValidUuidMixedCase_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateUuid("550e8400-E29B-41d4-A716-446655440000", "testField"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateUuid_withRandomUuid_doesNotThrow() {
        String uuid = java.util.UUID.randomUUID().toString();
        assertThatCode(() -> ValidationUtils.validateUuid(uuid, "testField"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateUuid_withNilUuid_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateUuid("00000000-0000-0000-0000-000000000000", "testField"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateUuid_withInvalidFormat_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateUuid("invalid-uuid", "testField"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("testField")
            .hasMessageContaining("Invalid UUID format");
    }

    @Test
    void validateUuid_withMissingHyphens_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateUuid("550e8400e29b41d4a716446655440000", "testField"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("testField")
            .hasMessageContaining("Invalid UUID format");
    }

    @Test
    void validateUuid_withTooShort_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateUuid("550e8400-e29b-41d4", "testField"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("testField")
            .hasMessageContaining("Invalid UUID format");
    }

    @Test
    void validateUuid_withTooLong_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateUuid("550e8400-e29b-41d4-a716-446655440000-extra", "testField"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("testField")
            .hasMessageContaining("Invalid UUID format");
    }

    @Test
    void validateUuid_withEmptyString_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateUuid("", "testField"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("testField")
            .hasMessageContaining("Invalid UUID format");
    }

    @Test
    void validateUuid_withWhitespace_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateUuid("   ", "testField"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("testField")
            .hasMessageContaining("Invalid UUID format");
    }

    @Test
    void validateUuid_withInvalidCharacters_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateUuid("550e8400-e29b-41d4-a716-44665544000g", "testField"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("testField")
            .hasMessageContaining("Invalid UUID format");
    }

    @Test
    void validateUuid_withWrongHyphenPositions_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateUuid("550e-8400-e29b-41d4-a716-446655440000", "testField"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("testField")
            .hasMessageContaining("Invalid UUID format");
    }

    @Test
    void validateUuid_withDifferentFieldNames_includesFieldNameInError() {
        assertThatThrownBy(() -> ValidationUtils.validateUuid("invalid", "environmentId"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("environmentId");

        assertThatThrownBy(() -> ValidationUtils.validateUuid("invalid", "queueId"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("queueId");
    }

    // ========== validateServiceName() Tests ==========

    @Test
    void validateServiceName_withValidName_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("StudentPersonal"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withLowercaseStart_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("studentPersonal"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withUppercaseStart_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("StudentPersonal"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withHyphens_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("student-personal"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withUnderscores_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("student_personal"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withNumbers_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("student123"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withMixedCase_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("StudentPersonalInfo"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withHyphensAndUnderscores_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("student-personal_info"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withSingleLetter_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("a"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withLongName_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("a".repeat(100)))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withNull_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName(null))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("serviceName")
            .hasMessageContaining("Must start with letter");
    }

    @Test
    void validateServiceName_withEmptyString_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName(""))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("serviceName")
            .hasMessageContaining("Must start with letter");
    }

    @Test
    void validateServiceName_startingWithNumber_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName("123student"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("serviceName")
            .hasMessageContaining("Must start with letter");
    }

    @Test
    void validateServiceName_startingWithHyphen_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName("-student"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("serviceName")
            .hasMessageContaining("Must start with letter");
    }

    @Test
    void validateServiceName_startingWithUnderscore_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName("_student"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("serviceName")
            .hasMessageContaining("Must start with letter");
    }

    @Test
    void validateServiceName_withSpaces_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName("student personal"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("serviceName")
            .hasMessageContaining("Must start with letter");
    }

    @Test
    void validateServiceName_withDots_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName("student.personal"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("serviceName")
            .hasMessageContaining("Must start with letter");
    }

    @Test
    void validateServiceName_withSlashes_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName("student/personal"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("serviceName")
            .hasMessageContaining("Must start with letter");
    }

    @Test
    void validateServiceName_withSpecialCharacters_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName("student@personal"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("serviceName")
            .hasMessageContaining("Must start with letter");
    }

    @Test
    void validateServiceName_withWhitespace_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName("   "))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("serviceName")
            .hasMessageContaining("Must start with letter");
    }

    // ========== validateZoneId() Tests ==========

    @Test
    void validateZoneId_withValidId_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("default"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_startingWithLetter_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("zone1"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_startingWithNumber_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("1zone"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withHyphens_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("test-zone"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withUnderscores_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("test_zone"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withMixedCase_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("TestZone"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withNumbers_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("zone123"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withHyphensAndUnderscores_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("test-zone_01"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withSingleCharacter_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("a"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withSingleDigit_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("1"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withLongId_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("a".repeat(100)))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withNull_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId(null))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("zoneId")
            .hasMessageContaining("Must start with alphanumeric");
    }

    @Test
    void validateZoneId_withEmptyString_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId(""))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("zoneId")
            .hasMessageContaining("Must start with alphanumeric");
    }

    @Test
    void validateZoneId_startingWithHyphen_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId("-zone"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("zoneId")
            .hasMessageContaining("Must start with alphanumeric");
    }

    @Test
    void validateZoneId_startingWithUnderscore_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId("_zone"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("zoneId")
            .hasMessageContaining("Must start with alphanumeric");
    }

    @Test
    void validateZoneId_withSpaces_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId("test zone"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("zoneId")
            .hasMessageContaining("Must start with alphanumeric");
    }

    @Test
    void validateZoneId_withDots_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId("test.zone"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("zoneId")
            .hasMessageContaining("Must start with alphanumeric");
    }

    @Test
    void validateZoneId_withSlashes_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId("test/zone"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("zoneId")
            .hasMessageContaining("Must start with alphanumeric");
    }

    @Test
    void validateZoneId_withSpecialCharacters_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId("test@zone"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("zoneId")
            .hasMessageContaining("Must start with alphanumeric");
    }

    @Test
    void validateZoneId_withWhitespace_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId("   "))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("zoneId")
            .hasMessageContaining("Must start with alphanumeric");
    }

    // ========== validateMessageExchangePattern() Tests ==========

    @Test
    void validateMessageExchangePattern_withImmediate_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateMessageExchangePattern("IMMEDIATE"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateMessageExchangePattern_withDelayed_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateMessageExchangePattern("DELAYED"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateMessageExchangePattern_withEvent_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateMessageExchangePattern("EVENT"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateMessageExchangePattern_withLowercase_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateMessageExchangePattern("immediate"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("messageExchangePattern")
            .hasMessageContaining("Must be one of: IMMEDIATE, DELAYED, EVENT");
    }

    @Test
    void validateMessageExchangePattern_withMixedCase_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateMessageExchangePattern("Immediate"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("messageExchangePattern")
            .hasMessageContaining("Must be one of: IMMEDIATE, DELAYED, EVENT");
    }

    @Test
    void validateMessageExchangePattern_withInvalidValue_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateMessageExchangePattern("INVALID"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("messageExchangePattern")
            .hasMessageContaining("Must be one of: IMMEDIATE, DELAYED, EVENT");
    }

    @Test
    void validateMessageExchangePattern_withEmptyString_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateMessageExchangePattern(""))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("messageExchangePattern")
            .hasMessageContaining("Must be one of: IMMEDIATE, DELAYED, EVENT");
    }

    @Test
    void validateMessageExchangePattern_withWhitespace_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateMessageExchangePattern("   "))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("messageExchangePattern")
            .hasMessageContaining("Must be one of: IMMEDIATE, DELAYED, EVENT");
    }

    @Test
    void validateMessageExchangePattern_withNull_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateMessageExchangePattern(null))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("messageExchangePattern")
            .hasMessageContaining("Must be one of: IMMEDIATE, DELAYED, EVENT");
    }

    @Test
    void validateMessageExchangePattern_withExtraSpaces_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateMessageExchangePattern(" IMMEDIATE "))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("messageExchangePattern")
            .hasMessageContaining("Must be one of: IMMEDIATE, DELAYED, EVENT");
    }

    @Test
    void validateMessageExchangePattern_withUnderscores_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateMessageExchangePattern("IMMEDIATE_PATTERN"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("messageExchangePattern")
            .hasMessageContaining("Must be one of: IMMEDIATE, DELAYED, EVENT");
    }

    // ========== validateAccessType() Tests ==========

    @Test
    void validateAccessType_withProvide_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateAccessType("PROVIDE"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateAccessType_withQuery_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateAccessType("QUERY"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateAccessType_withCreate_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateAccessType("CREATE"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateAccessType_withUpdate_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateAccessType("UPDATE"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateAccessType_withDelete_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateAccessType("DELETE"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateAccessType_withSubscribe_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateAccessType("SUBSCRIBE"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateAccessType_withLowercase_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateAccessType("provide"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("accessType")
            .hasMessageContaining("Must be one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE");
    }

    @Test
    void validateAccessType_withMixedCase_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateAccessType("Provide"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("accessType")
            .hasMessageContaining("Must be one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE");
    }

    @Test
    void validateAccessType_withInvalidValue_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateAccessType("INVALID"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("accessType")
            .hasMessageContaining("Must be one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE");
    }

    @Test
    void validateAccessType_withOldConsumeValue_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateAccessType("CONSUME"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("accessType")
            .hasMessageContaining("Must be one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE");
    }

    @Test
    void validateAccessType_withEmptyString_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateAccessType(""))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("accessType")
            .hasMessageContaining("Must be one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE");
    }

    @Test
    void validateAccessType_withWhitespace_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateAccessType("   "))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("accessType")
            .hasMessageContaining("Must be one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE");
    }

    @Test
    void validateAccessType_withNull_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateAccessType(null))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("accessType")
            .hasMessageContaining("Must be one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE");
    }

    @Test
    void validateAccessType_withExtraSpaces_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateAccessType(" PROVIDE "))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("accessType")
            .hasMessageContaining("Must be one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE");
    }

    @Test
    void validateAccessType_withUnderscores_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateAccessType("PROVIDE_ACCESS"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("accessType")
            .hasMessageContaining("Must be one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE");
    }

    // ========== Utility Class Tests ==========

    @Test
    void testUtilityClass_cannotBeInstantiated() {
        assertThatThrownBy(() -> {
            // Use reflection to try to instantiate the utility class
            var constructor = ValidationUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
        .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
        .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    // ========== Edge Case Tests ==========

    @Test
    void validateServiceName_withAllValidCharacters_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("aAbBcC123-_"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withAllValidCharacters_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("aAbBcC123-_"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withOnlyLetters_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withOnlyNumbers_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("0123456789"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withTrailingHyphen_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("student-"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateServiceName_withTrailingUnderscore_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateServiceName("student_"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withTrailingHyphen_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("zone-"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateZoneId_withTrailingUnderscore_doesNotThrow() {
        assertThatCode(() -> ValidationUtils.validateZoneId("zone_"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateUuid_withLeadingWhitespace_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateUuid(" 550e8400-e29b-41d4-a716-446655440000", "testField"))
            .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void validateUuid_withTrailingWhitespace_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateUuid("550e8400-e29b-41d4-a716-446655440000 ", "testField"))
            .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void validateServiceName_withLeadingWhitespace_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName(" student"))
            .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void validateServiceName_withTrailingWhitespace_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName("student "))
            .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void validateZoneId_withLeadingWhitespace_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId(" zone"))
            .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void validateZoneId_withTrailingWhitespace_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId("zone "))
            .isInstanceOf(InvalidParameterException.class);
    }
}
