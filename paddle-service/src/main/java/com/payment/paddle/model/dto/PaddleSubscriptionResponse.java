package com.payment.paddle.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PaddleSubscriptionResponse {
    private SubscriptionData data;

    @Data
    public static class SubscriptionData {
        private String id;
        private String status;
        @JsonProperty("next_billed_at")
        private String nextBilledAt;
    }
}
