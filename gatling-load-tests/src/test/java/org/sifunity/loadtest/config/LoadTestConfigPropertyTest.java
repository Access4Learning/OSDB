// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based test: for any parameter name and two distinct non-blank values,
 * when both a system property and an environment variable are set, the system
 * property value is returned by stringParam.
 *
 * Since we cannot programmatically set environment variables in a running JVM,
 * we test the precedence logic by verifying that when a system property IS set,
 * stringParam returns the system property value regardless of the default (which
 * simulates the env-var fallback path).
 *
 * Validates: Requirements 3.8
 * Feature: gatling-load-tests, Property 2: Config system property precedence
 */
class LoadTestConfigPropertyTest {

    /**
     * For any parameter name and two distinct non-blank values, when the system
     * property is set to sysPropValue, stringParam returns sysPropValue — not
     * the default (standing in for the env-var path).
     *
     * **Validates: Requirements 3.8**
     */
    @Property(tries = 100)
    @Tag("Feature: gatling-load-tests, Property 2: Config system property precedence")
    void systemPropertyTakesPrecedenceOverDefault(
            @ForAll("paramNames") String paramName,
            @ForAll @NotBlank String sysPropValue,
            @ForAll @NotBlank String envVarValue
    ) {
        Assume.that(!sysPropValue.equals(envVarValue));

        try {
            System.setProperty(paramName, sysPropValue);

            // stringParam should return the system property value, not the
            // envVarValue passed as the default (simulating env-var fallback)
            String resolved = LoadTestConfig.stringParam(paramName, envVarValue);
            assertEquals(sysPropValue, resolved,
                    "System property should take precedence. param=" + paramName
                            + " sysProp=" + sysPropValue + " default=" + envVarValue);
        } finally {
            System.clearProperty(paramName);
        }
    }

    @Provide
    Arbitrary<String> paramNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(30)
                .map(s -> "LOADTEST_PBT_" + s.toUpperCase());
    }
}
