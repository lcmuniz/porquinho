package com.porquinho.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Global exception handler for the application.
 * Handles exceptions and returns RFC 7807 Problem Details responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle ConflictException and return 409 Conflict status.
     *
     * @param ex ConflictException
     * @return Problem Detail with 409 status
     */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflictException(ConflictException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.getMessage()
        );
        problemDetail.setTitle("Conflict");
        problemDetail.setType(URI.create("https://api.porquinho.com/errors/conflict"));
        return problemDetail;
    }

    /**
     * Handle RateLimitExceededException and return 429 Too Many Requests status.
     *
     * @param ex RateLimitExceededException
     * @return Problem Detail with 429 status
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ProblemDetail handleRateLimitExceededException(RateLimitExceededException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            ex.getMessage()
        );
        problemDetail.setTitle("Too Many Requests");
        problemDetail.setType(URI.create("https://api.porquinho.com/errors/rate-limit-exceeded"));
        problemDetail.setProperty("retryAfter", 60); // 60 seconds
        return problemDetail;
    }

    /**
     * Handle AccountLockedException and return 423 Locked status.
     *
     * @param ex AccountLockedException
     * @return Problem Detail with 423 status
     */
    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail handleAccountLockedException(AccountLockedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.LOCKED,
            ex.getMessage()
        );
        problemDetail.setTitle("Account Locked");
        problemDetail.setType(URI.create("https://api.porquinho.com/errors/account-locked"));
        problemDetail.setProperty("retryAfter", 900); // 900 seconds = 15 minutes
        return problemDetail;
    }

    /**
     * Handle PaymentProviderUnavailableException (Stripe down/failed) and return 503 (Story 2.2).
     */
    @ExceptionHandler(PaymentProviderUnavailableException.class)
    public ProblemDetail handlePaymentProviderUnavailableException(PaymentProviderUnavailableException ex) {
        // Fixed detail: ex.getMessage() may carry config/internal hints (e.g. missing secret key)
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Pagamento indisponível no momento. Tente novamente mais tarde."
        );
        problemDetail.setTitle("Pagamento indisponível");
        problemDetail.setType(URI.create("https://api.porquinho.com/errors/payment-provider-unavailable"));
        return problemDetail;
    }

    /**
     * Handle InvalidWebhookSignatureException and return 400 (Story 2.2).
     */
    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ProblemDetail handleInvalidWebhookSignatureException(InvalidWebhookSignatureException ex) {
        // Public endpoint: never echo the reason (e.g. "secret not configured"); it is logged instead
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Invalid webhook signature"
        );
        problemDetail.setTitle("Invalid Webhook Signature");
        problemDetail.setType(URI.create("https://api.porquinho.com/errors/invalid-webhook-signature"));
        return problemDetail;
    }

    /**
     * Handle ResourceNotFoundException and return 404 Not Found status.
     *
     * @param ex ResourceNotFoundException
     * @return Problem Detail with 404 status
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFoundException(ResourceNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setType(URI.create("https://api.porquinho.com/errors/not-found"));
        return problemDetail;
    }
}
