package com.payment.authnet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "authnet")
@Data
public class AuthorizeNetConfig {

    private String apiLoginId;
    private String transactionKey;
    private String environment;
}
