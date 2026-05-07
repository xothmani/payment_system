package com.payment.paddle.controller;

import com.payment.paddle.model.PaddleSubscriptionResult;
import com.payment.paddle.model.dto.CheckoutRequest;
import com.payment.paddle.model.dto.CheckoutResult;
import com.payment.paddle.model.dto.PaddleCreateCustomerRequest;
import com.payment.paddle.model.dto.PaddleCreateSubscriptionRequest;
import com.payment.paddle.service.PaddleSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CustomerController {

    private final PaddleSubscriptionService paddleSubscriptionService;

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public String createCustomer(@RequestBody PaddleCreateCustomerRequest request) {
        return paddleSubscriptionService.createCustomer(request.getName(), request.getEmail());
    }

    @PostMapping("/customers/{customerId}/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public PaddleSubscriptionResult createSubscription(
            @PathVariable String customerId,
            @RequestBody PaddleCreateSubscriptionRequest request) {
        return paddleSubscriptionService.createSubscription(customerId, request.getItems().get(0).getPriceId());
    }

    @PostMapping("/subscriptions/{paddleSubscriptionId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelSubscription(@PathVariable String paddleSubscriptionId) {
        paddleSubscriptionService.cancelSubscription(paddleSubscriptionId);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResult createCheckout(@Valid @RequestBody CheckoutRequest request) {
        return paddleSubscriptionService.createCheckout(request.priceId(), request.userId(), request.email());
    }
}
