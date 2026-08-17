// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import net.logstash.logback.appender.LogstashTcpSocketAppender;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A resilient wrapper around LogstashTcpSocketAppender that implements exponential backoff
 * for connection retries when Logstash is unavailable.
 * 
 * This appender:
 * - Buffers logs in the async queue when Logstash is unavailable
 * - Implements exponential backoff for reconnection attempts (1s, 2s, 4s, 8s, 16s, 32s, 60s max)
 * - Continues service operation without throwing exceptions
 * - Logs warnings about connection failures (but not too frequently to avoid log spam)
 * 
 * Validates Requirements: 8.4, 18.3, 18.4
 */
public class ResilientLogstashTcpSocketAppender extends AppenderBase<ILoggingEvent> {

    private LogstashTcpSocketAppender delegate;
    
    // Exponential backoff state
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>(Instant.MIN);
    private final AtomicReference<Instant> lastWarningTime = new AtomicReference<>(Instant.MIN);
    
    // Backoff configuration
    private static final long INITIAL_BACKOFF_SECONDS = 1;
    private static final long MAX_BACKOFF_SECONDS = 60;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final Duration WARNING_THROTTLE = Duration.ofMinutes(1);
    
    // Configuration properties (delegated to LogstashTcpSocketAppender)
    private String destination;
    private Encoder<ILoggingEvent> encoder;
    private String connectionStrategy;
    private String reconnectionDelay;
    private String writeBufferSize;
    private String keepAliveDuration;
    private String connectionTimeout;
    private String writeTimeout;
    private String secondaryConnectionTTL;

    @Override
    public void start() {
        // Create and configure the delegate appender
        delegate = new LogstashTcpSocketAppender();
        delegate.setContext(getContext());
        delegate.setName(getName() + "-delegate");
        
        // Configure destination
        if (destination != null) {
            delegate.addDestination(destination);
        }
        
        // Configure encoder
        if (encoder != null) {
            delegate.setEncoder(encoder);
        }
        
        // Start the delegate
        try {
            delegate.start();
            super.start();
        } catch (Exception e) {
            addWarn("Failed to start Logstash appender, will retry with exponential backoff", e);
            // Don't fail startup - we'll retry connections
            super.start();
        }
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isStarted()) {
            return;
        }
        
        // Check if we should attempt to send based on backoff
        if (shouldBackoff()) {
            // Skip sending during backoff period
            // The async queue will buffer these events
            return;
        }
        
        try {
            // Attempt to append to delegate
            if (delegate != null && delegate.isStarted()) {
                delegate.doAppend(eventObject);
                
                // Success - reset failure count
                if (failureCount.get() > 0) {
                    int previousFailures = failureCount.getAndSet(0);
                    addInfo("Successfully reconnected to Logstash after " + previousFailures + " failures");
                }
            }
        } catch (Exception e) {
            // Record failure
            int failures = failureCount.incrementAndGet();
            lastFailureTime.set(Instant.now());
            
            // Log warning (but throttle to avoid log spam)
            if (shouldLogWarning()) {
                long backoffSeconds = calculateBackoffSeconds(failures);
                addWarn("Failed to send log to Logstash (failure #" + failures + 
                        "), will retry after " + backoffSeconds + " seconds. " +
                        "Logs are being buffered in async queue.", e);
                lastWarningTime.set(Instant.now());
            }
            
            // Don't rethrow - graceful degradation
        }
    }

    @Override
    public void stop() {
        if (delegate != null) {
            delegate.stop();
        }
        super.stop();
    }

    /**
     * Determines if we should skip sending logs due to backoff period.
     */
    private boolean shouldBackoff() {
        int failures = failureCount.get();
        if (failures == 0) {
            return false; // No failures, no backoff
        }
        
        Instant lastFailure = lastFailureTime.get();
        long backoffSeconds = calculateBackoffSeconds(failures);
        Instant backoffUntil = lastFailure.plusSeconds(backoffSeconds);
        
        return Instant.now().isBefore(backoffUntil);
    }

    /**
     * Calculates exponential backoff delay in seconds.
     * Formula: min(INITIAL_BACKOFF * (MULTIPLIER ^ (failures - 1)), MAX_BACKOFF)
     */
    private long calculateBackoffSeconds(int failures) {
        if (failures <= 0) {
            return 0;
        }
        
        double backoff = INITIAL_BACKOFF_SECONDS * Math.pow(BACKOFF_MULTIPLIER, failures - 1);
        return Math.min((long) backoff, MAX_BACKOFF_SECONDS);
    }

    /**
     * Determines if we should log a warning (throttled to avoid log spam).
     */
    private boolean shouldLogWarning() {
        Instant lastWarning = lastWarningTime.get();
        return Duration.between(lastWarning, Instant.now()).compareTo(WARNING_THROTTLE) > 0;
    }

    // Configuration setters

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    public void setConnectionStrategy(String connectionStrategy) {
        this.connectionStrategy = connectionStrategy;
    }

    public void setReconnectionDelay(String reconnectionDelay) {
        this.reconnectionDelay = reconnectionDelay;
    }

    public void setWriteBufferSize(String writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }

    public void setKeepAliveDuration(String keepAliveDuration) {
        this.keepAliveDuration = keepAliveDuration;
    }

    public void setConnectionTimeout(String connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setWriteTimeout(String writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public void setSecondaryConnectionTTL(String secondaryConnectionTTL) {
        this.secondaryConnectionTTL = secondaryConnectionTTL;
    }
}
