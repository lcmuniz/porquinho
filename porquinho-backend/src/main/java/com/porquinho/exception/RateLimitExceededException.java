package com.porquinho.exception;

/**
 * Exception thrown when rate limit is exceeded (HTTP 429).
 * For example, too many login attempts in a short time.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
