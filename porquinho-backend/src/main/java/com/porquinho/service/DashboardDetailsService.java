package com.porquinho.service;

import com.porquinho.dto.DashboardLayer3Response;
import com.porquinho.dto.TransactionResponse;
import com.porquinho.entity.Category;
import com.porquinho.entity.Transaction;
import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.TransactionRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.sqm.tree.SqmJoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dashboard layer 3 (Story 7.6): the confirmed transaction list with filters, sort and pages, its CSV and the cache
 * field of each filter (Story 7.7). Split from {@link DashboardService} (retro Epic 7 A6/B11).
 */
@Service
public class DashboardDetailsService {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    static final int DETAILS_PAGE_SIZE = 50;
    static final String INVALID_PERIOD_MESSAGE = "Período inválido: use datas AAAA-MM-DD com o início até o fim";
    static final String INVALID_SORT_MESSAGE = "Ordenação inválida";
    static final String INVALID_PAGE_MESSAGE = "Página inválida";
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final TransactionService transactionService;
    private final DashboardCache dashboardCache;
    private final Clock clock;

    public DashboardDetailsService(TransactionRepository transactionRepository, AccountRepository accountRepository,
                                   CreditCardRepository creditCardRepository, TransactionService transactionService,
                                   DashboardCache dashboardCache, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.transactionService = transactionService;
        this.dashboardCache = dashboardCache;
        this.clock = clock;
    }

    /**
     * Story 7.6, the filters of the layer 3. {@code from}/{@code to} are "YYYY-MM-DD" (blank = the current month's bound
     * in São Paulo). A transaction matches the categories when it is in {@code categoryIds} or, with
     * {@code uncategorized}, has none; the same for {@code accountIds}/{@code creditCardIds}; an empty pair is no filter.
     * {@code sort} is "field,dir" with field date, amount or category (null = "date,desc").
     */
    public record DetailsFilter(String from, String to, List<UUID> categoryIds, boolean uncategorized,
                                List<UUID> accountIds, List<UUID> creditCardIds, String q, String sort) {
        public DetailsFilter {
            categoryIds = categoryIds == null ? List.of() : categoryIds;
            accountIds = accountIds == null ? List.of() : accountIds;
            creditCardIds = creditCardIds == null ? List.of() : creditCardIds;
        }
    }

    /** Story 7.6: page {@code page} (0-based, 50 rows) of the filtered list, same base as /transacoes. */
    @Transactional(readOnly = true)
    public DashboardLayer3Response details(UUID userId, DetailsFilter filter, int page) {
        if (page < 0) throw new BadRequestException(INVALID_PAGE_MESSAGE, "page");
        return dashboardCache.get(DashboardCache.Layer.LAYER3, userId, filtersHash(filter, page),
            DashboardLayer3Response.class, () -> computeDetails(userId, filter, page));
    }

    private DashboardLayer3Response computeDetails(UUID userId, DetailsFilter filter, int page) {
        Page<Transaction> rows = transactionRepository.findAll(detailsSpec(userId, filter),
            PageRequest.of(page, DETAILS_PAGE_SIZE));
        return new DashboardLayer3Response(transactionService.toResponses(userId, rows.getContent()), page,
            rows.getTotalElements(), rows.getTotalPages());
    }

