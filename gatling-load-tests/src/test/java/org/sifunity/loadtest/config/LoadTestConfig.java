// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.config;

/**
 * Centralized configuration for all load test parameters.
 * Resolution order: Java system property → environment variable → default value.
 */
public final class LoadTestConfig {

    public static final int ZONE_COUNT = intParam("ZONE_COUNT", 300);
    public static final int DURATION_MINUTES = intParam("DURATION_MINUTES", 5);
    public static final int RECORDS_PER_EVENT = intParam("RECORDS_PER_EVENT", 25);
    public static final int THREAD_COUNT = intParam("THREAD_COUNT", 10);
    public static final int CONNECTIONS_PER_CONSUMER = intParam("CONNECTIONS_PER_CONSUMER", 3);
    public static final String GATEWAY_URL = stringParam("GATEWAY_URL", "http://localhost:8080");
    public static final String ADMIN_SESSION_TOKEN = stringParam("ADMIN_SESSION_TOKEN", "admin-bootstrap-token");
    public static final String ADMIN_SHARED_SECRET = stringParam("ADMIN_SHARED_SECRET", "admin-bootstrap-secret");

    private LoadTestConfig() {
        // utility class
    }

    /**
     * Resolve a string parameter by name.
     * System property takes precedence over environment variable, with fallback to default.
     * Package-private to allow direct testing of resolution logic.
     */
    static String stringParam(String name, String defaultValue) {
        String sysProp = System.getProperty(name);
        if (sysProp != null && !sysProp.isBlank()) return sysProp;
        String envVar = System.getenv(name);
        if (envVar != null && !envVar.isBlank()) return envVar;
        return defaultValue;
    }

    /**
     * Resolve an integer parameter by name.
     * Delegates to {@link #stringParam} then parses the result.
     */
    static int intParam(String name, int defaultValue) {
        String value = stringParam(name, null);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }
}
