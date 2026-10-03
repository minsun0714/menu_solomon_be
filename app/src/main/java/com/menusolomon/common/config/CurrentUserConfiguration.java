package com.menusolomon.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CurrentUserConfiguration {
    @Bean
    CurrentUserProvider currentUserProvider() {
        return () -> 1L;
    }
}
