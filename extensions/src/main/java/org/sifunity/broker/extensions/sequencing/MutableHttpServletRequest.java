package org.sifunity.broker.extensions.sequencing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Request wrapper that allows adding headers to an existing
 * {@link HttpServletRequest} without modifying or removing any
 * original headers.
 */
public class MutableHttpServletRequest extends HttpServletRequestWrapper {

    private final Map<String, String> additionalHeaders = new LinkedHashMap<>();

    public MutableHttpServletRequest(HttpServletRequest request) {
        super(request);
    }

    /**
     * Adds a header to this request. If a header with the same name
     * already exists in the additional headers map, the value is replaced.
     * Original request headers are never modified.
     *
     * @param name  header name
     * @param value header value
     */
    public void addHeader(String name, String value) {
        additionalHeaders.put(name, value);
    }

    @Override
    public String getHeader(String name) {
        String value = additionalHeaders.get(name);
        return value != null ? value : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();

        // Add all original header names first
        Enumeration<String> originalNames = super.getHeaderNames();
        if (originalNames != null) {
            while (originalNames.hasMoreElements()) {
                names.add(originalNames.nextElement());
            }
        }

        // Add additional header names
        names.addAll(additionalHeaders.keySet());

        return Collections.enumeration(names);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String additionalValue = additionalHeaders.get(name);
        if (additionalValue != null) {
            return Collections.enumeration(Collections.singletonList(additionalValue));
        }
        return super.getHeaders(name);
    }
}
