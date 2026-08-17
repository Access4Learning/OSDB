// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.function.Supplier;

/**
 * No-op implementation of TracingUtils when observability is disabled.
 * 
 * <p>This implementation provides the same API as TracingUtils but does nothing,
 * allowing services to use tracing utilities without requiring OpenTelemetry to be configured.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "observability.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpTracingUtils extends TracingUtilsBase {
    
    public NoOpTracingUtils() {
        log.debug("Using no-op TracingUtils (observability disabled)");
    }
    
    @Override
    public <T> T traceOperation(String operationName, Map<String, String> attributes, Supplier<T> operation) {
        // Simply execute the operation without tracing
        return operation.get();
    }
    
    @Override
    public void traceOperation(String operationName, Map<String, String> attributes, Runnable operation) {
        // Simply execute the operation without tracing
        operation.run();
    }
    
    @Override
    public void addAttribute(String key, String value) {
        // No-op
    }
    
    @Override
    public void addEvent(String eventName) {
        // No-op
    }
    
    @Override
    public void recordException(Exception exception) {
        // No-op
    }
}
