package com.porquinho.config;

import java.util.List;

import com.porquinho.repository.UserRepository;
import com.porquinho.service.SessionService;
import com.porquinho.service.JwtBlacklistService;
import com.porquinho.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
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
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final SessionService sessionService;

    public SecurityConfig(JwtBlacklistService jwtBlacklistService, SubscriptionService subscriptionService,
                          UserRepository userRepository, SessionService sessionService) {
        this.jwtBlacklistService = jwtBlacklistService;
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
        this.sessionService = sessionService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
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
                .requestMatchers("/api/v1/webhooks/stripe").permitAll()  // Story 2.2: authenticated by Stripe-Signature, not JWT
                .requestMatchers("/api/v1/auth/sessions/**").authenticated()  // Session management endpoints (Story 1.5)
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            // Retro Epic 1 A2 (S6): 403 for an account in deletion. Added first, so it runs before the 402 below.
            .addFilterAfter(new DeletedAccountFilter(userRepository), BearerTokenAuthenticationFilter.class)
            // Story 2.1 (AC4): 402 when the subscription gives no access. Runs after the JWT is authenticated.
            .addFilterAfter(new SubscriptionAccessFilter(subscriptionService), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtBlacklistService(jwtBlacklistService);
        converter.setSessionService(sessionService);
        return converter;
    }

    /**
     * Origins from app.cors.allowed-origins (env APP_CORS_ALLOWED_ORIGINS), comma-separated. Default: Vite dev server
     * (5173), its alternate port (5175) and the Vite preview server (4173).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5175,http://localhost:4173}")
            List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Story 9.1: the frontend is cross-origin and reads the CSV row count from this header
        config.setExposedHeaders(List.of("X-Export-Count"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
