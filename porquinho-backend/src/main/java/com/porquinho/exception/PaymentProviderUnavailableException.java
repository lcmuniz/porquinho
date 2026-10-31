package com.porquinho.exception;

/**
 * The payment provider (Stripe) could not be reached or rejected the call (HTTP 503).
 * Story 2.2: the trial keeps running; the user can try again later.
 */
public class PaymentProviderUnavailableException extends RuntimeException {

    public PaymentProviderUnavailableException(String message) {
        super(message);
    }

    public PaymentProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
