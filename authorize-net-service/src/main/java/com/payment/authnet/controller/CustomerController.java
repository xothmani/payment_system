package com.payment.authnet.controller;

import com.payment.authnet.model.CustomerProfileResult;
import com.payment.authnet.model.dto.CreateProfileRequest;
import com.payment.authnet.service.CustomerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerProfileService customerProfileService;

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerProfileResult createProfile(@Valid @RequestBody CreateProfileRequest request) {
        return customerProfileService.createCustomerProfile(
                request.userId(), request.email(),
                request.cardNumber(), request.expirationDate(), request.cardCode());
    }

    @DeleteMapping("/profiles/{customerProfileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfile(@PathVariable String customerProfileId) {
        customerProfileService.deleteCustomerProfile(customerProfileId);
    }
}
