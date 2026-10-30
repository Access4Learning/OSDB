// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests verifying default values for all LoadTestConfig parameters.
 */
class LoadTestConfigTest {

    @Test
    void zoneCountDefaultIs300() {
        assertEquals(300, LoadTestConfig.ZONE_COUNT);
    }

    @Test
    void durationMinutesDefaultIs5() {
        assertEquals(5, LoadTestConfig.DURATION_MINUTES);
    }

    @Test
    void recordsPerEventDefaultIs1() {
        assertEquals(1, LoadTestConfig.RECORDS_PER_EVENT);
    }

    @Test
    void threadCountDefaultIs10() {
        assertEquals(10, LoadTestConfig.THREAD_COUNT);
    }

    @Test
    void connectionsPerConsumerDefaultIs3() {
        assertEquals(3, LoadTestConfig.CONNECTIONS_PER_CONSUMER);
    }

    @Test
    void gatewayUrlDefaultIsLocalhost8080() {
        assertEquals("http://localhost:8080", LoadTestConfig.GATEWAY_URL);
    }

    @Test
    void adminSessionTokenDefaultIsBootstrapToken() {
        assertEquals("admin-bootstrap-token", LoadTestConfig.ADMIN_SESSION_TOKEN);
    }

    @Test
    void adminSharedSecretDefaultIsBootstrapSecret() {
        assertEquals("admin-bootstrap-secret", LoadTestConfig.ADMIN_SHARED_SECRET);
    }

    @Test
    void stringParamReturnsDefaultWhenNoSystemPropertyOrEnvVar() {
        String unique = "LOADTEST_NONEXISTENT_PARAM_" + System.nanoTime();
        assertEquals("fallback", LoadTestConfig.stringParam(unique, "fallback"));
    }

    @Test
    void intParamReturnsDefaultWhenNoSystemPropertyOrEnvVar() {
        String unique = "LOADTEST_NONEXISTENT_INT_" + System.nanoTime();
        assertEquals(42, LoadTestConfig.intParam(unique, 42));
    }

    @Test
    void stringParamReturnsSystemPropertyWhenSet() {
        String key = "LOADTEST_TEST_PARAM_" + System.nanoTime();
        try {
            System.setProperty(key, "from-sysprop");
            assertEquals("from-sysprop", LoadTestConfig.stringParam(key, "default"));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void intParamReturnsSystemPropertyWhenSet() {
        String key = "LOADTEST_TEST_INT_" + System.nanoTime();
        try {
            System.setProperty(key, "99");
            assertEquals(99, LoadTestConfig.intParam(key, 1));
        } finally {
            System.clearProperty(key);
        }
    }
}
