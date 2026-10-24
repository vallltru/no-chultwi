package com.nochultwi.backend.global.config;

import com.nochultwi.backend.global.filter.RequestIdFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * CORS policy applied by the Spring Security filter chain.
 * <ul>
 *   <li>Explicit origin whitelist only; wildcards are rejected at startup.</li>
 *   <li>No credentials: authentication uses headers (Authorization / X-Device-*), not cookies.</li>
 *   <li>Allows both Admin JWT headers and Kiosk device headers so that the React kiosk web app (PDF 3p)
 *       and local relay programs can communicate with the API without CORS preflight failures.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PATCH", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization",
            "Content-Type",
            "Idempotency-Key",
            "X-Device-Id",
            "X-Device-Key",
            RequestIdFilter.HEADER_NAME
    );
    private static final List<String> EXPOSED_HEADERS = List.of(RequestIdFilter.HEADER_NAME);

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        List<String> allowedOrigins = corsProperties.allowedOrigins();
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed: " + allowedOrigins);
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
