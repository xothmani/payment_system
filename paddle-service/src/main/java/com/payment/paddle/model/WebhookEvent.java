package com.payment.paddle.model;

import lombok.Data;

@Data
public class WebhookEvent {

    private String eventType;
    private String eventId;
    private Object data;
}
