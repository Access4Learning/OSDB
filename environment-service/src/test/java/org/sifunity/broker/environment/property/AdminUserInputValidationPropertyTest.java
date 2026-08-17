// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

// Feature: admin-user-management, Property 3: Input validation correctness

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.AdminUserCreateRequest;
import org.sifunity.broker.environment.repository.AdminUserRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.AdminUserService;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for input validation correctness.
 *
 * <p><b>Validates: Requirements 1.7, 1.8, 1.9</b></p>
 *
 * <p>Property 3: For any string {@code s}, the username validation should accept {@code s}
 * if and only if {@code s} matches the regex {@code ^[a-zA-Z0-9_-]{3,128}$}.
 * For any string {@code p}, the password validation should accept {@code p}
 * if and only if {@code p.length() >= 8 && p.length() <= 72}.</p>
 */
class AdminUserInputValidationPropertyTest {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,128}$");
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 72;

    private AdminUserService createServiceWithMocks() {
        AdminUserRepository adminUserRepository = mock(AdminUserRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);

        // Set up domain and solution to exist so validation is the only gate
        Domain domain = mock(Domain.class);
        when(domain.getId()).thenReturn(UUID.randomUUID());
        Solution solution = mock(Solution.class);
        when(solution.getId()).thenReturn(UUID.randomUUID());

        when(domainRepository.findByDomainId(any(String.class))).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(any(UUID.class), any(String.class))).thenReturn(Optional.of(solution));
        when(adminUserRepository.existsByUsernameAndDomain_IdAndSolution_Id(any(), any(), any())).thenReturn(false);

        return new AdminUserService(adminUserRepository, domainRepository, solutionRepository);
    }

    // ========== Username Validation Properties ==========

    /**
     * Property 3a: Valid usernames are accepted.
     *
     * <p>For any string matching {@code ^[a-zA-Z0-9_-]{3,128}$}, the service
     * should NOT throw an IllegalArgumentException for the username.</p>
     *
     * <p><b>Validates: Requirements 1.7</b></p>
     */
    @Property(tries = 100)
    void validUsernamesAreAccepted(@ForAll("validUsernames") String username) {
        AdminUserService service = createServiceWithMocks();
        String domainId = "test-domain-" + UUID.randomUUID();
        String solutionId = "test-solution-" + UUID.randomUUID();
        String validPassword = "validPass1";

        AdminUserCreateRequest request = new AdminUserCreateRequest(
                username, validPassword, domainId, solutionId);

        // Should not throw IllegalArgumentException for username validation
        // (may throw other exceptions from mocked save, but not validation)
        try {
            service.createUser(request);
        } catch (IllegalArgumentException e) {
            fail("Valid username should not throw IllegalArgumentException: " + e.getMessage());
        } catch (Exception e) {
            // Other exceptions (e.g., from mocked dependencies) are acceptable
        }
    }

    /**
     * Property 3b: Invalid usernames are rejected.
     *
     * <p>For any string NOT matching {@code ^[a-zA-Z0-9_-]{3,128}$}, the service
     * should throw an IllegalArgumentException indicating username validation failure.</p>
     *
     * <p><b>Validates: Requirements 1.7, 1.9</b></p>
     */
    @Property(tries = 100)
    void invalidUsernamesAreRejected(@ForAll("invalidUsernames") String username) {
        AdminUserService service = createServiceWithMocks();
        String domainId = "test-domain-" + UUID.randomUUID();
        String solutionId = "test-solution-" + UUID.randomUUID();
        String validPassword = "validPass1";

        AdminUserCreateRequest request = new AdminUserCreateRequest(
                username, validPassword, domainId, solutionId);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username must be 3-128 characters");
    }

    /**
     * Property 3c: Valid passwords are accepted.
     *
     * <p>For any string with length between 8 and 72 inclusive, the service
     * should NOT throw an IllegalArgumentException for the password.</p>
     *
     * <p><b>Validates: Requirements 1.8</b></p>
     */
    @Property(tries = 100)
    void validPasswordsAreAccepted(@ForAll("validPasswords") String password) {
        AdminUserService service = createServiceWithMocks();
        String domainId = "test-domain-" + UUID.randomUUID();
        String solutionId = "test-solution-" + UUID.randomUUID();
        String validUsername = "valid_user";

        AdminUserCreateRequest request = new AdminUserCreateRequest(
                validUsername, password, domainId, solutionId);

        // Should not throw IllegalArgumentException for password validation
        try {
            service.createUser(request);
        } catch (IllegalArgumentException e) {
            fail("Valid password should not throw IllegalArgumentException: " + e.getMessage());
        } catch (Exception e) {
            // Other exceptions (e.g., from mocked dependencies) are acceptable
        }
    }

    /**
     * Property 3d: Invalid passwords are rejected.
     *
     * <p>For any string with length less than 8 or greater than 72, the service
     * should throw an IllegalArgumentException indicating password validation failure.</p>
     *
     * <p><b>Validates: Requirements 1.8, 1.9</b></p>
     */
    @Property(tries = 100)
    void invalidPasswordsAreRejected(@ForAll("invalidPasswords") String password) {
        AdminUserService service = createServiceWithMocks();
        String domainId = "test-domain-" + UUID.randomUUID();
        String solutionId = "test-solution-" + UUID.randomUUID();
        String validUsername = "valid_user";

        AdminUserCreateRequest request = new AdminUserCreateRequest(
                validUsername, password, domainId, solutionId);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password must be 8-72 characters");
    }

    /**
     * Property 3e: Null username is rejected.
     *
     * <p><b>Validates: Requirements 1.9</b></p>
     */
    @Property(tries = 1)
    void nullUsernameIsRejected() {
        AdminUserService service = createServiceWithMocks();
        String domainId = "test-domain-" + UUID.randomUUID();
        String solutionId = "test-solution-" + UUID.randomUUID();

        AdminUserCreateRequest request = new AdminUserCreateRequest(
                null, "validPass1", domainId, solutionId);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username must be 3-128 characters");
    }

    /**
     * Property 3f: Null password is rejected.
     *
     * <p><b>Validates: Requirements 1.9</b></p>
     */
    @Property(tries = 1)
    void nullPasswordIsRejected() {
        AdminUserService service = createServiceWithMocks();
        String domainId = "test-domain-" + UUID.randomUUID();
        String solutionId = "test-solution-" + UUID.randomUUID();

        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "valid_user", null, domainId, solutionId);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password must be 8-72 characters");
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Generates valid usernames matching {@code ^[a-zA-Z0-9_-]{3,128}$}.
     */
    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(3)
                .ofMaxLength(128)
                .filter(s -> USERNAME_PATTERN.matcher(s).matches());
    }

    /**
     * Generates invalid usernames that do NOT match {@code ^[a-zA-Z0-9_-]{3,128}$}.
     *
     * <p>Covers: too short (length 1-2), too long (length 129+), invalid characters
     * (spaces, special chars), and empty strings.</p>
     */
    @Provide
    Arbitrary<String> invalidUsernames() {
        Arbitrary<String> tooShort = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(2);

        Arbitrary<String> tooLong = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(129)
                .ofMaxLength(140);

        Arbitrary<String> withInvalidChars = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars(' ', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '.', ',')
                .ofMinLength(3)
                .ofMaxLength(50)
                .filter(s -> !USERNAME_PATTERN.matcher(s).matches());

        Arbitrary<String> empty = Arbitraries.just("");

        return Arbitraries.oneOf(tooShort, tooLong, withInvalidChars, empty);
    }

    /**
     * Generates valid passwords with length between 8 and 72 inclusive.
     */
    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings()
                .withCharRange('!', '~')  // printable ASCII
                .ofMinLength(PASSWORD_MIN_LENGTH)
                .ofMaxLength(PASSWORD_MAX_LENGTH);
    }

    /**
     * Generates invalid passwords: too short (length 1-7) or too long (length 73+).
     */
    @Provide
    Arbitrary<String> invalidPasswords() {
        Arbitrary<String> tooShort = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(7);

        Arbitrary<String> tooLong = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(73)
                .ofMaxLength(100);

        return Arbitraries.oneOf(tooShort, tooLong);
    }
}
