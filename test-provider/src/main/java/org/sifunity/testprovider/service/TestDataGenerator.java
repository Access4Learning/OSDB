// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Generates deterministic test data payloads.
 * <p>
 * Every response contains {@code TESTOBJECT} records with a fixed RefId prefix,
 * a timestamped Name, and echo-back of request headers and URL for debugging.
 * <p>
 * For paged queries, generates N records per page with sequential identifiers
 * so each record can be traced to its exact position in the overall result set.
 */
@Component
public class TestDataGenerator {

    private static final String REF_ID = "D3E34B359D75101A8C3D00AA001A1652";

    /** Total number of pages returned for paged queries with queryIntention=ALL. */
    public static final int PAGED_TOTAL_PAGES = 7;

    /**
     * Build a single test object payload with request echo data.
     */
    public Map<String, Object> generateSingle(Map<String, String> requestHeaders, String requestUrl) {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("RefId", REF_ID);
        inner.put("Name", Instant.now() + "-TESTOBJECT");
        inner.put("REQUEST_HEADERS", requestHeaders);
        if (requestUrl != null) {
            inner.put("REQUEST_URL", requestUrl);
        }

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("TESTOBJECT", inner);
        return wrapper;
    }

    /**
     * Build a collection containing a single test object with request echo data.
     */
    public Map<String, Object> generateCollection(Map<String, String> requestHeaders, String requestUrl) {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("RefId", REF_ID);
        inner.put("Name", Instant.now() + "-TESTOBJECT");
        inner.put("REQUEST_HEADERS", requestHeaders);
        if (requestUrl != null) {
            inner.put("REQUEST_URL", requestUrl);
        }

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("TESTOBJECT", List.of(inner));

        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("TESTOBJECTs", wrapper);
        return collection;
    }

    /**
     * Build a paged collection containing {@code pageSize} test objects for the
     * given page number.
     * <p>
     * Each record has a unique sequential identifier based on its position in the
     * overall result set. For example, page 2 with pageSize 5 produces records
     * numbered 6–10 (globalIndex = (page-1)*pageSize + localIndex).
     * <p>
     * Record payload includes:
     * <ul>
     *   <li>{@code RefId} — unique per record: base RefId suffixed with global index</li>
     *   <li>{@code Name} — human-readable: "Page {page} Record {local} (Global #{global})"</li>
     *   <li>{@code echo_PageNumber} — which page this record belongs to</li>
     *   <li>{@code echo_RecordIndexOnPage} — 1-based position within this page</li>
     *   <li>{@code echo_GlobalRecordIndex} — 1-based position in the full result set</li>
     *   <li>{@code echo_TotalPages} — total number of pages</li>
     *   <li>{@code echo_Timestamp} — when this record was generated</li>
     * </ul>
     *
     * <p>The {@code echo_*} fields are purely diagnostic test data echoed into the record body so a
     * human reading a response can see which page/record it came from. They carry no SIF meaning and
     * drive nothing — the real paging contract lives entirely in the SIF navigation response headers
     * ({@code navigationPage}, {@code navigationLastPage}, {@code navigationPageSize},
     * {@code navigationCount}, {@code navigationId}). The {@code echo_} prefix makes that distinction
     * obvious so no one mistakes them for functional paging fields.</p>
     *
     * @param page     current page number (1-based)
     * @param pageSize number of records per page
     * @param totalPages total number of pages in the result set
     * @return a collection payload with {@code pageSize} TESTOBJECT records
     */
    public Map<String, Object> generatePagedCollection(int page, int pageSize, int totalPages) {
        List<Map<String, Object>> records = new ArrayList<>();
        int totalRecords = totalPages * pageSize;
        String timestamp = Instant.now().toString();

        for (int i = 1; i <= pageSize; i++) {
            int globalIndex = (page - 1) * pageSize + i;

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("RefId", REF_ID + "-P" + page + "R" + i + "-G" + globalIndex);
            record.put("Name", String.format("Page %d Record %d (Global #%d of %d)",
                    page, i, globalIndex, totalRecords));
            record.put("echo_PageNumber", page);
            record.put("echo_RecordIndexOnPage", i);
            record.put("echo_GlobalRecordIndex", globalIndex);
            record.put("echo_TotalPages", totalPages);
            record.put("echo_Timestamp", timestamp);

            records.add(record);
        }

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("TESTOBJECT", records);

        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("TESTOBJECTs", wrapper);
        return collection;
    }
}
