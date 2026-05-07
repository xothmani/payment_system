package com.payment.paddle.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaddleCancelRequest {
    @JsonProperty("effective_from")
    private String effectiveFrom;
}
