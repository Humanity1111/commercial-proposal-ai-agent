package com.cpprocessor.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class OllamaConfig {

    @Bean
    RestClientCustomizer ollamaTimeoutCustomizer() {
        return builder -> {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(30));
            factory.setReadTimeout(Duration.ofMinutes(10));
            builder.requestFactory(factory);
        };
    }
}
