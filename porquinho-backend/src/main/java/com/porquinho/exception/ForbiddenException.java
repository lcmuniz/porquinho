package com.porquinho.exception;

/** Exception thrown when the user may not use the resource (HTTP 403), e.g. a non-admin on /api/v1/admin/* (Story 10.1). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
