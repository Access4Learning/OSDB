// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.sifunity.broker.common.config.HeaderBlacklistProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Filters HTTP headers against the configured blacklist and structured SIF headers.
 *
 * <p>This component removes security-sensitive headers (from the blacklist) and
 * structured SIF headers (already carried in the {@code SifMessageEnvelope}) from
 * incoming header maps. The sanitizer never mutates the original input.</p>
 *
 * @see HeaderBlacklistProperties
 */
@Component
public class HeaderSanitizer {

    /**
     * Structured SIF header names that are already represented as first-class
     * fields on {@code SifMessageEnvelope}. These are excluded from the
     * passthrough map to avoid duplication.
     */
    static final Set<String> STRUCTURED_SIF_HEADERS;

    static {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.add("messageid");
        set.add("messagetype");
        set.add("servicename");
        set.add("servicetype");
        set.add("zoneid");
        set.add("contextid");
        set.add("requestid");
        set.add("queueid");
        set.add("sourceid");
        STRUCTURED_SIF_HEADERS = Collections.unmodifiableSet(set);
    }

    private final Set<String> effectiveBlacklist;

    public HeaderSanitizer(HeaderBlacklistProperties props) {
        this.effectiveBlacklist = props.getEffectiveBlacklist();
    }

    /**
     * Returns a new map containing only non-blacklisted, non-SIF-structured headers.
     * Does NOT mutate the input.
     *
     * @param incoming the raw header map (may be {@code null})
     * @return a new {@link LinkedHashMap} with allowed headers only
     */
    public Map<String, String> sanitize(Map<String, String> incoming) {
        if (incoming == null) {
            return new LinkedHashMap<>();
        }

        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key == null || value == null) {
                continue;
            }
            if (effectiveBlacklist.contains(key)) {
                continue;
            }
            if (STRUCTURED_SIF_HEADERS.contains(key)) {
                continue;
            }
            result.put(key, value);
        }
        return result;
    }

    /**
     * Extracts all headers from the given {@link HttpServletRequest} and delegates
     * to {@link #sanitize(Map)}.
     *
     * @param request the servlet request (may be {@code null})
     * @return a new {@link LinkedHashMap} with allowed headers only
     */
    public Map<String, String> sanitize(HttpServletRequest request) {
        if (request == null) {
            return new LinkedHashMap<>();
        }

        Map<String, String> headerMap = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                headerMap.put(name, request.getHeader(name));
            }
        }
        return sanitize(headerMap);
    }
}
