package com.porquinho.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;

/**
 * Converter that extracts user_id from Supabase JWT "sub" claim.
 * The sub claim in Supabase contains the user's UUID.
 * This allows controllers to use @AuthenticationPrincipal String userId.
 */
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Extract user_id from "sub" claim (Supabase stores user UUID in sub)
        String userId = jwt.getSubject();

        // Extract authorities/roles if needed (Supabase can store custom claims)
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);

        // Create authentication token with userId as principal
        return new JwtAuthenticationToken(jwt, authorities, userId);
    }

    /**
     * Extract granted authorities from JWT claims.
     * Can be extended to support role-based access control using custom claims.
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Default: all authenticated users have USER role
        // In the future, can extract roles from custom claims like jwt.getClaim("roles")
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
