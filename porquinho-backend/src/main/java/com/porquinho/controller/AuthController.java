package com.porquinho.controller;

import com.porquinho.dto.RegisterGoogleRequest;
import com.porquinho.dto.RegisterEmailRequest;
import com.porquinho.dto.LoginRequest;
import com.porquinho.dto.PasswordResetRequest;
import com.porquinho.dto.SessionResponse;
import com.porquinho.dto.UserResponse;
import com.porquinho.entity.User;
import com.porquinho.entity.UserSession;
import com.porquinho.service.AuthService;
import com.porquinho.service.AuditLogService;
import com.porquinho.service.RateLimitService;
import com.porquinho.service.AccountLockService;
import com.porquinho.service.SessionService;
import com.porquinho.exception.RateLimitExceededException;
import com.porquinho.exception.AccountLockedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
    private final SessionService sessionService;

    public AuthController(AuthService authService, AuditLogService auditLogService, RateLimitService rateLimitService, AccountLockService accountLockService, SessionService sessionService) {
        this.authService = authService;
        this.auditLogService = auditLogService;
        this.rateLimitService = rateLimitService;
        this.accountLockService = accountLockService;
        this.sessionService = sessionService;
    }

    /**
     * Register or login user with Google OAuth.
     * This endpoint is called by the frontend after Supabase completes Google OAuth.
     * The JWT from Supabase is validated by Spring Security, and user_id is extracted from the "sub" claim.
     *
     * @param authentication Authenticated JWT (user id = sub claim)
     * @param request Request containing email and googleId from Supabase JWT
     * @param httpRequest HTTP request to extract client IP address
     * @return UserResponse with user data
     */
    @PostMapping("/register/google")
    public ResponseEntity<UserResponse> registerWithGoogle(
        Authentication authentication,
        @Valid @RequestBody RegisterGoogleRequest request,
        HttpServletRequest httpRequest
    ) {
        // JWT sub. Resolved via Authentication.getName(): with JwtAuthenticationToken the
        // principal is the Jwt object, so @AuthenticationPrincipal String would resolve to null.
        String userId = resolveUserId(authentication);

        // Extract client IP address for audit logging
        String ipAddress = getClientIpAddress(httpRequest);

        // Register or retrieve user (new users get id = JWT sub, Story 2.1)
        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromGoogle(
            userId, request.getEmail(), request.getGoogleId(), ipAddress);

        // Register session if JWT token is available (AC5: session tracking on login)
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwtToken = authHeader.substring(7);
            String deviceInfo = httpRequest.getHeader("User-Agent");
            sessionService.createSession(result.getUser().getId(), jwtToken, deviceInfo, ipAddress);
        }

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
     * @param authentication Authenticated JWT (user id = sub claim)
     * @param request Request containing email and name
     * @param httpRequest HTTP request to extract client IP address
     * @return UserResponse with user data
     */
    @PostMapping("/register/email")
    public ResponseEntity<UserResponse> registerWithEmail(
        Authentication authentication,
        @Valid @RequestBody RegisterEmailRequest request,
        HttpServletRequest httpRequest
    ) {
        // JWT sub (see registerWithGoogle for why not @AuthenticationPrincipal String)
        String userId = resolveUserId(authentication);

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
     * @param request Request containing email
     * @param httpRequest HTTP request to extract client IP address
     * @return 200 OK if successful, 404 if user not found in backend
     */
    @PostMapping("/login")
    public ResponseEntity<Void> logLogin(
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

        // Register session if JWT token is available
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwtToken = authHeader.substring(7);
            String deviceInfo = httpRequest.getHeader("User-Agent");
            sessionService.createSession(user.getId(), jwtToken, deviceInfo, ipAddress);
        }

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
     * @param authentication Authenticated JWT (user id = sub claim)
     * @param httpRequest HTTP request to extract client IP address
     * @return 200 OK (no response body)
     */
    @PostMapping("/password-reset/completed")
    public ResponseEntity<Void> logPasswordResetCompleted(
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        // JWT sub (see registerWithGoogle for why not @AuthenticationPrincipal String)
        String userId = resolveUserId(authentication);

        // Extract client IP address for audit logging
        String ipAddress = getClientIpAddress(httpRequest);

        // Build metadata JSON string
        String metadata = String.format(
            "{\"user_agent\":\"%s\"}",
            httpRequest.getHeader("User-Agent") != null ? httpRequest.getHeader("User-Agent") : "unknown"
        );

        // Log audit event with user_id (null only when unauthenticated, e.g. test profile)
        if (userId != null) {
            auditLogService.log("password_reset_completed", java.util.UUID.fromString(userId), ipAddress, metadata);
        }

        return ResponseEntity.ok().build();
    }

    // ===== SESSION MANAGEMENT ENDPOINTS (Story 1.5) =====

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getActiveSessions(
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String userId = resolveUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!rateLimitService.allowRequest("sessions:" + userId, 10, 60)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }

        String jwtToken = extractJwtToken(httpRequest);
        String currentTokenHash = jwtToken != null ? SessionService.hashToken(jwtToken) : "";
        List<UserSession> sessions = sessionService.getActiveSessions(UUID.fromString(userId));
        List<SessionResponse> response = sessions.stream()
            .map(s -> SessionResponse.fromEntity(s, currentTokenHash))
            .toList();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
        @PathVariable UUID sessionId,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String userId = resolveUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!rateLimitService.allowRequest("sessions:" + userId, 10, 60)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }

        UUID userUuid = UUID.fromString(userId);
        sessionService.revokeSession(sessionId, userUuid);

        String ipAddress = getClientIpAddress(httpRequest);
        auditLogService.log("remote_logout", userUuid, ipAddress,
            String.format("{\"revoked_session_id\":\"%s\"}", sessionId));

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAllOtherSessions(
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String userId = resolveUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!rateLimitService.allowRequest("sessions:" + userId, 10, 60)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }

        UUID userUuid = UUID.fromString(userId);
        String jwtToken = extractJwtToken(httpRequest);
        String currentTokenHash = jwtToken != null ? SessionService.hashToken(jwtToken) : "";
        int revokedCount = sessionService.revokeAllOtherSessions(userUuid, currentTokenHash);

        String ipAddress = getClientIpAddress(httpRequest);
        auditLogService.log("remote_logout_all", userUuid, ipAddress,
            String.format("{\"revoked_count\":%d}", revokedCount));

        return ResponseEntity.noContent().build();
    }

    /**
     * Health check endpoint for testing.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth service is running");
    }

    /**
     * Extract JWT token from the Authorization header.
     *
     * @param request HTTP request
     * @return JWT token string, or null if not present
     */
    private String extractJwtToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * Resolve user ID from Authentication.
     * Uses Authentication.getName() which returns the JWT subject claim
     * from JwtAuthenticationToken (set by JwtAuthenticationConverter).
     */
    private String resolveUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || name.equals("anonymousUser")) {
            return null;
        }
        return name;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, use the first one
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
