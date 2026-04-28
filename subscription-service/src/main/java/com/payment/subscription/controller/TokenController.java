package com.payment.subscription.controller;

import com.payment.subscription.model.dto.TokenDeductionRequest;
import com.payment.subscription.model.dto.TokenDeductionResponse;
import com.payment.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/deduct")
    public TokenDeductionResponse deductTokens(@Valid @RequestBody TokenDeductionRequest request) {
        return subscriptionService.deductTokens(
                request.userId(), request.amount(), request.description());
    }
}
