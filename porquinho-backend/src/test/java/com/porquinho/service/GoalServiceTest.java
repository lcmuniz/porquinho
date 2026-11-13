package com.porquinho.service;

import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.Goal;
import com.porquinho.entity.GoalAllocation;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.GoalAllocationRepository;
import com.porquinho.repository.GoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private GoalAllocationRepository goalAllocationRepository;

    @Mock
    private AccountRepository accountRepository;

    // 2026-12-01T02:00Z is still 2026-11-30 23:00 in America/Sao_Paulo: the month must be November
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-12-01T02:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate MONTH = LocalDate.parse("2026-11-01");

    private GoalService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new GoalService(goalRepository, goalAllocationRepository, accountRepository, CLOCK);
        userId = UUID.randomUUID();
    }

    private Goal savedGoal() {
        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createStoresTheGoalOfTheUserWithCurrentAmountZeroAndAmountsScaled() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));
        LocalDate date = LocalDate.now().plusYears(1);

        service.create(userId, new CreateGoalRequest("  Reserva de Emergência ", new BigDecimal("25000"), date,
            new BigDecimal("2083.4"), null));

        Goal saved = savedGoal();
        assertEquals(userId, saved.getUserId());
        assertEquals("Reserva de Emergência", saved.getGoalName());
        assertEquals(new BigDecimal("25000.00"), saved.getTargetAmount());
        assertEquals(new BigDecimal("0.00"), saved.getCurrentAmount());
        assertEquals(date, saved.getTargetDate());
        assertEquals(new BigDecimal("2083.40"), saved.getMonthlyAllocation());
        assertFalse(saved.isCompleted());
        assertNull(saved.getDescription());
        assertNull(saved.getDeletedAt());
    }

    @Test
    void createAcceptsNoDateAndNoAllocation() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, new CreateGoalRequest("Viagem", new BigDecimal("8000.50"), null, null, null));

        Goal saved = savedGoal();
        assertNull(saved.getTargetDate());
        assertNull(saved.getMonthlyAllocation());
        assertEquals(new BigDecimal("8000.50"), saved.getTargetAmount());
    }

    @Test
    void listAndCountAreScopedToTheUser() {
        Goal goal = new Goal(userId, "Reserva", new BigDecimal("100.00"), null, null);
        when(goalRepository.findActiveByUserId(userId)).thenReturn(List.of(goal));
        when(goalRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(1L);

        assertEquals(List.of(goal), service.listActive(userId));
        assertEquals(1L, service.countActive(userId));
    }

    @Test
    void createRoundsHalfUpInsteadOfThrowingWhenGivenMoreThanTwoDecimals() {
        // The controller rejects 3 decimals; a direct caller must not hit an ArithmeticException
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, new CreateGoalRequest("Meta", new BigDecimal("10.005"), null, new BigDecimal("1.004"), null));

        Goal saved = savedGoal();
        assertEquals(new BigDecimal("10.01"), saved.getTargetAmount());
        assertEquals(new BigDecimal("1.00"), saved.getMonthlyAllocation());
    }

    @Test
    void nameIsCleanedOfInvisibleFormatCharactersAtTheEdges() {
        assertEquals("Reserva", new CreateGoalRequest("\u200B\uFEFF Reserva\u00A0\u200B", BigDecimal.ONE, null, null, null).name());
        assertEquals("", new CreateGoalRequest("\u200B\uFEFF", BigDecimal.ONE, null, null, null).name());
    }

    @Test
    void createStoresTheCleanedDescription() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, new CreateGoalRequest("Viagem", BigDecimal.TEN, null, null, "  Viagem  "));

        assertEquals("Viagem", savedGoal().getDescription());
    }

    @Test
    void blankDescriptionBecomesNull() {
        assertNull(new CreateGoalRequest("Meta", BigDecimal.ONE, null, null, " \u00A0\u200B ").description());
        assertNull(new CreateGoalRequest("Meta", BigDecimal.ONE, null, null, null).description());
    }

    private Goal existing(String current, String allocation) {
        Goal goal = new Goal(userId, "Viagem", new BigDecimal("1000.00"), null,
            allocation == null ? null : new BigDecimal(allocation));
        goal.setId(UUID.randomUUID());
        goal.setCurrentAmount(new BigDecimal(current));
        when(goalRepository.findActiveByIdAndUserIdForUpdate(goal.getId(), userId)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));
        return goal;
    }

    private GoalAllocation savedRow() {
        ArgumentCaptor<GoalAllocation> captor = ArgumentCaptor.forClass(GoalAllocation.class);
        verify(goalAllocationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void updateAllocationSetsTheGoalAndCreatesTheRowOfTheMonthInSaoPaulo() {
        Goal goal = existing("0.00", "500.00");
        when(goalAllocationRepository.findByUserIdAndGoalIdAndMonthYear(userId, goal.getId(), MONTH))
            .thenReturn(Optional.empty());

        Goal result = service.updateAllocation(userId, goal.getId(), new BigDecimal("700"));

        assertEquals(new BigDecimal("700.00"), result.getMonthlyAllocation());
        GoalAllocation row = savedRow();
        assertEquals(MONTH, row.getMonthYear());
        assertEquals(goal.getId(), row.getGoalId());
        assertEquals(userId, row.getUserId());
        assertEquals(new BigDecimal("700.00"), row.getAllocatedAmount());
        assertEquals(new BigDecimal("0.00"), row.getContributedAmount());
    }

    @Test
    void updateAllocationAgainInTheMonthUpdatesTheSameRowAndKeepsTheContributions() {
        Goal goal = existing("0.00", "500.00");
        GoalAllocation row = new GoalAllocation(userId, goal.getId(), MONTH, new BigDecimal("500.00"));
        row.setContributedAmount(new BigDecimal("80.00"));
        when(goalAllocationRepository.findByUserIdAndGoalIdAndMonthYear(userId, goal.getId(), MONTH))
            .thenReturn(Optional.of(row));

        service.updateAllocation(userId, goal.getId(), new BigDecimal("700.00"));

        assertSame(row, savedRow());
        assertEquals(new BigDecimal("700.00"), row.getAllocatedAmount());
        assertEquals(new BigDecimal("80.00"), row.getContributedAmount());
    }

    @Test
    void contributeAddsToTheGoalAndToTheRowOfTheMonth() {
        Goal goal = existing("100.00", "300.00");
        GoalAllocation row = new GoalAllocation(userId, goal.getId(), MONTH, new BigDecimal("300.00"));
        row.setContributedAmount(new BigDecimal("20.00"));
        when(goalAllocationRepository.findByUserIdAndGoalIdAndMonthYear(userId, goal.getId(), MONTH))
            .thenReturn(Optional.of(row));

        Goal result = service.contribute(userId, goal.getId(), new BigDecimal("50"));

        assertEquals(new BigDecimal("150.00"), result.getCurrentAmount());
        assertEquals(new BigDecimal("70.00"), savedRow().getContributedAmount());
        assertEquals(new BigDecimal("300.00"), row.getAllocatedAmount());
    }

    @Test
    void contributeWithoutRowInTheMonthCreatesItWithTheGoalsAllocation() {
        Goal goal = existing("0.00", "300.00");
        when(goalAllocationRepository.findByUserIdAndGoalIdAndMonthYear(userId, goal.getId(), MONTH))
            .thenReturn(Optional.empty());

        service.contribute(userId, goal.getId(), new BigDecimal("50.00"));

        GoalAllocation row = savedRow();
        assertEquals(new BigDecimal("300.00"), row.getAllocatedAmount());
        assertEquals(new BigDecimal("50.00"), row.getContributedAmount());
        assertEquals(MONTH, row.getMonthYear());
    }

    @Test
    void contributeWithoutAllocationCreatesTheRowWithZeroAllocated() {
        Goal goal = existing("0.00", null);
        when(goalAllocationRepository.findByUserIdAndGoalIdAndMonthYear(userId, goal.getId(), MONTH))
            .thenReturn(Optional.empty());

        service.contribute(userId, goal.getId(), new BigDecimal("10.00"));

        assertEquals(new BigDecimal("0.00"), savedRow().getAllocatedAmount());
    }

    @Test
    void contributeBeyondTheTargetIsAccepted() {
        Goal goal = existing("990.00", null);
        when(goalAllocationRepository.findByUserIdAndGoalIdAndMonthYear(any(), any(), any())).thenReturn(Optional.empty());

        assertEquals(new BigDecimal("1040.00"), service.contribute(userId, goal.getId(), new BigDecimal("50")).getCurrentAmount());
    }

    @Test
    void anotherUsersRemovedOrMissingGoalIsNotFoundAndNothingIsWritten() {
        UUID goalId = UUID.randomUUID();
        when(goalRepository.findActiveByIdAndUserIdForUpdate(goalId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateAllocation(userId, goalId, BigDecimal.TEN));
        assertThrows(ResourceNotFoundException.class, () -> service.contribute(userId, goalId, BigDecimal.TEN));
        verifyNoInteractions(goalAllocationRepository);
        verify(goalRepository, never()).save(any());
    }

    @Test
    void totalAvailableSumsTheActiveAccountsBalances() {
        Account a = new Account();
        a.setCurrentBalance(new BigDecimal("1000.00"));
        Account b = new Account();
        b.setCurrentBalance(new BigDecimal("-200.50"));
        when(accountRepository.findActiveByUserId(userId)).thenReturn(List.of(a, b));

        assertEquals(new BigDecimal("799.50"), service.totalAvailable(userId));
    }
}
