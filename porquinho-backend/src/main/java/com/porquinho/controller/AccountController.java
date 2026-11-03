package com.porquinho.controller;

import com.porquinho.dto.AccountResponse;
import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.UpdateAccountRequest;
import com.porquinho.service.AccountService;
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
 * Bank account endpoints (Story 3.2). Used by onboarding step 2 and, later, by the accounts page (Epic 4).
 * The user always comes from the JWT.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** The user's active accounts, oldest first. */
    @GetMapping
    public ResponseEntity<List<AccountResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(accountService.listActive(userId).stream().map(AccountResponse::of).toList());
    }

    /** 201 with the new account; 400 (ProblemDetail with the field errors) when invalid. */
    @PostMapping
    public ResponseEntity<AccountResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateAccountRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.of(accountService.create(userId, request)));
    }

    /**
     * Story 4.3: 200 with the edited account; 400 when invalid (same rules as POST, current balance instead of
     * initial); 404 if the account is not the user's active account.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AccountResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
                                                  @Valid @RequestBody UpdateAccountRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(AccountResponse.of(accountService.update(userId, id, request)));
    }

    /** Soft delete. 204; 404 if the account is not the user's (or does not exist). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        accountService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
