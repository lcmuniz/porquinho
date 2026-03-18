package com.porquinho.exception;

/**
 * Exception thrown when an account is temporarily locked (HTTP 423).
 * For example, after multiple failed login attempts.
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }

    public AccountLockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
