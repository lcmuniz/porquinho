package com.porquinho.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

/**
 * Test configuration for JWT decoder.
 * Provides a mock JwtDecoder for integration tests that don't need real JWT validation.
 * Automatically loaded for test profile.
 */
@TestConfiguration
@Profile("test")
public class TestSecurityConfig {

    /**
     * Mock JwtDecoder for tests.
     * Returns a valid Jwt with test subject for any token.
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token != null ? token : "test-token")
            .header("alg", "none")
            .claim("sub", "test-user-id")
            .claim("email", "test@example.com")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    }
}
