// SPDX-License-Identifier: MIT
package org.sifunity.gateway.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.sifunity.broker.common.dto.EnvironmentLookupResponse;
import org.sifunity.broker.common.dto.PermissionCheckRequest;
import org.sifunity.broker.common.dto.PermissionCheckResponse;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.exception.InsufficientPermissionsException;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.sifunity.broker.common.util.CredentialMasker;
import org.sifunity.broker.common.util.PermissionCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Reactive HTTP client for calling Environment Service internal APIs.
 * Handles token validation and permission checking for the API Gateway.
 *
 * <p>This client is configured with:
 * <ul>
 *   <li>5 second timeout for all requests</li>
 *   <li>2 retry attempts on transient failures</li>
 *   <li>Automatic error handling and exception mapping</li>
 *   <li>OpenTelemetry span instrumentation for auth/authz visibility in traces</li>
 * </ul>
 *
 * @see TokenValidationRequest
 * @see TokenValidationResponse
 * @see PermissionCheckRequest
 * @see PermissionCheckResponse
 */
@Component
public class EnvironmentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentServiceClient.class);

    private final WebClient webClient;
    private final Tracer tracer;
    private final CircuitBreaker circuitBreaker;

    /** Manual Caffeine cache for permission check results (reactive-compatible). */
    private final Cache<String, PermissionCheckResponse> permissionCache;

    /**
     * Constructs an EnvironmentServiceClient with the specified base URL and tracer.
     *
     * @param webClientBuilder      the WebClient builder for creating the HTTP client
     * @param environmentServiceUrl the base URL of the Environment Service
     * @param tracer                the OpenTelemetry tracer for creating spans (may be null if observability disabled)
     * @param propagator            the W3C trace context propagator (may be null if observability disabled)
     * @param circuitBreaker        the Resilience4j circuit breaker for fail-fast behavior when environment-service is down
     */
    public EnvironmentServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${environment-service.url}") String environmentServiceUrl,
            @Value("${broker.internal-service-token:}") String internalServiceToken,
            @Autowired(required = false) Tracer tracer,
            @Autowired(required = false) TextMapPropagator propagator,
            CircuitBreaker circuitBreaker) {

        WebClient.Builder builder = webClientBuilder
                .baseUrl(environmentServiceUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Internal-Service-Token", internalServiceToken);

        // Inject W3C trace context (traceparent/tracestate) into outgoing requests
        // so the environment-service links its spans to the gateway's trace
        if (propagator != null) {
            builder.filter(traceContextFilter(propagator));
        }

        this.webClient = builder.build();
        this.tracer = tracer;
        this.circuitBreaker = circuitBreaker;

        // Build the permission cache: 30s TTL, max 10,000 entries, stats for Micrometer
        this.permissionCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(10_000)
                .recordStats()
                .build();

        log.info("EnvironmentServiceClient initialized with base URL: {}, tracing: {}",
                environmentServiceUrl, tracer != null ? "enabled" : "disabled");
    }

    /**
     * Creates a WebClient filter that injects the current OTel trace context
     * (traceparent, tracestate) into outgoing HTTP request headers.
     */
    private static ExchangeFilterFunction traceContextFilter(TextMapPropagator propagator) {
        TextMapSetter<ClientRequest.Builder> setter =
                (carrier, key, value) -> carrier.header(key, value);

        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            ClientRequest.Builder mutated = ClientRequest.from(request);
            propagator.inject(Context.current(), mutated, setter);
            return Mono.just(mutated.build());
        });
    }

    /**
     * Validates a session token and HMAC signature via the Environment Service.
     *
     * <p>This method calls POST /internal/validate-token and returns environment details
     * if the token is valid. The request includes a 5-second timeout and will retry
     * up to 2 times on transient failures.
     *
     * @param request the token validation request containing sessionToken, timestamp, and hash
     * @return a Mono emitting the TokenValidationResponse with environment details
     * @throws InvalidTokenException       if the token is invalid or authentication fails (4xx errors)
     * @throws ServiceUnavailableException if the Environment Service is unavailable (5xx errors)
     */
    public Mono<TokenValidationResponse> validateToken(TokenValidationRequest request) {
        log.debug("Validating token for sessionToken: {}", CredentialMasker.mask(request.sessionToken()));

        Mono<TokenValidationResponse> operation = webClient.post()
                .uri("/internal/validate-token")
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError(),
                        response -> {
                            log.warn("Token validation failed with status: {}", response.statusCode());
                            return Mono.error(new InvalidTokenException(
                                    "Token validation failed: Invalid credentials"));
                        })
                .onStatus(
                        status -> status.is5xxServerError(),
                        response -> {
                            log.error("Environment Service unavailable during token validation: {}",
                                    response.statusCode());
                            return Mono.error(new ServiceUnavailableException(
                                    "Environment Service",
                                    "Token validation service unavailable"));
                        })
                .bodyToMono(TokenValidationResponse.class)
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.fixedDelay(2, Duration.ofMillis(100))
                        .filter(throwable -> !(throwable instanceof InvalidTokenException))
                        .doBeforeRetry(retrySignal ->
                                log.debug("Retrying token validation, attempt: {}",
                                        retrySignal.totalRetries() + 1)))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(CallNotPermittedException.class, ex ->
                        new ServiceUnavailableException("Environment Service", "Circuit breaker is open"))
                .doOnSuccess(response ->
                        log.debug("Token validation successful for environment: {}",
                                response.environmentId()))
                .doOnError(error ->
                        log.error("Token validation failed: {}", error.getMessage()));

        return wrapWithSpan("gateway.auth.validate_token", operation);
    }

    /**
     * Checks ACL permissions for an authenticated environment via the Environment Service.
     *
     * <p>This method first checks a local Caffeine cache keyed by
     * {@code environmentId:zoneId:contextId:serviceName:accessType} (built via
     * {@link PermissionCacheKey}, with null/blank zone/context normalized to a stable sentinel).
     * Including the zone and context scope ensures a decision cached for one zone/context is never
     * reused for a different scope. On a cache hit, the cached response is returned immediately as a
     * Mono. On a cache miss, it calls POST /internal/check-permission and caches the result before
     * returning it.</p>
     *
     * <p>The request includes a 5-second timeout and will retry up to 2 times on transient failures.
     *
     * @param request the permission check request containing environment ID, service name,
     *                zone, context, and access type
     * @return a Mono emitting the PermissionCheckResponse indicating if permission is granted
     * @throws InsufficientPermissionsException if permission is denied (4xx errors)
     * @throws ServiceUnavailableException      if the Environment Service is unavailable (5xx errors)
     */
    public Mono<PermissionCheckResponse> checkPermission(PermissionCheckRequest request) {
        log.debug("Checking permission for environment: {}, service: {}, zone: {}, context: {}, accessType: {}",
                request.environmentId(), request.serviceName(), request.zoneId(),
                request.contextId(), request.accessType());

        String cacheKey = PermissionCacheKey.of(
                request.environmentId(), request.zoneId(), request.contextId(),
                request.serviceName(), request.accessType());

        // Check cache first
        PermissionCheckResponse cached = permissionCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Permission cache hit for key: {}", cacheKey);
            return Mono.just(cached);
        }

        Mono<PermissionCheckResponse> operation = webClient.post()
                .uri("/internal/check-permission")
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError(),
                        response -> {
                            log.warn("Permission check failed with status: {}", response.statusCode());
                            return Mono.error(new InsufficientPermissionsException(
                                    "Permission denied for service: " + request.serviceName()));
                        })
                .onStatus(
                        status -> status.is5xxServerError(),
                        response -> {
                            log.error("Environment Service unavailable during permission check: {}",
                                    response.statusCode());
                            return Mono.error(new ServiceUnavailableException(
                                    "Environment Service",
                                    "Permission check service unavailable"));
                        })
                .bodyToMono(PermissionCheckResponse.class)
                .doOnNext(response -> {
                    // Cache the result on success
                    permissionCache.put(cacheKey, response);
                    log.debug("Permission cached for key: {}, granted: {}", cacheKey, response.granted());
                })
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.fixedDelay(2, Duration.ofMillis(100))
                        .filter(throwable -> !(throwable instanceof InsufficientPermissionsException))
                        .doBeforeRetry(retrySignal ->
                                log.debug("Retrying permission check, attempt: {}",
                                        retrySignal.totalRetries() + 1)))
                .doOnSuccess(response ->
                        log.debug("Permission check completed for environment: {}, granted: {}",
                                request.environmentId(), response.granted()))
                .doOnError(error ->
                        log.error("Permission check failed: {}", error.getMessage()));

        return wrapWithSpan("gateway.auth.check_permission", operation);
    }

    /**
     * Looks up an environment by fingerprint and solution ID via the Environment Service.
     *
     * <p>This method calls GET /internal/environments/by-fingerprint and returns environment
     * details if found. Used by the ProxyImpersonationFilter to resolve the "broker-services"
     * shorthand to an actual environment UUID.
     *
     * @param fingerprint the environment fingerprint to look up (e.g. "broker-services")
     * @param domainId    the domain ID that scopes the solution (solutionId is unique only
     *                    within a domain, so this is required for tenant isolation)
     * @param solutionId  the solution ID (UUID or human-readable) to scope the lookup
     * @return a Mono emitting the EnvironmentLookupResponse, or empty if not found (404)
     * @throws ServiceUnavailableException if the Environment Service is unavailable (5xx errors)
     */
    public Mono<EnvironmentLookupResponse> lookupEnvironmentByFingerprint(String fingerprint, String domainId,
            String solutionId) {
        log.debug("Looking up environment by fingerprint: {}, domainId: {}, solutionId: {}",
                fingerprint, domainId, solutionId);

        Mono<EnvironmentLookupResponse> operation = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/environments/by-fingerprint")
                        .queryParam("fingerprint", fingerprint)
                        .queryParam("domainId", domainId)
                        .queryParam("solutionId", solutionId)
                        .build())
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 404) {
                        log.debug("Environment not found for fingerprint: {}, solutionId: {}",
                                fingerprint, solutionId);
                        return response.releaseBody().then(Mono.empty());
                    } else if (response.statusCode().is4xxClientError()) {
                        log.warn("Environment lookup by fingerprint failed with status: {}",
                                response.statusCode());
                        return response.releaseBody().then(Mono.error(new ServiceUnavailableException(
                                "Environment Service",
                                "Environment lookup by fingerprint failed: " + response.statusCode())));
                    } else if (response.statusCode().is5xxServerError()) {
                        log.error("Environment Service unavailable during fingerprint lookup: {}",
                                response.statusCode());
                        return response.releaseBody().then(Mono.error(new ServiceUnavailableException(
                                "Environment Service",
                                "Environment lookup by fingerprint service unavailable")));
                    }
                    return response.bodyToMono(EnvironmentLookupResponse.class);
                })
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.fixedDelay(2, Duration.ofMillis(100))
                        .filter(throwable -> throwable instanceof ServiceUnavailableException ||
                                throwable instanceof TimeoutException)
                        .doBeforeRetry(retrySignal ->
                                log.debug("Retrying environment fingerprint lookup, attempt: {}",
                                        retrySignal.totalRetries() + 1)))
                .doOnSuccess(response -> {
                    if (response != null) {
                        log.debug("Environment fingerprint lookup successful: {}", response.id());
                    }
                })
                .doOnError(error ->
                        log.error("Environment fingerprint lookup failed: {}", error.getMessage()));

        return wrapWithSpan("gateway.proxy.lookup_by_fingerprint", operation);
    }

    /**
     * Looks up an environment by its UUID via the Environment Service.
     *
     * <p>This method calls GET /internal/environments/{id} and returns environment
     * details if found. Used by the ProxyImpersonationFilter to resolve a UUID-based
     * X-Run-As-Environment header value.
     *
     * @param environmentId the UUID of the environment to look up
     * @return a Mono emitting the EnvironmentLookupResponse, or empty if not found (404)
     * @throws ServiceUnavailableException if the Environment Service is unavailable (5xx errors)
     */
    public Mono<EnvironmentLookupResponse> lookupEnvironmentById(UUID environmentId) {
        log.debug("Looking up environment by ID: {}", environmentId);

        Mono<EnvironmentLookupResponse> operation = webClient.get()
                .uri("/internal/environments/{id}", environmentId)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 404) {
                        log.debug("Environment not found for ID: {}", environmentId);
                        return response.releaseBody().then(Mono.empty());
                    } else if (response.statusCode().is4xxClientError()) {
                        log.warn("Environment lookup by ID failed with status: {}",
                                response.statusCode());
                        return response.releaseBody().then(Mono.error(new ServiceUnavailableException(
                                "Environment Service",
                                "Environment lookup by ID failed: " + response.statusCode())));
                    } else if (response.statusCode().is5xxServerError()) {
                        log.error("Environment Service unavailable during ID lookup: {}",
                                response.statusCode());
                        return response.releaseBody().then(Mono.error(new ServiceUnavailableException(
                                "Environment Service",
                                "Environment lookup by ID service unavailable")));
                    }
                    return response.bodyToMono(EnvironmentLookupResponse.class);
                })
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.fixedDelay(2, Duration.ofMillis(100))
                        .filter(throwable -> throwable instanceof ServiceUnavailableException ||
                                throwable instanceof TimeoutException)
                        .doBeforeRetry(retrySignal ->
                                log.debug("Retrying environment ID lookup, attempt: {}",
                                        retrySignal.totalRetries() + 1)))
                .doOnSuccess(response -> {
                    if (response != null) {
                        log.debug("Environment ID lookup successful: {}", response.id());
                    }
                })
                .doOnError(error ->
                        log.error("Environment ID lookup failed: {}", error.getMessage()));

        return wrapWithSpan("gateway.proxy.lookup_by_id", operation);
    }

    /**
     * Wraps a reactive operation with an OpenTelemetry span.
     * Reads the parent span from the Reactor context (set by TraceContextFilter)
     * so the span nests correctly under gateway.request.
     *
     * @param spanName  the name for the span
     * @param operation the Mono to wrap
     * @param <T>       the emission type
     * @return the wrapped Mono
     */
    private <T> Mono<T> wrapWithSpan(String spanName, Mono<T> operation) {
        if (tracer == null) {
            return operation;
        }

        return Mono.deferContextual(ctx -> {
            // Read the gateway.request span from Reactor context (set by TraceContextFilter)
            Span parentSpan = ctx.hasKey(Span.class) ? ctx.get(Span.class) : Span.current();
            Context parentContext = Context.current().with(parentSpan);

            Span span = tracer.spanBuilder(spanName)
                    .setParent(parentContext)
                    .setSpanKind(SpanKind.CLIENT)
                    .startSpan();

            // Make this span current so the WebClient trace propagation filter
            // picks it up and injects traceparent into outgoing HTTP headers
            return Mono.using(
                    () -> span.makeCurrent(),
                    scope -> operation
                            .doOnSuccess(result -> {
                                span.setStatus(StatusCode.OK);
                                span.end();
                            })
                            .doOnError(error -> {
                                span.recordException(error);
                                span.setStatus(StatusCode.ERROR, error.getMessage());
                                span.end();
                            })
                            .doOnCancel(() -> {
                                span.setStatus(StatusCode.ERROR, "cancelled");
                                span.end();
                            }),
                    Scope::close
            );
        });
    }
}
