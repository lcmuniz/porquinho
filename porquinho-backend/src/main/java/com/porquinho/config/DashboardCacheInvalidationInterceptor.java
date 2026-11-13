package com.porquinho.config;

import com.porquinho.service.DashboardCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;
import java.util.UUID;

/**
 * Story 7.7: one rule per URL instead of an evict in every service. After any authenticated write under /api/v1 (the
 * service transaction is already committed) the user's dashboard cache goes; a budget write keeps the layer 3, which
 * does not depend on the budget. Writes that do not touch the dashboard (onboarding, ...) also evict: they are rare.
 */
@Configuration
public class DashboardCacheInvalidationInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");

    // a provider: the @WebMvcTest slices load this configurer without the Redis beans
    private final ObjectProvider<DashboardCache> dashboardCache;

    public DashboardCacheInvalidationInterceptor(ObjectProvider<DashboardCache> dashboardCache) {
        this.dashboardCache = dashboardCache;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/**");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        if (!WRITES.contains(request.getMethod())) return;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        DashboardCache cache = dashboardCache.getIfAvailable();
        if (!(auth instanceof JwtAuthenticationToken) || cache == null) return;
        UUID userId;
        try {
            userId = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api/v1/budgets") || path.startsWith("/api/v1/budgets/")) {
            cache.evictBudgetLayers(userId);
        } else {
            cache.evictAll(userId);
        }
    }
}
