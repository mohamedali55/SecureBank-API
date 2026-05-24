package com.securebank.exception;

/** Thrown when a requested entity (user, account, transaction) does not exist. -> HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
