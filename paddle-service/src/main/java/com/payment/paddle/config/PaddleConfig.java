package com.payment.paddle.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "paddle")
@Data
public class PaddleConfig {
    private String apiKey;
    private String webhookSecret;
    private String baseUrl;
    private String successUrl;
    private String cancelUrl;
}
