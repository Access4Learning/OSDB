// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.filter.InternalServiceTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration that provides two concerns for internal service token security:
 *
 * <ol>
 *   <li><strong>Startup validator</strong> ({@code @PostConstruct}): Unconditionally rejects
 *       blank or {@code "default-dev-token"} values with {@link IllegalStateException}.
 *       Applies to ALL services including the reactive gateway.</li>
 *   <li><strong>Filter registration</strong>: Registers {@link InternalServiceTokenFilter}
 *       via {@link FilterRegistrationBean} mapped to {@code /internal/*}. Gated with
 *       {@link ConditionalOnWebApplication} (SERVLET only) because the gateway is
 *       Spring Cloud Gateway (reactive WebFlux) where servlet filters don't apply.</li>
 * </ol>
 *
 * <p>Registered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so all modules depending on {@code sif-broker-common} get both automatically.</p>
 *
 * @see InternalServiceTokenFilter
 */
@Slf4j
@Configuration
public class InternalServiceTokenAutoConfiguration {

    private static final String INSECURE_DEFAULT = "default-dev-token";

    @Value("${broker.internal-service-token:}")
    private String internalServiceToken;

    /**
     * Unconditional startup validator — runs on ALL services (servlet and reactive).
     * Rejects blank or insecure default token values to prevent accidental deployment
     * without a proper secret configured.
     */
    @PostConstruct
    public void validateInternalServiceToken() {
        if (internalServiceToken == null || internalServiceToken.isBlank()) {
            throw new IllegalStateException(
                    "broker.internal-service-token must be configured. "
                            + "Set the INTERNAL_SERVICE_TOKEN environment variable to a secure, non-empty value.");
        }
        if (INSECURE_DEFAULT.equals(internalServiceToken)) {
            throw new IllegalStateException(
                    "broker.internal-service-token must not be the insecure default \"" + INSECURE_DEFAULT + "\". "
                            + "Set the INTERNAL_SERVICE_TOKEN environment variable to a proper secret.");
        }
        log.info("Internal service token validated — startup permitted");
    }

    /**
     * Servlet-only filter registration. Gated with {@code @ConditionalOnWebApplication(type = SERVLET)}
     * so that the reactive gateway (WebFlux) does not attempt to register a servlet filter.
     * The gateway hosts no {@code /internal} endpoints — it needs only the startup validator above.
     */
    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletFilterConfiguration {

        @Bean
        public FilterRegistrationBean<InternalServiceTokenFilter> internalServiceTokenFilterRegistration(
                @Value("${broker.internal-service-token:}") String token) {
            FilterRegistrationBean<InternalServiceTokenFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new InternalServiceTokenFilter(token));
            registration.addUrlPatterns("/internal/*");
            registration.setName("internalServiceTokenFilter");
            registration.setOrder(1);
            log.info("Registered InternalServiceTokenFilter for /internal/* URL pattern");
            return registration;
        }
    }
}
