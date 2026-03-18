package com.porquinho.exception;

/**
 * Exception thrown when a requested resource is not found (HTTP 404).
 * For example, user not found by email.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
