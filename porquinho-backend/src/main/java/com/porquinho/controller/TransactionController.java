package com.porquinho.controller;

import com.porquinho.dto.TransactionResponse;
import com.porquinho.dto.TransactionUpdateRequest;
import com.porquinho.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import java.util.Map;
import java.util.UUID;

/** The confirmed transactions of /transacoes (Story 5.12). The user always comes from the JWT. */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /** The confirmed transactions of {@code month} (YYYY-MM, default the current month), newest first. 400 for a bad month. */
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> list(@AuthenticationPrincipal Jwt jwt,
                                                          @RequestParam(value = "month", required = false) String month) {
        return ResponseEntity.ok(transactionService.list(UUID.fromString(jwt.getSubject()), month));
    }

    /** Edits a confirmed transaction. 400 for an invalid body or balance out of range; 404 for a row, target or category not available. */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                      @Valid @RequestBody TransactionUpdateRequest request,
                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.ok(transactionService.update(UUID.fromString(jwt.getSubject()), id, request,
            httpRequest.getRemoteAddr()));
    }

    /** Removes (soft delete) a confirmed transaction. 204; 404 if not available. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       HttpServletRequest httpRequest) {
        transactionService.delete(UUID.fromString(jwt.getSubject()), id, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /** Removes the future installments of the purchase. 200 with how many were removed (0 when none); 404 if not available. */
    @PostMapping("/{id}/remove-future-installments")
    public ResponseEntity<Map<String, Integer>> removeFutureInstallments(@AuthenticationPrincipal Jwt jwt,
                                                                         @PathVariable UUID id,
                                                                         HttpServletRequest httpRequest) {
        int removed = transactionService.removeFutureInstallments(UUID.fromString(jwt.getSubject()), id,
            httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("removedInstallments", removed));
    }
}
