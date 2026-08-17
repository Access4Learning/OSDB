// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for the Requests Connector.
 * 
 * <p>This configuration ensures that controller mappings take precedence over
 * static resource handling, preventing Spring Boot from treating controller
 * paths as static resource paths.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Ensure controller mappings take precedence over static resources
        configurer.setUseTrailingSlashMatch(false);
    }
}
