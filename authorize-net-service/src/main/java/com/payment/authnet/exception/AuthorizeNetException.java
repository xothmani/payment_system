package com.payment.authnet.exception;

public class AuthorizeNetException extends RuntimeException {

    private final int responseCode;

    public AuthorizeNetException(int responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public int getResponseCode() {
        return responseCode;
    }
}
