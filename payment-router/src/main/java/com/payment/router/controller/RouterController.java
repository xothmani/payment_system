package com.payment.router.controller;

import com.payment.router.model.RouteRequest;
import com.payment.router.model.RouteResponse;
import com.payment.router.model.dto.ProfileRouteRequest;
import com.payment.router.model.dto.ProfileRouteResponse;
import com.payment.router.service.RoutingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RouterController {

    private final RoutingService routingService;

    @PostMapping("/api/route")
    public ResponseEntity<RouteResponse> route(@Valid @RequestBody RouteRequest request) {
        return ResponseEntity.ok(routingService.route(request));
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileRouteResponse createProfile(@Valid @RequestBody ProfileRouteRequest request) {
        return routingService.routeCreateProfile(request);
    }

    @DeleteMapping("/profiles/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfile(@PathVariable String profileId,
                              @RequestParam String country) {
        routingService.routeDeleteProfile(profileId, country);
    }
}
