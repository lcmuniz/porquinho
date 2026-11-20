package com.porquinho.controller;

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
import com.porquinho.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for authentication operations.
 * Handles user registration and login via Google OAuth and email/password.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** Per client IP on the public /login/failed: well above a person mistyping, far below a script. */
    static final int FAILED_LOGINS_PER_IP_MINUTE = 30;
    /** Per client IP on the public /password-reset/requested (on top of the 3 per e-mail). */
    static final int PASSWORD_RESETS_PER_IP_HOUR = 10;
    /** Longest User-Agent kept in the audit metadata. */
    static final int MAX_USER_AGENT_LENGTH = 512;

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
     * Identity (sub, e-mail, Google id) comes only from the JWT; the body the frontend sends is ignored
     * (retro Epic 1 A1, S2): otherwise any logged-in user could claim someone else's e-mail.
     *
     * @param authentication Authenticated JWT (user id = sub claim)
     * @param httpRequest HTTP request to extract client IP address
     * @return UserResponse with user data, 401 without a JWT carrying sub and e-mail
     */
    @PostMapping("/register/google")
    public ResponseEntity<UserResponse> registerWithGoogle(
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        Jwt jwt = resolveJwt(authentication);
        String email = jwtEmail(jwt);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userId = jwt.getSubject();

        // Extract client IP address for audit logging
        String ipAddress = getClientIpAddress(httpRequest);

        // Register or retrieve user (new users get id = JWT sub, Story 2.1)
        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromGoogle(
            userId, email, googleId(jwt), ipAddress);

        // Every Google sign-in lands here (the frontend never calls /login for it): audit it as a login (retro Epic 1 A3)
        auditLogService.log("user_login", result.getUser().getId(), ipAddress);

        // Register session (AC5: session tracking on login), keyed by the JWT session_id claim (retro Epic 1 A2)
        sessionService.createSession(result.getUser().getId(), jwt, httpRequest.getHeader("User-Agent"), ipAddress);

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
     * Following hybrid architecture decision (README.md, Architecture), users are created
     * in Supabase Auth (authentication authority) and synced to backend database for:
     * - Account locking (failed login attempts)
     * - Audit logs (login history, IP tracking)
     * - Rate limiting per-user
     * - Business data (subscriptions, billing, etc)
     *
     * Identity comes only from the JWT (sub and e-mail claims); the body is ignored (retro Epic 1 A1, S2).
     *
     * @param authentication Authenticated JWT (user id = sub claim)
     * @param httpRequest HTTP request to extract client IP address
     * @return UserResponse with user data, 401 without a JWT carrying sub and e-mail
     */
    @PostMapping("/register/email")
    public ResponseEntity<UserResponse> registerWithEmail(
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        Jwt jwt = resolveJwt(authentication);
        String email = jwtEmail(jwt);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userId = jwt.getSubject();

        // Extract client IP address for audit logging
        String ipAddress = getClientIpAddress(httpRequest);

        // Register or retrieve user (userId from JWT)
        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromEmail(
            userId, email, ipAddress);

        // Convert to response DTO
        UserResponse response = UserResponse.fromEntity(result.getUser());

        // Return 201 Created for new users, 200 OK for existing users
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
    public ResponseEntity<Void> checkLoginAllowed(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        // Locked for this IP (15 minutes after 5 failed attempts from it)
        if (accountLockService.isLocked(request.getEmail(), getClientIpAddress(httpRequest))) {
            throw new AccountLockedException("Account temporarily locked due to multiple failed attempts. Try again in 15 minutes.");
        }

        // 5 attempts per 60 seconds per (normalized e-mail, IP): a third party cannot keep the victim at 429, and
        // changing the e-mail's case does not reset the limit (retro Epic 1 A1, S3)
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (!rateLimitService.allowRequest("login:" + normalizedEmail + ":" + getClientIpAddress(httpRequest), 5, 60)) {
            throw new RateLimitExceededException("Too many login attempts. Please wait 1 minute.");
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Record failed login attempt.
     * This endpoint is called when Supabase authentication fails (invalid credentials).
     * Increments the failure counter for this email and client IP; after 5 attempts that IP is locked for 15 minutes.
     * Does not require authentication (called after failed login), so the lock is per IP: a third party cannot
     * use this endpoint to lock someone else out of their account.
     *
     * @param request Request containing email
     * @return 200 OK (no response body)
     */
    @PostMapping("/login/failed")
    public ResponseEntity<Void> recordFailedLogin(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIpAddress(httpRequest);
        // public endpoint: caps how many lock counters (Redis keys) one IP can create
        if (!rateLimitService.allowRequest("login_failed_ip:" + ipAddress, FAILED_LOGINS_PER_IP_MINUTE, 60)) {
            throw new RateLimitExceededException("Muitas tentativas a partir deste endereço. Aguarde 1 minuto.");
        }

        // Record failed attempt for this email + IP (locks this IP if >= 5 attempts)
        accountLockService.recordFailedAttempt(normalizeEmail(request.getEmail()), ipAddress);

        return ResponseEntity.ok().build();
    }

    /**
     * Log successful user login for audit purposes.
     * This endpoint is ONLY for audit logging after successful Supabase authentication.
     *
     * HYBRID ARCHITECTURE: Returns 404 if user doesn't exist in backend database,
     * allowing frontend to perform lazy sync (create user in backend on first login).
     *
     * The user is the JWT sub; the e-mail in the body is ignored (retro Epic 1 A1, S1): otherwise any logged-in
     * user could reset someone else's lock counter and write sessions and audit rows on their account.
     *
     * @param authentication Authenticated JWT (user id = sub claim)
     * @param httpRequest HTTP request to extract client IP address
     * @return 200 OK if successful, 401 without JWT, 404 if user not found in backend
     */
    @PostMapping("/login")
    public ResponseEntity<Void> logLogin(
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String userId = resolveUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = authService.findById(userId).orElse(null);

        if (user == null) {
            // User doesn't exist in backend (Supabase-only) - return 404 for lazy sync
            return ResponseEntity.notFound().build();
        }

        // Extract client IP address for audit logging
        String ipAddress = getClientIpAddress(httpRequest);

        // Create audit log entry
        auditLogService.log("user_login", user.getId(), ipAddress);

        // Reset failed login attempts on successful login
        accountLockService.resetFailedAttempts(user.getEmail(), ipAddress);

        // Register session, keyed by the JWT session_id claim (retro Epic 1 A2)
        Jwt jwt = resolveJwt(authentication);
        if (jwt != null) {
            sessionService.createSession(user.getId(), jwt, httpRequest.getHeader("User-Agent"), ipAddress);
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
        String email = normalizeEmail(request.getEmail());
        String ipAddress = getClientIpAddress(httpRequest);

        // Check rate limit (3 requests per 60 minutes per email)
        if (!rateLimitService.allowRequest("password_reset:" + email, 3, 3600)) {
            throw new RateLimitExceededException("Too many password reset requests. Please wait 1 hour.");
        }
        // and per IP: one client cycling e-mails would otherwise fill the anonymous audit log
        if (!rateLimitService.allowRequest("password_reset_ip:" + ipAddress, PASSWORD_RESETS_PER_IP_HOUR, 3600)) {
            throw new RateLimitExceededException("Muitos pedidos a partir deste endereço. Aguarde 1 hora.");
        }

        String metadata = AuditLogService.metadata("email", email, "user_agent", userAgent(httpRequest));

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

        String metadata = AuditLogService.metadata("user_agent", userAgent(httpRequest));

        // Log audit event with user_id (null only when unauthenticated, e.g. test profile)
        if (userId != null) {
            UUID userUuid = UUID.fromString(userId);
            auditLogService.log("password_reset_completed", userUuid, ipAddress, metadata);

            // The new password logs out every other device, also in Supabase (retro Epic 1 A2, S5)
            Jwt jwt = resolveJwt(authentication);
            if (jwt != null) {
                try {
                    sessionService.revokeAllOtherSessions(userUuid, jwt);
                } catch (ResourceNotFoundException e) {
                    // token without session_id and not recorded as a session: nothing to tell "others" from; the reset stands
                }
            }
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

        Jwt jwt = resolveJwt(authentication);
        UUID currentSessionId = jwt != null ? SessionService.sessionId(jwt) : null;
        String currentTokenHash = jwt != null ? SessionService.hashToken(jwt.getTokenValue()) : "";
        List<UserSession> sessions = sessionService.getActiveSessions(UUID.fromString(userId));
        List<SessionResponse> response = sessions.stream()
            .map(s -> SessionResponse.fromEntity(s, currentSessionId, currentTokenHash))
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
            AuditLogService.metadata("revoked_session_id", sessionId));

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

        Jwt jwt = resolveJwt(authentication);
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID userUuid = UUID.fromString(userId);
        int revokedCount = sessionService.revokeAllOtherSessions(userUuid, jwt);

        String ipAddress = getClientIpAddress(httpRequest);
        auditLogService.log("remote_logout_all", userUuid, ipAddress,
            AuditLogService.metadata("revoked_count", revokedCount));

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

    private static String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return "unknown";
        }
        // the header comes from the client: a huge one would bloat the audit row
        return userAgent.length() > MAX_USER_AGENT_LENGTH ? userAgent.substring(0, MAX_USER_AGENT_LENGTH) : userAgent;
    }

    /** Trim + lower case, so changing the e-mail's case does not reset a limit nor split the audit trail. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static Jwt resolveJwt(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken token ? token.getToken() : null;
    }

    /** E-mail claim of a JWT that also has a sub; null when either is missing. */
    private static boolean isOAuthSession(Jwt jwt) {
        if (jwt.getClaims().get("amr") instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> m && "oauth".equals(m.get("method"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String jwtEmail(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            return null;
        }
        String email = jwt.getClaimAsString("email");
        return email == null || email.isBlank() ? null : email;
    }

    /**
     * Google id as the frontend used to send it: user_metadata.sub, then provider_id, then the Supabase sub.
     * Null when the session did not come from OAuth (amr): an e-mail/password session also has user_metadata.sub,
     * and taking it as a Google id would "link" a Google account that never signed in.
     */
    private static String googleId(Jwt jwt) {
        if (!isOAuthSession(jwt)) {
            return null;
        }
        Map<String, Object> metadata = jwt.getClaimAsMap("user_metadata");
        if (metadata != null) {
            for (String key : List.of("sub", "provider_id")) {
                Object value = metadata.get(key);
                if (value != null && !value.toString().isBlank()) {
                    return value.toString();
                }
            }
        }
        return jwt.getSubject();
    }

    /** Client IP: server.forward-headers-strategy=native already resolves X-Forwarded-For from the trusted proxy. */
    private String getClientIpAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
