// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson ObjectMapper configuration for consistent JSON handling across all SIF Broker services.
 * 
 * <p>This configuration ensures that all services serialize and deserialize JSON identically,
 * following SIF 3.7 specification requirements:
 * <ul>
 *   <li>Property names use camelCase convention</li>
 *   <li>Dates are serialized in ISO-8601 format</li>
 *   <li>Null values are excluded from serialization</li>
 *   <li>Unknown properties cause deserialization to fail (strict mode)</li>
 *   <li>Java 8 date/time types are supported (LocalDateTime, Instant, etc.)</li>
 * </ul>
 * 
 * @see com.fasterxml.jackson.databind.ObjectMapper
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@Configuration
public class JacksonConfiguration {
    
    /**
     * Creates and configures an ObjectMapper bean for consistent JSON serialization/deserialization.
     * 
     * <p>Configuration details:
     * <ul>
     *   <li><b>Property Naming:</b> Uses LOWER_CAMEL_CASE strategy (e.g., "firstName" not "first_name")</li>
     *   <li><b>Date Serialization:</b> Registers JavaTimeModule and disables timestamp format,
     *       ensuring dates are serialized as ISO-8601 strings (e.g., "2024-01-15T10:30:00Z")</li>
     *   <li><b>Null Handling:</b> Excludes null values from JSON output to reduce payload size</li>
     *   <li><b>Strict Deserialization:</b> Fails on unknown properties to catch schema mismatches early</li>
     * </ul>
     * 
     * <p>Example usage:
     * <pre>{@code
     * @Autowired
     * private ObjectMapper objectMapper;
     * 
     * // Serialize DTO to JSON
     * String json = objectMapper.writeValueAsString(environmentDTO);
     * 
     * // Deserialize JSON to DTO
     * EnvironmentDTO dto = objectMapper.readValue(json, EnvironmentDTO.class);
     * }</pre>
     * 
     * @return configured ObjectMapper instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Use camelCase for property names
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        
        // Serialize dates as ISO-8601 strings
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Ignore null values during serialization
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        // Fail on unknown properties during deserialization (strict mode)
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        
        return mapper;
    }
}
