package com.porquinho.service;

import com.porquinho.dto.AdminAlertsResponse;
import com.porquinho.dto.AdminHealthResponse.Status;
import com.porquinho.entity.SystemAlert;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.SystemAlertRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.UserRepository;
import com.porquinho.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.porquinho.service.ImportMetricsService.BANK_LABELS;
import static com.porquinho.service.ImportMetricsService.SAO_PAULO;

/**
 * Story 10.5 (FR86): every 5 minutes ({@link SystemAlertJob}) checks the critical metrics with the numbers of the
 * 10.1-10.3 panels and records each new violation in system_alerts, then e-mails every admin and posts to Slack.
 * <p>
 * Episode: the row of the key with normalized_at null. Violating without an episode opens one, unless the last alert
 * of the key is less than 1 h old (it waits for the first run past the hour). Violating with an episode does nothing,
 * even when an admin resolved it: one send per episode. Not violating (or without data) ends the episode. A metric
 * whose check failed is left as it is. Single instance, no ShedLock; the partial unique index keeps one open episode
 * per key anyway. The full backend being down is not alerted: the job goes down with it.
 */
@Service
public class SystemAlertService {

    private static final Logger log = LoggerFactory.getLogger(SystemAlertService.class);

    static final long MIN_REQUESTS = 100;
    static final long MIN_IMPORTS = 10;
    static final long MIN_CLASSIFICATIONS = 20;
    static final Duration WINDOW = Duration.ofHours(24);
    static final Duration DEDUP = Duration.ofHours(1);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** The critical metrics, their limit, the /admin tab and the suggested action. */
    public enum Metric {
        ERROR_RATE("Taxa de erro do sistema (24 h)", "health", "10", true,
            "Veja na aba Saúde os endpoints com mais erros e os logs do backend."),
        UPTIME("Uptime (30 dias)", "health", "99", false,
            "Verifique os reinícios e as quedas do backend nos logs do servidor e do container."),
        RESPONSE_TIME("Tempo médio de resposta (24 h)", "health", "5000", true,
            "Veja na aba Saúde os endpoints mais lentos e as queries lentas do banco."),
        IMPORT_ERROR_RATE("Taxa de erro de importação (24 h)", "imports", "10", true,
            "Abra o banco na aba Importações e veja os tipos de erro: o formato do extrato pode ter mudado."),
        AI_ACCURACY("Precisão da IA (24 h)", "ai", "75", false,
            "Abra o banco na aba IA e revise o prompt de classificação com as sugestões do detalhe."),
        ACTIVE_SESSIONS("Sessões ativas", "health", "1", false,
            "Há tráfego e nenhuma sessão ativa: verifique o login (Supabase) e a renovação das sessões."),
        REDIS_DOWN("Redis", "health", "1", false,
            "Verifique o container do Redis e a conexão do backend com ele."),
        // the limit of the pool is its size, read at each run
        DB_POOL_EXHAUSTED("Pool de conexões do banco", "health", "0", true,
            "Veja na aba Saúde as queries lentas e as conexões presas; se o uso for legítimo, aumente o pool.");

        final String label;
        final String tab;
        final BigDecimal limit;
        /** True when above the limit is a violation, false when below it is. */
        final boolean above;
        final String action;

        Metric(String label, String tab, String limit, boolean above, String action) {
            this.label = label;
            this.tab = tab;
            this.limit = new BigDecimal(limit);
            this.above = above;
            this.action = action;
        }

        boolean violates(BigDecimal value) {
            int c = value.compareTo(limit);
            return above ? c > 0 : c < 0;
        }
    }

    /** One violating key; value and threshold as stored and shown. */
    record Violation(Metric metric, String bank, BigDecimal value, BigDecimal threshold) {
        String key() {
            return bank == null ? metric.name() : metric.name() + ":" + bank;
        }
    }

