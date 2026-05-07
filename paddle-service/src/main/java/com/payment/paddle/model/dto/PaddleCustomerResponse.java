package com.payment.paddle.model.dto;

import lombok.Data;

@Data
public class PaddleCustomerResponse {
    private CustomerData data;

    @Data
    public static class CustomerData {
        private String id;
    }
}
