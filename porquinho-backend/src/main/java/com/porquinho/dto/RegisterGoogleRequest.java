package com.porquinho.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for Google OAuth registration.
 * Contains user information extracted from Supabase JWT after successful Google OAuth.
 */
public class RegisterGoogleRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Google ID is required")
    private String googleId;

    private String name;

    // Constructors
    public RegisterGoogleRequest() {
    }

    public RegisterGoogleRequest(String email, String googleId, String name) {
        this.email = email;
        this.googleId = googleId;
        this.name = name;
    }

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
