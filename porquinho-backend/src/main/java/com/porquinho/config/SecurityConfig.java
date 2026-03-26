package com.porquinho.config;

import java.util.List;

import com.porquinho.service.JwtBlacklistService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security configuration for Porquinho backend.
 * Configures JWT-based authentication using Supabase Auth as the identity provider.
 * Uses JWK Set (ES256) for token validation - Spring Boot auto-configures from issuer-uri.
 * CORS is enabled for local frontend development.
 * Only active in non-test profiles.
 */
@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    private final JwtBlacklistService jwtBlacklistService;

    public SecurityConfig(JwtBlacklistService jwtBlacklistService) {
        this.jwtBlacklistService = jwtBlacklistService;
    }

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
                .requestMatchers("/api/v1/auth/health").permitAll()
                .requestMatchers("/api/v1/auth/login/check").permitAll()
                .requestMatchers("/api/v1/auth/login/failed").permitAll()
                .requestMatchers("/api/v1/auth/password-reset/requested").permitAll()
                .requestMatchers("/api/v1/data-export/download/**").permitAll()  // Download via UUID token (no JWT needed)
                .requestMatchers("/api/v1/auth/sessions/**").authenticated()  // Session management endpoints (Story 1.5)
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtBlacklistService(jwtBlacklistService);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:5173",  // Vite dev server
            "http://localhost:5175",  // Vite dev server (alternate port)
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
