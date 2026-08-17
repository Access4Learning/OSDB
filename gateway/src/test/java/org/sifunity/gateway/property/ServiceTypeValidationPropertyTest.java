// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.constant.SifServiceType;
import org.sifunity.gateway.filter.ServiceTypePathMapping;
import org.sifunity.gateway.filter.ServiceTypePathMapping.PathCategory;
import org.sifunity.gateway.filter.ServiceTypeValidationFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for {@link ServiceTypeValidationFilter}.
 *
 * <p>Each property method validates a correctness property from the design document
 * using jqwik generative testing.</p>
 *
 * @see ServiceTypeValidationFilter
 * @see ServiceTypePathMapping
 */
class ServiceTypeValidationPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Property 1: Valid serviceType + matching path always passes through.
     *
     * <p><b>Validates: Requirements 1.3, 2.3, 3.2, 4.3</b></p>
     *
     * <p>For any URL path and for any serviceType value that is in the valid set
     * for that path's category, the ServiceTypeValidationFilter SHALL allow the
     * request to proceed (return the chain filter result, not a 400 error).</p>
     */
    @Property(tries = 20)
    @Label("Property 1: Valid serviceType + matching path always passes through")
    void validServiceTypeWithMatchingPathAlwaysPassesThrough(
            @ForAll("validServiceTypeWithPath") Tuple.Tuple2<String, String> serviceTypeAndPath) {

        String serviceType = serviceTypeAndPath.get1();
        String path = serviceTypeAndPath.get2();

        // --- Arrange ---
        ServiceTypeValidationFilter filter = new ServiceTypeValidationFilter(objectMapper);

        MockServerHttpRequest request = MockServerHttpRequest.get(path)
                .header(SifHeaders.SERVICE_TYPE, serviceType)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert: chain was invoked (request passed through, not rejected) ---
        verify(mockChain).filter(exchange);
        // Response status should NOT be 400
        assertThat(exchange.getResponse().getStatusCode())
                .as("Valid serviceType '%s' on path '%s' should pass through (not 400)", serviceType, path)
                .isNotEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Property 3: Absent serviceType header always passes through.
     *
     * <p><b>Validates: Requirements 1.2, 2.2, 3.3, 4.2</b></p>
     *
     * <p>For any URL path (regardless of path category), when the serviceType
     * header is absent or blank, the filter SHALL allow the request to proceed
     * (chain is invoked, not a 400 response).</p>
     */
    @Property(tries = 20)
    @Label("Property 3: Absent serviceType header always passes through")
    void absentServiceTypeAlwaysPassesThrough(
            @ForAll("urlPaths") String path,
            @ForAll("absentServiceTypeValues") String serviceTypeValue) {

        // --- Arrange ---
        ServiceTypeValidationFilter filter = new ServiceTypeValidationFilter(objectMapper);

        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest.get(path);
        if (serviceTypeValue != null) {
            // Blank string — set header to blank value
            requestBuilder.header(SifHeaders.SERVICE_TYPE, serviceTypeValue);
        }
        // If serviceTypeValue is null, don't set header at all (absent)

        MockServerHttpRequest request = requestBuilder.build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        // Chain must have been invoked (request passed through)
        verify(mockChain).filter(exchange);
    }

    /**
     * Property 4: Case-insensitive matching.
     *
     * <p><b>Validates: Requirements 1.4, 10.4</b></p>
     *
     * <p>For any valid serviceType value and for any case variation of that value
     * (uppercase, lowercase, mixed), the filter SHALL treat them equivalently —
     * accepting the value if the canonical uppercase form is in the valid set.</p>
     */
    @Property(tries = 20)
    @Label("Property 4: Case-insensitive matching always passes")
    void caseInsensitiveMatchingAlwaysPasses(
            @ForAll("caseScrambledValidServiceTypes") java.util.Map.Entry<ServiceTypePathMapping.PathCategory, String> categoryAndScrambled) {

        // --- Arrange ---
        ServiceTypePathMapping.PathCategory category = categoryAndScrambled.getKey();
        String scrambledServiceType = categoryAndScrambled.getValue();

        ServiceTypeValidationFilter filter = new ServiceTypeValidationFilter(objectMapper);

        // Pick a representative path for the category
        String resourcePath = switch (category) {
            case REQUESTS -> "/requests/Students";
            case SERVICES -> "/services/StudentLocator";
            case EVENTS -> "/events";
            case UTILITY -> "/zones";
        };

        String url = "/domainId/solutionId" + resourcePath;

        MockServerHttpRequest request = MockServerHttpRequest.get(url)
                .header(SifHeaders.SERVICE_TYPE, scrambledServiceType)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        // Chain must have been invoked (request passed through, no 400 error)
        verify(mockChain).filter(exchange);
    }

    /**
     * Property 2: Invalid serviceType + path always produces HTTP 400.
     *
     * <p><b>Validates: Requirements 1.1, 2.1, 3.1, 4.1</b></p>
     *
     * <p>For any URL path that maps to a known path category, and for any
     * serviceType value that is a recognized SIF serviceType but NOT in the
     * valid set for that category, the filter SHALL reject with HTTP 400.</p>
     */
    @Property(tries = 20)
    @Label("Property 2: Invalid serviceType + path always produces HTTP 400")
    void invalidServiceTypeForPathAlwaysReturns400(
            @ForAll("invalidServiceTypeWithPath") Tuple.Tuple2<String, String> pathAndServiceType) {

        String path = pathAndServiceType.get1();
        String serviceType = pathAndServiceType.get2();

        // --- Arrange ---
        ServiceTypeValidationFilter filter = new ServiceTypeValidationFilter(objectMapper);

        MockServerHttpRequest request = MockServerHttpRequest.get(path)
                .header(SifHeaders.SERVICE_TYPE, serviceType)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        // Chain must NOT have been invoked (request rejected)
        verify(mockChain, never()).filter(any());
        // Response status must be 400 BAD REQUEST
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Property 5: Unrecognized serviceType value produces HTTP 400 with full SIF value list.
     *
     * <p><b>Validates: Requirements 5.2, 5.3</b></p>
     *
     * <p>For any string that is not one of the six valid SIF serviceType values
     * (case-insensitive), and for any URL path that maps to a known category,
     * the filter SHALL reject with HTTP 400 and the error response description
     * SHALL contain all six valid SIF serviceType values.</p>
     */
    @Property(tries = 20)
    @Label("Property 5: Unrecognized serviceType value produces HTTP 400 with full SIF value list")
    void unrecognizedServiceTypeProduces400WithFullValueList(
            @ForAll("unrecognizedServiceTypes") String unrecognizedValue,
            @ForAll("categorizedPaths") String path) {

        // --- Arrange ---
        ServiceTypeValidationFilter filter = new ServiceTypeValidationFilter(objectMapper);

        MockServerHttpRequest request = MockServerHttpRequest.get(path)
                .header(SifHeaders.SERVICE_TYPE, unrecognizedValue)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        // Chain must NOT have been invoked (request rejected)
        verify(mockChain, never()).filter(any());
        // Response status must be 400 BAD REQUEST
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Extract response body and verify description contains all six SIF values
        byte[] responseBytes = exchange.getResponse().getBodyAsString()
                .map(String::getBytes).block();
        assertThat(responseBytes).isNotNull();

        SifErrorResponse errorResponse;
        try {
            errorResponse = objectMapper.readValue(responseBytes, SifErrorResponseEnvelope.class).error();
        } catch (Exception e) {
            throw new AssertionError("Failed to deserialize error response", e);
        }

        assertThat(errorResponse.subCode()).isEqualTo("SVC_TYPE_002");
        // Description must contain all six SIF service type values
        assertThat(errorResponse.description())
                .contains("OBJECT")
                .contains("UTILITY")
                .contains("FUNCTIONAL")
                .contains("SERVICEPATH")
                .contains("XQUERYTEMPLATE")
                .contains("SERVICE");
    }

    // ========== Arbitraries ==========

    /**
     * Generates URL paths covering all categories and uncategorized paths.
     * Format: /{domainId}/{solutionId}/{resourcePath}
     */
    @Provide
    Arbitrary<String> urlPaths() {
        Arbitrary<String> domainId = Arbitraries.of("iowa", "texas", "domain1");
        Arbitrary<String> solutionId = Arbitraries.of("prod", "staging", "test");

        // Categorized resource paths
        Arbitrary<String> requestsPaths = Arbitraries.of(
                "/requests/Students", "/requests/SchoolInfos", "/requests/StaffPersonals");
        Arbitrary<String> servicesPaths = Arbitraries.of(
                "/services/StudentLocator", "/services/Enrollment");
        Arbitrary<String> eventsPaths = Arbitraries.of(
                "/events", "/events/Students");
        Arbitrary<String> utilityPaths = Arbitraries.of(
                "/zones", "/providers", "/xquerys", "/alerts", "/namespaces", "/codesets");

        // Uncategorized paths (no validation applies)
        Arbitrary<String> uncategorizedPaths = Arbitraries.of(
                "/environments", "/subscriptions", "/queues", "/something-else");

        Arbitrary<String> resourcePath = Arbitraries.oneOf(
                requestsPaths, servicesPaths, eventsPaths, utilityPaths, uncategorizedPaths);

        return Combinators.combine(domainId, solutionId, resourcePath)
                .as((d, s, r) -> "/" + d + "/" + s + r);
    }

    /**
     * Generates absent/blank serviceType values — null or blank strings.
     * Null means header not set at all; blank means header set to empty/whitespace.
     */
    @Provide
    Arbitrary<String> absentServiceTypeValues() {
        return Arbitraries.of(null, "", "   ", "\t", " \n ");
    }

    /**
     * Generates a random PathCategory paired with a valid serviceType for that category,
     * with its case randomly scrambled (e.g., "OBJECT" → "oBjEcT", "object", "Object").
     */
    @Provide
    Arbitrary<java.util.Map.Entry<ServiceTypePathMapping.PathCategory, String>> caseScrambledValidServiceTypes() {
        return Arbitraries.of(ServiceTypePathMapping.PathCategory.values())
                .flatMap(category -> {
                    String[] validTypes = category.getValidServiceTypes().toArray(new String[0]);
                    return Arbitraries.of(validTypes)
                            .flatMap(serviceType -> scrambleCase(serviceType)
                                    .map(scrambled -> java.util.Map.entry(category, scrambled)));
                });
    }

    /**
     * Scrambles the case of each character in the input string randomly.
     */
    private Arbitrary<String> scrambleCase(String input) {
        if (input.isEmpty()) {
            return Arbitraries.just(input);
        }
        // For each character, randomly choose uppercase or lowercase
        Arbitrary<Boolean> boolArb = Arbitraries.of(true, false);
        return boolArb.list().ofSize(input.length()).map(booleans -> {
            StringBuilder sb = new StringBuilder(input.length());
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                sb.append(booleans.get(i) ? Character.toUpperCase(c) : Character.toLowerCase(c));
            }
            return sb.toString();
        });
    }

    /**
     * Generates a Tuple2 of (validServiceType, fullPath) where the serviceType
     * is a valid value for the path's category.
     *
     * <p>For each PathCategory, picks a random valid serviceType from that
     * category's allowed set and pairs it with a matching URL path.</p>
     */
    @Provide
    Arbitrary<Tuple.Tuple2<String, String>> validServiceTypeWithPath() {
        Arbitrary<String> domainId = Arbitraries.of("iowa", "texas", "domain1", "testDomain");
        Arbitrary<String> solutionId = Arbitraries.of("prod", "staging", "testSolution");

        return Arbitraries.of(PathCategory.values()).flatMap(category -> {
            // Pick a valid serviceType for this category
            String[] validTypes = category.getValidServiceTypes().toArray(new String[0]);
            Arbitrary<String> serviceType = Arbitraries.of(validTypes);

            // Pick a resource path matching this category
            Arbitrary<String> resourcePath = switch (category) {
                case REQUESTS -> Arbitraries.of(
                        "/requests/Students", "/requests/SchoolInfos", "/requests/StaffPersonals");
                case SERVICES -> Arbitraries.of(
                        "/services/StudentLocator", "/services/Enrollment", "/services/TranscriptService");
                case EVENTS -> Arbitraries.of(
                        "/events", "/events/Students", "/events/StaffPersonals");
                case UTILITY -> Arbitraries.of(
                        "/zones", "/providers", "/xquerys", "/alerts", "/namespaces", "/codesets");
            };

            return Combinators.combine(serviceType, domainId, solutionId, resourcePath)
                    .as((st, d, s, r) -> Tuple.of(st, "/" + d + "/" + s + r));
        });
    }

    /**
     * Property 6: Path categorization is deterministic and total over known patterns.
     *
     * <p><b>Validates: Requirements 1.1, 2.1, 3.1, 4.1</b></p>
     *
     * <p>For any resource path starting with a known prefix (/requests, /services,
     * /events, /zones, /providers, /xquerys, /alerts, /namespaces, /codesets),
     * {@code ServiceTypePathMapping.categorize()} SHALL return a non-null PathCategory.
     * For all other resource paths, it SHALL return null.</p>
     */
    @Property(tries = 20)
    @Label("Property 6: Known pattern paths always categorize to non-null")
    void pathCategorizationIsDeterministicForKnownPatterns(
            @ForAll("knownPatternPaths") String resourcePath) {

        PathCategory result = ServiceTypePathMapping.categorize(resourcePath);

        assertThat(result)
                .as("Path '%s' starts with a known prefix and should categorize to non-null", resourcePath)
                .isNotNull();
    }

    /**
     * Property 6b: Unknown paths always categorize to null.
     *
     * <p><b>Validates: Requirements 1.1, 2.1, 3.1, 4.1</b></p>
     *
     * <p>For resource paths that do NOT start with any known prefix,
     * {@code ServiceTypePathMapping.categorize()} SHALL return null
     * (meaning no validation applies).</p>
     */
    @Property(tries = 20)
    @Label("Property 6b: Unknown paths always categorize to null")
    void pathCategorizationReturnsNullForUnknownPaths(
            @ForAll("unknownPatternPaths") String resourcePath) {

        PathCategory result = ServiceTypePathMapping.categorize(resourcePath);

        assertThat(result)
                .as("Path '%s' does not match any known prefix and should categorize to null", resourcePath)
                .isNull();
    }

    /**
     * Generates a Tuple2 of (fullPath, invalidServiceType) where the serviceType
     * is a recognized SIF serviceType that is NOT valid for the path's category.
     *
     * <p>For each PathCategory (excluding EVENTS which accepts all 6 SIF types),
     * picks a SIF serviceType NOT in that category's valid set and pairs it
     * with a matching URL path.</p>
     */
    @Provide
    Arbitrary<Tuple.Tuple2<String, String>> invalidServiceTypeWithPath() {
        Arbitrary<String> domainId = Arbitraries.of("iowa", "texas", "domain1");
        Arbitrary<String> solutionId = Arbitraries.of("prod", "staging", "test");

        // Only use categories that have invalid types (EVENTS accepts all 6, so skip)
        List<PathCategory> restrictiveCategories = Arrays.stream(PathCategory.values())
                .filter(c -> c.getValidServiceTypes().size() < SifServiceType.values().length)
                .collect(Collectors.toList());

        return Arbitraries.of(restrictiveCategories).flatMap(category -> {
            // Pick an invalid serviceType (valid SIF value but NOT in this category's valid set)
            Set<String> validForCategory = category.getValidServiceTypes();
            List<String> invalidTypes = Arrays.stream(SifServiceType.values())
                    .map(Enum::name)
                    .filter(name -> !validForCategory.contains(name))
                    .collect(Collectors.toList());

            Arbitrary<String> serviceType = Arbitraries.of(invalidTypes);

            // Pick a resource path matching this category
            Arbitrary<String> resourcePath = switch (category) {
                case REQUESTS -> Arbitraries.of(
                        "/requests/Students", "/requests/SchoolInfos");
                case SERVICES -> Arbitraries.of(
                        "/services/StudentLocator", "/services/Enrollment");
                case EVENTS -> Arbitraries.of("/events", "/events/Students");
                case UTILITY -> Arbitraries.of(
                        "/zones", "/providers", "/xquerys", "/alerts");
            };

            return Combinators.combine(domainId, solutionId, resourcePath, serviceType)
                    .as((d, s, r, st) -> Tuple.of("/" + d + "/" + s + r, st));
        });
    }

    /**
     * Generates resource paths that start with a known prefix (should categorize to non-null).
     * Covers: /requests, /services, /events, /zones, /providers, /xquerys, /alerts, /namespaces, /codesets
     */
    @Provide
    Arbitrary<String> knownPatternPaths() {
        Arbitrary<String> suffix = Arbitraries.of(
                "", "/Students", "/SchoolInfos", "/Enrollment", "/something");

        Arbitrary<String> knownPrefix = Arbitraries.of(
                "/requests", "/services", "/events",
                "/zones", "/providers", "/xquerys", "/alerts", "/namespaces", "/codesets");

        return Combinators.combine(knownPrefix, suffix).as((prefix, s) -> prefix + s);
    }

    /**
     * Generates resource paths that do NOT start with any known prefix (should categorize to null).
     */
    @Provide
    Arbitrary<String> unknownPatternPaths() {
        return Arbitraries.of(
                "/environments", "/subscriptions", "/queues", "/something-else",
                "/unknown", "/admin", "/health", "/metrics", "/info",
                "/registrations", "/domains", "/solutions");
    }

    /**
     * Generates arbitrary strings that are NOT one of the six valid SIF serviceType values
     * (case-insensitive). Used for Property 5.
     */
    @Provide
    Arbitrary<String> unrecognizedServiceTypes() {
        Set<String> validUpperCase = Arrays.stream(SifServiceType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !validUpperCase.contains(s.toUpperCase()));
    }

    /**
     * Generates full URL paths (/{domainId}/{solutionId}/{resourcePath}) that map
     * to a known PathCategory. Used for Property 5.
     */
    @Provide
    Arbitrary<String> categorizedPaths() {
        Arbitrary<String> domainId = Arbitraries.of("iowa", "texas", "domain1");
        Arbitrary<String> solutionId = Arbitraries.of("prod", "staging", "test");

        Arbitrary<String> resourcePath = Arbitraries.of(
                "/requests/Students", "/requests/SchoolInfos",
                "/services/StudentLocator", "/services/Enrollment",
                "/events", "/events/Students",
                "/zones", "/providers", "/xquerys", "/alerts", "/namespaces", "/codesets");

        return Combinators.combine(domainId, solutionId, resourcePath)
                .as((d, s, r) -> "/" + d + "/" + s + r);
    }
}
