package com.porquinho.controller;

import com.porquinho.dto.AccountResponse;
import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.UpdateAccountRequest;
import com.porquinho.entity.Account;
import com.porquinho.service.AccountService;
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
 * Bank account endpoints (Story 3.2). Used by onboarding step 2 and, later, by the accounts page (Epic 4).
 * The user always comes from the JWT.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    static final String STATUS_DELETED = "deleted";
    static final String INVALID_STATUS_MESSAGE = "Filtro de status inválido";

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * The user's active accounts, oldest first. Story 4.4: {@code ?status=deleted} lists the removed ones instead,
     * most recently removed first; any other status is 400.
     */
    @GetMapping
    public ResponseEntity<List<AccountResponse>> list(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(name = "status", required = false) String status) {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<Account> accounts;
        if (status == null) {
            accounts = accountService.listActive(userId);
        } else if (STATUS_DELETED.equals(status)) {
            accounts = accountService.listDeleted(userId);
        } else {
            throw new BadRequestException(INVALID_STATUS_MESSAGE);
        }
        return ResponseEntity.ok(accounts.stream().map(AccountResponse::of).toList());
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

    /**
     * Soft delete with audit (Story 4.4). 204; 404 if the account is not the user's active account; 409 when it is the last active account and the onboarding is completed.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
                                       HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        accountService.delete(userId, id, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /** Story 4.4: 200 with the restored account; 404 if it is not one of the user's removed accounts. */
    @PostMapping("/{id}/restore")
    public ResponseEntity<AccountResponse> restore(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
                                                  HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(AccountResponse.of(accountService.restore(userId, id, request.getRemoteAddr())));
    }
}
