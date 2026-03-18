package com.porquinho.service;

import com.porquinho.entity.User;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for account locking after failed login attempts.
 * Implements security feature to prevent brute force attacks.
 */
@Service
public class AccountLockService {

    private final UserRepository userRepository;

    public AccountLockService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Record a failed login attempt for the user.
     * Locks the account for 15 minutes after 5 failed attempts.
     * Does nothing if user doesn't exist (Supabase-only architecture).
     *
     * @param email User's email address
     */
    @Transactional
    public void recordFailedAttempt(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);

        if (user == null) {
            // User doesn't exist in backend (Supabase-only architecture) - skip recording
            return;
        }

        int failedAttempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(failedAttempts);

        // Lock account after 5 failed attempts
        if (failedAttempts >= 5) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        }

        userRepository.save(user);
    }

    /**
     * Check if the user's account is currently locked.
     * Account is locked if locked_until is in the future.
     *
     * @param email User's email address
     * @return true if account is locked, false otherwise
     */
    public boolean isLocked(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);

        if (user == null || user.getLockedUntil() == null) {
            return false;
        }

        // Check if lock has expired
        return LocalDateTime.now().isBefore(user.getLockedUntil());
    }

    /**
     * Reset failed login attempts counter and unlock the account.
     * Called after a successful login.
     * Does nothing if user doesn't exist (Supabase-only architecture).
     *
     * @param email User's email address
     */
    @Transactional
    public void resetFailedAttempts(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);

        if (user == null) {
            // User doesn't exist in backend (Supabase-only architecture) - skip reset
            return;
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        userRepository.save(user);
    }
}
