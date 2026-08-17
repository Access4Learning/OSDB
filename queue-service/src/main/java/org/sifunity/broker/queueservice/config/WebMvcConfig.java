// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Handles SIF matrix parameters in request URIs.
 *
 * <p>The SIF spec uses matrix parameter syntax for deleteMessageId (legacy/deprecated):
 * <pre>GET /messages;deleteMessageId={id}</pre>
 *
 * <p>Spring's default {@code UrlPathHelper} strips everything after {@code ;} from the path,
 * which breaks route matching. This filter strips the matrix portion from the URI for clean
 * routing, and stores the original URI as a request attribute so controllers can extract
 * matrix parameters directly.</p>
 *
 * <p>SIF 3.7+ clients should use the {@code deleteMessageId} HTTP header instead.</p>
 */
@Configuration
public class WebMvcConfig {

    /** Request attribute holding the original URI before matrix-param stripping. */
    public static final String ORIGINAL_URI_ATTR = "org.sifunity.broker.ORIGINAL_URI";

    @Bean
    public FilterRegistrationBean<Filter> matrixParamFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MatrixParamFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }

    /**
     * Servlet filter that strips matrix parameters (;key=value) from the URI path
     * for clean Spring MVC routing, and stores the original URI as a request attribute
     * so controllers can extract matrix parameters when needed.
     */
    static class MatrixParamFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (request instanceof HttpServletRequest httpReq) {
                String uri = httpReq.getRequestURI();
                int semiIdx = uri.indexOf(';');
                if (semiIdx >= 0) {
                    String cleanPath = uri.substring(0, semiIdx);

                    // Store original URI so controllers can extract matrix params
                    httpReq.setAttribute(ORIGINAL_URI_ATTR, uri);

                    HttpServletRequest wrapped = new HttpServletRequestWrapper(httpReq) {
                        @Override
                        public String getRequestURI() {
                            return cleanPath;
                        }

                        @Override
                        public String getServletPath() {
                            return cleanPath;
                        }
                    };
                    chain.doFilter(wrapped, response);
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }
}
