// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Base class for TracingUtils implementations.
 * 
 * <p>This allows services to depend on TracingUtilsBase and get either the real
 * implementation (when observability is enabled) or the no-op implementation
 * (when observability is disabled).
 */
public abstract class TracingUtilsBase {
    
    /**
     * Traces a synchronous operation with a custom span.
     * 
     * @param operationName the name of the operation
     * @param attributes custom attributes to add to the span
     * @param operation the operation to execute
     * @param <T> the return type of the operation
     * @return the result of the operation
     */
    public abstract <T> T traceOperation(String operationName, Map<String, String> attributes, Supplier<T> operation);
    
    /**
     * Traces a synchronous operation with a custom span (no return value).
     * 
     * @param operationName the name of the operation
     * @param attributes custom attributes to add to the span
     * @param operation the operation to execute
     */
    public abstract void traceOperation(String operationName, Map<String, String> attributes, Runnable operation);
    
    /**
     * Adds a custom attribute to the current span.
     * 
     * @param key the attribute key
     * @param value the attribute value
     */
    public abstract void addAttribute(String key, String value);
    
    /**
     * Adds a custom event to the current span.
     * 
     * @param eventName the name of the event
     */
    public abstract void addEvent(String eventName);
    
    /**
     * Records an exception in the current span.
     * 
     * @param exception the exception to record
     */
    public abstract void recordException(Exception exception);
}
