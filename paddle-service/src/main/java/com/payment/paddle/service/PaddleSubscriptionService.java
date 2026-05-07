package com.payment.paddle.service;

import com.payment.paddle.config.PaddleConfig;
import com.payment.paddle.exception.PaddleException;
import com.payment.paddle.model.PaddleSubscriptionResult;
import com.payment.paddle.model.dto.*;

import java.util.Map;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

@Service
@Slf4j
public class PaddleSubscriptionService {

    private final RestClient paddleApiClient;
    private final PaddleConfig paddleConfig;

    public PaddleSubscriptionService(
            @Qualifier("paddleApiClient") RestClient paddleApiClient,
            PaddleConfig paddleConfig) {
        this.paddleApiClient = paddleApiClient;
        this.paddleConfig = paddleConfig;
    }

    @CircuitBreaker(name = "gatewayCircuitBreaker", fallbackMethod = "fallbackCustomer")
    @Retry(name = "gatewayRetry")
    public String createCustomer(String userId, String email) {
        log.info("[correlationId={}] Creating Paddle customer for userId={}", MDC.get("correlationId"), userId);
        try {
            PaddleCustomerResponse response = paddleApiClient.post()
                    .uri("/customers")
                    .body(new PaddleCreateCustomerRequest(email, "user_" + userId))
                    .retrieve()
                    .body(PaddleCustomerResponse.class);
            if (response == null || response.getData() == null) {
                throw new PaddleException("Empty response from Paddle createCustomer");
            }
            String customerId = response.getData().getId();
            log.info("[correlationId={}] Created Paddle customer customerId={}", MDC.get("correlationId"), customerId);
            return customerId;
        } catch (PaddleException e) {
            throw e;
        } catch (Exception e) {
            log.error("[correlationId={}] createCustomer failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new PaddleException("createCustomer error: " + e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = "gatewayCircuitBreaker", fallbackMethod = "fallbackSubscription")
    @Retry(name = "gatewayRetry")
    public PaddleSubscriptionResult createSubscription(String customerId, String priceId) {
        log.info("[correlationId={}] Creating Paddle subscription for customerId={} priceId={}",
                MDC.get("correlationId"), customerId, priceId);
        try {
            PaddleSubscriptionResponse response = paddleApiClient.post()
                    .uri("/subscriptions")
                    .body(new PaddleCreateSubscriptionRequest(
                            List.of(new PaddleSubscriptionItem(priceId, 1)),
                            customerId, "USD", "automatic"))
                    .retrieve()
                    .body(PaddleSubscriptionResponse.class);
            if (response == null || response.getData() == null) {
                throw new PaddleException("Empty response from Paddle createSubscription");
            }
            PaddleSubscriptionResponse.SubscriptionData data = response.getData();
            log.info("[correlationId={}] Created Paddle subscription subscriptionId={}",
                    MDC.get("correlationId"), data.getId());
            return new PaddleSubscriptionResult(data.getId(), data.getStatus(), data.getNextBilledAt());
        } catch (PaddleException e) {
            throw e;
        } catch (Exception e) {
            log.error("[correlationId={}] createSubscription failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new PaddleException("createSubscription error: " + e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = "gatewayCircuitBreaker", fallbackMethod = "fallbackVoid")
    @Retry(name = "gatewayRetry")
    public void cancelSubscription(String paddleSubscriptionId) {
        log.info("[correlationId={}] Cancelling Paddle subscription subscriptionId={}",
                MDC.get("correlationId"), paddleSubscriptionId);
        try {
            paddleApiClient.post()
                    .uri("/subscriptions/{id}/cancel", paddleSubscriptionId)
                    .body(new PaddleCancelRequest("next_billing_period"))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[correlationId={}] Cancelled Paddle subscription subscriptionId={}",
                    MDC.get("correlationId"), paddleSubscriptionId);
        } catch (PaddleException e) {
            throw e;
        } catch (Exception e) {
            log.error("[correlationId={}] cancelSubscription failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new PaddleException("cancelSubscription error: " + e.getMessage(), e);
        }
    }

    public void verifyWebhookSignature(String signatureHeader, String rawBody) {
        try {
            String ts = null;
            String h1 = null;
            for (String part : signatureHeader.split(";")) {
                if (part.startsWith("ts=")) ts = part.substring(3);
                if (part.startsWith("h1=")) h1 = part.substring(3);
            }
            if (ts == null || h1 == null) {
                throw new PaddleException("Invalid Paddle-Signature header format");
            }
            String signedPayload = ts + ":" + rawBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    paddleConfig.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            if (!computed.equals(h1)) {
                throw new PaddleException("Webhook signature mismatch");
            }
        } catch (PaddleException e) {
            throw e;
        } catch (Exception e) {
            throw new PaddleException("Webhook signature verification failed: " + e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = "gatewayCircuitBreaker", fallbackMethod = "fallbackCheckout")
    @Retry(name = "gatewayRetry")
    public CheckoutResult createCheckout(String priceId, String userId, String email) {
        log.info("[correlationId={}] Creating Paddle checkout for userId={} priceId={}",
                MDC.get("correlationId"), userId, priceId);
        try {
            record Item(String price_id, int quantity) {}
            record CheckoutConfig(String url) {}
            record TransactionRequest(List<Item> items, String customer_email,
                                      Map<String, String> custom_data, CheckoutConfig checkout) {}
            record CheckoutData(String url) {}
            record TransactionData(String id, CheckoutData checkout) {}
            record TransactionResponse(TransactionData data) {}

            TransactionResponse response = paddleApiClient.post()
                    .uri("/transactions")
                    .body(new TransactionRequest(
                            List.of(new Item(priceId, 1)),
                            email,
                            Map.of("userId", userId),
                            new CheckoutConfig(paddleConfig.getSuccessUrl())))
                    .retrieve()
                    .body(TransactionResponse.class);

            if (response == null || response.data() == null) {
                throw new PaddleException("Empty response from Paddle createCheckout");
            }

            String checkoutUrl = response.data().checkout() != null ? response.data().checkout().url() : null;
            String transactionId = response.data().id();
            log.info("[correlationId={}] Created Paddle checkout transactionId={}",
                    MDC.get("correlationId"), transactionId);
            return new CheckoutResult(checkoutUrl, transactionId);
        } catch (PaddleException e) {
            throw e;
        } catch (Exception e) {
            log.error("[correlationId={}] createCheckout failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new PaddleException("createCheckout error: " + e.getMessage(), e);
        }
    }

    public String fallbackCustomer(String userId, String email, Throwable t) {
        throw new PaddleException("Paddle unavailable: " + t.getMessage(), t);
    }

    public PaddleSubscriptionResult fallbackSubscription(String customerId, String priceId, Throwable t) {
        throw new PaddleException("Paddle unavailable: " + t.getMessage(), t);
    }

    public void fallbackVoid(String paddleSubscriptionId, Throwable t) {
        throw new PaddleException("Paddle unavailable: " + t.getMessage(), t);
    }

    public CheckoutResult fallbackCheckout(String priceId, String userId, String email, Throwable t) {
        throw new PaddleException("Paddle unavailable: " + t.getMessage(), t);
    }
}
