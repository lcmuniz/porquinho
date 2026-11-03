package com.porquinho.controller;

import com.porquinho.dto.CreateCreditCardRequest;
import com.porquinho.dto.CreditCardResponse;
import com.porquinho.entity.CreditCard;
import com.porquinho.service.CreditCardService;
import com.porquinho.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    static final String STATUS_DELETED = "deleted";
    static final String INVALID_STATUS_MESSAGE = "Filtro de status inválido";

    private final CreditCardService creditCardService;

    public CreditCardController(CreditCardService creditCardService) {
        this.creditCardService = creditCardService;
    }

    /**
     * The user's active cards, oldest first. Story 4.4: {@code ?status=deleted} lists the removed ones instead,
     * most recently removed first; any other status is 400.
     */
    @GetMapping
    public ResponseEntity<List<CreditCardResponse>> list(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(name = "status", required = false) String status) {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<CreditCard> cards;
        if (status == null) {
            cards = creditCardService.listActive(userId);
        } else if (STATUS_DELETED.equals(status)) {
            cards = creditCardService.listDeleted(userId);
        } else {
            throw new BadRequestException(INVALID_STATUS_MESSAGE);
        }
        return ResponseEntity.ok(cards.stream().map(CreditCardResponse::of).toList());
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

    /**
     * Soft delete with audit (Story 4.4). 204; 404 if the card is not the user's active card.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
                                       HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        creditCardService.delete(userId, id, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /** Story 4.4: 200 with the restored card; 404 if it is not one of the user's removed cards. */
    @PostMapping("/{id}/restore")
    public ResponseEntity<CreditCardResponse> restore(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
                                                  HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(CreditCardResponse.of(creditCardService.restore(userId, id, request.getRemoteAddr())));
    }
}
