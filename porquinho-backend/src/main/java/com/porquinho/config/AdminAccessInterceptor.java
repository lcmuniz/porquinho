package com.porquinho.config;

import com.porquinho.exception.ForbiddenException;
import com.porquinho.service.AdminService;
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

import java.util.UUID;

/**
 * Story 10.1: every admin route except {@code access} answers 403 to non-admins. Checked here, before the argument
 * binding, so a non-admin gets the 403 and never the 400 of a {@code @Valid @RequestBody}. The DispatcherServlet hands
 * the exception to the GlobalExceptionHandler. The role is read from the database: the Supabase JWT does not carry it.
 */
@Configuration
public class AdminAccessInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    // a provider: the @WebMvcTest slices load this configurer without the service beans
    private final ObjectProvider<AdminService> adminService;

    public AdminAccessInterceptor(ObjectProvider<AdminService> adminService) {
        this.adminService = adminService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/admin/**").excludePathPatterns("/api/v1/admin/access");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            throw new ForbiddenException(AdminService.FORBIDDEN_MESSAGE);
        }
        adminService.getObject().requireAdmin(UUID.fromString(token.getToken().getSubject()));
        return true;
    }
}
