package com.porquinho.exception;

/**
 * Exception thrown when a resource conflict occurs (HTTP 409).
 * For example, attempting to register with an email that already exists.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
