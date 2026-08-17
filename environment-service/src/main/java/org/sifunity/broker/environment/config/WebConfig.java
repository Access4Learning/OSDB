// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC configuration for the Environment Service.
 * 
 * <p>Configures Jackson ObjectMapper to include null values in JSON responses.
 * This ensures that optional fields like contextId and description are present
 * in the JSON even when they are null.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.stream()
                .filter(converter -> converter instanceof MappingJackson2HttpMessageConverter)
                .map(converter -> (MappingJackson2HttpMessageConverter) converter)
                .forEach(converter -> {
                    ObjectMapper objectMapper = converter.getObjectMapper();
                    objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
                });
    }
}
