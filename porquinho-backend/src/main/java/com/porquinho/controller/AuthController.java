package com.porquinho.controller;

import com.porquinho.dto.RegisterGoogleRequest;
import com.porquinho.dto.RegisterEmailRequest;
import com.porquinho.dto.LoginRequest;
import com.porquinho.dto.PasswordResetRequest;
import com.porquinho.dto.UserResponse;
import com.porquinho.entity.User;
import com.porquinho.service.AuthService;
import com.porquinho.service.AuditLogService;
import com.porquinho.service.RateLimitService;
import com.porquinho.service.AccountLockService;
import com.porquinho.exception.RateLimitExceededException;
import com.porquinho.exception.AccountLockedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations.
 * Handles user registration and login via Google OAuth and email/password.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final RateLimitService rateLimitService;
    private final AccountLockService accountLockService;

    public AuthController(AuthService authService, AuditLogService auditLogService, RateLimitService rateLimitService, AccountLockService accountLockService) {
        this.authService = authService;
        this.auditLogService = auditLogService;
        this.rateLimitService = rateLimitService;
        this.accountLockService = accountLockService;
    }

    /**
     * Register or login user with Google OAuth.
     * This endpoint is called by the frontend after Supabase completes Google OAuth.
     * The JWT from Supabase is validated by Spring Security, and user_id is extracted from the "sub" claim.
     *
     * @param userId User ID extracted from JWT sub claim by JwtAuthenticationConverter
     * @param request Request containing email and googleId from Supabase JWT
     * @param httpRequest HTTP request to extract client IP address
     * @return UserResponse with user data
     */
    @PostMapping("/register/google")
    public ResponseEntity<UserResponse> registerWithGoogle(
        @AuthenticationPrincipal String userId,
        @Valid @RequestBody RegisterGoogleRequest request,
        HttpServletRequest httpRequest
    ) {
        // Extract client IP address for audit logging
        String ipAddress = getClientIpAddress(httpRequest);

        // Register or retrieve user
        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromGoogle(
            request.getEmail(), request.getGoogleId(), ipAddress);

        // Convert to response DTO
        UserResponse response = UserResponse.fromEntity(result.getUser());

        // Return 201 Created for new users, 200 OK for existing users
        HttpStatus status = result.isNewUser() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Register user with email/password.
     * Called by frontend after Supabase creates user to sync to backend database.
     *
     * HYBRID ARCHITECTURE - This endpoint IS CALLED by frontend for user sync.
     * Following hybrid architecture decision (ARQUITETURA-SIMPLIFICADA.md), users are created
     * in Supabase Auth (authentication authority) and synced to backend database for:
     * - Account locking (failed login attempts)
     * - Audit logs (login history, IP tracking)
     * - Rate limiting per-user
     * - Business data (subscriptions, billing, etc)
     *
     * @param userId User ID extracted from JWT sub claim by JwtAuthenticationConverter
     * @param request Request containing email and name
     * @param httpRequest HTTP request to extract client IP address
     * @return UserResponse with user data
     */
    @PostMapping("/register/email")
    public ResponseEntity<UserResponse> registerWithEmail(
        @AuthenticationPrincipal String userId,
        @Valid @RequestBody RegisterEmailRequest request,
        HttpServletRequest httpRequest
    ) {
        // Extract client IP address for audit logging
        String ipAddress = getClientIpAddress(httpRequest);

        // Register or retrieve user (userId from JWT)
        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromEmail(
            userId, request, ipAddress);

        // Convert to response DTO
        UserResponse response = UserResponse.fromEntity(result.getUser());

        // Return 201 Created for new users, 200 OK for existing users (account linking)
        HttpStatus status = result.isNewUser() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Check if login attempt is allowed (rate limiting and account lock check).
     * This endpoint MUST be called BEFORE attempting Supabase authentication.
     * Does not require authentication (called before user is authenticated).
     *
     * @param request Request containing email
     * @return 200 OK if login attempt is allowed
     * @throws AccountLockedException if account is locked (423)
     * @throws RateLimitExceededException if rate limit exceeded (429)
     */
    @PostMapping("/login/check")
    public ResponseEntity<Void> checkLoginAllowed(@Valid @RequestBody LoginRequest request) {
        // Check if account is locked (15 minutes after 5 failed attempts)
        if (accountLockService.isLocked(request.getEmail())) {
            throw new AccountLockedException("Account temporarily locked due to multiple failed attempts. Try again in 15 minutes.");
        }

        // Check rate limit (5 attempts per 60 seconds per email)
        if (!rateLimitService.allowRequest("login:" + request.getEmail(), 5, 60)) {
            throw new RateLimitExceededException("Too many login attempts. Please wait 1 minute.");
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Record failed login attempt.
     * This endpoint is called when Supabase authentication fails (invalid credentials).
     * Increments failed login counter and locks account after 5 attempts.
     * Does not require authentication (called after failed login).
     *
     * @param request Request containing email
     * @return 200 OK (no response body)
     */
    @PostMapping("/login/failed")
    public ResponseEntity<Void> recordFailedLogin(@Valid @RequestBody LoginRequest request) {
        // Record failed attempt (increments counter, locks account if >= 5 attempts)
        accountLockService.recordFailedAttempt(request.getEmail());

        return ResponseEntity.ok().build();
    }

    /**
     * Log successful user login for audit purposes.
     * This endpoint is ONLY for audit logging after successful Supabase authentication.
     *
     * HYBRID ARCHITECTURE: Returns 404 if user doesn't exist in backend database,
     * allowing frontend to perform lazy sync (create user in backend on first login).
     *
     * @param userId User ID extracted from JWT sub claim by JwtAuthenticationConverter
     * @param request Request containing email
     * @param httpRequest HTTP request to extract client IP address
     * @return 200 OK if successful, 404 if user not found in backend
     */
    @PostMapping("/login")
    public ResponseEntity<Void> logLogin(
        @AuthenticationPrincipal String userId,
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        // Find user by email to verify they exist in backend database
        User user = authService.findUserByEmail(request.getEmail());

        if (user == null) {
            // User doesn't exist in backend (Supabase-only) - return 404 for lazy sync
            return ResponseEntity.notFound().build();
        }

        // Extract client IP address for audit logging
        String ipAddress = getClientIpAddress(httpRequest);

        // Create audit log entry
        auditLogService.log("user_login", user.getId(), ipAddress);

        // Reset failed login attempts on successful login
        accountLockService.resetFailedAttempts(request.getEmail());

        return ResponseEntity.ok().build();
    }

    /**
     * Log password reset request for audit purposes.
     * This endpoint is called when user requests password reset via email.
     * Does not require authentication (public endpoint).
     * Rate limited to 3 requests per hour per email to prevent abuse.
     *
     * SECURITY: Does NOT reveal if email exists in database (returns 200 regardless).
     * Creates anonymous audit log (no user_id) to prevent email enumeration.
     *
     * @param request Request containing email
     * @param httpRequest HTTP request to extract client IP address
     * @return 200 OK (no response body)
     * @throws RateLimitExceededException if rate limit exceeded (429)
     */
    @PostMapping("/password-reset/requested")
    public ResponseEntity<Void> logPasswordResetRequested(
        @Valid @RequestBody PasswordResetRequest request,
        HttpServletRequest httpRequest
    ) {
        // Check rate limit (3 requests per 60 minutes per email)
        if (!rateLimitService.allowRequest("password_reset:" + request.getEmail(), 3, 3600)) {
            throw new RateLimitExceededException("Too many password reset requests. Please wait 1 hour.");
        }

        // Extract client IP address for audit logging
        String ipAddress = getClientIpAddress(httpRequest);

        // Build metadata JSON string manually (simple approach)
        String metadata = String.format(
            "{\"email\":\"%s\",\"user_agent\":\"%s\"}",
            request.getEmail(),
            httpRequest.getHeader("User-Agent") != null ? httpRequest.getHeader("User-Agent") : "unknown"
        );

        // Log anonymous audit event (no user_id - security feature)
        auditLogService.logAnonymous("password_reset_requested", ipAddress, metadata);

        return ResponseEntity.ok().build();
    }

    /**
     * Log successful password reset completion for audit purposes.
     * This endpoint is called after Supabase successfully resets the password.
     * Requires authentication (JWT from Supabase reset token flow).
     *
     * @param userId User ID extracted from JWT sub claim by JwtAuthenticationConverter
     * @param httpRequest HTTP request to extract client IP address
     * @return 200 OK (no response body)
     */
    @PostMapping("/password-reset/completed")
    public ResponseEntity<Void> logPasswordResetCompleted(
        @AuthenticationPrincipal String userId,
        HttpServletRequest httpRequest
    ) {
        // Extract client IP address for audit logging
        String ipAddress = getClientIpAddress(httpRequest);

        // Build metadata JSON string
        String metadata = String.format(
            "{\"user_agent\":\"%s\"}",
            httpRequest.getHeader("User-Agent") != null ? httpRequest.getHeader("User-Agent") : "unknown"
        );

        // Log audit event with user_id
        // In test environment, userId might be null or "anonymousUser" - handle gracefully
        if (userId != null && !userId.equals("anonymousUser")) {
            auditLogService.log("password_reset_completed", java.util.UUID.fromString(userId), ipAddress, metadata);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Health check endpoint for testing.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth service is running");
    }

    /**
     * Extract client IP address from HTTP request.
     * Checks X-Forwarded-For header first (for proxied requests), then falls back to remote address.
     *
     * @param request HTTP request
     * @return Client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, use the first one
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
