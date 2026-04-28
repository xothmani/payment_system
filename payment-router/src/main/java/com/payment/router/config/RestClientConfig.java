package com.payment.router.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Value("${payment.authorize-net.url}")
    private String authorizeNetUrl;

    @Value("${payment.paddle.url}")
    private String paddleUrl;

    @Bean("authorizeNetRestClient")
    public RestClient authorizeNetRestClient() {
        return RestClient.builder()
                .baseUrl(authorizeNetUrl)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null) {
                        request.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean("paddleRestClient")
    public RestClient paddleRestClient() {
        return RestClient.builder()
                .baseUrl(paddleUrl)
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
