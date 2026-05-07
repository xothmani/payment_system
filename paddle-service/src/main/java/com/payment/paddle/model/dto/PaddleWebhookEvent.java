package com.payment.paddle.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class PaddleWebhookEvent {
    @JsonProperty("event_type")
    private String eventType;
    private Map<String, Object> data;
    @JsonProperty("occurred_at")
    private String occurredAt;
}
