package com.fijalkoa.biosso.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * HTTP Client Configuration
 * 
 * Provides RestTemplate for synchronous REST calls to Python microservice
 * Provides WebClient for reactive operations (if needed in future)
 */
@Configuration
public class WebClientConfig {

    /**
     * RestTemplate for biometric microservice communication
     * Supports multipart form data for image uploads
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .requestFactory(this::clientHttpRequestFactory)
                .setConnectTimeout(java.time.Duration.ofSeconds(30))
                .setReadTimeout(java.time.Duration.ofSeconds(120))  // Longer timeout for image processing
                .build();
    }

    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);  // 30 seconds
        factory.setReadTimeout(120000);    // 2 minutes (for image processing)
        factory.setBufferRequestBody(true);
        return new BufferingClientHttpRequestFactory(factory);
    }

    /**
     * WebClient for reactive operations (for future use)
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                // increase limit for large images
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                        .build())
                .build();
    }
}
