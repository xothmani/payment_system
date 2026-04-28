package com.payment.subscription.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Value("${payment.router.url}")
    private String paymentRouterUrl;

    @Bean("paymentRouterRestClient")
    public RestClient paymentRouterRestClient() {
        return RestClient.builder()
                .baseUrl(paymentRouterUrl)
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
