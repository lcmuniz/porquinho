package com.porquinho.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for email/password registration.
 * Contains user information for email/password registration.
 * Password is NOT included - Supabase manages password hashing.
 * User ID comes from JWT sub claim via @AuthenticationPrincipal.
 */
public class RegisterEmailRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;

    // Constructors
    public RegisterEmailRequest() {
    }

    public RegisterEmailRequest(String email, String name) {
        this.email = email;
        this.name = name;
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
}
