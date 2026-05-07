package com.payment.paddle.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PaddleCreateSubscriptionRequest {
    private List<PaddleSubscriptionItem> items;
    @JsonProperty("customer_id")
    private String customerId;
    @JsonProperty("currency_code")
    private String currencyCode;
    @JsonProperty("collection_mode")
    private String collectionMode;
}
