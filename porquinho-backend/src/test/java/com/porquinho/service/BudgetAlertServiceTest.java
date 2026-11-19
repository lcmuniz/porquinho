package com.porquinho.service;

import com.porquinho.entity.AccountDeletion;
import com.porquinho.entity.AccountDeletion.DeletionStatus;
import com.porquinho.entity.BudgetAllocation;
import com.porquinho.entity.Category;
import com.porquinho.entity.User;
import com.porquinho.entity.User.AuthProvider;
import com.porquinho.repository.AccountDeletionRepository;
import com.porquinho.repository.BudgetAlertNotificationRepository;
import com.porquinho.repository.BudgetAllocationRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.TransactionRepository;
import com.porquinho.repository.UserRepository;
import com.porquinho.service.EmailService.BudgetAlertLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pre-Epic 10: I/O matrix of the budget alert job. Clock fixed at 2026-12-01 02:00 UTC, still 30/11 in Sao Paulo, so
 * the month is November. The dedup table is a set; no transaction is active, so the "after commit" e-mail runs inline.
 */
@ExtendWith(MockitoExtension.class)
class BudgetAlertServiceTest {

    static final OffsetDateTime NOW = OffsetDateTime.of(2026, 12, 1, 2, 0, 0, 0, ZoneOffset.UTC);
    static final LocalDate MONTH = LocalDate.of(2026, 11, 1);
    static final String EMAIL = "user@example.com";

    @Mock
    private BudgetAllocationRepository budgetAllocationRepository;
    @Mock
    private BudgetAlertNotificationRepository budgetAlertNotificationRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountDeletionRepository accountDeletionRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private EmailService emailService;
    @Mock
    private EmailUnsubscribeService emailUnsubscribeService;

    private BudgetAlertService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID parent = UUID.randomUUID();
    private final User user = new User(EMAIL, AuthProvider.EMAIL, null);
    private final List<Category> categories = new ArrayList<>();
    private final List<BudgetAllocation> allocations = new ArrayList<>();
    private final List<Object[]> spent = new ArrayList<>();
    private final Set<String> sent = new HashSet<>();

