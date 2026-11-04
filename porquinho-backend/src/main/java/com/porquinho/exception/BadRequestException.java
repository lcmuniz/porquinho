package com.porquinho.exception;

/**
 * Invalid input detected in the service layer (400 Bad Request). The message is shown to the user (pt-BR).
 * When {@code field} is set, the 400 also carries it in {@code errors} (Story 4.6), like bean validation.
 */
public class BadRequestException extends RuntimeException {

    private final String field;

    public BadRequestException(String message) {
        this(message, null);
    }

    public BadRequestException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
