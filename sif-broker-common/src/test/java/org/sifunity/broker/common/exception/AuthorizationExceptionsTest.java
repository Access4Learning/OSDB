// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for authorization exception classes.
 * Tests InsufficientPermissionsException and ZoneAccessDeniedException.
 */
class AuthorizationExceptionsTest {

    // InsufficientPermissionsException Tests

    @Test
    void insufficientPermissionsException_hasCorrectErrorCode() {
        InsufficientPermissionsException exception = new InsufficientPermissionsException("Missing PROVIDE permission");

        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_001");
    }

    @Test
    void insufficientPermissionsException_hasForbiddenStatus() {
        InsufficientPermissionsException exception = new InsufficientPermissionsException("Access denied");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void insufficientPermissionsException_preservesMessage() {
        String message = "Environment lacks CONSUME permission for service StudentPersonal";
        InsufficientPermissionsException exception = new InsufficientPermissionsException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void insufficientPermissionsException_extendsSifBrokerException() {
        InsufficientPermissionsException exception = new InsufficientPermissionsException("Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void insufficientPermissionsException_withProvidePermission() {
        String message = "Environment does not have PROVIDE permission for service StudentPersonal in zone default";
        InsufficientPermissionsException exception = new InsufficientPermissionsException(message);

        assertThat(exception.getMessage()).contains("PROVIDE");
        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void insufficientPermissionsException_withQueryPermission() {
        String message = "Environment does not have QUERY permission for service SchoolInfo";
        InsufficientPermissionsException exception = new InsufficientPermissionsException(message);

        assertThat(exception.getMessage()).contains("QUERY");
        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_001");
    }

    @Test
    void insufficientPermissionsException_withSubscribePermission() {
        String message = "Environment does not have SUBSCRIBE permission for events in zone default";
        InsufficientPermissionsException exception = new InsufficientPermissionsException(message);

        assertThat(exception.getMessage()).contains("SUBSCRIBE");
        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_001");
    }

    @Test
    void insufficientPermissionsException_withRejectedAcl() {
        String message = "ACL entry exists but has REJECTED status";
        InsufficientPermissionsException exception = new InsufficientPermissionsException(message);

        assertThat(exception.getMessage()).contains("REJECTED");
        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_001");
    }

    @Test
    void insufficientPermissionsException_withRequestedAcl() {
        String message = "ACL entry exists but has REQUESTED status, awaiting approval";
        InsufficientPermissionsException exception = new InsufficientPermissionsException(message);

        assertThat(exception.getMessage()).contains("REQUESTED");
        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_001");
    }

    // ZoneAccessDeniedException Tests

    @Test
    void zoneAccessDeniedException_hasCorrectErrorCode() {
        ZoneAccessDeniedException exception = new ZoneAccessDeniedException("Zone access denied");

        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_002");
    }

    @Test
    void zoneAccessDeniedException_hasForbiddenStatus() {
        ZoneAccessDeniedException exception = new ZoneAccessDeniedException("Unauthorized zone");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void zoneAccessDeniedException_preservesMessage() {
        String message = "Environment is not authorized to access zone 'production'";
        ZoneAccessDeniedException exception = new ZoneAccessDeniedException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void zoneAccessDeniedException_extendsSifBrokerException() {
        ZoneAccessDeniedException exception = new ZoneAccessDeniedException("Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void zoneAccessDeniedException_withServiceAccess() {
        String message = "Environment cannot access service StudentPersonal in zone 'restricted'";
        ZoneAccessDeniedException exception = new ZoneAccessDeniedException(message);

        assertThat(exception.getMessage()).contains("zone");
        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_002");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void zoneAccessDeniedException_withQueueCreation() {
        String message = "Environment cannot create queue in zone 'admin'";
        ZoneAccessDeniedException exception = new ZoneAccessDeniedException(message);

        assertThat(exception.getMessage()).contains("queue");
        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_002");
    }

    @Test
    void zoneAccessDeniedException_withEventSubscription() {
        String message = "Environment cannot subscribe to events in zone 'secure'";
        ZoneAccessDeniedException exception = new ZoneAccessDeniedException(message);

        assertThat(exception.getMessage()).contains("subscribe");
        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_002");
    }

    @Test
    void zoneAccessDeniedException_withZonePolicy() {
        String message = "Zone-level access control policy denies access to zone 'private'";
        ZoneAccessDeniedException exception = new ZoneAccessDeniedException(message);

        assertThat(exception.getMessage()).contains("policy");
        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_002");
    }

    // Cross-Exception Tests

    @Test
    void allAuthorizationExceptions_haveForbiddenStatus() {
        InsufficientPermissionsException permissionsException = new InsufficientPermissionsException("msg");
        ZoneAccessDeniedException zoneException = new ZoneAccessDeniedException("msg");

        assertThat(permissionsException.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(zoneException.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void allAuthorizationExceptions_haveUniqueErrorCodes() {
        InsufficientPermissionsException permissionsException = new InsufficientPermissionsException("msg");
        ZoneAccessDeniedException zoneException = new ZoneAccessDeniedException("msg");

        assertThat(permissionsException.getErrorCode()).isEqualTo("AUTHZ_001");
        assertThat(zoneException.getErrorCode()).isEqualTo("AUTHZ_002");

        // Verify codes are unique
        assertThat(permissionsException.getErrorCode()).isNotEqualTo(zoneException.getErrorCode());
    }

    @Test
    void allAuthorizationExceptions_extendSifBrokerException() {
        InsufficientPermissionsException permissionsException = new InsufficientPermissionsException("msg");
        ZoneAccessDeniedException zoneException = new ZoneAccessDeniedException("msg");

        assertThat(permissionsException).isInstanceOf(SifBrokerException.class);
        assertThat(zoneException).isInstanceOf(SifBrokerException.class);
    }

    @Test
    void allAuthorizationExceptions_areRuntimeExceptions() {
        InsufficientPermissionsException permissionsException = new InsufficientPermissionsException("msg");
        ZoneAccessDeniedException zoneException = new ZoneAccessDeniedException("msg");

        assertThat(permissionsException).isInstanceOf(RuntimeException.class);
        assertThat(zoneException).isInstanceOf(RuntimeException.class);
    }

    @Test
    void authorizationExceptions_canBeDistinguishedByErrorCode() {
        // Simulate catching a generic SifBrokerException and identifying the specific type
        SifBrokerException permissionsException = new InsufficientPermissionsException("Insufficient permissions");
        SifBrokerException zoneException = new ZoneAccessDeniedException("Zone access denied");

        // Can identify exception type by error code
        assertThat(permissionsException.getErrorCode()).startsWith("AUTHZ_");
        assertThat(zoneException.getErrorCode()).startsWith("AUTHZ_");

        // Can distinguish between specific authorization errors
        if (permissionsException.getErrorCode().equals("AUTHZ_001")) {
            assertThat(permissionsException).isInstanceOf(InsufficientPermissionsException.class);
        }
        if (zoneException.getErrorCode().equals("AUTHZ_002")) {
            assertThat(zoneException).isInstanceOf(ZoneAccessDeniedException.class);
        }
    }

    @Test
    void authorizationExceptions_supportDetailedMessages() {
        String detailedPermissionsMessage = "Environment 'env-123' lacks PROVIDE permission for service 'StudentPersonal' in zone 'default' context 'school-district'";
        String detailedZoneMessage = "Environment 'env-456' attempted to access zone 'production' but only has access to zones: [default, test]";

        InsufficientPermissionsException permissionsException = new InsufficientPermissionsException(detailedPermissionsMessage);
        ZoneAccessDeniedException zoneException = new ZoneAccessDeniedException(detailedZoneMessage);

        assertThat(permissionsException.getMessage()).contains("env-123");
        assertThat(permissionsException.getMessage()).contains("StudentPersonal");
        assertThat(zoneException.getMessage()).contains("env-456");
        assertThat(zoneException.getMessage()).contains("production");
    }

    @Test
    void authorizationExceptions_distinguishFromAuthenticationExceptions() {
        // Authorization exceptions (403) are different from authentication exceptions (401)
        InsufficientPermissionsException authzException = new InsufficientPermissionsException("msg");
        InvalidTokenException authnException = new InvalidTokenException("msg");

        // Different HTTP status codes
        assertThat(authzException.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authnException.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Different error code prefixes
        assertThat(authzException.getErrorCode()).startsWith("AUTHZ_");
        assertThat(authnException.getErrorCode()).startsWith("AUTH_");
    }

    @Test
    void insufficientPermissionsException_withMultipleAccessTypes() {
        String message = "Environment requires both PROVIDE and QUERY permissions but only has QUERY";
        InsufficientPermissionsException exception = new InsufficientPermissionsException(message);

        assertThat(exception.getMessage()).contains("PROVIDE");
        assertThat(exception.getMessage()).contains("QUERY");
        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_001");
    }

    @Test
    void zoneAccessDeniedException_withMultipleZones() {
        String message = "Environment attempted to access zones [zone1, zone2] but only authorized for [default]";
        ZoneAccessDeniedException exception = new ZoneAccessDeniedException(message);

        assertThat(exception.getMessage()).contains("zone1");
        assertThat(exception.getMessage()).contains("zone2");
        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_002");
    }

    @Test
    void authorizationExceptions_indicateAuthenticatedButUnauthorized() {
        // These exceptions should only be thrown after successful authentication
        // They indicate the user is authenticated but lacks authorization
        InsufficientPermissionsException permissionsException = new InsufficientPermissionsException(
            "Authenticated environment lacks required permissions"
        );
        ZoneAccessDeniedException zoneException = new ZoneAccessDeniedException(
            "Authenticated environment not authorized for this zone"
        );

        assertThat(permissionsException.getMessage()).contains("Authenticated");
        assertThat(zoneException.getMessage()).contains("Authenticated");
        assertThat(permissionsException.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(zoneException.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