    private final SystemAlertRepository repository;
    private final HealthSnapshotService snapshots;
    private final AdminService adminService;
    private final UserSessionRepository userSessionRepository;
    private final TransactionImportRepository importRepository;
    private final AiClassificationHistoryRepository aiRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SlackNotifier slack;
    private final Clock clock;
    private final String adminUrl;

    /** Spring proxy of this bean, so open() runs in its own REQUIRES_NEW transaction. */
    private final SystemAlertService self;

    public SystemAlertService(SystemAlertRepository repository, HealthSnapshotService snapshots,
                              AdminService adminService, UserSessionRepository userSessionRepository,
                              TransactionImportRepository importRepository,
                              AiClassificationHistoryRepository aiRepository, UserRepository userRepository,
                              EmailService emailService, SlackNotifier slack, Clock clock,
                              @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                              @Lazy SystemAlertService self) {
        this.repository = repository;
        this.snapshots = snapshots;
        this.adminService = adminService;
        this.userSessionRepository = userSessionRepository;
        this.importRepository = importRepository;
        this.aiRepository = aiRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.slack = slack;
        this.clock = clock;
        String base = frontendUrl != null && frontendUrl.endsWith("/")
            ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.adminUrl = base + "/admin?tab=";
        this.self = self;
    }

    /** The job. Not @Transactional: each new alert commits on its own before it is sent. */
    public void runAlerts() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Set<Metric> unchecked = EnumSet.noneOf(Metric.class);
        Map<String, Violation> violations = collect(now, unchecked);

