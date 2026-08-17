// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.exception.InvalidParameterException;
import org.sifunity.broker.common.exception.MissingRequiredFieldException;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SifHeaderUtils.
 * Tests header extraction, validation, and error handling.
 */
class SifHeaderUtilsTest {

    // ========== getRequiredHeader() Tests ==========

    @Test
    void getRequiredHeader_withValidHeader_returnsValue() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.SERVICE_NAME, "StudentPersonal");

        String result = SifHeaderUtils.getRequiredHeader(headers, SifHeaders.SERVICE_NAME);

        assertThat(result).isEqualTo("StudentPersonal");
    }

    @Test
    void getRequiredHeader_withMissingHeader_throwsException() {
        HttpHeaders headers = new HttpHeaders();

        assertThatThrownBy(() -> SifHeaderUtils.getRequiredHeader(headers, SifHeaders.SERVICE_NAME))
            .isInstanceOf(MissingRequiredFieldException.class)
            .hasMessageContaining("Required field missing: serviceName");
    }

    @Test
    void getRequiredHeader_withNullValue_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.SERVICE_NAME, null);

        assertThatThrownBy(() -> SifHeaderUtils.getRequiredHeader(headers, SifHeaders.SERVICE_NAME))
            .isInstanceOf(MissingRequiredFieldException.class)
            .hasMessageContaining("Required field missing: serviceName");
    }

    @Test
    void getRequiredHeader_withEmptyString_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.SERVICE_NAME, "");

        assertThatThrownBy(() -> SifHeaderUtils.getRequiredHeader(headers, SifHeaders.SERVICE_NAME))
            .isInstanceOf(MissingRequiredFieldException.class)
            .hasMessageContaining("Required field missing: serviceName");
    }

    @Test
    void getRequiredHeader_withWhitespaceOnly_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.SERVICE_NAME, "   ");

        assertThatThrownBy(() -> SifHeaderUtils.getRequiredHeader(headers, SifHeaders.SERVICE_NAME))
            .isInstanceOf(MissingRequiredFieldException.class)
            .hasMessageContaining("Required field missing: serviceName");
    }

    @Test
    void getRequiredHeader_withMultipleValues_returnsFirstValue() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.SERVICE_NAME, "StudentPersonal");
        headers.add(SifHeaders.SERVICE_NAME, "SchoolInfo");

        String result = SifHeaderUtils.getRequiredHeader(headers, SifHeaders.SERVICE_NAME);

        assertThat(result).isEqualTo("StudentPersonal");
    }

    @Test
    void getRequiredHeader_withAuthorizationHeader_returnsValue() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");

        String result = SifHeaderUtils.getRequiredHeader(headers, SifHeaders.AUTHORIZATION);

        assertThat(result).isEqualTo("SIF_HMACSHA256 token:timestamp:hash");
    }

    @Test
    void getRequiredHeader_withZoneIdHeader_returnsValue() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.ZONE_ID, "zone-default");

        String result = SifHeaderUtils.getRequiredHeader(headers, SifHeaders.ZONE_ID);

        assertThat(result).isEqualTo("zone-default");
    }

    // ========== getOptionalHeader() Tests ==========

    @Test
    void getOptionalHeader_withValidHeader_returnsValue() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.CONTEXT_ID, "context-test");

        String result = SifHeaderUtils.getOptionalHeader(headers, SifHeaders.CONTEXT_ID);

        assertThat(result).isEqualTo("context-test");
    }

    @Test
    void getOptionalHeader_withMissingHeader_returnsNull() {
        HttpHeaders headers = new HttpHeaders();

        String result = SifHeaderUtils.getOptionalHeader(headers, SifHeaders.CONTEXT_ID);

        assertThat(result).isNull();
    }

    @Test
    void getOptionalHeader_withNullValue_returnsNull() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.CONTEXT_ID, null);

        String result = SifHeaderUtils.getOptionalHeader(headers, SifHeaders.CONTEXT_ID);

        assertThat(result).isNull();
    }

    @Test
    void getOptionalHeader_withEmptyString_returnsEmptyString() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.CONTEXT_ID, "");

        String result = SifHeaderUtils.getOptionalHeader(headers, SifHeaders.CONTEXT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getOptionalHeader_withWhitespaceOnly_returnsWhitespace() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.CONTEXT_ID, "   ");

        String result = SifHeaderUtils.getOptionalHeader(headers, SifHeaders.CONTEXT_ID);

        assertThat(result).isEqualTo("   ");
    }

    @Test
    void getOptionalHeader_withMultipleValues_returnsFirstValue() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.CONTEXT_ID, "context-1");
        headers.add(SifHeaders.CONTEXT_ID, "context-2");

        String result = SifHeaderUtils.getOptionalHeader(headers, SifHeaders.CONTEXT_ID);

        assertThat(result).isEqualTo("context-1");
    }

    // ========== getUuidHeader() Tests ==========

    @Test
    void getUuidHeader_withValidUuid_returnsUuid() {
        HttpHeaders headers = new HttpHeaders();
        UUID expectedUuid = UUID.randomUUID();
        headers.add(SifHeaders.REQUEST_ID, expectedUuid.toString());

        UUID result = SifHeaderUtils.getUuidHeader(headers, SifHeaders.REQUEST_ID);

        assertThat(result).isEqualTo(expectedUuid);
    }

    @Test
    void getUuidHeader_withMissingHeader_returnsNull() {
        HttpHeaders headers = new HttpHeaders();

        UUID result = SifHeaderUtils.getUuidHeader(headers, SifHeaders.REQUEST_ID);

        assertThat(result).isNull();
    }

    @Test
    void getUuidHeader_withNullValue_returnsNull() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.REQUEST_ID, null);

        UUID result = SifHeaderUtils.getUuidHeader(headers, SifHeaders.REQUEST_ID);

        assertThat(result).isNull();
    }

    @Test
    void getUuidHeader_withInvalidUuidFormat_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.REQUEST_ID, "not-a-valid-uuid");

        assertThatThrownBy(() -> SifHeaderUtils.getUuidHeader(headers, SifHeaders.REQUEST_ID))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("Invalid parameter 'requestId': Invalid UUID format");
    }

    @Test
    void getUuidHeader_withEmptyString_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.REQUEST_ID, "");

        assertThatThrownBy(() -> SifHeaderUtils.getUuidHeader(headers, SifHeaders.REQUEST_ID))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("Invalid parameter 'requestId': Invalid UUID format");
    }

    @Test
    void getUuidHeader_withMalformedUuid_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.REQUEST_ID, "12345678-1234-1234-1234");

        assertThatThrownBy(() -> SifHeaderUtils.getUuidHeader(headers, SifHeaders.REQUEST_ID))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("Invalid parameter 'requestId': Invalid UUID format");
    }

    @Test
    void getUuidHeader_withMessageId_returnsUuid() {
        HttpHeaders headers = new HttpHeaders();
        UUID expectedUuid = UUID.randomUUID();
        headers.add(SifHeaders.MESSAGE_ID, expectedUuid.toString());

        UUID result = SifHeaderUtils.getUuidHeader(headers, SifHeaders.MESSAGE_ID);

        assertThat(result).isEqualTo(expectedUuid);
    }

    @Test
    void getUuidHeader_withQueueId_returnsUuid() {
        HttpHeaders headers = new HttpHeaders();
        UUID expectedUuid = UUID.randomUUID();
        headers.add(SifHeaders.QUEUE_ID, expectedUuid.toString());

        UUID result = SifHeaderUtils.getUuidHeader(headers, SifHeaders.QUEUE_ID);

        assertThat(result).isEqualTo(expectedUuid);
    }

    @Test
    void getUuidHeader_withSourceId_returnsUuid() {
        HttpHeaders headers = new HttpHeaders();
        UUID expectedUuid = UUID.randomUUID();
        headers.add(SifHeaders.SOURCE_ID, expectedUuid.toString());

        UUID result = SifHeaderUtils.getUuidHeader(headers, SifHeaders.SOURCE_ID);

        assertThat(result).isEqualTo(expectedUuid);
    }

    @Test
    void getUuidHeader_withUppercaseUuid_returnsUuid() {
        HttpHeaders headers = new HttpHeaders();
        String uppercaseUuid = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890";
        headers.add(SifHeaders.REQUEST_ID, uppercaseUuid);

        UUID result = SifHeaderUtils.getUuidHeader(headers, SifHeaders.REQUEST_ID);

        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualToIgnoringCase(uppercaseUuid);
    }

    // ========== validateRequiredHeaders() Tests ==========

    @Test
    void validateRequiredHeaders_withAllRequiredHeaders_succeeds() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");
        headers.add(SifHeaders.SERVICE_NAME, "StudentPersonal");
        headers.add(SifHeaders.ZONE_ID, "zone-default");

        // Should not throw exception
        SifHeaderUtils.validateRequiredHeaders(headers);
    }

    @Test
    void validateRequiredHeaders_withMissingAuthorization_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.SERVICE_NAME, "StudentPersonal");
        headers.add(SifHeaders.ZONE_ID, "zone-default");

        assertThatThrownBy(() -> SifHeaderUtils.validateRequiredHeaders(headers))
            .isInstanceOf(MissingRequiredFieldException.class)
            .hasMessageContaining("Required field missing: Authorization");
    }

    @Test
    void validateRequiredHeaders_withMissingServiceName_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");
        headers.add(SifHeaders.ZONE_ID, "zone-default");

        assertThatThrownBy(() -> SifHeaderUtils.validateRequiredHeaders(headers))
            .isInstanceOf(MissingRequiredFieldException.class)
            .hasMessageContaining("Required field missing: serviceName");
    }

    @Test
    void validateRequiredHeaders_withMissingZoneId_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");
        headers.add(SifHeaders.SERVICE_NAME, "StudentPersonal");

        assertThatThrownBy(() -> SifHeaderUtils.validateRequiredHeaders(headers))
            .isInstanceOf(MissingRequiredFieldException.class)
            .hasMessageContaining("Required field missing: zoneId");
    }

    @Test
    void validateRequiredHeaders_withEmptyAuthorization_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.AUTHORIZATION, "");
        headers.add(SifHeaders.SERVICE_NAME, "StudentPersonal");
        headers.add(SifHeaders.ZONE_ID, "zone-default");

        assertThatThrownBy(() -> SifHeaderUtils.validateRequiredHeaders(headers))
            .isInstanceOf(MissingRequiredFieldException.class)
            .hasMessageContaining("Required field missing: Authorization");
    }

    @Test
    void validateRequiredHeaders_withEmptyServiceName_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");
        headers.add(SifHeaders.SERVICE_NAME, "   ");
        headers.add(SifHeaders.ZONE_ID, "zone-default");

        assertThatThrownBy(() -> SifHeaderUtils.validateRequiredHeaders(headers))
            .isInstanceOf(MissingRequiredFieldException.class)
            .hasMessageContaining("Required field missing: serviceName");
    }

    @Test
    void validateRequiredHeaders_withEmptyZoneId_throwsException() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");
        headers.add(SifHeaders.SERVICE_NAME, "StudentPersonal");
        headers.add(SifHeaders.ZONE_ID, "");

        assertThatThrownBy(() -> SifHeaderUtils.validateRequiredHeaders(headers))
            .isInstanceOf(MissingRequiredFieldException.class)
            .hasMessageContaining("Required field missing: zoneId");
    }

    @Test
    void validateRequiredHeaders_withAllHeadersEmpty_throwsExceptionForFirst() {
        HttpHeaders headers = new HttpHeaders();

        assertThatThrownBy(() -> SifHeaderUtils.validateRequiredHeaders(headers))
            .isInstanceOf(MissingRequiredFieldException.class)
            .hasMessageContaining("Required field missing: Authorization");
    }

    @Test
    void validateRequiredHeaders_withOptionalHeadersPresent_succeeds() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");
        headers.add(SifHeaders.SERVICE_NAME, "StudentPersonal");
        headers.add(SifHeaders.ZONE_ID, "zone-default");
        headers.add(SifHeaders.CONTEXT_ID, "context-test");
        headers.add(SifHeaders.REQUEST_ID, UUID.randomUUID().toString());

        // Should not throw exception - optional headers don't affect validation
        SifHeaderUtils.validateRequiredHeaders(headers);
    }

    @Test
    void validateRequiredHeaders_withOptionalHeadersMissing_succeeds() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");
        headers.add(SifHeaders.SERVICE_NAME, "StudentPersonal");
        headers.add(SifHeaders.ZONE_ID, "zone-default");
        // No optional headers

        // Should not throw exception - optional headers are not required
        SifHeaderUtils.validateRequiredHeaders(headers);
    }

    // ========== Edge Cases and Integration Tests ==========

    @Test
    void testHeaderExtraction_withCompleteRequest() {
        HttpHeaders headers = new HttpHeaders();
        UUID requestId = UUID.randomUUID();
        headers.add(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");
        headers.add(SifHeaders.SERVICE_NAME, "StudentPersonal");
        headers.add(SifHeaders.ZONE_ID, "zone-default");
        headers.add(SifHeaders.CONTEXT_ID, "context-test");
        headers.add(SifHeaders.REQUEST_ID, requestId.toString());

        // Validate all required headers
        SifHeaderUtils.validateRequiredHeaders(headers);

        // Extract required headers
        String auth = SifHeaderUtils.getRequiredHeader(headers, SifHeaders.AUTHORIZATION);
        String serviceName = SifHeaderUtils.getRequiredHeader(headers, SifHeaders.SERVICE_NAME);
        String zoneId = SifHeaderUtils.getRequiredHeader(headers, SifHeaders.ZONE_ID);

        // Extract optional headers
        String contextId = SifHeaderUtils.getOptionalHeader(headers, SifHeaders.CONTEXT_ID);
        UUID extractedRequestId = SifHeaderUtils.getUuidHeader(headers, SifHeaders.REQUEST_ID);

        assertThat(auth).isEqualTo("SIF_HMACSHA256 token:timestamp:hash");
        assertThat(serviceName).isEqualTo("StudentPersonal");
        assertThat(zoneId).isEqualTo("zone-default");
        assertThat(contextId).isEqualTo("context-test");
        assertThat(extractedRequestId).isEqualTo(requestId);
    }

    @Test
    void testHeaderExtraction_withMinimalRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");
        headers.add(SifHeaders.SERVICE_NAME, "StudentPersonal");
        headers.add(SifHeaders.ZONE_ID, "zone-default");

        // Validate all required headers
        SifHeaderUtils.validateRequiredHeaders(headers);

        // Extract required headers
        String auth = SifHeaderUtils.getRequiredHeader(headers, SifHeaders.AUTHORIZATION);
        String serviceName = SifHeaderUtils.getRequiredHeader(headers, SifHeaders.SERVICE_NAME);
        String zoneId = SifHeaderUtils.getRequiredHeader(headers, SifHeaders.ZONE_ID);

        // Extract optional headers (should be null)
        String contextId = SifHeaderUtils.getOptionalHeader(headers, SifHeaders.CONTEXT_ID);
        UUID requestId = SifHeaderUtils.getUuidHeader(headers, SifHeaders.REQUEST_ID);

        assertThat(auth).isNotNull();
        assertThat(serviceName).isNotNull();
        assertThat(zoneId).isNotNull();
        assertThat(contextId).isNull();
        assertThat(requestId).isNull();
    }

    @Test
    void testUtilityClass_cannotBeInstantiated() {
        assertThatThrownBy(() -> {
            // Use reflection to try to instantiate the utility class
            var constructor = SifHeaderUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
        .hasCauseInstanceOf(UnsupportedOperationException.class)
        .satisfies(ex -> {
            assertThat(ex.getCause().getMessage()).contains("Utility class");
        });
    }
}
