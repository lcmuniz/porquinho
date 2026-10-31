package com.porquinho.config;

import com.porquinho.service.JwtBlacklistService;
import com.porquinho.service.SessionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.Collection;
import java.util.Collections;

/**
 * Converter that extracts user_id from Supabase JWT "sub" claim.
 * The sub claim in Supabase contains the user's UUID.
 * The token's name is the user id: controllers read it via Authentication.getName().
 * (The principal is the Jwt object, so @AuthenticationPrincipal String resolves to null.)
 * Also checks JWT blacklist for revoked sessions.
 */
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private JwtBlacklistService jwtBlacklistService;

    public void setJwtBlacklistService(JwtBlacklistService jwtBlacklistService) {
        this.jwtBlacklistService = jwtBlacklistService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Check if token is blacklisted (revoked session)
        if (jwtBlacklistService != null) {
            String tokenHash = SessionService.hashToken(jwt.getTokenValue());
            if (jwtBlacklistService.isBlacklisted(tokenHash)) {
                throw new InvalidBearerTokenException("Token has been revoked");
            }
        }

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
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
