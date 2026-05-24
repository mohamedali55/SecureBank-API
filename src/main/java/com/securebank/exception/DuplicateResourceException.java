package com.securebank.exception;

/** Thrown when creating something that must be unique but already exists. -> HTTP 409. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
