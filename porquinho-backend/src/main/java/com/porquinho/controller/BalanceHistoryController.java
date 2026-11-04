package com.porquinho.controller;

import com.porquinho.dto.BalanceHistoryResponse;
import com.porquinho.service.BalanceHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Balance history of an account (Story 4.7), used by the history page. The user always comes from the JWT. */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/balance-history")
public class BalanceHistoryController {

    private final BalanceHistoryService balanceHistoryService;

    public BalanceHistoryController(BalanceHistoryService balanceHistoryService) {
        this.balanceHistoryService = balanceHistoryService;
    }

    /** 200 with the account and its points; 400 "Período inválido"; 404 if the account is not the user's. */
    @GetMapping
    public ResponseEntity<BalanceHistoryResponse> get(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable("accountId") UUID accountId,
                                                      @RequestParam(name = "range", defaultValue = BalanceHistoryService.DEFAULT_RANGE)
                                                      String range) {
        return ResponseEntity.ok(balanceHistoryService.history(UUID.fromString(jwt.getSubject()), accountId, range));
    }
}
