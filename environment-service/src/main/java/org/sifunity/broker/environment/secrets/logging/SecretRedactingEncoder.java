// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.logging;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.sifunity.broker.environment.secrets.util.SecretRedactor;

/**
 * Custom Logback encoder that redacts sensitive data from log output.
 * 
 * <p>This encoder wraps the standard PatternLayoutEncoder and applies the
 * {@link SecretRedactor} utility to redact secrets, encryption keys, tokens,
 * and other sensitive data from the formatted log output before it is written
 * to any appender (console, file, network).
 * 
 * <p>The encoder operates after the log message has been formatted according
 * to the pattern, ensuring that all sensitive data is redacted from the final
 * output regardless of where it appears in the log message, exception stack
 * traces, or MDC context.
 * 
 * <p>This encoder can be used as a drop-in replacement for PatternLayoutEncoder
 * in any Logback appender configuration.
 * 
 * <p>Example usage in logback-spring.xml:
 * <pre>
 * &lt;appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender"&gt;
 *   &lt;encoder class="org.sifunity.broker.environment.secrets.logging.SecretRedactingEncoder"&gt;
 *     &lt;pattern&gt;%d{yyyy-MM-dd HH:mm:ss} - %msg%n&lt;/pattern&gt;
 *   &lt;/encoder&gt;
 * &lt;/appender&gt;
 * </pre>
 * 
 * @see SecretRedactor
 * @see PatternLayoutEncoder
 */
public class SecretRedactingEncoder extends PatternLayoutEncoder {
    
    /**
     * Encodes a logging event to bytes with sensitive data redacted.
     * 
     * <p>This method formats the log event using the configured pattern,
     * applies secret redaction to the formatted output, and returns the
     * redacted bytes.
     * 
     * @param event the logging event to encode
     * @return the encoded and redacted log output as bytes
     */
    @Override
    public byte[] encode(ILoggingEvent event) {
        // Use the parent's encode method to get the formatted bytes
        byte[] originalBytes = super.encode(event);
        
        // Handle null case (shouldn't happen in normal operation)
        if (originalBytes == null) {
            return new byte[0];
        }
        
        // Convert to string
        String formattedMessage = new String(originalBytes, getCharset());
        
        // Apply secret redaction
        String redactedMessage = SecretRedactor.redactSecrets(formattedMessage);
        
        // Convert back to bytes using the configured charset
        return redactedMessage.getBytes(getCharset());
    }
}
