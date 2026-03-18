package com.porquinho.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for email/password registration.
 * Contains user information for email/password registration.
 * Password is NOT included - Supabase manages password hashing.
 *
 * NOTE: userId added for hybrid architecture (TEMPORARY until JWT validation is fixed).
 * Frontend sends Supabase user UUID in request body.
 */
public class RegisterEmailRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;

    // TEMPORARY: userId in body until JWT validation is fixed
    private String userId;

    // Constructors
    public RegisterEmailRequest() {
    }

    public RegisterEmailRequest(String email, String name) {
        this.email = email;
        this.name = name;
    }

    public RegisterEmailRequest(String email, String name, String userId) {
        this.email = email;
        this.name = name;
        this.userId = userId;
    }

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
