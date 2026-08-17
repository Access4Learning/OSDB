// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.gateway.filter.EnvironmentContextFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Integration tests for X-Environment-Id header security enforcement.
 *
 * <p>Verifies that non-admin environments have their client-supplied X-Environment-Id
 * headers stripped and replaced with the authenticated environment ID, while admin
 * and broker-services environments retain impersonation capability.</p>
 *
 * <p><strong>Validates: Requirements 2.3, 3.3</strong></p>
 */
@DisplayName("Security Enforcement Integration Tests - X-Environment-Id Header")
class SecurityEnforcementHeaderIntegrationTest {

    private EnvironmentContextFilter filter;
    private GatewayFilterChain mockChain;

    @BeforeEach
    void setUp() {
        filter = new EnvironmentContextFilter("test-internal-token");
        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("Non-Admin Header Stripping")
    class NonAdminHeaderStripping {

        @Test
        @DisplayName("Non-admin environment: client-supplied X-Environment-Id is stripped and replaced with authenticated ID")
        void nonAdmin_clientHeader_strippedAndReplacedWithAuthenticatedId() {
            // Arrange: Non-admin environment with client-supplied X-Environment-Id header
            UUID authenticatedEnvId = UUID.randomUUID();
            UUID spoofedEnvId = UUID.randomUUID();
            String environmentName = "ConsumerApp";

            MockServerHttpRequest request = MockServerHttpRequest
                .get("/testDomain/testSolution/queues")
                .header(SifHeaders.X_ENVIRONMENT_ID, spoofedEnvId.toString())
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Simulate authenticated environment context (set by AuthenticationFilter)
            exchange.getAttributes().put("environmentId", authenticatedEnvId);
            exchange.getAttributes().put("environmentName", environmentName);
            // Non-admin: no special fingerprint

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

            // Assert: Backend receives only the authenticated environment's ID, not the spoofed one
            verify(mockChain).filter(argThat(mutatedExchange -> {
                ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
                String receivedEnvId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID);

                assertThat(receivedEnvId)
                    .as("Backend should receive authenticated env ID, not client-supplied spoofed ID")
                    .isEqualTo(authenticatedEnvId.toString());
                assertThat(receivedEnvId)
                    .as("Spoofed environment ID should NOT pass through")
                    .isNotEqualTo(spoofedEnvId.toString());
                return true;
            }));
        }

        @Test
        @DisplayName("Non-admin environment without client-supplied header: authenticated ID is injected")
        void nonAdmin_noClientHeader_authenticatedIdInjected() {
            // Arrange: Non-admin environment without any X-Environment-Id header
            UUID authenticatedEnvId = UUID.randomUUID();
            String environmentName = "ProviderApp";

            MockServerHttpRequest request = MockServerHttpRequest
                .get("/testDomain/testSolution/queues")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            exchange.getAttributes().put("environmentId", authenticatedEnvId);
            exchange.getAttributes().put("environmentName", environmentName);

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

            // Assert: Backend receives the authenticated environment's ID
            verify(mockChain).filter(argThat(mutatedExchange -> {
                ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
                String receivedEnvId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID);

                assertThat(receivedEnvId)
                    .as("Backend should receive authenticated environment ID")
                    .isEqualTo(authenticatedEnvId.toString());
                return true;
            }));
        }
    }

    @Nested
    @DisplayName("Admin/Broker-Services Impersonation Preserved")
    class AdminImpersonationPreserved {

        @Test
        @DisplayName("Admin environment: impersonation works via exchange attributes (ProxyImpersonationFilter sets attributes)")
        void admin_impersonationViaAttributes_works() {
            // Arrange: After ProxyImpersonationFilter runs, the exchange attributes
            // are overridden to the target environment's values.
            // EnvironmentContextFilter then injects those values as headers.
            UUID targetEnvId = UUID.randomUUID();
            String targetEnvName = "broker-services-target";

            MockServerHttpRequest request = MockServerHttpRequest
                .get("/testDomain/testSolution/queues")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Simulate what ProxyImpersonationFilter does for admin impersonation:
            // it overrides exchange attributes to the impersonation target
            exchange.getAttributes().put("environmentId", targetEnvId);
            exchange.getAttributes().put("environmentName", targetEnvName);
            exchange.getAttributes().put("fingerprint", "broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

            // Assert: Backend receives the impersonated (target) environment's ID
            verify(mockChain).filter(argThat(mutatedExchange -> {
                ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
                String receivedEnvId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID);
                String receivedFingerprint = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_FINGERPRINT);

                assertThat(receivedEnvId)
                    .as("Backend should receive impersonated environment ID")
                    .isEqualTo(targetEnvId.toString());
                assertThat(receivedFingerprint)
                    .as("Backend should receive broker-services fingerprint")
                    .isEqualTo("broker-services");
                return true;
            }));
        }

        @Test
        @DisplayName("Broker-services environment: fingerprint header is preserved")
        void brokerServices_fingerprintPreserved() {
            // Arrange: broker-services environment making a request
            UUID brokerEnvId = UUID.randomUUID();
            String brokerEnvName = "broker-services";

            MockServerHttpRequest request = MockServerHttpRequest
                .get("/testDomain/testSolution/queues")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            exchange.getAttributes().put("environmentId", brokerEnvId);
            exchange.getAttributes().put("environmentName", brokerEnvName);
            exchange.getAttributes().put("fingerprint", "broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

            // Assert: Backend receives broker-services fingerprint and correct ID
            verify(mockChain).filter(argThat(mutatedExchange -> {
                ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
                String receivedEnvId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID);
                String receivedFingerprint = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_FINGERPRINT);

                assertThat(receivedEnvId).isEqualTo(brokerEnvId.toString());
                assertThat(receivedFingerprint).isEqualTo("broker-services");
                return true;
            }));
        }
    }

    @Nested
    @DisplayName("Exempt Paths - Header Stripping")
    class ExemptPathsHeaderStripping {

        @Test
        @DisplayName("Exempt path (no env context): all internal headers stripped even if client supplies them")
        void exemptPath_clientHeaders_allStripped() {
            // Arrange: Request on exempt path with client-supplied internal headers
            UUID spoofedEnvId = UUID.randomUUID();

            MockServerHttpRequest request = MockServerHttpRequest
                .post("/environments")
                .header(SifHeaders.X_ENVIRONMENT_ID, spoofedEnvId.toString())
                .header(SifHeaders.X_ENVIRONMENT_NAME, "SpoofedApp")
                .header(SifHeaders.X_ENVIRONMENT_FINGERPRINT, "broker-services")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // No environment context set (exempt path)

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

            // Assert: All internal headers are stripped
            verify(mockChain).filter(argThat(mutatedExchange -> {
                ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();

                assertThat(mutatedRequest.getHeaders().containsKey(SifHeaders.X_ENVIRONMENT_ID))
                    .as("X-Environment-Id should be stripped on exempt paths")
                    .isFalse();
                assertThat(mutatedRequest.getHeaders().containsKey(SifHeaders.X_ENVIRONMENT_NAME))
                    .as("X-Environment-Name should be stripped on exempt paths")
                    .isFalse();
                assertThat(mutatedRequest.getHeaders().containsKey(SifHeaders.X_ENVIRONMENT_FINGERPRINT))
                    .as("X-Environment-Fingerprint should be stripped on exempt paths")
                    .isFalse();
                return true;
            }));
        }
    }
}
