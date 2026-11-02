package com.porquinho.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.service.SubscriptionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Blocks authenticated /api/v1/** requests with 402 Payment Required when the user has no
 * access (SubscriptionService.hasAccess: no subscription, trial ended, cancelled past the paid period,
 * EXPIRED or SUSPENDED; PAYMENT_FAILED keeps access). Story 2.1, AC4.
 *
 * Intentionally NOT a @Component: Spring Boot would register it as a global servlet filter,
 * including in the test profile. It is added to the security chain in SecurityConfig, after
 * BearerTokenAuthenticationFilter, so the JwtAuthenticationToken is already in the context.
 *
 * ponytail: one indexed query by user_id per request, no cache. If latency becomes a problem,
 * add @Cacheable on Redis (RedisConfig) with a short TTL and evict on startTrial/updates.
 */
public class SubscriptionAccessFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";

    private static final List<String> EXEMPT_PREFIXES = List.of(
        "/api/v1/auth/",              // registration, login, sessions, password reset
        "/api/v1/subscriptions/",     // user must see own status (and subscribe in 2.2)
        "/api/v1/data-export/",       // LGPD right: independent of payment
        "/api/v1/account-deletion/"   // LGPD right: independent of payment
    );

    private final SubscriptionService subscriptionService;
    // Mixin gives the same JSON shape Spring MVC uses for ProblemDetail (no nulls, flat properties)
    private final ObjectMapper objectMapper = new ObjectMapper()
        .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);

    public SubscriptionAccessFilter(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathOf(request);
        if (!path.startsWith(API_PREFIX)) {
            return true;
        }
        for (String prefix : EXEMPT_PREFIXES) {
            // Match both "/api/v1/auth/..." and the bare "/api/v1/auth"
            if (path.startsWith(prefix) || path.equals(prefix.substring(0, prefix.length() - 1))) {
                return true;
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return !(auth instanceof JwtAuthenticationToken);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

        UUID userId;
        try {
            userId = UUID.fromString(auth.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            // Token already validated by authentication; the controller handles an odd subject.
            filterChain.doFilter(request, response);
            return;
        }

        if (subscriptionService.hasAccess(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYMENT_REQUIRED,
            "Seu período de trial terminou. Assine para continuar."
        );
        problem.setTitle("Assinatura necessária");

        response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
