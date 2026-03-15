package com.porquinho.controller;

import com.porquinho.dto.RegisterGoogleRequest;
import com.porquinho.dto.UserResponse;
import com.porquinho.entity.User;
import com.porquinho.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations.
 * Handles user registration and login via Google OAuth.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
