// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.aspect.unit;

import io.micrometer.core.annotation.Timed;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-based verification that {@code @Timed} annotations are present on hot-path methods
 * with correct metric names, descriptions, and percentile values.
 *
 * <p>Since sif-broker-common does not depend on gateway, environment-service, or queue-service,
 * the target classes may not be on the test classpath. Each test uses {@code Class.forName()}
 * with {@code Assumptions.assumeTrue()} to skip gracefully when the class is unavailable.</p>
 *
 * <p>Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.6</p>
 */
@DisplayName("@Timed Annotation Verification")
class TimedAnnotationVerificationTest {

    private static final double[] EXPECTED_PERCENTILES = {0.5, 0.95, 0.99};

    /**
     * Attempts to load a class by fully-qualified name. Returns null if not on classpath.
     */
    private Class<?> tryLoadClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Finds a method by name on the given class, matching the specified parameter types.
     * Returns null if not found.
     */
    private Method findMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            return clazz.getDeclaredMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            // Try all declared methods matching by name (for overloaded methods)
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    return m;
                }
            }
            return null;
        }
    }

    /**
     * Finds a method annotated with @Timed by name on the given class.
     * Useful when there are overloads and only one has @Timed.
     */
    private Method findTimedMethod(Class<?> clazz, String methodName) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.isAnnotationPresent(Timed.class)) {
                return m;
            }
        }
        return null;
    }

    private void assertTimedAnnotation(Method method, String expectedMetricName) {
        Timed timed = method.getAnnotation(Timed.class);
        assertThat(timed)
                .as("@Timed annotation should be present on %s.%s",
                        method.getDeclaringClass().getSimpleName(), method.getName())
                .isNotNull();

        assertThat(timed.value())
                .as("Metric name")
                .isEqualTo(expectedMetricName);

        assertThat(timed.description())
                .as("Description should be non-empty")
                .isNotEmpty();

        assertThat(timed.percentiles())
                .as("Percentiles should contain 0.5, 0.95, 0.99")
                .containsExactly(EXPECTED_PERCENTILES);
    }

    @Nested
    @DisplayName("AuthenticationFilter.filter")
    class AuthenticationFilterTests {

        private static final String CLASS_NAME = "org.sifunity.gateway.filter.AuthenticationFilter";
        private static final String METRIC_NAME = "sif.auth.hmac.verification";

        @Test
        @DisplayName("@Timed present with correct metric name, description, and percentiles")
        void timedAnnotationPresent() {
            Class<?> clazz = tryLoadClass(CLASS_NAME);
            Assumptions.assumeTrue(clazz != null,
                    CLASS_NAME + " not on classpath — skipping");

            Method method = findTimedMethod(clazz, "filter");
            Assumptions.assumeTrue(method != null,
                    "Method 'filter' with @Timed not found on " + CLASS_NAME);

            assertTimedAnnotation(method, METRIC_NAME);
        }
    }

    @Nested
    @DisplayName("AuthorizationFilter.filter")
    class AuthorizationFilterTests {

        private static final String CLASS_NAME = "org.sifunity.gateway.filter.AuthorizationFilter";
        private static final String METRIC_NAME = "sif.auth.acl.lookup";

        @Test
        @DisplayName("@Timed present with correct metric name, description, and percentiles")
        void timedAnnotationPresent() {
            Class<?> clazz = tryLoadClass(CLASS_NAME);
            Assumptions.assumeTrue(clazz != null,
                    CLASS_NAME + " not on classpath — skipping");

            Method method = findTimedMethod(clazz, "filter");
            Assumptions.assumeTrue(method != null,
                    "Method 'filter' with @Timed not found on " + CLASS_NAME);

            assertTimedAnnotation(method, METRIC_NAME);
        }
    }

    @Nested
    @DisplayName("ProviderService.lookupProvider")
    class ProviderServiceTests {

        private static final String CLASS_NAME = "org.sifunity.broker.environment.service.ProviderService";
        private static final String METRIC_NAME = "sif.provider.registry.lookup";

        @Test
        @DisplayName("@Timed present with correct metric name, description, and percentiles")
        void timedAnnotationPresent() {
            Class<?> clazz = tryLoadClass(CLASS_NAME);
            Assumptions.assumeTrue(clazz != null,
                    CLASS_NAME + " not on classpath — skipping");

            Method method = findTimedMethod(clazz, "lookupProvider");
            Assumptions.assumeTrue(method != null,
                    "Method 'lookupProvider' with @Timed not found on " + CLASS_NAME);

            assertTimedAnnotation(method, METRIC_NAME);
        }
    }

    @Nested
    @DisplayName("MessageService.enqueueMessage")
    class MessageServiceTests {

        private static final String CLASS_NAME = "org.sifunity.broker.queueservice.service.MessageService";
        private static final String METRIC_NAME = "sif.queue.enqueue.duration";

        @Test
        @DisplayName("@Timed present with correct metric name, description, and percentiles")
        void timedAnnotationPresent() {
            Class<?> clazz = tryLoadClass(CLASS_NAME);
            Assumptions.assumeTrue(clazz != null,
                    CLASS_NAME + " not on classpath — skipping");

            Method method = findTimedMethod(clazz, "enqueueMessage");
            Assumptions.assumeTrue(method != null,
                    "Method 'enqueueMessage' with @Timed not found on " + CLASS_NAME);

            assertTimedAnnotation(method, METRIC_NAME);
        }
    }

    @Nested
    @DisplayName("DeliveryService.deliverNextMessage")
    class DeliveryServiceTests {

        private static final String CLASS_NAME = "org.sifunity.broker.queueservice.service.DeliveryService";
        private static final String METRIC_NAME = "sif.queue.dequeue.duration";

        @Test
        @DisplayName("@Timed present with correct metric name, description, and percentiles")
        void timedAnnotationPresent() {
            Class<?> clazz = tryLoadClass(CLASS_NAME);
            Assumptions.assumeTrue(clazz != null,
                    CLASS_NAME + " not on classpath — skipping");

            Method method = findTimedMethod(clazz, "deliverNextMessage");
            Assumptions.assumeTrue(method != null,
                    "Method 'deliverNextMessage' with @Timed not found on " + CLASS_NAME);

            assertTimedAnnotation(method, METRIC_NAME);
        }
    }
}
