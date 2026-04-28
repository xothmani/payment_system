package com.payment.authnet.model;

public record TransactionResult(String transactionId, String authCode, int responseCode) {
}