    /** Story 7.7: SHA-256 of the whole filter, the page and the current month (blank dates mean the current month). */
    String filtersHash(DetailsFilter f, int page) {
        StringBuilder text = new StringBuilder();
        // length-prefixed parts: no free text (q) can pass for another field; null is "-1:", which no string gives
        for (Object part : java.util.Arrays.asList(f.from(), f.to(), f.categoryIds(), f.uncategorized(),
                f.accountIds(), f.creditCardIds(), f.q(), f.sort(), page,
                YearMonth.from(LocalDate.now(clock.withZone(SAO_PAULO))))) {
            String value = part == null ? "" : part.toString();
            text.append(part == null ? -1 : value.length()).append(':').append(value);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Story 7.6: every row of the filtered list in the same order, as CSV for Excel pt-BR (BOM, ";", decimal comma).
     * The value is signed (debit negative); origin is the import or a manual entry.
     */
    @Transactional(readOnly = true)
    public String detailsCsv(UUID userId, DetailsFilter filter) {
        List<Transaction> rows = transactionRepository.findAll(detailsSpec(userId, filter));
        List<TransactionResponse> responses = transactionService.toResponses(userId, rows);
        Map<UUID, String> targets = new HashMap<>();
        accountRepository.findByUserIdOrderByCreatedAtAsc(userId).forEach(a -> targets.put(a.getId(), a.getAccountName()));
        creditCardRepository.findByUserIdOrderByCreatedAtAsc(userId).forEach(c -> targets.put(c.getId(), c.getCardName()));

        StringBuilder csv = new StringBuilder("﻿Data;Descrição;Valor;Tipo;Categoria;Conta;Pix;Parcela;Origem\r\n");
        for (int i = 0; i < rows.size(); i++) {
            Transaction row = rows.get(i);
            TransactionResponse r = responses.get(i);
            boolean debit = row.getTransactionType() == Transaction.Type.DEBIT;
            UUID target = row.getAccountId() != null ? row.getAccountId() : row.getCreditCardId();
            csv.append(String.join(";",
                row.getTransactionDate().format(CSV_DATE),
                csvText(row.getDescription()),
                (debit ? row.getAmount().negate() : row.getAmount()).toPlainString().replace('.', ','),
                debit ? "Saída" : "Entrada",
                csvText(categoryLabel(row, r.category())),
                csvText(targets.getOrDefault(target, "")),
                row.isPix() ? "Sim" : "Não",
                row.isInstallment() && row.getInstallmentNumber() != null && row.getInstallmentTotal() != null
                    ? row.getInstallmentNumber() + "/" + row.getInstallmentTotal() : "",
                row.getImportId() != null ? "Importação" : "Manual")).append("\r\n");
        }
        return csv.toString();
    }

    /** Same label as /transacoes: "Grupo › Categoria", card payments and uncategorized by name. */
    private static String categoryLabel(Transaction row, com.porquinho.dto.ImportTransactionResponse.Category category) {
        if (row.getKind() == Transaction.Kind.CARD_PAYMENT) return "Pagamento de fatura";
        if (category == null) return "Não classificado";
        return category.group() == null ? category.name() : category.group() + " › " + category.name();
    }

    /** Quotes a field with ; " or a line break; a leading = + - @ gets a ' so a spreadsheet does not run it. */
    static String csvText(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** Base of /transacoes (findConfirmedBetween) plus the optional filters; the order is skipped on the count query. */
    private Specification<Transaction> detailsSpec(UUID userId, DetailsFilter f) {
        LocalDate today = LocalDate.now(clock.withZone(SAO_PAULO));
        LocalDate from = date(f.from(), today.withDayOfMonth(1), "from");
        LocalDate to = date(f.to(), today.withDayOfMonth(today.lengthOfMonth()), "to");
        if (from.isAfter(to)) throw new BadRequestException(INVALID_PERIOD_MESSAGE, "from");
        String[] sort = (f.sort() == null || f.sort().isBlank() ? "date,desc" : f.sort()).split(",", -1);
        if (sort.length != 2 || !List.of("date", "amount", "category").contains(sort[0])
            || !List.of("asc", "desc").contains(sort[1])) {
            throw new BadRequestException(INVALID_SORT_MESSAGE, "sort");
        }
        String field = sort[0];
        boolean asc = sort[1].equals("asc");
        String q = f.q() == null ? "" : f.q().strip().toLowerCase(Locale.ROOT);

        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("userId"), userId));
            where.add(cb.equal(root.get("reviewStatus"), Transaction.ReviewStatus.CONFIRMED));
            where.add(cb.isFalse(root.get("futureInstallment")));
            where.add(cb.isNull(root.get("deletedAt")));
            where.add(cb.between(root.get("transactionDate"), from, to));
            List<Predicate> categories = new ArrayList<>();
            if (!f.categoryIds().isEmpty()) categories.add(root.get("categoryId").in(f.categoryIds()));
            // "Não classificado" as the list shows it: a card payment without category is "Pagamento de fatura"
            if (f.uncategorized()) categories.add(cb.and(cb.isNull(root.get("categoryId")),
                cb.notEqual(root.get("kind"), Transaction.Kind.CARD_PAYMENT)));
            if (!categories.isEmpty()) where.add(cb.or(categories.toArray(Predicate[]::new)));
            List<Predicate> targets = new ArrayList<>();
            if (!f.accountIds().isEmpty()) targets.add(root.get("accountId").in(f.accountIds()));
            if (!f.creditCardIds().isEmpty()) targets.add(root.get("creditCardId").in(f.creditCardIds()));
            if (!targets.isEmpty()) where.add(cb.or(targets.toArray(Predicate[]::new)));
            if (!q.isEmpty()) {
                String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.like(cb.lower(root.get("description")), pattern, '\\'));
            }

            Class<?> result = query.getResultType();
            if (result != Long.class && result != long.class) {
                List<Expression<?>> keys = new ArrayList<>();
                switch (field) {
                    case "amount" -> keys.add(root.get("amount"));
                    case "category" -> {
                        // same order as the label "Grupo › Categoria": group, then child
                        var category = ((JpaRoot<Transaction>) root).join(Category.class, SqmJoinType.LEFT);
                        category.on(cb.equal(category.get("id"), root.get("categoryId")));
                        var group = ((JpaRoot<Transaction>) root).join(Category.class, SqmJoinType.LEFT);
                        group.on(cb.equal(group.get("id"), category.get("parentCategoryId")));
                        keys.add(cb.lower(group.get("categoryName")));
                        keys.add(cb.lower(category.get("categoryName")));
                    }
                    default -> keys.add(root.get("transactionDate"));
                }
                List<Order> orders = new ArrayList<>();
                keys.forEach(k -> orders.add(asc ? cb.asc(k) : cb.desc(k)));
                // stable tie-break: newest date, then id
                orders.add(cb.desc(root.get("transactionDate")));
                orders.add(cb.desc(root.get("id")));
                query.orderBy(orders);
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static LocalDate date(String value, LocalDate fallback, String field) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(INVALID_PERIOD_MESSAGE, field);
        }
    }
}
