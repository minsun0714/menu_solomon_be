package com.menusolomon.common.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebConfiguration(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(origin -> !origin.isEmpty()).toList();
        if (this.allowedOrigins.isEmpty() || this.allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("Credential CORS requires explicit origins");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*").allowCredentials(true);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // Numeric path IDs also accept the public IDs returned in API responses.
        registry.addConverter(String.class, Long.class, value ->
                Long.valueOf(value.startsWith("teamRestaurant_") ? value.substring(15)
                        : value.startsWith("vote_") ? value.substring(5)
                        : value.startsWith("team_") ? value.substring(5) : value));
    }
}
