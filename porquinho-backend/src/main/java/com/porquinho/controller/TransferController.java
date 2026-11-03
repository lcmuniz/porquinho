package com.porquinho.controller;

import com.porquinho.dto.CreateTransferRequest;
import com.porquinho.dto.TransferResponse;
import com.porquinho.service.TransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Transfers between the user's own accounts (Story 4.5), used by the accounts page. The user always comes from the
 * JWT.
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /** The user's 20 most recent active transfers, with the account names. */
    @GetMapping
    public ResponseEntity<List<TransferResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(transferService.listRecent(UUID.fromString(jwt.getSubject())));
    }

    /**
     * 201 with the new transfer; 200 with the existing one when the id was already used by this user (idempotent
     * retry, nothing changes); 404 when the id belongs to another user; 400 (ProblemDetail with the field errors) when invalid, including the same account on
     * both sides ({@code errors.differentAccounts}); 404 when either account is not one of the user's active accounts.
     */
    @PostMapping
    public ResponseEntity<TransferResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CreateTransferRequest request,
                                                   HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(jwt.getSubject());
        TransferService.Result result = transferService.create(userId, request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.transfer());
    }
}
