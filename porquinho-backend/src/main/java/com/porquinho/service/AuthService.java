package com.porquinho.service;

import com.porquinho.dto.RegisterEmailRequest;
import com.porquinho.entity.User;
import com.porquinho.exception.ConflictException;
import com.porquinho.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for authentication and user registration operations.
 * Handles user creation and lookup for Google OAuth flow.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public AuthService(UserRepository userRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Result wrapper for registerOrGetUserFromGoogle to indicate if user was newly created.
     */
    public static class UserRegistrationResult {
        private final User user;
        private final boolean isNewUser;

        public UserRegistrationResult(User user, boolean isNewUser) {
            this.user = user;
            this.isNewUser = isNewUser;
        }

        public User getUser() {
            return user;
        }

        public boolean isNewUser() {
            return isNewUser;
        }
    }

    /**
     * Register or retrieve user from Google OAuth.
     * If user already exists by google_id or email, returns existing user.
     * Otherwise, creates new user with Google provider and logs audit event.
     *
     * @param email User's email from Google
     * @param googleId User's Google OAuth ID
     * @param ipAddress Client IP address for audit logging
     * @return UserRegistrationResult with user entity and isNewUser flag
     */
    @Transactional
    public UserRegistrationResult registerOrGetUserFromGoogle(String email, String googleId, String ipAddress) {
        // Check if user exists by Google ID
        Optional<User> existingByGoogleId = userRepository.findByGoogleIdAndDeletedAtIsNull(googleId);
        if (existingByGoogleId.isPresent()) {
            return new UserRegistrationResult(existingByGoogleId.get(), false);
        }

        // Check if user exists by email (might have registered with email/password before)
        Optional<User> existingByEmail = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (existingByEmail.isPresent()) {
            // User exists with email - link Google account if not already linked
            User existingUser = existingByEmail.get();
            if (existingUser.getGoogleId() == null || !existingUser.getGoogleId().equals(googleId)) {
                existingUser.setGoogleId(googleId);
                if (existingUser.getAuthProvider() == User.AuthProvider.EMAIL) {
                    // User initially registered with email, now adding Google OAuth
                    existingUser.setAuthProvider(User.AuthProvider.GOOGLE);
                }
                User updatedUser = userRepository.save(existingUser);

                // Log account linking event
                auditLogService.log("google_account_linked", updatedUser.getId(), ipAddress,
                    String.format("{\"email\":\"%s\",\"previous_provider\":\"EMAIL\"}", email));

                return new UserRegistrationResult(updatedUser, false);
            }
            return new UserRegistrationResult(existingUser, false);
        }

        // Create new user with LGPD consent timestamp (NFR25)
        User newUser = new User(email, User.AuthProvider.GOOGLE, googleId);
        newUser.setLgpdConsentAt(java.time.LocalDateTime.now());
        User savedUser = userRepository.save(newUser);

        // Log audit event for user registration (NFR20)
        auditLogService.log("user_registration", savedUser.getId(), ipAddress,
            String.format("{\"provider\":\"google\",\"email\":\"%s\"}", email));

        return new UserRegistrationResult(savedUser, true);
    }

    /**
     * Register or retrieve user from email/password registration.
     * If user already exists with EMAIL provider, throws ConflictException.
     * If user exists with GOOGLE provider, links email/password method.
     * Otherwise, creates new user with Email provider and logs audit event.
     *
     * NOTE (2026-03-17): HYBRID ARCHITECTURE - This method IS CALLED by frontend.
     * Following hybrid architecture (ARQUITETURA-SIMPLIFICADA.md), users are created
     * in Supabase Auth (authentication authority) and synced to backend for:
     * - Account locking, audit logs, rate limiting, business data
     *
     * @param userId User ID from Supabase JWT
     * @param request Registration request with email and name
     * @param ipAddress Client IP address for audit logging
     * @return UserRegistrationResult with user entity and isNewUser flag
     * @throws ConflictException if user already exists with EMAIL provider
     */
    @Transactional
    public UserRegistrationResult registerOrGetUserFromEmail(String userId, RegisterEmailRequest request, String ipAddress) {
        String email = request.getEmail();

        // Check if user exists by email
        Optional<User> existingByEmail = userRepository.findByEmailAndDeletedAtIsNull(email);

        if (existingByEmail.isPresent()) {
            User existingUser = existingByEmail.get();

            // If user already has EMAIL provider, throw conflict
            if (existingUser.getAuthProvider() == User.AuthProvider.EMAIL) {
                throw new ConflictException("Email already registered");
            }

            // User exists with GOOGLE provider - link email/password method
            existingUser.setAuthProvider(User.AuthProvider.EMAIL);
            User updatedUser = userRepository.save(existingUser);

            // Log account linking event
            auditLogService.log("auth_method_linked", updatedUser.getId(), ipAddress,
                String.format("{\"email\":\"%s\",\"previous_provider\":\"GOOGLE\"}", email));

            return new UserRegistrationResult(updatedUser, false);
        }

        // Create new user with LGPD consent timestamp (NFR25)
        User newUser = new User(email, User.AuthProvider.EMAIL, null);
        newUser.setId(UUID.fromString(userId)); // Use Supabase user ID
        newUser.setLgpdConsentAt(java.time.LocalDateTime.now());
        User savedUser = userRepository.save(newUser);

        // Log audit event for user registration (NFR20)
        auditLogService.log("user_registration", savedUser.getId(), ipAddress,
            String.format("{\"provider\":\"email\",\"email\":\"%s\"}", email));

        return new UserRegistrationResult(savedUser, true);
    }

    /**
     * Find user by ID.
     *
     * @param userId User's UUID
     * @return Optional containing user if found
     */
    public Optional<User> findById(String userId) {
        try {
            return userRepository.findById(java.util.UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Find user by email address.
     * Used by login flow to check if user exists in backend database (hybrid architecture).
     *
     * @param email User's email address
     * @return User entity if found, null otherwise
     */
    public User findUserByEmail(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
    }
}
