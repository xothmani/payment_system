package com.payment.paddle.controller;

import com.payment.paddle.exception.PaddleException;
import com.payment.paddle.service.PaddleSubscriptionService;
import com.payment.paddle.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final PaddleSubscriptionService paddleSubscriptionService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader("Paddle-Signature") String signature) {
        try {
            paddleSubscriptionService.verifyWebhookSignature(signature, rawBody);
        } catch (PaddleException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        webhookService.processAsync(rawBody);
        return ResponseEntity.ok().build();
    }
}
