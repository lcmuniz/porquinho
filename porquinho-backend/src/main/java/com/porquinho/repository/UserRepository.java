package com.porquinho.repository;

import com.porquinho.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entity.
 * Provides CRUD operations and custom query methods.
 * All queries automatically exclude soft-deleted users via @Where clause in User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by email address (non-deleted only).
     * @param email The email address to search for
     * @return Optional containing the user if found, empty otherwise
     */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    /**
     * Find a user by Google OAuth ID (non-deleted only).
     * @param googleId The Google OAuth user ID to search for
     * @return Optional containing the user if found, empty otherwise
     */
    Optional<User> findByGoogleIdAndDeletedAtIsNull(String googleId);
}
