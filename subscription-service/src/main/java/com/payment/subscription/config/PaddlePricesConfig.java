package com.payment.subscription.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "paddle.prices")
@Data
public class PaddlePricesConfig {
    private String individualMonthly;
    private String individualAnnual;
    private String individualAiMonthly;
    private String individualAiAnnual;
}
