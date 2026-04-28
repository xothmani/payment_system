package com.payment.paddle.exception;

public class PaddleException extends RuntimeException {
    public PaddleException(String message) {
        super(message);
    }

    public PaddleException(String message, Throwable cause) {
        super(message, cause);
    }
}
