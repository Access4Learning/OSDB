// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.testprovider.service.TestDataGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints exposed to the broker for IMMEDIATE request proxying.
 * <p>
 * The broker forwards consumer requests here; this controller responds
 * synchronously with test data including echo of request headers and URL.
 * <p>
 * <strong>Paged Query Support:</strong> When a GET collection request includes
 * {@code queryIntention: ALL} and {@code navigationPageSize}, this controller
 * returns paginated responses with full SIF navigation headers. The total
 * result set is {@link TestDataGenerator#PAGED_TOTAL_PAGES} pages (default 7).
 * Each page contains exactly {@code navigationPageSize} records with sequential
 * identifiers so the broker's paging loop can be verified end-to-end.
 */
@RestController
@Slf4j
public class ProviderController {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final TestDataGenerator dataGenerator;

    public ProviderController(TestDataGenerator dataGenerator) {
        this.dataGenerator = dataGenerator;
    }

    /**
     * Handle an immediate CREATE / UPDATE request (POST).
     */
    @PostMapping("/{serviceName}")
    public ResponseEntity<Map<String, Object>> handleCreate(
            @PathVariable String serviceName,
            @RequestBody(required = false) Object body,
            HttpServletRequest request) {

        log.info("IMMEDIATE POST /{} from {} — body present: {}",
                serviceName, request.getRemoteAddr(), body != null);

        Map<String, String> headers = extractHeaders(request);
        String url = getFullUrl(request);

        Map<String, Object> payload = dataGenerator.generateSingle(headers, url);
        payload.put("receivedHeaders", headers);
        log.info("Responding with test data for POST /{}", serviceName);

        HttpHeaders responseHeaders = buildEchoResponseHeaders(request);
        return ResponseEntity.ok().headers(responseHeaders).body(payload);
    }

    /**
     * Handle an immediate QUERY request (GET collection).
     * <p>
     * If the request contains paging prerequisites ({@code queryIntention=ALL}
     * and {@code navigationPageSize}), returns a paginated response with full
     * SIF navigation headers. Otherwise returns a simple single-object collection.
     */
    @GetMapping("/{serviceName}")
    public ResponseEntity<Map<String, Object>> handleQuery(
            @PathVariable String serviceName,
            HttpServletRequest request) {

        log.info("IMMEDIATE GET /{} from {}", serviceName, request.getRemoteAddr());

        Map<String, String> headers = extractHeaders(request);
        String url = getFullUrl(request);

        // Check for paged query prerequisites
        String queryIntention = getHeaderCaseInsensitive(request, "queryIntention");
        String navPageSizeStr = getHeaderCaseInsensitive(request, "navigationPageSize");

        if ("ALL".equals(queryIntention) && navPageSizeStr != null) {
            return handlePagedQuery(serviceName, request, headers, url, navPageSizeStr);
        }

        // Non-paged: return simple collection
        Map<String, Object> payload = dataGenerator.generateCollection(headers, url);
        payload.put("receivedHeaders", headers);
        log.info("Responding with test collection for GET /{}", serviceName);

        HttpHeaders responseHeaders = buildEchoResponseHeaders(request);
        return ResponseEntity.ok().headers(responseHeaders).body(payload);
    }

    /**
     * Handle an immediate QUERY-BY-ID request (GET single).
     */
    @GetMapping("/{serviceName}/{id}")
    public ResponseEntity<Map<String, Object>> handleQueryById(
            @PathVariable String serviceName,
            @PathVariable String id,
            HttpServletRequest request) {

        log.info("IMMEDIATE GET /{}/{} from {}", serviceName, id, request.getRemoteAddr());

        Map<String, String> headers = extractHeaders(request);
        String url = getFullUrl(request);

        Map<String, Object> payload = dataGenerator.generateSingle(headers, url);
        payload.put("receivedHeaders", headers);
        log.info("Responding with test data for GET /{}/{}", serviceName, id);

        HttpHeaders responseHeaders = buildEchoResponseHeaders(request);
        return ResponseEntity.ok().headers(responseHeaders).body(payload);
    }

    /**
     * Handle PUT requests.
     */
    @PutMapping(value = {"/{serviceName}", "/{serviceName}/{id}"})
    public ResponseEntity<Map<String, Object>> handlePut(
            @PathVariable String serviceName,
            @PathVariable(required = false) String id,
            @RequestBody(required = false) Object body,
            HttpServletRequest request) {

        log.info("IMMEDIATE PUT /{}{} from {}", serviceName, id != null ? "/" + id : "", request.getRemoteAddr());

        Map<String, String> headers = extractHeaders(request);
        String url = getFullUrl(request);

        Map<String, Object> payload = dataGenerator.generateSingle(headers, url);
        payload.put("receivedHeaders", headers);

        HttpHeaders responseHeaders = buildEchoResponseHeaders(request);
        return ResponseEntity.ok().headers(responseHeaders).body(payload);
    }

    /**
     * Handle DELETE requests.
     */
    @DeleteMapping(value = {"/{serviceName}", "/{serviceName}/{id}"})
    public ResponseEntity<Map<String, Object>> handleDelete(
            @PathVariable String serviceName,
            @PathVariable(required = false) String id,
            HttpServletRequest request) {

        log.info("IMMEDIATE DELETE /{}{} from {}", serviceName, id != null ? "/" + id : "", request.getRemoteAddr());

        Map<String, String> headers = extractHeaders(request);
        String url = getFullUrl(request);

        Map<String, Object> payload = dataGenerator.generateSingle(headers, url);
        payload.put("receivedHeaders", headers);

        HttpHeaders responseHeaders = buildEchoResponseHeaders(request);
        return ResponseEntity.ok().headers(responseHeaders).body(payload);
    }

    // ========== Paged Query Logic ==========

    /**
     * Handles a paged GET query per SIF 3.3 §5.14.
     * <p>
     * Reads navigation headers from the request, generates a page of test data,
     * and returns the response with all required SIF navigation response headers:
     * <ul>
     *   <li>{@code navigationPage} — current page number</li>
     *   <li>{@code navigationLastPage} — total number of pages</li>
     *   <li>{@code navigationPageSize} — records per page (echoed from request)</li>
     *   <li>{@code navigationCount} — total record count across all pages</li>
     *   <li>{@code navigationId} — server-side cursor identifier (stable across pages)</li>
     * </ul>
     */
    private ResponseEntity<Map<String, Object>> handlePagedQuery(
            String serviceName,
            HttpServletRequest request,
            Map<String, String> requestHeaders,
            String url,
            String navPageSizeStr) {

        int totalPages = TestDataGenerator.PAGED_TOTAL_PAGES;
        int pageSize = parseIntOrDefault(navPageSizeStr, DEFAULT_PAGE_SIZE);
        int totalRecords = totalPages * pageSize;

        // Determine current page (default to 1 if not specified)
        String navPageStr = getHeaderCaseInsensitive(request, "navigationPage");
        int currentPage = parseIntOrDefault(navPageStr, 1);

        // Clamp page to valid range
        if (currentPage < 1) currentPage = 1;
        if (currentPage > totalPages) {
            // SIF spec: page outside range returns 204 No Content
            log.info("Paged GET /{} — page {} exceeds lastPage {}, returning 204",
                    serviceName, currentPage, totalPages);
            return ResponseEntity.noContent().build();
        }

        // Use navigationId from request if provided (carry forward), otherwise generate new one
        String navIdFromRequest = getHeaderCaseInsensitive(request, "navigationId");
        String navigationId = (navIdFromRequest != null && !navIdFromRequest.isBlank())
                ? navIdFromRequest
                : "nav-" + UUID.randomUUID().toString().substring(0, 12);

        // Generate page data
        Map<String, Object> payload = dataGenerator.generatePagedCollection(currentPage, pageSize, totalPages);

        log.info("Paged GET /{} — page {}/{}, pageSize={}, totalRecords={}, navigationId={}",
                serviceName, currentPage, totalPages, pageSize, totalRecords, navigationId);

        // Build response with all SIF navigation headers
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("navigationPage", String.valueOf(currentPage));
        responseHeaders.set("navigationLastPage", String.valueOf(totalPages));
        responseHeaders.set("navigationPageSize", String.valueOf(pageSize));
        responseHeaders.set("navigationCount", String.valueOf(totalRecords));
        responseHeaders.set("navigationId", navigationId);

        return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(payload);
    }

    // ========== Utility Methods ==========

    /**
     * Parse the {@code X-Echo-Response-Headers} directive and return an
     * {@link HttpHeaders} instance containing the specified key=value pairs.
     * <p>
     * Format: {@code X-Echo-Response-Headers: Key1=Value1,Key2=Value2}
     */
    private HttpHeaders buildEchoResponseHeaders(HttpServletRequest request) {
        HttpHeaders responseHeaders = new HttpHeaders();
        String echoDirective = getHeaderCaseInsensitive(request, "X-Echo-Response-Headers");
        if (echoDirective != null && !echoDirective.isBlank()) {
            for (String pair : echoDirective.split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String key = pair.substring(0, eq).trim();
                    String value = pair.substring(eq + 1).trim();
                    responseHeaders.set(key, value);
                }
            }
        }
        return responseHeaders;
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    private String getFullUrl(HttpServletRequest request) {
        StringBuffer url = request.getRequestURL();
        String query = request.getQueryString();
        if (query != null) {
            url.append('?').append(query);
        }
        return url.toString();
    }

    /**
     * Get a header value with case-insensitive lookup.
     * SIF headers like navigationPage may arrive in various casings.
     */
    private String getHeaderCaseInsensitive(HttpServletRequest request, String headerName) {
        // Try exact match first (most common)
        String value = request.getHeader(headerName);
        if (value != null) return value;

        // Fall back to case-insensitive scan
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.equalsIgnoreCase(headerName)) {
                return request.getHeader(name);
            }
        }
        return null;
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
