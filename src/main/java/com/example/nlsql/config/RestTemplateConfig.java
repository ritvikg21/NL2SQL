package com.example.nlsql.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class that defines a RestTemplate bean used
 * to call the LLM HTTP API.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // RestTemplate is a simple blocking HTTP client
        // provided by Spring, good enough for this use case.
        return new RestTemplate();
    }
}
