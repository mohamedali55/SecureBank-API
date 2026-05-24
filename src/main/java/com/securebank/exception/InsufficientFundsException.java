package com.securebank.exception;

/** Thrown when a source account cannot cover a transfer. -> HTTP 422. */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
