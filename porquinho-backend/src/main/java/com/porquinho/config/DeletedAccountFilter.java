package com.porquinho.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.repository.UserRepository;
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
import java.util.UUID;

/**
 * Retro Epic 1 A2 (S6): blocks authenticated /api/v1/** requests with 403 when the user asked for account deletion
 * (users.deleted_at set). The user stays in Supabase until the purge, so a new login yields a token that is not in
 * the blacklist; without this barrier it would read and write data for up to 30 days.
 *
 * Only /api/v1/account-deletion is exempt: the request is idempotent and returns the pending deletion.
 * Registration would answer 409 (rejectIfDeletionPending) anyway; here it gets the same 403 as every other route.
 *
 * Not a @Component for the same reason as SubscriptionAccessFilter; SecurityConfig adds it before that filter,
 * so an account in deletion gets 403 and not 402.
 *
 * ponytail: one primary-key query per request, no cache (same trade-off as SubscriptionAccessFilter).
 */
public class DeletedAccountFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";
    private static final String EXEMPT_PATH = "/api/v1/account-deletion";

    private final UserRepository userRepository;
    // Mixin gives the same JSON shape Spring MVC uses for ProblemDetail (no nulls, flat properties)
    private final ObjectMapper objectMapper = new ObjectMapper()
        .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);

    public DeletedAccountFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(API_PREFIX) || path.equals(EXEMPT_PATH) || path.startsWith(EXEMPT_PATH + "/")) {
            return true;
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

        if (!userRepository.isDeletionPending(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            "Esta conta está em exclusão e não pode mais ser usada."
        );
        problem.setTitle("Conta em exclusão");

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
