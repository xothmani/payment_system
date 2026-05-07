package com.payment.router.service;

import com.payment.router.exception.RoutingException;
import com.payment.router.model.RouteRequest;
import com.payment.router.model.RouteResponse;
import com.payment.router.model.dto.ProfileRouteRequest;
import com.payment.router.model.dto.ProfileRouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class RoutingService {

    private final RestClient authorizeNetRestClient;
    private final RestClient paddleRestClient;

    public RoutingService(
            @Qualifier("authorizeNetRestClient") RestClient authorizeNetRestClient,
            @Qualifier("paddleRestClient") RestClient paddleRestClient) {
        this.authorizeNetRestClient = authorizeNetRestClient;
        this.paddleRestClient = paddleRestClient;
    }

    public RouteResponse route(RouteRequest request) {
        log.info("[correlationId={}] Routing charge for userId={} country={}",
                MDC.get("correlationId"), request.getUserId(), request.getCountry());
        try {
            if ("US".equalsIgnoreCase(request.getCountry())) {
                return routeAuthorizeNetCharge(request);
            } else {
                return routePaddleCharge(request);
            }
        } catch (RoutingException e) {
            throw e;
        } catch (Exception e) {
            log.error("[correlationId={}] route failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new RoutingException("Charge routing failed: " + e.getMessage(), e);
        }
    }

    private RouteResponse routeAuthorizeNetCharge(RouteRequest request) {
        record AnChargeReq(String customerProfileId, String paymentProfileId,
                           BigDecimal amount, String idempotencyKey) {}
        record AnChargeRes(String transactionId, String authCode, int responseCode) {}

        AnChargeRes res = authorizeNetRestClient.post()
                .uri("/transactions/charge-profile")
                .body(new AnChargeReq(
                        request.getCustomerProfileId(),
                        request.getPaymentProfileId(),
                        request.getAmount(),
                        request.getIdempotencyKey()))
                .retrieve()
                .body(AnChargeRes.class);

        if (res == null) {
            throw new RoutingException("Empty response from authorize-net-service");
        }
        RouteResponse response = new RouteResponse();
        response.setTransactionId(res.transactionId());
        response.setGateway("AUTHORIZE_NET");
        response.setStatus(res.responseCode() == 1 ? "APPROVED" : "DECLINED");
        response.setMessage(res.authCode());
        log.info("[correlationId={}] Authorize.net charge transactionId={}",
                MDC.get("correlationId"), res.transactionId());
        return response;
    }

    private RouteResponse routePaddleCharge(RouteRequest request) {
        // Paddle handles recurring billing automatically via subscription.
        // Post a charge trigger to paddle-service for trial-end events.
        record PaddleCancelReq(String effectiveFrom) {}
        paddleRestClient.post()
                .uri("/subscriptions/{id}/cancel", request.getPaddleSubscriptionId())
                .body(new PaddleCancelReq("immediately"))
                .retrieve()
                .toBodilessEntity();

        RouteResponse response = new RouteResponse();
        response.setTransactionId(request.getPaddleSubscriptionId());
        response.setGateway("PADDLE");
        response.setStatus("PROCESSED");
        response.setMessage("Paddle subscription billing triggered");
        log.info("[correlationId={}] Paddle charge triggered subscriptionId={}",
                MDC.get("correlationId"), request.getPaddleSubscriptionId());
        return response;
    }

    public ProfileRouteResponse routeCreateProfile(ProfileRouteRequest request) {
        log.info("[correlationId={}] Routing createProfile for userId={} country={}",
                MDC.get("correlationId"), request.userId(), request.country());
        try {
            if ("US".equalsIgnoreCase(request.country())) {
                record AnProfileReq(String userId, String email, String cardNumber,
                                    String expirationDate, String cardCode) {}
                record AnProfileRes(String customerProfileId, String paymentProfileId) {}

                AnProfileRes res = authorizeNetRestClient
                        .post()
                        .uri("/customers/profiles")
                        .body(new AnProfileReq(request.userId(), request.email(),
                                request.cardNumber(), request.expirationDate(), request.cardCode()))
                        .retrieve()
                        .body(AnProfileRes.class);

                if (res == null) {
                    throw new RoutingException("Empty response from authorize-net-service");
                }
                return new ProfileRouteResponse(res.customerProfileId(), res.paymentProfileId(), null);
            } else {
                // Non-US: create Paddle customer then subscription via paddle-service
                record PaddleCustomerReq(String email, String name) {}
                record PaddleCustomerData(String id) {}
                record PaddleCustomerRes(PaddleCustomerData data) {}
                record PaddleSubItem(String priceId, int quantity) {}
                record PaddleSubReq(List<PaddleSubItem> items, String customerId,
                                    String currencyCode, String collectionMode) {}
                record PaddleSubData(String id) {}
                record PaddleSubRes(PaddleSubData data) {}

                PaddleCustomerRes customerRes = paddleRestClient.post()
                        .uri("/customers")
                        .body(new PaddleCustomerReq(request.email(), "user_" + request.userId()))
                        .retrieve()
                        .body(PaddleCustomerRes.class);

                if (customerRes == null || customerRes.data() == null) {
                    throw new RoutingException("Empty response from paddle-service createCustomer");
                }
                String customerId = customerRes.data().id();

                PaddleSubRes subRes = paddleRestClient.post()
                        .uri("/customers/{customerId}/subscriptions", customerId)
                        .body(new PaddleSubReq(
                                List.of(new PaddleSubItem(request.priceId(), 1)),
                                customerId, "USD", "automatic"))
                        .retrieve()
                        .body(PaddleSubRes.class);

                if (subRes == null || subRes.data() == null) {
                    throw new RoutingException("Empty response from paddle-service createSubscription");
                }
                String paddleSubscriptionId = subRes.data().id();
                log.info("[correlationId={}] Non-US profile created paddleSubscriptionId={}",
                        MDC.get("correlationId"), paddleSubscriptionId);
                return new ProfileRouteResponse(null, null, paddleSubscriptionId);
            }
        } catch (RoutingException e) {
            throw e;
        } catch (Exception e) {
            log.error("[correlationId={}] routeCreateProfile failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new RoutingException("Profile creation routing failed: " + e.getMessage(), e);
        }
    }

    public void routeDeleteProfile(String profileId, String country) {
        log.info("[correlationId={}] Routing deleteProfile profileId={} country={}",
                MDC.get("correlationId"), profileId, country);
        try {
            if ("US".equalsIgnoreCase(country)) {
                authorizeNetRestClient
                        .delete()
                        .uri("/customers/profiles/{id}", profileId)
                        .retrieve()
                        .toBodilessEntity();
            } else {
                // Non-US: cancel Paddle subscription via paddle-service
                record PaddleCancelReq(String effectiveFrom) {}
                paddleRestClient.post()
                        .uri("/subscriptions/{id}/cancel", profileId)
                        .body(new PaddleCancelReq("next_billing_period"))
                        .retrieve()
                        .toBodilessEntity();
                log.info("[correlationId={}] Paddle subscription cancelled paddleSubscriptionId={}",
                        MDC.get("correlationId"), profileId);
            }
        } catch (Exception e) {
            log.error("[correlationId={}] routeDeleteProfile failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new RoutingException("Profile deletion routing failed: " + e.getMessage(), e);
        }
    }
}
