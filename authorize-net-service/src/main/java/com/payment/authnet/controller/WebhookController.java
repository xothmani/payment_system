package com.payment.authnet.controller;

import com.payment.authnet.model.WebhookEvent;
import com.payment.authnet.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(@RequestBody WebhookEvent event) {
        webhookService.processAsync(event);
        return ResponseEntity.ok().build();
    }
}
