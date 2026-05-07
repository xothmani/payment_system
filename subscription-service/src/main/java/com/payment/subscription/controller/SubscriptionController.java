package com.payment.subscription.controller;

import com.payment.subscription.model.dto.CancelSubscriptionResponse;
import com.payment.subscription.model.dto.CreateSubscriptionRequest;
import com.payment.subscription.model.dto.CreateSubscriptionResponse;
import com.payment.subscription.model.dto.PlanResponse;
import com.payment.subscription.model.dto.SubscriptionResponse;
import com.payment.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    public List<PlanResponse> getActivePlans() {
        return subscriptionService.getActivePlans();
    }

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

    @PutMapping("/paddle/{paddleSubscriptionId}/status")
    public ResponseEntity<Void> updatePaddleStatus(
            @PathVariable String paddleSubscriptionId,
            @RequestBody Map<String, String> body) {
        subscriptionService.updateStatusByPaddleSubscriptionId(paddleSubscriptionId, body.get("status"));
        return ResponseEntity.ok().build();
    }
}
