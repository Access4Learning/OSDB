// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic application context test to verify the Spring Boot application starts correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
class ResponsesConnectorApplicationTest {

    /**
     * Verify that the Spring application context loads successfully.
     */
    @Test
    void contextLoads() {
        // This test will fail if the application context cannot be loaded
    }
}
