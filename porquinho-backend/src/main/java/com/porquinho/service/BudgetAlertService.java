package com.porquinho.service;

import com.porquinho.entity.AccountDeletion.DeletionStatus;
import com.porquinho.entity.BudgetAllocation;
import com.porquinho.entity.Category;
import com.porquinho.entity.User;
import com.porquinho.repository.AccountDeletionRepository;
import com.porquinho.repository.BudgetAlertNotificationRepository;
import com.porquinho.repository.BudgetAllocationRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.TransactionRepository;
import com.porquinho.repository.UserRepository;
import com.porquinho.service.EmailService.BudgetAlertLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Budget alert e-mail (pre-Epic 10, deferred AC of Story 6.7). Every hour, for the current Sao Paulo month, compares
 * spent and allocated of each active child category with an allocation above zero and e-mails the user when it
 * reaches 90% and 100%: at most once per (category, month, level), deduplicated in budget_alert_notifications.
 * Outside the transaction write paths on purpose: a delay of up to 1 h is accepted. Spent is read straight from the
 * database (no BudgetBalanceCache). The mark commits first and the e-mail goes after commit (a lost alert is
 * acceptable, a duplicate is not).
 */
@Service
public class BudgetAlertService {

    static final short LEVEL_90 = 90;
    static final short LEVEL_100 = 100;

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final BigDecimal NINETY_PERCENT = new BigDecimal("0.9");
    private static final Logger log = LoggerFactory.getLogger(BudgetAlertService.class);

    private final BudgetAllocationRepository budgetAllocationRepository;
    private final BudgetAlertNotificationRepository budgetAlertNotificationRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AccountDeletionRepository accountDeletionRepository;
    private final SubscriptionService subscriptionService;
    private final EmailService emailService;
    private final Clock clock;
    private final String budgetUrl;
    private final String settingsUrl;

    /** Spring proxy of this bean, so processUser runs in its own REQUIRES_NEW transaction. */
    private final BudgetAlertService self;

    public BudgetAlertService(BudgetAllocationRepository budgetAllocationRepository,
                              BudgetAlertNotificationRepository budgetAlertNotificationRepository,
                              TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              UserRepository userRepository,
                              AccountDeletionRepository accountDeletionRepository,
                              SubscriptionService subscriptionService,
                              EmailService emailService,
                              Clock clock,
                              @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                              @Lazy BudgetAlertService self) {
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.budgetAlertNotificationRepository = budgetAlertNotificationRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.accountDeletionRepository = accountDeletionRepository;
        this.subscriptionService = subscriptionService;
        this.emailService = emailService;
        this.clock = clock;
        String base = frontendUrl != null && frontendUrl.endsWith("/")
            ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.budgetUrl = base + "/orcamento";
        this.settingsUrl = base + "/dashboard";
        this.self = self;
    }

    /** Hourly job - intentionally NOT @Transactional: one transaction per user, so one failure does not stop the others. */
    @Scheduled(cron = "0 0 * * * *", zone = "America/Sao_Paulo")
    public void runAlerts() {
        LocalDate month = YearMonth.now(clock.withZone(SAO_PAULO)).atDay(1);
        List<UUID> candidates = budgetAllocationRepository.findBudgetAlertCandidates(month);

        int alerted = 0;
        int failed = 0;
        for (UUID userId : candidates) {
            try {
                if (self.processUser(userId)) {
                    alerted++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("Budget alert failed for user {}: {} - {}", userId, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        if (alerted > 0 || failed > 0) {
            log.info("Budget alert job done: {} candidates, {} alerted, {} failed", candidates.size(), alerted, failed);
        }
    }

    /**
     * Records the levels newly reached by the user's categories this month and schedules one e-mail after commit.
     * True when an e-mail was scheduled.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processUser(UUID userId) {
        // Soft-deleted users: @Where hides them
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isBudgetAlertEmailEnabled()
            || accountDeletionRepository.findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE).isPresent()
            || !subscriptionService.hasAccess(userId)) {
            return false;
        }
        // Without e-mail the levels would be recorded but never sent: leave them for when the e-mail is back
        if (!emailService.isConfigured()) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        YearMonth yearMonth = YearMonth.now(clock.withZone(SAO_PAULO));
        LocalDate month = yearMonth.atDay(1);
        Map<UUID, BigDecimal> allocated = new HashMap<>();
        for (BudgetAllocation a : budgetAllocationRepository.findByUserIdAndMonthYear(userId, month)) {
            if (a.getAllocatedAmount().signum() > 0) {
                allocated.put(a.getCategoryId(), a.getAllocatedAmount());
            }
        }
        if (allocated.isEmpty()) {
            return false;
        }
        Map<UUID, BigDecimal> spent = new HashMap<>();
        for (Object[] row : transactionRepository.sumSpentByCategory(userId, month, yearMonth.atEndOfMonth())) {
            if (row[0] != null) {
                spent.put((UUID) row[0], (BigDecimal) row[1]);
            }
        }

        List<BudgetAlertLine> lines = new ArrayList<>();
        // Active children only, by name (the order of the e-mail)
        for (Category category : categoryRepository.findActiveByUserId(userId)) {
            BigDecimal categoryAllocated = allocated.get(category.getId());
            if (category.getParentCategoryId() == null || categoryAllocated == null) {
                continue;
            }
            BigDecimal categorySpent = spent.getOrDefault(category.getId(), BigDecimal.ZERO);
            short reached = reachedLevel(categorySpent, categoryAllocated);
            if (reached == 0) {
                continue;
            }
            // Both levels are recorded on a jump straight to 100%; the e-mail shows only the highest new one
            boolean new90 = budgetAlertNotificationRepository.insertIfAbsent(userId, category.getId(), month, LEVEL_90, now) > 0;
            boolean new100 = reached == LEVEL_100
                && budgetAlertNotificationRepository.insertIfAbsent(userId, category.getId(), month, LEVEL_100, now) > 0;
            if (new100 || new90) {
                lines.add(new BudgetAlertLine(category.getCategoryName(), categorySpent, categoryAllocated,
                    new100 ? LEVEL_100 : LEVEL_90));
            }
        }
        if (lines.isEmpty()) {
            return false;
        }

        String email = user.getEmail();
        AfterCommit.run(() -> {
            try {
                emailService.sendBudgetAlert(email, lines, budgetUrl, settingsUrl);
            } catch (RuntimeException e) {
                // Already marked as sent: a lost alert is acceptable, a duplicate is not.
                log.warn("Budget alert e-mail failed for user {}: {} - {}",
                    userId, e.getClass().getSimpleName(), e.getMessage());
            }
        });
        return true;
    }

    /** 100 when spent >= allocated, 90 when spent >= 90% of it, else 0. Allocated is above zero. */
    static short reachedLevel(BigDecimal spent, BigDecimal allocated) {
        if (spent.compareTo(allocated) >= 0) {
            return LEVEL_100;
        }
        return spent.compareTo(allocated.multiply(NINETY_PERCENT)) >= 0 ? LEVEL_90 : 0;
    }
}
