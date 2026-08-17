// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.extension.ProviderRequestContext;
import org.sifunity.broker.common.extension.ProviderRequestInterceptor;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.requestsconnector.cache.ProviderRegistryCache;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.dto.RequestResponseDTO;
import org.sifunity.requestsconnector.exception.NoProviderException;
import org.sifunity.requestsconnector.exception.UnsupportedPatternException;
import org.sifunity.requestsconnector.service.RoutingService;
import org.sifunity.requestsconnector.util.HeaderExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.sifunity.broker.common.dto.ProtocolDTO;

@RestController
@RequestMapping("/{domainId}/{solutionId}/requests")
@Slf4j
public class RequestController {
    
    private static final String CONNECTOR_NAME = "requests-connector";
    
    private final RoutingService routingService;
    private final HeaderExtractor headerExtractor;
    private final HeaderSanitizer headerSanitizer;
    private final ProviderRegistryCache providerCache;
    private final WebClient webClient;
    private final ConnectorMetricsService connectorMetricsService;
    private final TracingUtilsBase tracingUtils;
    private final List<ProviderRequestInterceptor> providerInterceptors;
    
    @Autowired
    public RequestController(RoutingService routingService, HeaderExtractor headerExtractor,
                             HeaderSanitizer headerSanitizer, ProviderRegistryCache providerCache,
                             WebClient.Builder webClientBuilder,
                             ConnectorMetricsService connectorMetricsService,
                             TracingUtilsBase tracingUtils,
                             @Autowired(required = false) List<ProviderRequestInterceptor> providerInterceptors) {
        this.routingService = routingService;
        this.headerExtractor = headerExtractor;
        this.headerSanitizer = headerSanitizer;
        this.providerCache = providerCache;
        this.webClient = webClientBuilder.build();
        this.connectorMetricsService = connectorMetricsService;
        this.tracingUtils = tracingUtils;
        this.providerInterceptors = providerInterceptors != null ? providerInterceptors : List.of();
        
        if (!this.providerInterceptors.isEmpty()) {
            log.info("RequestController: {} provider request interceptor(s) registered", this.providerInterceptors.size());
        }
    }
    
    /**
     * Handles GET requests (SIF QUERY) — IMMEDIATE pass-through or DELAYED based on requestType header.
     */
    @GetMapping(value = {"/{objectName}", "/{objectName}/{objectId}"})
    public ResponseEntity<Object> handleGetRequest(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String objectName,
            @PathVariable(required = false) String objectId,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader(value = "requestType", defaultValue = "IMMEDIATE") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            @RequestHeader(value = "serviceType", defaultValue = "OBJECT") String serviceType,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            HttpServletRequest httpServletRequest) {
        
        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }
        
        long startTime = System.currentTimeMillis();
        
        log.info("Received GET request: objectName={}, objectId={}, zoneId={}, contextId={}, requestType={}", 
            objectName, objectId, zoneId, contextId, requestType);
        
        connectorMetricsService.recordConnectorRequest(
            CONNECTOR_NAME, objectName, zoneId, "GET", 0);
        
        try {
            if ("DELAYED".equalsIgnoreCase(requestType)) {
                if (queueId == null) {
                    throw new IllegalArgumentException("queueId header is required for DELAYED requests");
                }
                
                Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);
                
                // Build relativeServicePath from the consumer's original request URL
                String queryString = httpServletRequest.getQueryString();
                String relativeServicePath = buildRelativeServicePath(objectName, objectId, queryString);
                
                DelayedRequestDTO request = new DelayedRequestDTO();
                request.setServiceName(objectName);
                request.setServiceType(serviceType);
                request.setMessageType("REQUEST");
                request.setPayload(null);
                
                RequestResponseDTO response = routingService.routeDelayedRequest(
                    environmentId, environmentName, domainId, solutionId, zoneId, contextId, queueId, request, passthroughHeaders, "GET", relativeServicePath, requestId);
                
                long durationMs = System.currentTimeMillis() - startTime;
                connectorMetricsService.recordConnectorRequestDuration(
                    CONNECTOR_NAME, objectName, HttpStatus.ACCEPTED.value(), durationMs);
                
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            }
            
