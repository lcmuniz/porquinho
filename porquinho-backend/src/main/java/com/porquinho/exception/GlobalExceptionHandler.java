package com.porquinho.exception;

import com.porquinho.entity.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for the application.
 * Handles exceptions and returns RFC 7807 Problem Details responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
     * Handle a version collision detected at flush and return 409 (retro do Epic 4): the stale-edit message for an
     * account, a generic one for any other entity.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleObjectOptimisticLockingFailureException(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic lock collision on {} {}", ex.getPersistentClassName(), ex.getIdentifier());
        boolean account = Account.class.getName().equals(ex.getPersistentClassName());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            account ? StaleDataMessages.ACCOUNT : StaleDataMessages.GENERIC
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

    /**
     * Handle @Valid failures and return 400 (Story 3.2). {@code detail} is the first field message
     * (already in pt-BR with the fix, e.g. "Informe o nome da conta"); {@code errors} maps each
     * invalid field to its message so the form can show it next to the field.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        String detail = errors.isEmpty()
            ? "Dados inválidos. Confira os campos e tente novamente."
            : errors.values().iterator().next();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problemDetail.setTitle("Dados inválidos");
        problemDetail.setType(URI.create("https://api.porquinho.com/errors/validation"));
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }

    /**
     * Handle invalid input detected in the service layer and return 400 (Story 3.4), e.g. no category
     * chosen or an unknown suggestion key. The message is already in pt-BR.
     */
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequestException(BadRequestException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Dados inválidos");
        problemDetail.setType(URI.create("https://api.porquinho.com/errors/validation"));
        if (ex.getField() != null) {
            problemDetail.setProperty("errors", Map.of(ex.getField(), ex.getMessage()));
        }
        return problemDetail;
    }

    /**
     * Handle unreadable JSON (malformed body or a value of the wrong type, e.g. text in a number field)
     * and return 400 (Story 3.2). Fixed detail: the parser message exposes internal class names.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Dados inválidos. Confira os campos e tente novamente."
        );
        problemDetail.setTitle("Dados inválidos");
        problemDetail.setType(URI.create("https://api.porquinho.com/errors/validation"));
        return problemDetail;
    }

    /**
     * Handle an upload above the multipart limits (spring.servlet.multipart, 10MB per file) and return 400
     * (Story 5.1), with the same message the import service uses for a file above 10MB.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Arquivo muito grande (máximo 10MB)"
        );
        problemDetail.setTitle("Dados inválidos");
        problemDetail.setType(URI.create("https://api.porquinho.com/errors/validation"));
        return problemDetail;
    }
}
