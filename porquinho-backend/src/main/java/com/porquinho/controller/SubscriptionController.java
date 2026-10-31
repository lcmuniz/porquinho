package com.porquinho.controller;

import com.porquinho.dto.SubscriptionResponse;
import com.porquinho.entity.Subscription;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Subscription endpoints (Story 2.1). Exempt from SubscriptionAccessFilter so the user can
 * always see their own status (and subscribe, Story 2.2).
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/me")
    public ResponseEntity<SubscriptionResponse> getMySubscription(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());

        Subscription subscription = subscriptionService.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        return ResponseEntity.ok(toResponse(subscription));
    }

    /**
     * Story 2.2: create a Stripe Checkout session; the frontend just redirects to {@code url}.
     * 409 if already MONTHLY/ACTIVE, 503 if Stripe fails. The plan is activated by the webhook only.
     */
    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> createCheckout(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(Map.of("url", subscriptionService.createCheckout(userId)));
    }

    /**
     * Story 2.4: cancel the monthly plan at the end of the current period (no further charge).
     * 200 with the updated status (CANCELLED, access until nextBillingDate); 409 if not MONTHLY/ACTIVE;
     * 503 if Stripe fails (nothing changes).
     */
    @PostMapping("/cancel")
    public ResponseEntity<SubscriptionResponse> cancel(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Subscription cancelled = subscriptionService.cancel(userId, request.getRemoteAddr());
        return ResponseEntity.ok(toResponse(cancelled));
    }

    private SubscriptionResponse toResponse(Subscription subscription) {
        return SubscriptionResponse.of(
            subscription,
            subscriptionService.daysRemaining(subscription),
            subscriptionService.hasAccess(subscription)
        );
    }
}
