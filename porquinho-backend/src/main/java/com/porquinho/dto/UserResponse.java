package com.porquinho.dto;

import com.porquinho.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for User entity.
 * Contains user information to be returned to the client.
 * Does not expose sensitive fields like deleted_at or deleted_by.
 */
public class UserResponse {

    private UUID id;
    private String email;
    private String authProvider;
    private LocalDateTime createdAt;

    // Constructors
    public UserResponse() {
    }

    public UserResponse(UUID id, String email, String authProvider, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.authProvider = authProvider;
        this.createdAt = createdAt;
    }

    /**
     * Factory method to create UserResponse from User entity.
     */
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getAuthProvider().name(),
            user.getCreatedAt()
        );
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

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
