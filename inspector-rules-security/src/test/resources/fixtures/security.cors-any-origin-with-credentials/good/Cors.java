package com.acme;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class Cors {
    void listed(CorsRegistry registry) {
        registry.addMapping("/**").allowedOriginPatterns("https://*.example.com").allowCredentials(true);
    }

    void publicApi(CorsRegistry registry) {
        registry.addMapping("/**").allowedOriginPatterns("*");
    }

    void withoutCredentials(CorsRegistry registry) {
        registry.addMapping("/**").allowedOriginPatterns("*").allowCredentials(false);
    }

    @CrossOrigin(originPatterns = "*")
    void annotated() {}
}
