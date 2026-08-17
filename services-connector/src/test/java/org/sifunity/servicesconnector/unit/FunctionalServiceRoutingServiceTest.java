// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.servicesconnector.cache.ProviderRegistryCache;
import org.sifunity.servicesconnector.client.EnvironmentServiceClient;
import org.sifunity.servicesconnector.client.ProviderHttpClient;
import org.sifunity.servicesconnector.client.QueueServiceClient;
import org.sifunity.servicesconnector.exception.NoProviderException;
import org.sifunity.servicesconnector.service.ConsumerQueueOwnershipValidator;
import org.sifunity.servicesconnector.service.DelayedRequestHandler;
import org.sifunity.servicesconnector.service.FunctionalServiceRoutingService;
import org.sifunity.servicesconnector.util.MessageBuilder;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Unit tests for {@link FunctionalServiceRoutingService}.
 *
 * <p>Tests provider lookup, URL construction, IMMEDIATE/DELAYED flow branching,
 * and error handling (no provider, unsupported pattern).</p>
 */
@ExtendWith(MockitoExtension.class)
class FunctionalServiceRoutingServiceTest {

    @Mock
    private ProviderRegistryCache providerCache;

    private TracingUtilsBase tracingUtils;

    private FunctionalServiceRoutingService routingService;

    @BeforeEach
    void setUp() {
        // Create a no-op tracing utils that just executes the supplier
        tracingUtils = mock(TracingUtilsBase.class);
        lenient().when(tracingUtils.traceOperation(anyString(), anyMap(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());

        ProviderHttpClient providerHttpClient = mock(ProviderHttpClient.class);
        DelayedRequestHandler delayedRequestHandler = mock(DelayedRequestHandler.class);
        EnvironmentServiceClient environmentServiceClient = mock(EnvironmentServiceClient.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        ConsumerQueueOwnershipValidator queueOwnershipValidator = mock(ConsumerQueueOwnershipValidator.class);
        routingService = new FunctionalServiceRoutingService(
                providerCache, providerHttpClient, tracingUtils, new MessageBuilder(), delayedRequestHandler, environmentServiceClient, queueServiceClient,
                queueOwnershipValidator);
    }

    // ── URL Construction Tests ──────────────────────────────────────────

    @Nested
    class BuildProviderUrlTests {

        @Test
        void serviceNameOnly() {
            String url = FunctionalServiceRoutingService.buildProviderUrl(
                    "http://provider.example.com", "DataExport", null, null);
            assertThat(url).isEqualTo("http://provider.example.com/DataExport");
        }

        @Test
        void serviceNameWithJobId() {
            String url = FunctionalServiceRoutingService.buildProviderUrl(
                    "http://provider.example.com", "DataExport", "job-123", null);
            assertThat(url).isEqualTo("http://provider.example.com/DataExport/job-123");
        }

        @Test
        void serviceNameWithJobIdAndPhaseName() {
            String url = FunctionalServiceRoutingService.buildProviderUrl(
                    "http://provider.example.com", "DataExport", "job-123", "export");
            assertThat(url).isEqualTo("http://provider.example.com/DataExport/job-123/export");
        }

        @Test
        void serviceNameWithJobIdPhaseAndStates() {
            String url = FunctionalServiceRoutingService.buildProviderUrl(
                    "http://provider.example.com", "DataExport", "job-123", "export/states");
            assertThat(url).isEqualTo("http://provider.example.com/DataExport/job-123/export/states");
        }

        @Test
        void serviceNameWithJobIdPhaseAndStatesState() {
            String url = FunctionalServiceRoutingService.buildProviderUrl(
                    "http://provider.example.com", "DataExport", "job-123", "export/states/state");
            assertThat(url).isEqualTo("http://provider.example.com/DataExport/job-123/export/states/state");
        }

        @Test
        void trailingSlashOnEndpointIsStripped() {
            String url = FunctionalServiceRoutingService.buildProviderUrl(
                    "http://provider.example.com/", "DataExport", null, null);
            assertThat(url).isEqualTo("http://provider.example.com/DataExport");
        }

        @Test
        void blankJobIdIsTreatedAsNull() {
            String url = FunctionalServiceRoutingService.buildProviderUrl(
                    "http://provider.example.com", "DataExport", "  ", null);
            assertThat(url).isEqualTo("http://provider.example.com/DataExport");
        }

        @Test
        void blankPathSuffixIsTreatedAsNull() {
            String url = FunctionalServiceRoutingService.buildProviderUrl(
                    "http://provider.example.com", "DataExport", "job-123", "  ");
            assertThat(url).isEqualTo("http://provider.example.com/DataExport/job-123");
        }
    }

    // ── IMMEDIATE Flow Tests ────────────────────────────────────────────

    @Nested
    class ImmediateFlowTests {

        @Test
        void throwsNoProviderExceptionWhenNoProviderRegistered() {
            when(providerCache.getProvider("domain1", "solution1", "DataExport", "zone1", "ctx1"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> routingService.routeImmediateRequest(
                    "POST", "DataExport", null, null,
                    UUID.randomUUID(), "TestEnv", "domain1", "solution1", "zone1", "ctx1",
                    Map.of("data", "test"), Collections.emptyMap()))
                .isInstanceOf(NoProviderException.class)
                .hasMessageContaining("DataExport")
                .hasMessageContaining("zone1");
        }

        @Test
        void throwsIllegalArgumentWhenProviderDoesNotSupportImmediate() {
            ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DataExport",
                "zone1",
                "ctx1",
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com"),
                ServiceType.FUNCTIONAL,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY))))),
                Instant.now()
        );

