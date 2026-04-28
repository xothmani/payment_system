package com.payment.subscription.controller;

import com.payment.subscription.model.dto.CancelSubscriptionResponse;
import com.payment.subscription.model.dto.CreateSubscriptionRequest;
import com.payment.subscription.model.dto.CreateSubscriptionResponse;
import com.payment.subscription.model.dto.SubscriptionResponse;
import com.payment.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSubscriptionResponse createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request) {
        return subscriptionService.createSubscription(request);
    }

    @GetMapping("/{id}")
    public SubscriptionResponse getSubscription(@PathVariable UUID id) {
        return subscriptionService.getSubscription(id);
    }

    @DeleteMapping("/{id}")
    public CancelSubscriptionResponse cancelSubscription(@PathVariable UUID id) {
        return subscriptionService.cancelSubscription(id);
    }
}
