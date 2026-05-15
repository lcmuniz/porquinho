package com.porquinho.controller;

import com.porquinho.dto.AccountDeletionResponse;
import com.porquinho.service.AccountDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * REST controller for account deletion operations (Story 1.7 - LGPD right to erasure).
 *
 * Dedicated controller (separate from AuthController which already handles 5 endpoint groups).
 * The single POST endpoint is sufficient: once a user soft-deletes their account, the
 * @Where(deleted_at IS NULL) on User entity prevents authenticated re-access, so a GET
 * status endpoint would be unreachable in practice. If admin needs visibility, add it
 * in Epic 10 (admin module).
 */
@RestController
@RequestMapping("/api/v1/account-deletion")
public class AccountDeletionController {

    /** Max length of an IPv6 address string (e.g. fe80:0000:...:7334) is 39, plus zone id buffer. */
    private static final int MAX_IP_LENGTH = 45;

    private final AccountDeletionService accountDeletionService;

    public AccountDeletionController(AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
    }

    /**
     * Request permanent account deletion.
     * Idempotent: if a PENDING_PURGE record already exists, returns it without re-running.
     * Rate-limited to 1 request per day per user (returns 429).
     */
    @PostMapping
    public ResponseEntity<AccountDeletionResponse> requestDeletion(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            // Subject is not a valid UUID - treat as unauthenticated.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
        String ipAddress = getClientIpAddress(request);

        AccountDeletionResponse response = accountDeletionService.requestDeletion(userId, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Extract client IP from headers/socket and bound it to MAX_IP_LENGTH.
     *
     * Reads X-Forwarded-For (first hop) when present, falling back to remote addr. Truncates
     * the result to MAX_IP_LENGTH so a malicious client cannot trigger a DataException by
     * sending a 1000-character header (the audit_logs.ip_address and account_deletions.requested_ip
     * columns are VARCHAR(45)).
     *
     * NOTE on trust: this method does NOT validate that the request came through a trusted
     * proxy. If the deployment puts the app directly on the public internet, X-Forwarded-For
     * is client-controlled and IP values logged here may be spoofed. For production, configure
     * server.forward-headers-strategy=native (or framework) and only honor the header when
     * the request originates from a trusted proxy.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String raw;
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            raw = xForwardedFor.split(",")[0].trim();
        } else {
            raw = request.getRemoteAddr();
        }
        if (raw == null) {
            return null;
        }
        return raw.length() > MAX_IP_LENGTH ? raw.substring(0, MAX_IP_LENGTH) : raw;
    }
}
