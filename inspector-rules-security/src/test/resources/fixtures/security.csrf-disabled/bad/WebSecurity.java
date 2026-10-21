package com.acme.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class WebSecurity {

    @Bean
    SecurityFilterChain web(HttpSecurity http) throws Exception {
        return http.formLogin(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    SecurityFilterChain legacy(HttpSecurity http) throws Exception {
        http.csrf().disable();
        return http.build();
    }
}