            when(providerCache.getProvider("domain1", "solution1", "DataExport", "zone1", "ctx1"))
                    .thenReturn(Optional.of(provider));

            assertThatThrownBy(() -> routingService.routeImmediateRequest(
                    "POST", "DataExport", null, null,
                    UUID.randomUUID(), "TestEnv", "domain1", "solution1", "zone1", "ctx1",
                    Map.of("data", "test"), Collections.emptyMap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IMMEDIATE");
        }

        @Test
        void queriesProviderCacheWithCorrectParameters() {
            when(providerCache.getProvider("domain1", "solution1", "BulkImport", "zoneA", "ctxB"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> routingService.routeImmediateRequest(
                    "POST", "BulkImport", null, null,
                    UUID.randomUUID(), "TestEnv", "domain1", "solution1", "zoneA", "ctxB",
                    null, Collections.emptyMap()))
                .isInstanceOf(NoProviderException.class);

            verify(providerCache).getProvider("domain1", "solution1", "BulkImport", "zoneA", "ctxB");
        }

        @Test
        void handlesNullContextId() {
            when(providerCache.getProvider("domain1", "solution1", "DataExport", "zone1", null))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> routingService.routeImmediateRequest(
                    "POST", "DataExport", null, null,
                    UUID.randomUUID(), "TestEnv", "domain1", "solution1", "zone1", null,
                    Map.of("data", "test"), Collections.emptyMap()))
                .isInstanceOf(NoProviderException.class);

            verify(providerCache).getProvider("domain1", "solution1", "DataExport", "zone1", null);
        }
    }

    // ── DELAYED Flow Tests ──────────────────────────────────────────────

    @Nested
    class DelayedFlowTests {

        @Test
        void throwsNoProviderExceptionWhenNoProviderRegistered() {
            when(providerCache.getProvider("domain1", "solution1", "DataExport", "zone1", "ctx1"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> routingService.routeDelayedRequest(
                    "POST", "DataExport", null, null,
                    UUID.randomUUID(), "TestEnv", "domain1", "solution1",
                    "zone1", "ctx1", UUID.randomUUID(),
                    (String) null,
                    Map.of("data", "test"), Collections.emptyMap()))
                .isInstanceOf(NoProviderException.class)
                .hasMessageContaining("DataExport");
        }

        @Test
        @SuppressWarnings("unchecked")
        void returnsAcceptedResponseWithMessageIdAndQueueId() {
            UUID queueId = UUID.randomUUID();
            ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DataExport",
                "zone1",
                "ctx1",
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com"),
                ServiceType.FUNCTIONAL,
                SupportedRequestsDTO.of(List.of(
                        new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))),
                        new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY)))
                    )),
                Instant.now()
        );

            when(providerCache.getProvider("domain1", "solution1", "DataExport", "zone1", "ctx1"))
                    .thenReturn(Optional.of(provider));

            // Mock the DelayedRequestHandler to return the expected response
            DelayedRequestHandler delayedHandler = (DelayedRequestHandler) org.springframework.test.util.ReflectionTestUtils
                    .getField(routingService, "delayedRequestHandler");
            when(delayedHandler.handleDelayedToDelayed(
                    anyString(), anyString(), any(), any(),
                    any(), anyString(), anyString(), anyString(),
                    any(), any(), anyString(), any(),
                    any(), any(), any()))
                .thenReturn(Map.of(
                    "messageId", UUID.randomUUID().toString(),
                    "queueId", queueId.toString(),
                    "status", "ACCEPTED"
                ));

            Object result = routingService.routeDelayedRequest(
                    "POST", "DataExport", null, null,
                    UUID.randomUUID(), "TestEnv", "domain1", "solution1",
                    "zone1", "ctx1", queueId,
                    (String) null,
                    Map.of("data", "test"), Collections.emptyMap());

            assertThat(result).isInstanceOf(Map.class);
            Map<String, String> response = (Map<String, String>) result;
            assertThat(response).containsKey("messageId");
            assertThat(response).containsEntry("queueId", queueId.toString());
            assertThat(response).containsEntry("status", "ACCEPTED");
        }

        @Test
        void queriesProviderCacheWithCorrectParameters() {
            when(providerCache.getProvider("domain1", "solution1", "BulkImport", "zoneA", null))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> routingService.routeDelayedRequest(
                    "POST", "BulkImport", null, null,
                    UUID.randomUUID(), "TestEnv", "domain1", "solution1",
                    "zoneA", null, UUID.randomUUID(),
                    (String) null,
                    null, Collections.emptyMap()))
                .isInstanceOf(NoProviderException.class);

            verify(providerCache).getProvider("domain1", "solution1", "BulkImport", "zoneA", null);
        }
    }
}
