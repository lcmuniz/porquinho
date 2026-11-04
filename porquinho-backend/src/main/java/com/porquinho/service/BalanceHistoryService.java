package com.porquinho.service;

import com.porquinho.dto.BalanceHistoryResponse;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Balance history of one account for the chart (Story 4.7). The period counts back from now in America/Sao_Paulo;
 * the last snapshot before the period comes first so the line does not start from nothing. Points are oldest first
 * (snapshot_date, then created_at, then id). Removed accounts stay readable; another user's account is 404.
 */
@Service
public class BalanceHistoryService {

    public static final String INVALID_RANGE_MESSAGE = "Período inválido";
    public static final String DEFAULT_RANGE = "3m";

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final AccountRepository accountRepository;
    private final AccountBalanceHistoryRepository balanceHistoryRepository;
    private final Clock clock;

    public BalanceHistoryService(AccountRepository accountRepository,
                                 AccountBalanceHistoryRepository balanceHistoryRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
        this.clock = clock;
    }

    /** 400 "Período inválido" for an unknown range; 404 "Conta não encontrada" when the account is not the user's. */
    @Transactional(readOnly = true)
    public BalanceHistoryResponse history(UUID userId, UUID accountId, String range) {
        OffsetDateTime from = periodStart(range == null ? DEFAULT_RANGE : range);
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(AccountService.NOT_FOUND_MESSAGE));

        if (from == null) {
            return BalanceHistoryResponse.of(account, null, balanceHistoryRepository.findByAccount(accountId, userId));
        }
        List<AccountBalanceHistory> points = new ArrayList<>(
            balanceHistoryRepository.findByAccountBefore(accountId, userId, from, PageRequest.of(0, 1)));
        points.addAll(balanceHistoryRepository.findByAccountSince(accountId, userId, from));
        return BalanceHistoryResponse.of(account, from, points);
    }

    /** Start of the period, or null for "all". */
    private OffsetDateTime periodStart(String range) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(SAO_PAULO));
        ZonedDateTime start = switch (range) {
            case "30d" -> now.minusDays(30);
            case "3m" -> now.minusMonths(3);
            case "6m" -> now.minusMonths(6);
            case "1y" -> now.minusYears(1);
            case "all" -> null;
            default -> throw new BadRequestException(INVALID_RANGE_MESSAGE, "range");
        };
        return start == null ? null : start.toOffsetDateTime();
    }
}
