package com.payment.paddle.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaddleSubscriptionItem {
    @JsonProperty("price_id")
    private String priceId;
    private int quantity;
}
