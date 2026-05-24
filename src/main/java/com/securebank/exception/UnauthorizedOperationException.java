package com.securebank.exception;

/** Thrown when an authenticated user acts on a resource they do not own. -> HTTP 403. */
public class UnauthorizedOperationException extends RuntimeException {
    public UnauthorizedOperationException(String message) {
        super(message);
    }
}
