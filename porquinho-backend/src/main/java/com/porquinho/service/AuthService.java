package com.porquinho.service;

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
    private final SubscriptionService subscriptionService;

    public AuthService(UserRepository userRepository, AuditLogService auditLogService,
                       SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.subscriptionService = subscriptionService;
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
     * Identity is the Supabase JWT (sub, e-mail, Google id from its claims): the user is found only by id = sub
     * (Story 2.1, Task 0). Google is linked to that user only when the e-mail also matches; an e-mail already used
     * by another id is a 409, never a takeover of that account (retro Epic 1 A1, S2).
     *
     * @param userId User ID from Supabase JWT (sub claim)
     * @param email User's email from the JWT
     * @param googleId User's Google OAuth ID from the JWT
     * @param ipAddress Client IP address for audit logging
     * @return UserRegistrationResult with user entity and isNewUser flag
     */
    @Transactional
    public UserRegistrationResult registerOrGetUserFromGoogle(String userId, String email, String googleId, String ipAddress) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID (JWT sub) is required to create a user");
        }
        UUID userUuid = UUID.fromString(userId);

        Optional<User> existingById = userRepository.findById(userUuid);
        if (existingById.isPresent()) {
            User existingUser = existingById.get();
            if (!googleId.equals(existingUser.getGoogleId()) && existingUser.getEmail().equalsIgnoreCase(email)) {
                // Same sub and same e-mail: the user registered with e-mail/password and now signs in with Google
                boolean wasEmail = existingUser.getAuthProvider() == User.AuthProvider.EMAIL;
                existingUser.setGoogleId(googleId);
                if (wasEmail) {
                    existingUser.setAuthProvider(User.AuthProvider.GOOGLE);
                }
                User updatedUser = userRepository.save(existingUser);
                auditLogService.log("google_account_linked", updatedUser.getId(), ipAddress,
                    AuditLogService.metadata("email", email, "previous_provider", wasEmail ? "EMAIL" : "GOOGLE"));
                return new UserRegistrationResult(updatedUser, false);
            }
            return new UserRegistrationResult(existingUser, false);
        }

        rejectIfEmailTaken(email);
        rejectIfDeletionPending(userUuid);

        // Create new user with LGPD consent timestamp (NFR25), using the Supabase id
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        userRepository.insertWithId(
            userUuid,
            email,
            User.AuthProvider.GOOGLE.name(),
            googleId,
            now,  // lgpdConsentAt
            now,  // createdAt
            now   // updatedAt
        );

        User savedUser = userRepository.findById(userUuid)
            .orElseThrow(() -> new RuntimeException("Failed to create user"));

        // Story 2.1: 30-day trial in the same transaction (failure rolls back the registration)
        subscriptionService.startTrial(savedUser.getId(), ipAddress);

        // Log audit event for user registration (NFR20)
        auditLogService.log("user_registration", savedUser.getId(), ipAddress,
            AuditLogService.metadata("provider", "google", "email", email));

        return new UserRegistrationResult(savedUser, true);
    }

    /**
     * Register or retrieve user from email/password registration.
     * Identity is the Supabase JWT (sub and e-mail claims): an existing user is found only by id = sub and returned
     * as is (idempotent). An e-mail already used by another id is a 409 (retro Epic 1 A1, S2).
     *
     * NOTE (2026-03-17): HYBRID ARCHITECTURE - This method IS CALLED by frontend.
     * Following hybrid architecture (README.md, Architecture), users are created
     * in Supabase Auth (authentication authority) and synced to backend for:
     * - Account locking, audit logs, rate limiting, business data
     *
     * @param userId User ID from Supabase JWT
     * @param email User's e-mail from the JWT
     * @param ipAddress Client IP address for audit logging
     * @return UserRegistrationResult with user entity and isNewUser flag
     * @throws ConflictException if the e-mail belongs to another user, or the account is being deleted
     */
    @Transactional
    public UserRegistrationResult registerOrGetUserFromEmail(String userId, String email, String ipAddress) {
        UUID userUuid = UUID.fromString(userId);

        Optional<User> existingById = userRepository.findById(userUuid);
        if (existingById.isPresent()) {
            return new UserRegistrationResult(existingById.get(), false);
        }

        rejectIfEmailTaken(email);
        rejectIfDeletionPending(userUuid);

        // Create new user with LGPD consent timestamp (NFR25)
        // Use native query to insert with pre-defined ID (from Supabase)
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        userRepository.insertWithId(
            userUuid,
            email,
            User.AuthProvider.EMAIL.name(),
            null, // googleId
            now,  // lgpdConsentAt
            now,  // createdAt
            now   // updatedAt
        );

        // Fetch the created user to return
        User createdUser = userRepository.findById(userUuid)
            .orElseThrow(() -> new RuntimeException("Failed to create user"));

        // Story 2.1: 30-day trial in the same transaction (failure rolls back the registration)
        subscriptionService.startTrial(createdUser.getId(), ipAddress);

        // Log audit event for user registration (NFR20)
        auditLogService.log("user_registration", createdUser.getId(), ipAddress,
            AuditLogService.metadata("provider", "email", "email", email));

        return new UserRegistrationResult(createdUser, true);
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

    static final String DELETION_PENDING_MESSAGE =
        "Esta conta está em exclusão. Aguarde a conclusão para se cadastrar de novo.";

    static final String EMAIL_TAKEN_MESSAGE = "Este e-mail já está cadastrado em outra conta.";

    /** Another user (different JWT sub) already has this e-mail: never hand that account over. */
    private void rejectIfEmailTaken(String email) {
        if (userRepository.findByEmailAndDeletedAtIsNull(email).isPresent()) {
            throw new ConflictException(EMAIL_TAKEN_MESSAGE);
        }
    }

    /** A soft-deleted row (waiting for the purge, Story 1.7) still holds the id: inserting it again would be a 500. */
    private void rejectIfDeletionPending(UUID userId) {
        if (userRepository.findByIdIncludingDeleted(userId).isPresent()) {
            throw new ConflictException(DELETION_PENDING_MESSAGE);
        }
    }
}
