package com.payment.router.service;

import com.payment.router.exception.RoutingException;
import com.payment.router.model.RouteRequest;
import com.payment.router.model.RouteResponse;
import com.payment.router.model.dto.ProfileRouteRequest;
import com.payment.router.model.dto.ProfileRouteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
        throw new UnsupportedOperationException("TODO: implement charge routing");
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
                // TODO: call paddle-service for non-US profile setup when Paddle is implemented
                log.info("[correlationId={}] Non-US user — Paddle profile setup pending",
                        MDC.get("correlationId"));
                return new ProfileRouteResponse(null, null, null);
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
                // TODO: call paddle-service when Paddle cancellation is implemented
                log.info("[correlationId={}] Non-US delete profile — Paddle pending",
                        MDC.get("correlationId"));
            }
        } catch (Exception e) {
            log.error("[correlationId={}] routeDeleteProfile failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new RoutingException("Profile deletion routing failed: " + e.getMessage(), e);
        }
    }
}
