package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User entity representing user accounts in the system.
 * Supports multiple authentication providers (Google OAuth, Email/Password).
 * Implements soft delete pattern via deleted_at field.
 */
@Entity
@Table(name = "users")
@Where(clause = "deleted_at IS NULL")
public class User {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "auth_provider", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    @Column(name = "google_id", length = 255)
    private String googleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "lgpd_consent_at")
    private LocalDateTime lgpdConsentAt;

    // Constructors
    public User() {
    }

    public User(String email, AuthProvider authProvider, String googleId) {
        this.email = email;
        this.authProvider = authProvider;
        this.googleId = googleId;
    }

    // Lifecycle callbacks
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Soft delete the user.
     * @param deletedBy UUID of the user performing the deletion
     */
    public void softDelete(UUID deletedBy) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }

    public LocalDateTime getLgpdConsentAt() {
        return lgpdConsentAt;
    }

    public void setLgpdConsentAt(LocalDateTime lgpdConsentAt) {
        this.lgpdConsentAt = lgpdConsentAt;
    }

    /**
     * Authentication provider enum.
     */
    public enum AuthProvider {
        GOOGLE,
        EMAIL
    }
}
