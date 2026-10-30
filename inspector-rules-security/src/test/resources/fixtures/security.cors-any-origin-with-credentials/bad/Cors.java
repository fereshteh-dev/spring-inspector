package com.acme;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class Cors {
    void chained(CorsRegistry registry) {
        registry.addMapping("/**").allowedOriginPatterns("*").allowCredentials(true);
    }

    CorsConfiguration object() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.List.of("*"));
        configuration.setAllowCredentials(true);
        return configuration;
    }

    @CrossOrigin(originPatterns = "*", allowCredentials = "true")
    void annotated() {}
}
