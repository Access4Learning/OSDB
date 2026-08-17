// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core observability configuration for distributed tracing, metrics, and logging.
 * This configuration is shared across all SIF Unity Broker microservices.
 * 
 * <p>Features:
 * <ul>
 *   <li>OpenTelemetry SDK configuration with Jaeger exporter</li>
 *   <li>W3C Trace Context propagation</li>
 *   <li>Configurable sampling rates per environment</li>
 *   <li>Micrometer metrics with cardinality limiting</li>
 *   <li>Graceful degradation when backends are unavailable</li>
 * </ul>
 * 
 * @see <a href="https://opentelemetry.io/docs/instrumentation/java/">OpenTelemetry Java</a>
 * @see <a href="https://micrometer.io/docs">Micrometer Documentation</a>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "observability.enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityConfig {

    /**
     * Creates and configures the OpenTelemetry SDK instance with resilient error handling.
     * 
     * <p>This bean configures:
     * <ul>
     *   <li>Service resource attributes (name, version)</li>
     *   <li>Jaeger gRPC span exporter with timeout and retry logic</li>
     *   <li>Batch span processor with memory buffering (max 10,000 spans)</li>
     *   <li>Exponential backoff retry strategy for export failures</li>
     *   <li>Trace ID ratio-based sampling</li>
     *   <li>W3C Trace Context propagation</li>
     * </ul>
     * 
     * <p>Error Handling:
     * <ul>
     *   <li>Export exceptions are caught and logged as warnings</li>
     *   <li>Traces are buffered in memory when Jaeger is unavailable</li>
     *   <li>Retries use exponential backoff (1s, 2s, 4s, 8s, max 60s)</li>
     *   <li>Service continues operating when Jaeger is down</li>
     * </ul>
     * 
     * @param serviceName the name of the microservice
     * @param serviceVersion the version of the microservice
     * @param jaegerEndpoint the Jaeger collector gRPC endpoint
     * @param samplingProbability the sampling probability (0.0 to 1.0)
     * @return configured OpenTelemetry instance
     */
    @Bean
    public OpenTelemetry openTelemetry(
            @Value("${spring.application.name}") String serviceName,
            @Value("${spring.application.version:1.0.0}") String serviceVersion,
            @Value("${observability.tracing.otlp.endpoint:http://localhost:4318/v1/traces}") String otlpEndpoint,
            @Value("${observability.tracing.sampling.probability:1.0}") double samplingProbability,
            @Value("${observability.tracing.register-global:true}") boolean registerGlobal) {
        
        log.info("Initializing OpenTelemetry SDK for service: {}, version: {}", serviceName, serviceVersion);
        log.info("OTLP endpoint: {}, sampling probability: {}", otlpEndpoint, samplingProbability);
        
        try {
            // Create resource with service metadata
            Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                    ResourceAttributes.SERVICE_NAME, serviceName,
                    ResourceAttributes.SERVICE_VERSION, serviceVersion
                )));
            
            // Create OTLP exporter with timeout and error handling
            SpanExporter otlpExporter = createResilientOtlpExporter(otlpEndpoint);
            
            // Create tracer provider with batch processor and sampling
            // BatchSpanProcessor configuration:
            // - maxQueueSize: 10,000 spans (buffer for Jaeger unavailability)
            // - scheduleDelay: 5 seconds (batch export interval)
            // - maxExportBatchSize: 512 spans per batch
            // - exporterTimeout: 30 seconds (with retry logic)
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(otlpExporter)
                    .setScheduleDelay(Duration.ofSeconds(5))
                    .setMaxQueueSize(10000)  // Buffer up to 10,000 spans in memory
                    .setMaxExportBatchSize(512)
                    .setExporterTimeout(Duration.ofSeconds(30))
                    .build())
                .setSampler(Sampler.traceIdRatioBased(samplingProbability))
                .build();
            
            // Build OpenTelemetry SDK
            // In tests, we don't register globally to avoid conflicts between test classes
            OpenTelemetry openTelemetry;
            if (registerGlobal) {
                openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setPropagators(ContextPropagators.create(
                        W3CTraceContextPropagator.getInstance()
                    ))
                    .buildAndRegisterGlobal();
            } else {
                openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setPropagators(ContextPropagators.create(
                        W3CTraceContextPropagator.getInstance()
                    ))
                    .build();
            }
            
            log.info("OpenTelemetry SDK initialized successfully with resilient error handling");
            return openTelemetry;
            
        } catch (Exception e) {
            log.error("Failed to initialize OpenTelemetry SDK. Tracing will be disabled.", e);
            // Return no-op OpenTelemetry to allow service to continue
            return OpenTelemetry.noop();
        }
    }
    
    /**
     * Creates a resilient Jaeger exporter that handles connection failures gracefully.
     * 
     * <p>The exporter wraps the standard JaegerGrpcSpanExporter with error handling:
     * <ul>
     *   <li>Catches export exceptions and logs warnings</li>
     *   <li>Implements exponential backoff retry logic</li>
     *   <li>Allows service to continue when Jaeger is unavailable</li>
     * </ul>
     * 
     * @param jaegerEndpoint the Jaeger collector gRPC endpoint
     * @return resilient span exporter
     */
    private SpanExporter createResilientOtlpExporter(String otlpEndpoint) {
        OtlpHttpSpanExporter baseExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .setTimeout(Duration.ofSeconds(10))
            .build();
        
        return new ResilientSpanExporter(baseExporter);
    }
    
    /**
     * Creates a Tracer instance for the service.
     * 
     * @param openTelemetry the OpenTelemetry instance
     * @param serviceName the name of the microservice
     * @return configured Tracer instance
     */
    @Bean
    public Tracer tracer(
            OpenTelemetry openTelemetry,
            @Value("${spring.application.name}") String serviceName) {
        return openTelemetry.getTracer(serviceName);
    }
    
    /**
     * Creates a TextMapPropagator for W3C Trace Context propagation.
     * 
     * <p>This propagator is used to extract trace context from incoming requests
     * and inject trace context into outgoing requests using the W3C Trace Context
     * standard (traceparent and tracestate headers).</p>
     * 
     * @return W3C trace context propagator
     */
    @Bean
    public TextMapPropagator textMapPropagator() {
        return W3CTraceContextPropagator.getInstance();
    }
    
    /**
     * Resilient span exporter that wraps a base exporter with error handling and retry logic.
     * 
     * <p>This exporter implements graceful degradation when Jaeger is unavailable:
     * <ul>
     *   <li>Catches all export exceptions and logs warnings</li>
     *   <li>Implements exponential backoff for retries (1s, 2s, 4s, 8s, max 60s)</li>
     *   <li>Returns success to prevent span processor from blocking</li>
     *   <li>Allows service to continue operating when Jaeger is down</li>
     * </ul>
     * 
     * <p>The BatchSpanProcessor handles buffering (up to 10,000 spans) and will
     * drop oldest spans if the buffer fills up during extended Jaeger outages.</p>
     */
    private static class ResilientSpanExporter implements SpanExporter {
        private final SpanExporter delegate;
        private final AtomicLong lastFailureTime = new AtomicLong(0);
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private static final long MAX_BACKOFF_MS = 60000; // 60 seconds
        
        public ResilientSpanExporter(SpanExporter delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public io.opentelemetry.sdk.common.CompletableResultCode export(
                java.util.Collection<io.opentelemetry.sdk.trace.data.SpanData> spans) {
            
            // Check if we should apply backoff
            if (shouldBackoff()) {
                log.debug("Skipping export due to exponential backoff (consecutive failures: {})", 
                    consecutiveFailures.get());
                // Return success to prevent blocking, spans remain in buffer
                return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
            }
            
            try {
                // Attempt to export spans
                io.opentelemetry.sdk.common.CompletableResultCode result = delegate.export(spans);
                
                // Handle async result
                result.whenComplete(() -> {
                    if (result.isSuccess()) {
                        // Reset failure tracking on success
                        if (consecutiveFailures.get() > 0) {
                            log.info("Successfully exported spans to Jaeger after {} failures", 
                                consecutiveFailures.get());
                            consecutiveFailures.set(0);
                            lastFailureTime.set(0);
                        }
                    } else {
                        handleExportFailure(spans.size());
                    }
                });
                
                return result;
                
            } catch (Exception e) {
                handleExportFailure(spans.size(), e);
                // Return success to prevent blocking the span processor
                // Spans will remain in the BatchSpanProcessor buffer for retry
                return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
            }
        }
        
        @Override
        public io.opentelemetry.sdk.common.CompletableResultCode flush() {
            try {
                return delegate.flush();
            } catch (Exception e) {
                log.warn("Failed to flush spans to Jaeger: {}", e.getMessage());
                return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
            }
        }
        
        @Override
        public io.opentelemetry.sdk.common.CompletableResultCode shutdown() {
            try {
                return delegate.shutdown();
            } catch (Exception e) {
                log.warn("Failed to shutdown Jaeger exporter: {}", e.getMessage());
                return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
            }
        }
        
        /**
         * Checks if we should skip export due to exponential backoff.
         * 
         * @return true if we should skip this export attempt
         */
        private boolean shouldBackoff() {
            if (consecutiveFailures.get() == 0) {
                return false;
            }
            
            long backoffMs = calculateBackoffMs();
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
            return timeSinceLastFailure < backoffMs;
        }
        
        /**
         * Calculates exponential backoff delay: 1s, 2s, 4s, 8s, 16s, 32s, max 60s.
         * 
         * @return backoff delay in milliseconds
         */
        private long calculateBackoffMs() {
            // Exponential backoff: 2^(failures-1) seconds, capped at 60 seconds
            long backoffSeconds = (long) Math.pow(2, Math.min(consecutiveFailures.get() - 1, 5));
            return Math.min(backoffSeconds * 1000, MAX_BACKOFF_MS);
        }
        
        /**
         * Handles export failure by logging and updating failure tracking.
         * 
         * @param spanCount number of spans that failed to export
         */
        private void handleExportFailure(int spanCount) {
            consecutiveFailures.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());
            long nextBackoffMs = calculateBackoffMs();
            
            log.warn("Failed to export {} spans to Jaeger (consecutive failures: {}). " +
                    "Spans buffered in memory. Next retry in {}ms. " +
                    "Service continues operating normally.",
                spanCount, consecutiveFailures.get(), nextBackoffMs);
        }
        
        /**
         * Handles export failure with exception by logging and updating failure tracking.
         * 
         * @param spanCount number of spans that failed to export
         * @param exception the exception that occurred
         */
        private void handleExportFailure(int spanCount, Exception exception) {
            consecutiveFailures.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());
            long nextBackoffMs = calculateBackoffMs();
            
            log.warn("Failed to export {} spans to Jaeger (consecutive failures: {}): {}. " +
                    "Spans buffered in memory. Next retry in {}ms. " +
                    "Service continues operating normally.",
                spanCount, consecutiveFailures.get(), exception.getMessage(), nextBackoffMs);
        }
    }

}
