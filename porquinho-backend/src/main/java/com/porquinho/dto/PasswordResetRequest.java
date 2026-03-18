package com.porquinho.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for password reset audit logging.
 * Used when user requests password reset via email.
 */
public class PasswordResetRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    // Constructors
    public PasswordResetRequest() {
    }

    public PasswordResetRequest(String email) {
        this.email = email;
    }

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
