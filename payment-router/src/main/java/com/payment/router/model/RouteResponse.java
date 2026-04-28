package com.payment.router.model;

import lombok.Data;

@Data
public class RouteResponse {

    private String transactionId;
    private String gateway;
    private String status;
    private String message;
}
