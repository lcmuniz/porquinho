package com.porquinho.service;

import com.porquinho.dto.DashboardLayer3Response;
import com.porquinho.entity.Category;
import com.porquinho.entity.Transaction;
import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.jpa.HibernateHints;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.sqm.tree.SqmJoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

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
import java.util.stream.Stream;

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
    private static final int CSV_FETCH_SIZE = 1000;
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;
    private final TransactionService transactionService;
    private final DashboardCache dashboardCache;
    private final Clock clock;

    public DashboardDetailsService(TransactionRepository transactionRepository, AccountRepository accountRepository,
                                   CreditCardRepository creditCardRepository, CategoryRepository categoryRepository,
                                   EntityManager entityManager, TransactionService transactionService,
                                   DashboardCache dashboardCache, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
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

    /**
     * Story 9.1: what the CSV may add to the base of /transacoes. Each flag only lifts its own rule (future installments,
     * deleted rows); both off is the same list as the layer 3.
     */
    public record ExportOptions(boolean futureInstallments, boolean deleted) {
        public static final ExportOptions NONE = new ExportOptions(false, false);
    }

    /** Story 9.1: where the CSV goes, opened once the row count is known (it goes in a header before the body). */
    @FunctionalInterface
    public interface CsvTarget {
        OutputStream open(long count) throws IOException;
    }

    /** Story 7.6: page {@code page} (0-based, 50 rows) of the filtered list, same base as /transacoes. */
    @Transactional(readOnly = true)
    public DashboardLayer3Response details(UUID userId, DetailsFilter filter, int page) {
        if (page < 0) throw new BadRequestException(INVALID_PAGE_MESSAGE, "page");
        return dashboardCache.get(DashboardCache.Layer.LAYER3, userId, filtersHash(filter, page),
            DashboardLayer3Response.class, () -> computeDetails(userId, filter, page));
    }

    private DashboardLayer3Response computeDetails(UUID userId, DetailsFilter filter, int page) {
        Page<Transaction> rows = transactionRepository.findAll(detailsSpec(userId, filter, ExportOptions.NONE),
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
     * The value is signed (debit negative); origin is the import or a manual entry. Story 9.1: {@code options} may add
     * future installments and deleted rows, flagged in the columns Parcelado and Excluída. Grupo C (pre-10-6): the rows
     * are streamed from a cursor straight to {@code target}, nothing is kept in memory; REPEATABLE READ makes the count
     * and the rows the same snapshot. Returns the row count (header not counted).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public long detailsCsv(UUID userId, DetailsFilter filter, ExportOptions options, CsvTarget target) throws IOException {
        Specification<Transaction> spec = detailsSpec(userId, filter, options);
        long count = transactionRepository.count(spec);
        Map<UUID, Category> categories = new HashMap<>();
        categoryRepository.findByUserIdOrderByCreatedAtAsc(userId).forEach(c -> categories.put(c.getId(), c));
        Map<UUID, String> targets = new HashMap<>();
        accountRepository.findByUserIdOrderByCreatedAtAsc(userId).forEach(a -> targets.put(a.getId(), a.getAccountName()));
        creditCardRepository.findByUserIdOrderByCreatedAtAsc(userId).forEach(c -> targets.put(c.getId(), c.getCardName()));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Transaction> query = cb.createQuery(Transaction.class);
        Root<Transaction> root = query.from(Transaction.class);
        query.where(spec.toPredicate(root, query, cb));
        Writer csv = new BufferedWriter(new OutputStreamWriter(target.open(count), StandardCharsets.UTF_8));
        csv.write(CsvPtBr.BOM + "Data;Descrição;Valor;Tipo;Categoria;Conta;Pix;Parcelado;Parcela;Origem;Excluída" + CsvPtBr.CRLF);
        try (Stream<Transaction> rows = entityManager.createQuery(query)
                .setHint(HibernateHints.HINT_FETCH_SIZE, CSV_FETCH_SIZE).getResultStream()) {
            for (Transaction row : (Iterable<Transaction>) rows::iterator) {
                boolean debit = row.getTransactionType() == Transaction.Type.DEBIT;
                UUID account = row.getAccountId() != null ? row.getAccountId() : row.getCreditCardId();
                csv.write(String.join(";",
                    row.getTransactionDate().format(CSV_DATE),
                    CsvPtBr.text(row.getDescription()),
                    CsvPtBr.money(debit ? row.getAmount().negate() : row.getAmount()),
                    debit ? "Saída" : "Entrada",
                    CsvPtBr.text(categoryLabel(row, TransactionCore.category(row.getCategoryId(), categories))),
                    CsvPtBr.text(targets.getOrDefault(account, "")),
                    row.isPix() ? "Sim" : "Não",
                    row.isInstallment() ? "Sim" : "Não",
                    row.isInstallment() && row.getInstallmentNumber() != null && row.getInstallmentTotal() != null
                        ? row.getInstallmentNumber() + "/" + row.getInstallmentTotal() : "",
                    row.getImportId() != null ? "Importação" : "Manual",
                    row.getDeletedAt() != null ? "Sim" : "Não"));
                csv.write(CsvPtBr.CRLF);
                entityManager.detach(row); // the persistence context would keep every row otherwise
            }
        }
        csv.flush();
        return count;
    }

    /** Same label as /transacoes: "Grupo › Categoria", card payments and uncategorized by name. */
    private static String categoryLabel(Transaction row, com.porquinho.dto.ImportTransactionResponse.Category category) {
        if (row.getKind() == Transaction.Kind.CARD_PAYMENT) return "Pagamento de fatura";
        if (category == null) return "Sem categoria";
        return category.group() == null ? category.name() : category.group() + " › " + category.name();
    }

    /** Base of /transacoes (findConfirmedBetween) plus the optional filters; the order is skipped on the count query. */
    private Specification<Transaction> detailsSpec(UUID userId, DetailsFilter f, ExportOptions options) {
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
            if (!options.futureInstallments()) where.add(cb.isFalse(root.get("futureInstallment")));
            if (!options.deleted()) where.add(cb.isNull(root.get("deletedAt")));
            where.add(cb.between(root.get("transactionDate"), from, to));
            List<Predicate> categories = new ArrayList<>();
            if (!f.categoryIds().isEmpty()) categories.add(root.get("categoryId").in(f.categoryIds()));
            // "Sem categoria" as the list shows it: a card payment without category is "Pagamento de fatura"
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
                // "Sem categoria" and "Pagamento de fatura" (no category) go last in A-Z and Z-A alike
                if (field.equals("category")) orders.add(cb.asc(cb.selectCase()
                    .when(cb.isNull(root.get("categoryId")), 1).otherwise(0)));
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
