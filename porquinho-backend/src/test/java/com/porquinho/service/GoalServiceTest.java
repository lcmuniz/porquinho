package com.porquinho.service;

import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.entity.Goal;
import com.porquinho.repository.GoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    private GoalService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new GoalService(goalRepository);
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
}
