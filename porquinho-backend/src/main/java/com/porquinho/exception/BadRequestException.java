package com.porquinho.exception;

/**
 * Invalid input detected in the service layer (400 Bad Request). The message is shown to the user (pt-BR).
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
