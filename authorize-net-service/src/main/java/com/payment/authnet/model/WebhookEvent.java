package com.payment.authnet.model;

import lombok.Data;

@Data
public class WebhookEvent {

    private String eventType;
    private String notificationId;
    private Object payload;
}
