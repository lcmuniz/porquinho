package com.porquinho.controller;

import com.porquinho.dto.CreateReconciliationRequest;
import com.porquinho.dto.ReconciliationResponse;
import com.porquinho.service.ReconciliationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Reconciliation of an account's balance with the bank (Story 4.6), used by the accounts page. The user always comes
 * from the JWT.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/reconciliations")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /** The account's 20 most recent reconciliations; 404 if it is not one of the user's active accounts. */
    @GetMapping
    public ResponseEntity<List<ReconciliationResponse>> list(@AuthenticationPrincipal Jwt jwt,
                                                             @PathVariable("accountId") UUID accountId) {
        return ResponseEntity.ok(reconciliationService.listRecent(UUID.fromString(jwt.getSubject()), accountId));
    }

    /**
     * 201 with the updated account and the recorded event; 400 (ProblemDetail with the field errors) when invalid;
     * 404 if the account is not one of the user's active accounts.
     */
    @PostMapping
    public ResponseEntity<ReconciliationResponse.Created> create(@AuthenticationPrincipal Jwt jwt,
                                                                 @PathVariable("accountId") UUID accountId,
                                                                 @Valid @RequestBody CreateReconciliationRequest request,
                                                                 HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(reconciliationService.reconcile(userId, accountId, request, httpRequest.getRemoteAddr()));
    }
}
