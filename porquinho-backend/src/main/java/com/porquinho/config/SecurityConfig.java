package com.porquinho.config;

import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security configuration for Porquinho backend.
 * Configures JWT-based authentication using Supabase Auth as the identity provider.
 * Uses JWT_SECRET for token validation (HMAC SHA-256).
 * CORS is enabled for local frontend development.
 * Only active in non-test profiles.
 */
@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    @Value("${SUPABASE_JWT_SECRET}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/auth/health").permitAll() // Health check endpoint
                .requestMatchers("/api/v1/auth/login/check").permitAll() // Login check (rate limit + account lock) - no auth required
                .requestMatchers("/api/v1/auth/login/failed").permitAll() // Record failed login - no auth required
                .requestMatchers("/api/v1/auth/login").permitAll() // TEMPORARY: Allow login audit without JWT until JWT validation is fixed
                .requestMatchers("/api/v1/auth/register/email").permitAll() // TEMPORARY: Allow email registration without JWT until JWT validation is fixed
                .requestMatchers("/api/v1/auth/register/**").authenticated() // Other registration endpoints require JWT
                .requestMatchers("/api/v1/**").authenticated() // All other API endpoints require authentication
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Create HMAC secret key from JWT_SECRET (Supabase uses HS256)
        SecretKey secretKey = new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        return new JwtAuthenticationConverter();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:5173",  // Vite dev server
            "http://localhost:4173"   // Vite preview server
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
