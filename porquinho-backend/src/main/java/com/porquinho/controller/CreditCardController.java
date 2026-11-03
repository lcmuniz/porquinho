package com.porquinho.controller;

import com.porquinho.dto.CreateCreditCardRequest;
import com.porquinho.dto.CreditCardResponse;
import com.porquinho.service.CreditCardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Credit card endpoints (Story 3.3). Used by onboarding step 3 and, later, by the cards page (Epic 4).
 * The user always comes from the JWT.
 */
@RestController
@RequestMapping("/api/v1/credit-cards")
public class CreditCardController {

    private final CreditCardService creditCardService;

    public CreditCardController(CreditCardService creditCardService) {
        this.creditCardService = creditCardService;
    }

    /** The user's active cards, oldest first. */
    @GetMapping
    public ResponseEntity<List<CreditCardResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(creditCardService.listActive(userId).stream().map(CreditCardResponse::of).toList());
    }

    /** 201 with the new card; 400 (ProblemDetail with the field errors) when invalid. */
    @PostMapping
    public ResponseEntity<CreditCardResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                     @Valid @RequestBody CreateCreditCardRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CreditCardResponse.of(creditCardService.create(userId, request)));
    }

    /** Story 4.3: 200 with the edited card; 400 when invalid (same rules as POST); 404 if not the user's active card. */
    @PutMapping("/{id}")
    public ResponseEntity<CreditCardResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
                                                     @Valid @RequestBody CreateCreditCardRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(CreditCardResponse.of(creditCardService.update(userId, id, request)));
    }

    /** Soft delete. 204; 404 if the card is not the user's (or does not exist, or was already removed). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        creditCardService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
