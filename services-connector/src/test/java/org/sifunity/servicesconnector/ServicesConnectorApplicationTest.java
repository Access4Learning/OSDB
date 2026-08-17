// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic test to verify the Spring Boot application context loads successfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class ServicesConnectorApplicationTest {

    @Test
    void contextLoads() {
        // This test verifies that the Spring application context loads successfully
        // If the context fails to load, this test will fail
    }
}
