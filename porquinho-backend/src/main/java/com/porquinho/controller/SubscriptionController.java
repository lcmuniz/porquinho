package com.porquinho.controller;

import com.porquinho.dto.SubscriptionResponse;
import com.porquinho.entity.Subscription;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

        return ResponseEntity.ok(SubscriptionResponse.of(
            subscription,
            subscriptionService.daysRemaining(subscription),
            subscriptionService.hasAccess(subscription)
        ));
    }
}
