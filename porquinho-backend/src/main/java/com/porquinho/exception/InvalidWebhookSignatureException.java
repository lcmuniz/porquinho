package com.porquinho.exception;

/**
 * Webhook payload whose Stripe-Signature header is missing or does not match (HTTP 400).
 */
public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException(String message) {
        super(message);
    }

    public InvalidWebhookSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