            // Default: IMMEDIATE pass-through
            ResponseEntity<Object> result = proxyToProvider(HttpMethod.GET, objectName, objectId, zoneId, contextId, null, httpServletRequest, domainId, solutionId, serviceType);
            
            long durationMs = System.currentTimeMillis() - startTime;
            int status = result.getStatusCode().value();
            connectorMetricsService.recordConnectorRequestDuration(
                CONNECTOR_NAME, objectName, status, durationMs);
            
            return result;
        } catch (Exception ex) {
            String errorType = ex.getClass().getSimpleName();
            connectorMetricsService.recordConnectorError(
                CONNECTOR_NAME, objectName, zoneId, errorType);
            long durationMs = System.currentTimeMillis() - startTime;
            connectorMetricsService.recordConnectorRequestDuration(
                CONNECTOR_NAME, objectName, 500, durationMs);
            throw ex;
        }
    }
    
    /**
     * Handles PUT requests — IMMEDIATE pass-through or DELAYED based on requestType header.
     */
    @PutMapping(value = {"/{objectName}", "/{objectName}/{objectId}"})
    public ResponseEntity<Object> handlePutRequest(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String objectName,
            @PathVariable(required = false) String objectId,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader(value = "requestType", defaultValue = "IMMEDIATE") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            @RequestHeader(value = "serviceType", defaultValue = "OBJECT") String serviceType,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestBody(required = false) Object body,
            HttpServletRequest httpServletRequest) {
        
        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }
        
        long startTime = System.currentTimeMillis();
        
        log.info("Received PUT request: objectName={}, objectId={}, zoneId={}, contextId={}, requestType={}", 
            objectName, objectId, zoneId, contextId, requestType);
        
        connectorMetricsService.recordConnectorRequest(
            CONNECTOR_NAME, objectName, zoneId, "PUT", 0);
        
        try {
            if ("DELAYED".equalsIgnoreCase(requestType)) {
                if (queueId == null) {
                    throw new IllegalArgumentException("queueId header is required for DELAYED requests");
                }
                Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);
                
                String queryString = httpServletRequest.getQueryString();
                String relativeServicePath = buildRelativeServicePath(objectName, objectId, queryString);
                
                DelayedRequestDTO request = new DelayedRequestDTO();
                request.setServiceName(objectName);
                request.setServiceType(serviceType);
                request.setMessageType("REQUEST");
                request.setPayload(body);
                RequestResponseDTO response = routingService.routeDelayedRequest(
                    environmentId, environmentName, domainId, solutionId, zoneId, contextId, queueId, request, passthroughHeaders, "PUT", relativeServicePath, requestId);
                
                long durationMs = System.currentTimeMillis() - startTime;
                connectorMetricsService.recordConnectorRequestDuration(
                    CONNECTOR_NAME, objectName, HttpStatus.ACCEPTED.value(), durationMs);
                
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            }
            
            ResponseEntity<Object> result = proxyToProvider(HttpMethod.PUT, objectName, objectId, zoneId, contextId, body, httpServletRequest, domainId, solutionId, serviceType);
            
            long durationMs = System.currentTimeMillis() - startTime;
            int status = result.getStatusCode().value();
            connectorMetricsService.recordConnectorRequestDuration(
                CONNECTOR_NAME, objectName, status, durationMs);
            
            return result;
        } catch (Exception ex) {
            String errorType = ex.getClass().getSimpleName();
            connectorMetricsService.recordConnectorError(
                CONNECTOR_NAME, objectName, zoneId, errorType);
            long durationMs = System.currentTimeMillis() - startTime;
            connectorMetricsService.recordConnectorRequestDuration(
                CONNECTOR_NAME, objectName, 500, durationMs);
            throw ex;
        }
    }
    
    /**
     * Handles DELETE requests — IMMEDIATE pass-through or DELAYED based on requestType header.
     */
    @DeleteMapping(value = {"/{objectName}", "/{objectName}/{objectId}"})
    public ResponseEntity<Object> handleDeleteRequest(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String objectName,
            @PathVariable(required = false) String objectId,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader(value = "requestType", defaultValue = "IMMEDIATE") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            @RequestHeader(value = "serviceType", defaultValue = "OBJECT") String serviceType,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            HttpServletRequest httpServletRequest) {
        
        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }
        
        long startTime = System.currentTimeMillis();
        
        log.info("Received DELETE request: objectName={}, objectId={}, zoneId={}, contextId={}, requestType={}", 
            objectName, objectId, zoneId, contextId, requestType);
        
        connectorMetricsService.recordConnectorRequest(
            CONNECTOR_NAME, objectName, zoneId, "DELETE", 0);
        
        try {
            if ("DELAYED".equalsIgnoreCase(requestType)) {
                if (queueId == null) {
                    throw new IllegalArgumentException("queueId header is required for DELAYED requests");
                }
                Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);
                
                String queryString = httpServletRequest.getQueryString();
                String relativeServicePath = buildRelativeServicePath(objectName, objectId, queryString);
                
                DelayedRequestDTO request = new DelayedRequestDTO();
                request.setServiceName(objectName);
                request.setServiceType(serviceType);
                request.setMessageType("REQUEST");
                request.setPayload(null);
                RequestResponseDTO response = routingService.routeDelayedRequest(
                    environmentId, environmentName, domainId, solutionId, zoneId, contextId, queueId, request, passthroughHeaders, "DELETE", relativeServicePath, requestId);
                
                long durationMs = System.currentTimeMillis() - startTime;
                connectorMetricsService.recordConnectorRequestDuration(
                    CONNECTOR_NAME, objectName, HttpStatus.ACCEPTED.value(), durationMs);
                
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            }
            
            ResponseEntity<Object> result = proxyToProvider(HttpMethod.DELETE, objectName, objectId, zoneId, contextId, null, httpServletRequest, domainId, solutionId, serviceType);
            
            long durationMs = System.currentTimeMillis() - startTime;
            int status = result.getStatusCode().value();
            connectorMetricsService.recordConnectorRequestDuration(
                CONNECTOR_NAME, objectName, status, durationMs);
            
            return result;
        } catch (Exception ex) {
            String errorType = ex.getClass().getSimpleName();
            connectorMetricsService.recordConnectorError(
                CONNECTOR_NAME, objectName, zoneId, errorType);
            long durationMs = System.currentTimeMillis() - startTime;
            connectorMetricsService.recordConnectorRequestDuration(
                CONNECTOR_NAME, objectName, 500, durationMs);
            throw ex;
        }
    }
    
    /**
     * Handles POST requests — IMMEDIATE or DELAYED based on requestType header.
     * IMMEDIATE POSTs are proxied transparently. DELAYED POSTs go through the queue.
     */
    @PostMapping("/{objectName}")
    public ResponseEntity<?> handleRequest(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String objectName,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader(value = "requestType", defaultValue = "IMMEDIATE") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            @RequestHeader(value = "serviceType", defaultValue = "OBJECT") String serviceType,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestBody(required = false) Object requestBody,
            HttpServletRequest httpServletRequest) {
        
        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }
        
        long startTime = System.currentTimeMillis();
        
        log.info("Received {} POST request: objectName={}, zoneId={}, contextId={}", 
            requestType, objectName, zoneId, contextId);
        
        connectorMetricsService.recordConnectorRequest(
            CONNECTOR_NAME, objectName, zoneId, "POST", 0);
        
        try {
            if ("IMMEDIATE".equalsIgnoreCase(requestType)) {
                // Transparent pass-through to provider
                ResponseEntity<Object> result = proxyToProvider(HttpMethod.POST, objectName, null, zoneId, contextId, requestBody, httpServletRequest, domainId, solutionId, serviceType);
                
                long durationMs = System.currentTimeMillis() - startTime;
                int status = result.getStatusCode().value();
                connectorMetricsService.recordConnectorRequestDuration(
                    CONNECTOR_NAME, objectName, status, durationMs);
                
                return result;
                
            } else if ("DELAYED".equalsIgnoreCase(requestType)) {
                if (queueId == null) {
                    throw new IllegalArgumentException("queueId header is required for DELAYED requests");
                }
                
                Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);
                
                String queryString = httpServletRequest.getQueryString();
                String relativeServicePath = buildRelativeServicePath(objectName, null, queryString);
                
                DelayedRequestDTO request = new DelayedRequestDTO();
                request.setServiceName(objectName);
                request.setServiceType(serviceType);
                request.setMessageType("REQUEST");
                request.setPayload(requestBody);
                
                RequestResponseDTO response = routingService.routeDelayedRequest(
                    environmentId, environmentName, domainId, solutionId, zoneId, contextId, queueId, request, passthroughHeaders, "POST", relativeServicePath, requestId);
                
                long durationMs = System.currentTimeMillis() - startTime;
                connectorMetricsService.recordConnectorRequestDuration(
                    CONNECTOR_NAME, objectName, HttpStatus.ACCEPTED.value(), durationMs);
                
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
                
            } else {
                throw new IllegalArgumentException("Invalid requestType: " + requestType + ". Must be IMMEDIATE or DELAYED");
            }
        } catch (Exception ex) {
            String errorType = ex.getClass().getSimpleName();
            connectorMetricsService.recordConnectorError(
                CONNECTOR_NAME, objectName, zoneId, errorType);
            long durationMs = System.currentTimeMillis() - startTime;
            connectorMetricsService.recordConnectorRequestDuration(
                CONNECTOR_NAME, objectName, 500, durationMs);
            throw ex;
        }
    }
    
    /**
     * Builds the relativeServicePath from the consumer's original request URL.
     *
     * <p>Format: {@code /{objectName}} optionally followed by {@code /{objectId}}
     * and {@code ?queryString}.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code /StudentPersonals}</li>
     *   <li>{@code /StudentPersonals/uuid123}</li>
     *   <li>{@code /StudentPersonals?where=name='John'}</li>
     * </ul>
     *
     * @param objectName  the SIF object/service name
     * @param objectId    the object ID (may be null)
     * @param queryString the query string from the HTTP request (may be null)
     * @return the formatted relativeServicePath starting with /
     */
    private static String buildRelativeServicePath(String objectName, String objectId, String queryString) {
        StringBuilder rsp = new StringBuilder("/").append(objectName);
        if (objectId != null && !objectId.isBlank()) {
            rsp.append("/").append(objectId);
        }
        if (queryString != null && !queryString.isBlank()) {
            rsp.append("?").append(queryString);
        }
        return rsp.toString();
    }

    /**
     * Transparent HTTP proxy to the registered provider.
     * Looks up the provider, forwards the request with the same HTTP method, and returns the raw response.
     * If any provider request interceptors are registered, invokes them before/after the outbound call.
     */
    private ResponseEntity<Object> proxyToProvider(
            HttpMethod method, String objectName, String objectId,
            String zoneId, String contextId, Object body,
            HttpServletRequest httpServletRequest,
            String domainId, String solutionId, String serviceType) {
        
        return tracingUtils.traceOperation(
            "sif.request.immediate.proxy",
            Map.of(
                "sif.service_name", objectName,
                "sif.zone_id", zoneId,
                "sif.context_id", contextId != null ? contextId : "DEFAULT",
                "sif.http_method", method.name()
            ),
            () -> {
                // Span: provider lookup
                ProviderRegistrationDTO provider = tracingUtils.traceOperation(
                    "sif.provider.lookup",
                    Map.of("sif.service_name", objectName, "sif.zone_id", zoneId),
                    () -> {
                        return providerCache.getProvider(domainId, solutionId, objectName, zoneId, contextId, serviceType)
                            .orElseThrow(() -> new NoProviderException(objectName, zoneId, contextId));
                    }
                );
                
                if (provider.supportedRequestTypes().supportedRequestType().stream()
                        .noneMatch(srt -> srt.requestType() == RequestType.IMMEDIATE)) {
                    throw new UnsupportedPatternException("Provider does not support IMMEDIATE pattern");
                }
                
                String targetUrl = ProtocolDTO.locationOf(provider.endPoint());
                if (objectId != null) {
                    targetUrl += "/" + objectId;
                }
                
                // Forward query string parameters to provider (e.g., where, order, page)
                String queryString = httpServletRequest.getQueryString();
                if (queryString != null && !queryString.isBlank()) {
                    targetUrl += "?" + queryString;
                }
                
                log.info("Proxying {} {} to provider: {}", method, objectName, targetUrl);
                
                Map<String, String> passthroughHeaders = new LinkedHashMap<>(headerSanitizer.sanitize(httpServletRequest));
                
                // Re-inject structured SIF routing headers that providers need.
                // HeaderSanitizer strips these because they are first-class envelope
                // fields for queue/event paths, but the IMMEDIATE proxy must forward
                // them so the provider can scope its response (e.g., zone-scoped queries).
                passthroughHeaders.put("zoneId", zoneId);
                passthroughHeaders.put("contextId", contextId);
                
                // Invoke provider request interceptors before the outbound call
                // immediateProxy=true signals that this is a transparent proxy — no broker auth signing
                ProviderRequestContext interceptorContext = new ProviderRequestContext(
                        domainId, solutionId, provider.environmentId(), targetUrl, passthroughHeaders, true);
                providerInterceptors.forEach(i -> i.beforeProviderRequest(interceptorContext));
                
                try {
                    // Span: HTTP call to provider (this is where slow providers show up)
                    final String providerUrl = targetUrl;
                    ResponseEntity<Object> providerResponse = tracingUtils.traceOperation(
                        "sif.provider.call",
                        Map.of(
                            "sif.service_name", objectName,
                            "sif.provider_endpoint", providerUrl,
                            "sif.provider_name", provider.providerName() != null ? provider.providerName() : "unknown",
                            "sif.http_method", method.name()
                        ),
                        () -> {
                            WebClient.RequestBodySpec requestSpec = webClient.method(method)
                                .uri(providerUrl)
                                .headers(h -> passthroughHeaders.forEach(h::set))
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                            
                            WebClient.RequestHeadersSpec<?> headersSpec;
                            if (body != null && (method == HttpMethod.POST || method == HttpMethod.PUT)) {
                                headersSpec = requestSpec.bodyValue(body);
                            } else {
                                headersSpec = requestSpec;
                            }
                            
                            return headersSpec
                                .retrieve()
                                .toEntity(Object.class)
                                .block();
                        }
                    );
                    
                    log.info("Provider responded for {} {}", method, objectName);
                    
                    HttpHeaders responseHeaders = new HttpHeaders();
                    if (providerResponse != null && providerResponse.getHeaders() != null) {
                        providerResponse.getHeaders().forEach((key, values) -> {
                            String lowerKey = key.toLowerCase();
                            // Strip hop-by-hop headers and content-length/content-encoding:
                            // the body is deserialized and re-serialized here, so the original
                            // Content-Length no longer matches the outgoing body. Leaving a stale
                            // Content-Length causes the client to hang waiting for bytes that never
                            // arrive when the re-serialized body is shorter than the original.
                            // Spring recomputes Content-Length for the new body automatically.
                            if (!lowerKey.equals("transfer-encoding")
                                    && !lowerKey.equals("connection")
                                    && !lowerKey.equals("content-length")
                                    && !lowerKey.equals("content-encoding")) {
                                responseHeaders.put(key, values);
                            }
                        });
                    }
                    
                    Object responseBody = providerResponse != null ? providerResponse.getBody() : null;
                    return ResponseEntity.ok().headers(responseHeaders).body(responseBody);
                } finally {
                    // Clean up interceptor state to prevent ThreadLocal leaks
                    providerInterceptors.forEach(i -> i.afterProviderRequest(interceptorContext));
                }
            }
        );
    }
}