        int opened = 0;
        int normalized = 0;
        int sendFailures = 0;
        for (Violation v : violations.values()) {
            try {
                SystemAlert alert = self.open(v, now);
                if (alert != null) {
                    opened++;
                    sendFailures += notifyAdmins(alert);
                }
            } catch (RuntimeException e) {
                log.warn("System alert {} failed: {} - {}", v.key(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
        for (SystemAlert open : repository.findByNormalizedAtIsNull()) {
            if (violations.containsKey(open.getAlertKey()) || unchecked.contains(metric(open))) continue;
            normalized += repository.normalize(open.getAlertKey(), now);
        }

        if (opened > 0 || normalized > 0 || sendFailures > 0 || !unchecked.isEmpty()) {
            log.info("System alert job done: {} violations, {} opened, {} normalized, {} send failures, unchecked {}",
                violations.size(), opened, normalized, sendFailures, unchecked);
        }
    }

    /** The violating keys right now. A metric whose check throws goes to {@code unchecked}. */
    Map<String, Violation> collect(OffsetDateTime now, Set<Metric> unchecked) {
        Map<String, Violation> out = new LinkedHashMap<>();
        check(unchecked, Metric.ERROR_RATE, () -> {
            HealthSnapshotService.Totals day = snapshots.totalsSince(now.minus(WINDOW));
            if (day.requests() >= MIN_REQUESTS) {
                add(out, Metric.ERROR_RATE, null, round1(day.errors() * 100.0 / day.requests()));
                add(out, Metric.RESPONSE_TIME, null, round1(day.totalTimeMs() / day.requests()));
            }
        });
        if (unchecked.contains(Metric.ERROR_RATE)) unchecked.add(Metric.RESPONSE_TIME);
        check(unchecked, Metric.UPTIME, () -> {
            Double uptime = snapshots.uptimePercent(Duration.ofDays(30));
            if (uptime != null) add(out, Metric.UPTIME, null, round1(uptime));
        });
        check(unchecked, Metric.IMPORT_ERROR_RATE, () -> {
            for (TransactionImportRepository.BankCounts c : importRepository.countByBank(now.minus(WINDOW), now, null)) {
                long total = c.getSuccesses() + c.getFailures();
                if (total >= MIN_IMPORTS) {
                    add(out, Metric.IMPORT_ERROR_RATE, c.getBank(), round1(c.getFailures() * 100.0 / total));
                }
            }
        });
        check(unchecked, Metric.AI_ACCURACY, () -> {
            for (AiClassificationHistoryRepository.BankCounts c : aiRepository.countByBank(now.minus(WINDOW), now, null)) {
                if (c.getClassifications() >= MIN_CLASSIFICATIONS) {
                    add(out, Metric.AI_ACCURACY, c.getBank(),
                        BigDecimal.valueOf(AiMetricsService.accuracy(c.getClassifications(), c.getCorrections())));
                }
            }
        });
        check(unchecked, Metric.ACTIVE_SESSIONS, () -> {
            // No session at night without traffic is normal: only with 2xx user requests in the last hour (bots and
            // the Stripe webhook do not count)
            long active = userSessionRepository.countActive(now, now.minus(AdminService.ACTIVE_SESSION_WINDOW));
            if (active == 0 && snapshots.totalsSince(now.minusHours(1)).okRequests() > 0) {
                add(out, Metric.ACTIVE_SESSIONS, null, BigDecimal.ZERO);
            }
        });
        check(unchecked, Metric.REDIS_DOWN, () -> {
            if (!adminService.redis().up()) add(out, Metric.REDIS_DOWN, null, BigDecimal.ZERO);
        });
        check(unchecked, Metric.DB_POOL_EXHAUSTED, () -> {
            // MXBean only: a query here would wait for the exhausted pool and the alert would never fire
            AdminService.Pool db = adminService.pool();
            if (db.status() == Status.RED) {
                // value: connections in use (and waiting); limit: the pool size
                out.put(Metric.DB_POOL_EXHAUSTED.name(), new Violation(Metric.DB_POOL_EXHAUSTED, null,
                    BigDecimal.valueOf(db.active() + db.waiting()), BigDecimal.valueOf(db.max())));
            }
        });
        return out;
    }

    /**
     * Opens the alert of the violation in its own transaction; null when the key has an open episode or its last
     * alert is less than 1 h old (minutes compared, so 09:50 and 10:50 runs a few ms apart still count as 1 h).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SystemAlert open(Violation v, OffsetDateTime now) {
        SystemAlert last = repository.findFirstByAlertKeyOrderByTriggeredAtDesc(v.key()).orElse(null);
        if (last != null) {
            if (last.getNormalizedAt() == null) return null;
            OffsetDateTime cutoff = now.truncatedTo(ChronoUnit.MINUTES).minus(DEDUP);
            if (last.getTriggeredAt().truncatedTo(ChronoUnit.MINUTES).isAfter(cutoff)) return null;
        }
        if (repository.insertIfNoOpenEpisode(v.key(), v.metric().name(), v.bank(), v.value(), v.threshold(), now) == 0) {
            return null;
        }
        return new SystemAlert(v.key(), v.metric().name(), v.bank(), v.value(), v.threshold(), now);
    }

    /** E-mail to every admin and Slack, best-effort; returns how many sends failed. */
    int notifyAdmins(SystemAlert alert) {
        String link = link(alert);
        int failures = 0;
        if (emailService.isConfigured()) {
            for (String to : userRepository.findAdminEmails()) {
                if (!emailService.sendSystemAlert(to, alert, link)) failures++;
            }
        } else {
            log.warn("SMTP not configured - system alert {} recorded without e-mail", alert.getAlertKey());
        }
        if (slack.isConfigured() && !slack.send("*Alerta do Porquinho*\n" + describe(alert, link))) failures++;
        return failures;
    }

    @Transactional(readOnly = true)
    public AdminAlertsResponse list() {
        List<AdminAlertsResponse.Alert> alerts = repository.findTop100UnresolvedFirst().stream()
            .map(this::item).toList();
        return new AdminAlertsResponse(repository.countByResolvedAtIsNull(), emailService.isConfigured(),
            slack.isConfigured(), alerts);
    }

    /** Idempotent: an alert already resolved keeps its first resolved_at and resolved_by. 404 when it does not exist. */
    @Transactional
    public AdminAlertsResponse.Alert resolve(long id, UUID adminId) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("Alerta não encontrado.");
        repository.resolve(id, OffsetDateTime.now(clock), adminId);
        return item(repository.findById(id).orElseThrow());
    }

    /** Metric (and bank), current value, limit, time in Sao Paulo, action and link: the text of the e-mail and Slack. */
    static String describe(SystemAlert alert, String link) {
        Metric m = metric(alert);
        return "Métrica: " + title(alert) + "\n"
            + "Valor atual: " + valueLabel(m, alert.getMetricValue(), alert.getThreshold()) + "\n"
            + "Limite: " + thresholdLabel(m, alert.getThreshold()) + "\n"
            + "Hora da violação: " + alert.getTriggeredAt().atZoneSameInstant(SAO_PAULO).format(TIME)
            + " (horário de Brasília)\n"
            + "Ação sugerida: " + m.action + "\n"
            + "Abrir no painel: " + link;
    }

    static String title(SystemAlert alert) {
        String label = metric(alert).label;
        return alert.getBank() == null ? label : label + " - " + BANK_LABELS.getOrDefault(alert.getBank(), alert.getBank());
    }

    static String valueLabel(Metric m, BigDecimal value, BigDecimal threshold) {
        return switch (m) {
            case ERROR_RATE, UPTIME, IMPORT_ERROR_RATE, AI_ACCURACY -> String.format(PT_BR, "%.1f %%", value);
            case RESPONSE_TIME -> String.format(PT_BR, "%.0f ms", value);
            case ACTIVE_SESSIONS -> "0 sessões, com requisições na última hora";
            case REDIS_DOWN -> "fora do ar";
            case DB_POOL_EXHAUSTED -> String.format(PT_BR, "%d conexões em uso ou na fila, de %d", value.longValue(),
                threshold.longValue());
        };
    }

    static String thresholdLabel(Metric m, BigDecimal threshold) {
        return switch (m) {
            case ERROR_RATE, IMPORT_ERROR_RATE -> String.format(PT_BR, "acima de %.0f %%", threshold);
            case UPTIME, AI_ACCURACY -> String.format(PT_BR, "abaixo de %.0f %%", threshold);
            case RESPONSE_TIME -> String.format(PT_BR, "acima de %.0f ms", threshold);
            case ACTIVE_SESSIONS -> "pelo menos 1 sessão ativa quando há tráfego";
            case REDIS_DOWN -> "no ar";
            case DB_POOL_EXHAUSTED -> String.format(PT_BR, "menos de %d em uso e ninguém na fila", threshold.longValue());
        };
    }

    private AdminAlertsResponse.Alert item(SystemAlert a) {
        Metric m = metric(a);
        return new AdminAlertsResponse.Alert(a.getId(), a.getAlertKey(), a.getAlertType(), m.label, a.getBank(),
            a.getBank() == null ? null : BANK_LABELS.getOrDefault(a.getBank(), a.getBank()), a.getMetricValue(),
            a.getThreshold(), valueLabel(m, a.getMetricValue(), a.getThreshold()), thresholdLabel(m, a.getThreshold()),
            m.action, m.tab, link(a), a.getTriggeredAt(), a.getNormalizedAt(), a.getResolvedAt());
    }

    private String link(SystemAlert alert) {
        return adminUrl + metric(alert).tab;
    }

    private static Metric metric(SystemAlert alert) {
        return Metric.valueOf(alert.getAlertType());
    }

    private static void add(Map<String, Violation> out, Metric m, String bank, BigDecimal value) {
        if (m.violates(value)) {
            Violation v = new Violation(m, bank, value, m.limit);
            out.put(v.key(), v);
        }
    }

    private static void check(Set<Metric> unchecked, Metric m, Runnable check) {
        try {
            check.run();
        } catch (RuntimeException e) {
            unchecked.add(m);
            log.warn("System alert check {} failed: {} - {}", m, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /** One decimal, as the panel shows it, so 10.04 % does not fire while "10,0 %" is on screen. */
    private static BigDecimal round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
