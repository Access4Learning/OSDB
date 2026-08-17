// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MembershipValidationFilter}.
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>ADMIN bypass</li>
 *   <li>Domain management path bypass</li>
 *   <li>Solution management path — domain-only validation</li>
 *   <li>Resource path — domain + solution validation</li>
 *   <li>403 responses with correct error codes</li>
 * </ul>
 *
 * <p>Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8</p>
 */
@DisplayName("MembershipValidationFilter Tests")
class MembershipValidationFilterTest {

    private MembershipValidationFilter filter;
    private GatewayFilterChain mockChain;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        filter = new MembershipValidationFilter(objectMapper);
        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    // ========== Order Tests ==========

    @Test
    @DisplayName("getOrder should return 5")
    void getOrder_Returns4() {
        assertThat(filter.getOrder()).isEqualTo(5);
    }

    // ========== ADMIN Bypass Tests ==========

    @Test
    @DisplayName("filter should skip validation for global admin environments")
    void filter_SkipsValidation_ForGlobalAdminEnvironment() {
        MockServerWebExchange exchange = buildExchange("/iowa/production/environments");
        exchange.getAttributes().put("environmentType", "BROKERED");
        exchange.getAttributes().put("tokenDomainId", "GLOBAL_DOMAIN");
        exchange.getAttributes().put("tokenSolutionId", "GLOBAL_SOLUTION");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();
        verify(mockChain).filter(any(ServerWebExchange.class));
    }

    // ========== Domain Management Path Bypass ==========

    @Test
    @DisplayName("filter should skip validation for /domains paths")
    void filter_SkipsValidation_ForDomainsPath() {
        MockServerWebExchange exchange = buildExchange("/domains/iowa");
        // No environmentType — simulates exempt (unauthenticated) path

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();
        verify(mockChain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("filter should skip validation for /domains root")
    void filter_SkipsValidation_ForDomainsRoot() {
        MockServerWebExchange exchange = buildExchange("/domains");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();
        verify(mockChain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("filter should skip validation for /actuator/health")
    void filter_SkipsValidation_ForActuatorHealth() {
        MockServerWebExchange exchange = buildExchange("/actuator/health");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();
        verify(mockChain).filter(any(ServerWebExchange.class));
    }

    // ========== No EnvironmentType (Exempt Path) ==========

    @Test
    @DisplayName("filter should skip validation when environmentType is not set")
    void filter_SkipsValidation_WhenNoEnvironmentType() {
        MockServerWebExchange exchange = buildExchange("/iowa/production/environments");
        // No environmentType attribute set

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();
        verify(mockChain).filter(any(ServerWebExchange.class));
    }

    // ========== Solution Management — Domain-Only Validation ==========

    @Test
    @DisplayName("filter should allow solution path when domain matches")
    void filter_AllowsSolutionPath_WhenDomainMatches() {
        MockServerWebExchange exchange = buildExchange("/iowa/solutions");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("tokenDomainId", "iowa");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();
        verify(mockChain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("filter should reject solution path when domain does not match")
    void filter_RejectsSolutionPath_WhenDomainMismatch() {
        MockServerWebExchange exchange = buildExchange("/iowa/solutions");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("tokenDomainId", "maine");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertResponseContainsErrorCode(exchange, "MEMBERSHIP_001");
    }

    @Test
    @DisplayName("filter should reject solution path when tokenDomainId is null")
    void filter_RejectsSolutionPath_WhenTokenDomainIdNull() {
        MockServerWebExchange exchange = buildExchange("/iowa/solutions/prod");
        exchange.getAttributes().put("environmentType", EnvironmentType.DIRECT);
        // tokenDomainId not set

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ========== Resource Path — Domain + Solution Validation ==========

    @Test
    @DisplayName("filter should allow resource path when both domain and solution match")
    void filter_AllowsResourcePath_WhenBothMatch() {
        MockServerWebExchange exchange = buildExchange("/iowa/production/environments");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("tokenDomainId", "iowa");
        exchange.getAttributes().put("tokenSolutionId", "production");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();
        verify(mockChain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("filter should reject resource path when domain does not match")
    void filter_RejectsResourcePath_WhenDomainMismatch() {
        MockServerWebExchange exchange = buildExchange("/iowa/production/queues");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("tokenDomainId", "maine");
        exchange.getAttributes().put("tokenSolutionId", "production");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertResponseContainsErrorCode(exchange, "MEMBERSHIP_001");
    }

    @Test
    @DisplayName("filter should reject resource path when solution does not match")
    void filter_RejectsResourcePath_WhenSolutionMismatch() {
        MockServerWebExchange exchange = buildExchange("/iowa/production/queues");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("tokenDomainId", "iowa");
        exchange.getAttributes().put("tokenSolutionId", "staging");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertResponseContainsErrorCode(exchange, "MEMBERSHIP_002");
    }

    @Test
    @DisplayName("filter should reject resource path when both domain and solution mismatch")
    void filter_RejectsResourcePath_WhenBothMismatch() {
        MockServerWebExchange exchange = buildExchange("/iowa/production/environments");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("tokenDomainId", "maine");
        exchange.getAttributes().put("tokenSolutionId", "staging");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        // Domain check fails first → MEMBERSHIP_001
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertResponseContainsErrorCode(exchange, "MEMBERSHIP_001");
    }

    @Test
    @DisplayName("filter should reject resource path when tokenSolutionId is null")
    void filter_RejectsResourcePath_WhenTokenSolutionIdNull() {
        MockServerWebExchange exchange = buildExchange("/iowa/production/environments");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("tokenDomainId", "iowa");
        // tokenSolutionId not set

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertResponseContainsErrorCode(exchange, "MEMBERSHIP_002");
    }

    // ========== Helpers ==========

    private MockServerWebExchange buildExchange(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        return MockServerWebExchange.from(request);
    }

    private void assertResponseContainsErrorCode(MockServerWebExchange exchange, String expectedCode) {
        // Read the response body written by the filter
        byte[] body = exchange.getResponse().getBodyAsString()
                .map(s -> s.getBytes())
                .block();
        assertThat(body).isNotNull();
        try {
            SifErrorResponse errorResponse = objectMapper.readValue(body, SifErrorResponseEnvelope.class).error();
            assertThat(errorResponse.subCode()).isEqualTo(expectedCode);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse error response body", e);
        }
    }
}
