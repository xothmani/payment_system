package com.payment.authnet.controller;

import com.payment.authnet.model.TransactionResult;
import com.payment.authnet.model.dto.ChargeProfileRequest;
import com.payment.authnet.model.dto.RefundRequest;
import com.payment.authnet.model.dto.VoidRequest;
import com.payment.authnet.service.CustomerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final CustomerProfileService customerProfileService;

    @PostMapping("/charge-profile")
    public TransactionResult chargeProfile(@Valid @RequestBody ChargeProfileRequest request) {
        return customerProfileService.chargeCustomerProfile(
                request.customerProfileId(), request.paymentProfileId(),
                request.amount(), request.idempotencyKey());
    }

    @PostMapping("/refund")
    public TransactionResult refund(@Valid @RequestBody RefundRequest request) {
        return customerProfileService.refundTransaction(
                request.transactionId(), request.amount(),
                request.cardLast4(), request.cardExpiry());
    }

    @PostMapping("/void")
    public TransactionResult voidTxn(@Valid @RequestBody VoidRequest request) {
        return customerProfileService.voidTransaction(request.transactionId());
    }
}
