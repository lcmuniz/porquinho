package com.porquinho.service;

import com.porquinho.entity.User;
import com.porquinho.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
}
