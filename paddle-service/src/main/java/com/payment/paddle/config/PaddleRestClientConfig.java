package com.payment.paddle.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class PaddleRestClientConfig {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final PaddleConfig paddleConfig;

    @Value("${subscription.service.url}")
    private String subscriptionServiceUrl;

    @Bean("paddleApiClient")
    public RestClient paddleApiClient() {
        return RestClient.builder()
                .baseUrl(paddleConfig.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + paddleConfig.getApiKey())
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null) {
                        request.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean("subscriptionServiceRestClient")
    public RestClient subscriptionServiceRestClient() {
        return RestClient.builder()
                .baseUrl(subscriptionServiceUrl)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null) {
                        request.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
