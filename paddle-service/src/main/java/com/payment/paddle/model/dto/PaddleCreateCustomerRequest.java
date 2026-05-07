package com.payment.paddle.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaddleCreateCustomerRequest {
    private String email;
    private String name;
}
