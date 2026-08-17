// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import org.sifunity.broker.queueservice.service.QueueCleanupService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Test helper to invoke package-private methods on QueueCleanupService via reflection
 * and set @Value-injected fields that aren't populated outside Spring context.
 */
final class CleanupTestHelper {

    private CleanupTestHelper() {}

    /**
     * Set the configuration fields that are normally injected by Spring @Value.
     */
    static void setConfigFields(QueueCleanupService service) {
        try {
            setField(service, "batchSize", 1000);
            setField(service, "progressLogInterval", 10000);
            setField(service, "interBatchYieldMs", 0L); // no sleep in tests
        } catch (Exception e) {
            throw new RuntimeException("Failed to set config fields", e);
        }
    }

    /**
     * Invoke the package-private {@code cleanupStream} method on QueueCleanupService.
     */
    static void invokeCleanupStream(QueueCleanupService service,
                                    String streamKey, String domainId,
                                    String solutionId, String progressKey) {
        try {
            Method method = QueueCleanupService.class.getDeclaredMethod(
                    "cleanupStream", String.class, String.class, String.class, String.class);
            method.setAccessible(true);
            method.invoke(service, streamKey, domainId, solutionId, progressKey);
        } catch (Exception e) {
            if (e.getCause() != null) {
                throw new RuntimeException("cleanupStream failed: " + e.getCause().getMessage(), e.getCause());
            }
            throw new RuntimeException("Failed to invoke cleanupStream", e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
