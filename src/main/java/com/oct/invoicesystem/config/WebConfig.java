package com.oct.invoicesystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class WebConfig {

    @Value("${webhook.delivery.timeout.seconds:5}")
    private int webhookDeliveryTimeoutSeconds;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(webhookClientHttpRequestFactory());
    }

    @Bean
    public ClientHttpRequestFactory webhookClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = webhookDeliveryTimeoutSeconds * 1000;
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return factory;
    }
}