    @BeforeEach
    void setUp() {
        service = new BudgetAlertService(budgetAllocationRepository, budgetAlertNotificationRepository,
            transactionRepository, categoryRepository, userRepository, accountDeletionRepository, subscriptionService,
            emailService, emailUnsubscribeService, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC), "https://app.example.com/", null);
        ReflectionTestUtils.setField(service, "self", service);
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(subscriptionService.hasAccess(userId)).thenReturn(true);
        lenient().when(accountDeletionRepository.findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE))
            .thenReturn(Optional.empty());
        lenient().when(budgetAllocationRepository.findByUserIdAndMonthYear(userId, MONTH)).thenReturn(allocations);
        lenient().when(transactionRepository.sumSpentByCategory(userId, MONTH, LocalDate.of(2026, 11, 30)))
            .thenReturn(spent);
        lenient().when(categoryRepository.findActiveByUserId(userId)).thenReturn(categories);
        lenient().when(emailService.isConfigured()).thenReturn(true);
        lenient().when(emailUnsubscribeService.budgetAlertUnsubscribeUrl(userId)).thenReturn("https://api.example.com/unsubscribe");
        lenient().when(budgetAlertNotificationRepository.insertIfAbsent(eq(userId), any(), eq(MONTH), anyShort(), eq(NOW)))
            .thenAnswer(inv -> sent.add(inv.getArgument(1) + "/" + inv.getArgument(3)) ? 1 : 0);
    }

    private UUID child(String name, String allocated, String spentAmount) {
        Category category = new Category(userId, name, parent, "#000000", "tag", false);
        UUID id = UUID.randomUUID();
        category.setId(id);
        categories.add(category);
        if (allocated != null) {
            allocations.add(new BudgetAllocation(userId, id, MONTH, new BigDecimal(allocated)));
        }
        spent.add(new Object[] {id, new BigDecimal(spentAmount)});
        return id;
    }

    @SuppressWarnings("unchecked")
    private List<BudgetAlertLine> emailed() {
        ArgumentCaptor<List<BudgetAlertLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(emailService).sendBudgetAlert(eq(EMAIL), captor.capture(), eq("https://app.example.com/orcamento"),
            eq("https://app.example.com/dashboard"), eq("https://api.example.com/unsubscribe"));
        return captor.getValue();
    }

    @Test
    void ninetyPercentSendsOneAlertAndTheNextRunNothing() {
        UUID food = child("Mercado", "100", "92");

        assertThat(service.processUser(userId)).isTrue();
        assertThat(service.processUser(userId)).isFalse();

        assertThat(emailed()).containsExactly(
            new BudgetAlertLine("Mercado", new BigDecimal("92"), new BigDecimal("100"), 90));
        assertThat(sent).containsExactly(food + "/90");
    }

    @Test
    void jumpStraightToOverspentRecordsBothLevelsAndShowsOnlyHundred() {
        UUID food = child("Mercado", "100", "130");

        service.processUser(userId);

        assertThat(emailed()).extracting(BudgetAlertLine::level).containsExactly(100);
        assertThat(sent).containsExactlyInAnyOrder(food + "/90", food + "/100");
    }

    @Test
    void ninetyAlreadySentThenHundred() {
        UUID food = child("Mercado", "100", "101");
        sent.add(food + "/90");

        service.processUser(userId);

        assertThat(emailed()).extracting(BudgetAlertLine::level).containsExactly(100);
        assertThat(sent).contains(food + "/100");
    }

    @Test
    void severalCategoriesGoInOneEmailByName() {
        child("Lazer", "200", "200");
        child("Mercado", "100", "95");
        child("Farmácia", "100", "10");

        service.processUser(userId);

        assertThat(emailed()).extracting(BudgetAlertLine::categoryName).containsExactly("Lazer", "Mercado");
        verify(emailService, times(1)).sendBudgetAlert(any(), any(), any(), any(), any());
    }

    @Test
    void exactlyNinetyPercentCounts() {
        child("Mercado", "100.00", "90.00");

        assertThat(service.processUser(userId)).isTrue();
    }

    @Test
    void zeroOrMissingAllocationNeverAlerts() {
        child("Mercado", "0", "50");
        child("Lazer", null, "50");

        assertThat(service.processUser(userId)).isFalse();

        verify(emailService, never()).sendBudgetAlert(any(), any(), any(), any(), any());
        verifyNoInteractions(budgetAlertNotificationRepository);
    }

    @Test
    void groupCategoryIsIgnored() {
        Category group = new Category(userId, "Casa", null, "#000000", "tag", false);
        group.setId(UUID.randomUUID());
        categories.add(group);
        allocations.add(new BudgetAllocation(userId, group.getId(), MONTH, new BigDecimal("100")));
        spent.add(new Object[] {group.getId(), new BigDecimal("150")});

        assertThat(service.processUser(userId)).isFalse();

        verify(emailService, never()).sendBudgetAlert(any(), any(), any(), any(), any());
    }

    @Test
    void withoutSmtpRecordsNothingSoTheAlertGoesOutLater() {
        UUID food = child("Mercado", "100", "92");
        when(emailService.isConfigured()).thenReturn(false);

        assertThat(service.processUser(userId)).isFalse();

        verifyNoInteractions(budgetAlertNotificationRepository);
        verify(emailService, never()).sendBudgetAlert(any(), any(), any(), any(), any());
        // E-mail back: still due
        when(emailService.isConfigured()).thenReturn(true);
        assertThat(service.processUser(userId)).isTrue();
        assertThat(sent).containsExactly(food + "/90");
    }

    @Test
    void preferenceOffSendsNothingAndRecordsNothing() {
        child("Mercado", "100", "130");
        user.setBudgetAlertEmailEnabled(false);

        assertThat(service.processUser(userId)).isFalse();

        verifyNoInteractions(emailService, budgetAlertNotificationRepository);
    }

    @Test
    void noAccessSendsNothing() {
        child("Mercado", "100", "130");
        when(subscriptionService.hasAccess(userId)).thenReturn(false);

        assertThat(service.processUser(userId)).isFalse();

        verifyNoInteractions(emailService, budgetAlertNotificationRepository);
    }

    @Test
    void pendingPurgeSendsNothing() {
        child("Mercado", "100", "130");
        when(accountDeletionRepository.findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE))
            .thenReturn(Optional.of(new AccountDeletion()));

        assertThat(service.processUser(userId)).isFalse();

        verifyNoInteractions(emailService, budgetAlertNotificationRepository);
    }

    @Test
    void softDeletedUserSendsNothing() {
        child("Mercado", "100", "130");
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(service.processUser(userId)).isFalse();

        verifyNoInteractions(emailService);
    }

    @Test
    void failedEmailKeepsTheMark() {
        UUID food = child("Mercado", "100", "92");
        doThrow(new RuntimeException("smtp down")).when(emailService).sendBudgetAlert(any(), any(), any(), any(), any());

        assertThat(service.processUser(userId)).isTrue();

        assertThat(sent).containsExactly(food + "/90");
    }

    @Test
    void jobUsesSaoPauloMonthAndOneFailureDoesNotStopTheOthers() {
        UUID broken = UUID.randomUUID();
        when(budgetAllocationRepository.findBudgetAlertCandidates(MONTH)).thenReturn(List.of(broken, userId));
        when(userRepository.findById(broken)).thenThrow(new RuntimeException("db down"));
        child("Mercado", "100", "92");

        service.runAlerts();

        verify(emailService).sendBudgetAlert(eq(EMAIL), any(), any(), any(), any());
    }

    @Test
    void reachedLevelThresholds() {
        assertThat(BudgetAlertService.reachedLevel(new BigDecimal("89.99"), new BigDecimal("100"))).isZero();
        assertThat(BudgetAlertService.reachedLevel(new BigDecimal("90"), new BigDecimal("100"))).isEqualTo((short) 90);
        assertThat(BudgetAlertService.reachedLevel(new BigDecimal("99.99"), new BigDecimal("100"))).isEqualTo((short) 90);
        assertThat(BudgetAlertService.reachedLevel(new BigDecimal("100"), new BigDecimal("100"))).isEqualTo((short) 100);
    }
}
