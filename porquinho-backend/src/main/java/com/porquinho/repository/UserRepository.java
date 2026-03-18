package com.porquinho.repository;

import com.porquinho.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /**
     * Insert a new user with a specific ID (used for Supabase UUID sync).
     * Uses native SQL to bypass JPA's merge behavior when ID is pre-set.
     *
     * @param id Supabase user UUID
     * @param email User's email address
     * @param authProvider Authentication provider (EMAIL, GOOGLE, etc)
     * @param lgpdConsentAt LGPD consent timestamp
     * @param createdAt Creation timestamp
     * @param updatedAt Update timestamp
     */
    @Modifying
    @Query(value = "INSERT INTO users (id, email, auth_provider, lgpd_consent_at, failed_login_attempts, created_at, updated_at) " +
                   "VALUES (:id, :email, :authProvider, :lgpdConsentAt, 0, :createdAt, :updatedAt)",
           nativeQuery = true)
    void insertWithId(@Param("id") UUID id,
                     @Param("email") String email,
                     @Param("authProvider") String authProvider,
                     @Param("lgpdConsentAt") LocalDateTime lgpdConsentAt,
                     @Param("createdAt") LocalDateTime createdAt,
                     @Param("updatedAt") LocalDateTime updatedAt);
}
