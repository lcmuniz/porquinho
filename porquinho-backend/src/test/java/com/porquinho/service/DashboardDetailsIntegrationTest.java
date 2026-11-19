package com.porquinho.service;

import com.porquinho.dto.DashboardLayer3Response;
import com.porquinho.dto.TransactionResponse;
import com.porquinho.exception.BadRequestException;
import com.porquinho.service.DashboardDetailsService.DetailsFilter;
import com.porquinho.service.DashboardDetailsService.ExportOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 7.6: the layer 3 against the real schema (PostgreSQL via Testcontainers): base of /transacoes, the optional
 * filters together, pagination, sorting with a stable tie-break, another user's ids, the period check and the CSV.
 * Not @Transactional; cleans up after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
class DashboardDetailsIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate DAY = TODAY.withDayOfMonth(1);

    @Autowired
    private DashboardDetailsService detailsService;
    @Autowired
    private BudgetBalanceCache balanceCache;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DashboardCache dashboardCache;

    private final List<UUID> users = new ArrayList<>();
    private UUID userId;
    private UUID account;
    private UUID card;
    private UUID food;
    private UUID fun;

    @BeforeEach
    void setUp() {
        userId = newUser();
        account = newAccount(userId, "Nubank");
        card = UUID.randomUUID();
        jdbc.update("INSERT INTO credit_cards (id, user_id, card_name, card_limit, due_day) VALUES (?, ?, 'Roxinho', 0, 10)",
            card, userId);
        UUID group = newCategory(userId, "Essenciais", null);
        food = newCategory(userId, "Mercado", group);
        fun = newCategory(userId, "Lazer", group);
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            dashboardCache.evictAll(id);
            balanceCache.evict(id);
            jdbc.update("DELETE FROM transactions WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transaction_imports WHERE user_id = ?", id);
            jdbc.update("DELETE FROM credit_cards WHERE user_id = ?", id);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", id);
            jdbc.update("DELETE FROM categories WHERE user_id = ? AND parent_category_id IS NOT NULL", id);
            jdbc.update("DELETE FROM categories WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, id + "@example.com");
        users.add(id);
        return id;
    }

    private UUID newAccount(UUID owner, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
            + "VALUES (?, ?, ?, 'CHECKING', 0, 0)", id, owner, name);
        return id;
    }

    private UUID newCategory(UUID owner, String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)", id, owner, name, parent);
        return id;
    }

    private UUID row(UUID owner, UUID accountId, UUID cardId, UUID category, LocalDate date, String description,
                     String amount, String type, String status, boolean future) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, credit_card_id, category_id, transaction_date, "
            + "description, original_description, amount, transaction_type, kind, review_status, is_future_installment) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'REGULAR', ?, ?)", id, owner, accountId, cardId, category, date,
            description, description, new BigDecimal(amount), type, status, future);
        return id;
    }

    private UUID confirmed(UUID accountId, UUID cardId, UUID category, LocalDate date, String description, String amount) {
        return row(userId, accountId, cardId, category, date, description, amount, "DEBIT", "CONFIRMED", false);
    }

    private static DetailsFilter filter(List<UUID> categories, boolean uncategorized, List<UUID> accounts,
                                        List<UUID> cards, String q, String sort) {
        return new DetailsFilter(null, null, categories, uncategorized, accounts, cards, q, sort);
    }

    private static List<String> descriptions(DashboardLayer3Response response) {
        return response.items().stream().map(TransactionResponse::description).toList();
    }

    @Test
    void pagesOf50WithTotalsAndAnEmptyPagePastTheEnd() {
        for (int i = 0; i < 120; i++) confirmed(account, null, food, DAY, "Compra " + i, "1.00");
        DetailsFilter all = filter(null, false, null, null, null, null);

        DashboardLayer3Response first = detailsService.details(userId, all, 0);
        assertThat(first.items()).hasSize(50);
        assertThat(first.totalItems()).isEqualTo(120);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(detailsService.details(userId, all, 2).items()).hasSize(20);
        assertThat(detailsService.details(userId, all, 3).items()).isEmpty();
        assertThatThrownBy(() -> detailsService.details(userId, all, -1)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void theBaseOfTransacoesAndEveryFilterTogether() {
        confirmed(account, null, food, DAY, "Mercado Extra", "10.00");
        confirmed(account, null, fun, DAY, "Mercado do cinema", "11.00");
        confirmed(null, card, food, DAY, "MERCADO no cartão", "12.00");
        confirmed(account, null, food, DAY, "Padaria", "13.00");
        confirmed(account, null, null, DAY, "Mercadinho sem categoria", "14.00");
        // a card payment without category is "Pagamento de fatura", not "Não classificado"
        UUID payment = confirmed(account, null, null, DAY, "Pagamento fatura", "15.00");
        jdbc.update("UPDATE transactions SET kind = 'CARD_PAYMENT' WHERE id = ?", payment);
        row(userId, account, null, food, DAY, "Mercado pendente", "1.00", "DEBIT", "PENDING", false);
        row(userId, account, null, food, DAY, "Mercado futuro", "1.00", "DEBIT", "CONFIRMED", true);
        UUID removed = confirmed(account, null, food, DAY, "Mercado removido", "1.00");
        jdbc.update("UPDATE transactions SET deleted_at = NOW() WHERE id = ?", removed);
        confirmed(account, null, food, DAY.minusMonths(1), "Mercado do mês passado", "1.00");

        assertThat(descriptions(detailsService.details(userId,
            filter(List.of(food), false, List.of(account), null, "mercado", null), 0)))
            .containsExactly("Mercado Extra");
        assertThat(descriptions(detailsService.details(userId,
            filter(List.of(food), false, List.of(account), List.of(card), "MERCADO", "amount,desc"), 0)))
            .containsExactly("MERCADO no cartão", "Mercado Extra");
        assertThat(descriptions(detailsService.details(userId, filter(null, true, null, null, null, null), 0)))
            .containsExactly("Mercadinho sem categoria");
        assertThat(descriptions(detailsService.details(userId, filter(List.of(fun), true, null, null, null,
            "amount,asc"), 0))).containsExactly("Mercado do cinema", "Mercadinho sem categoria");
        // % and _ are plain text
        assertThat(detailsService.details(userId, filter(null, false, null, null, "%", null), 0).items()).isEmpty();
        // the period picks last month's row
        LocalDate lastMonth = DAY.minusMonths(1);
        assertThat(descriptions(detailsService.details(userId, new DetailsFilter(lastMonth.toString(),
            lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).toString(), null, false, null, null, null, null), 0)))
            .containsExactly("Mercado do mês passado");
    }

    @Test
    void anotherUsersIdsBringNothingOfTheirs() {
        UUID other = newUser();
        UUID otherAccount = newAccount(other, "Itaú");
        UUID otherCategory = newCategory(other, "Outra", null);
        row(other, otherAccount, null, otherCategory, DAY, "Do outro", "5.00", "DEBIT", "CONFIRMED", false);
        confirmed(account, null, food, DAY, "Minha", "5.00");

        assertThat(detailsService.details(userId, filter(List.of(otherCategory), false, List.of(otherAccount), null,
            null, null), 0).items()).isEmpty();
        assertThat(descriptions(detailsService.details(userId, filter(null, false, null, null, null, null), 0)))
            .containsExactly("Minha");
    }

    @Test
    void sortsByDateAmountOrCategoryWithAStableTieBreak() {
        confirmed(account, null, food, DAY, "Mercado 1", "30.00");
        confirmed(account, null, fun, DAY.plusDays(1), "Lazer", "10.00");
        confirmed(account, null, null, DAY, "Sem categoria", "20.00");
        confirmed(account, null, food, DAY.plusDays(2), "Mercado 2", "5.00");
        // group "Casa" comes before "Essenciais" although "Zebra" comes after "Lazer"
        confirmed(account, null, newCategory(userId, "Zebra", newCategory(userId, "Casa", null)), DAY, "Zebra", "1.00");

        assertThat(descriptions(detailsService.details(userId, filter(null, false, null, null, null, null), 0)))
            .startsWith("Mercado 2", "Lazer");
        assertThat(descriptions(detailsService.details(userId, filter(null, false, null, null, null, "date,asc"), 0)))
            .endsWith("Lazer", "Mercado 2");
        assertThat(descriptions(detailsService.details(userId, filter(null, false, null, null, null, "amount,desc"),
            0))).containsExactly("Mercado 1", "Sem categoria", "Lazer", "Mercado 2", "Zebra");
        // A-Z by group then category, same category newest first, uncategorized last
        assertThat(descriptions(detailsService.details(userId, filter(null, false, null, null, null, "category,asc"),
            0))).containsExactly("Zebra", "Lazer", "Mercado 2", "Mercado 1", "Sem categoria");

        List<UUID> twice = detailsService.details(userId, filter(null, false, null, null, null, null), 0).items()
            .stream().map(TransactionResponse::id).toList();
        assertThat(detailsService.details(userId, filter(null, false, null, null, null, null), 0).items())
            .extracting(TransactionResponse::id).isEqualTo(twice);
    }

    @Test
    void anInvalidPeriodOrSortIsA400() {
        assertThatThrownBy(() -> detailsService.details(userId, new DetailsFilter("2026-11-30", "2026-11-01", null,
            false, null, null, null, null), 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> detailsService.details(userId, new DetailsFilter("30/11/2026", null, null,
            false, null, null, null, null), 0)).isInstanceOf(BadRequestException.class)
            .extracting(e -> ((BadRequestException) e).getField()).isEqualTo("from");
        assertThatThrownBy(() -> detailsService.details(userId, new DetailsFilter(null, "x", null,
            false, null, null, null, null), 0)).extracting(e -> ((BadRequestException) e).getField()).isEqualTo("to");
        assertThatThrownBy(() -> detailsService.details(userId, filter(null, false, null, null, null, "name,asc"), 0))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void theCsvHasEveryRowInTheSameOrderWithEscaping() {
        for (int i = 0; i < 60; i++) confirmed(account, null, food, DAY, "Compra " + i, "1.00");
        confirmed(null, card, null, DAY, "Loja; \"Boa\"", "1234.50");
        jdbc.update("UPDATE transactions SET is_pix = TRUE, is_installment = TRUE, installment_number = 2, "
            + "installment_total = 5 WHERE user_id = ? AND amount = 1234.50", userId);
        row(userId, account, null, fun, DAY, "=HYPERLINK(1)", "7.00", "CREDIT", "CONFIRMED", false);
        UUID importId = UUID.randomUUID();
        jdbc.update("INSERT INTO transaction_imports (id, user_id, account_id, file_name, file_type, file_size_kb, status) "
            + "VALUES (?, ?, ?, 'x.ofx', 'OFX', 1, 'COMPLETED')", importId, userId, account);
        UUID imported = confirmed(account, null, food, DAY, "Importada", "500.00");
        jdbc.update("UPDATE transactions SET import_id = ? WHERE id = ?", importId, imported);
        UUID payment = confirmed(account, null, null, DAY, "Fatura", "300.00");
        jdbc.update("UPDATE transactions SET kind = 'CARD_PAYMENT' WHERE id = ?", payment);

        CsvExport export = csv(filter(null, false, null, null, null, "amount,desc"),
            ExportOptions.NONE);

        String[] lines = export.csv().split("\r\n");
        assertThat(lines[0]).isEqualTo("﻿Data;Descrição;Valor;Tipo;Categoria;Conta;Pix;Parcelado;Parcela;Origem;Excluída");
        assertThat(lines).hasSize(65);
        assertThat(export.count()).isEqualTo(64);
        String date = DAY.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertThat(lines[1]).isEqualTo(date + ";\"Loja; \"\"Boa\"\"\";-1234,50;Saída;Não classificado;Roxinho;Sim;Sim;2/5;Manual;Não");
        assertThat(lines[2]).isEqualTo(date + ";Importada;-500,00;Saída;Essenciais › Mercado;Nubank;Não;Não;;Importação;Não");
        assertThat(lines[3]).isEqualTo(date + ";Fatura;-300,00;Saída;Pagamento de fatura;Nubank;Não;Não;;Manual;Não");
        assertThat(lines[4]).isEqualTo(date + ";'=HYPERLINK(1);7,00;Entrada;Essenciais › Lazer;Nubank;Não;Não;;Manual;Não");
        assertThat(lines[5]).startsWith(date + ";Compra ").contains(";-1,00;Saída;Essenciais › Mercado;Nubank;");
    }

    @Test
    void theExportFlagsOnlyLiftTheirOwnRule() {
        confirmed(account, null, food, DAY, "Normal", "1.00");
        UUID future = row(userId, null, card, food, DAY.plusMonths(2), "Parcelada", "10.00", "DEBIT", "CONFIRMED", true);
        jdbc.update("UPDATE transactions SET is_installment = TRUE, installment_number = 3, installment_total = 10 "
            + "WHERE id = ?", future);
        UUID removed = confirmed(account, null, food, DAY, "Removida", "2.00");
        jdbc.update("UPDATE transactions SET deleted_at = NOW() WHERE id = ?", removed);
        row(userId, account, null, food, DAY, "Pendente", "3.00", "DEBIT", "PENDING", false);
        UUID other = newUser();
        UUID otherAccount = newAccount(other, "Itaú");
        UUID otherRemoved = row(other, otherAccount, null, null, DAY, "Do outro", "4.00", "DEBIT", "CONFIRMED", true);
        jdbc.update("UPDATE transactions SET deleted_at = NOW() WHERE id = ?", otherRemoved);
        // "Tudo": the widest dates LocalDate and PostgreSQL take
        DetailsFilter all = new DetailsFilter("0001-01-01", "9999-12-31", null, false, null, null, null, null);

        CsvExport none = csv(all, ExportOptions.NONE);
        assertThat(none.count()).isEqualTo(1);
        assertThat(none.csv()).contains(";Normal;").doesNotContain("Parcelada", "Removida", "Pendente", "Do outro");

        CsvExport futures = csv(all, new ExportOptions(true, false));
        assertThat(futures.count()).isEqualTo(2);
        String date = DAY.plusMonths(2).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertThat(futures.csv()).contains(date + ";Parcelada;-10,00;Saída;Essenciais › Mercado;Roxinho;Não;Sim;3/10;Manual;Não\r\n")
            .doesNotContain("Removida");

        CsvExport deleted = csv(all, new ExportOptions(false, true));
        assertThat(deleted.count()).isEqualTo(2);
        assertThat(deleted.csv()).contains(";Removida;-2,00;Saída;Essenciais › Mercado;Nubank;Não;Não;;Manual;Sim\r\n")
            .doesNotContain("Parcelada");

        CsvExport both = csv(all, new ExportOptions(true, true));
        assertThat(both.count()).isEqualTo(3);
        assertThat(both.csv()).doesNotContain("Pendente", "Do outro");
    }

    @Test
    void anEmptyPeriodIsJustTheHeader() {
        CsvExport export = csv(filter(null, false, null, null, null, null),
            new ExportOptions(true, true));

        assertThat(export.count()).isZero();
        assertThat(export.csv()).isEqualTo("﻿Data;Descrição;Valor;Tipo;Categoria;Conta;Pix;Parcelado;Parcela;Origem;Excluída\r\n");
    }

    @Test
    void tenThousandRowsExportEverythingInUnderFiveSeconds() {
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            batch.add(new Object[] {UUID.randomUUID(), userId, account, i % 2 == 0 ? food : fun,
                DAY.minusDays(i % 700), "Compra " + i, "Compra " + i, BigDecimal.valueOf(i + 1, 2)});
        }
        jdbc.batchUpdate("INSERT INTO transactions (id, user_id, account_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind, review_status, is_future_installment) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DEBIT', 'REGULAR', 'CONFIRMED', FALSE)", batch);
        DetailsFilter all = new DetailsFilter("0001-01-01", "9999-12-31", null, false, null, null, null, null);
        ExportOptions flags = new ExportOptions(true, true);
        csv(all, flags); // warm-up

        // best of 3 runs: one slow run on a busy CI runner is noise, not the query
        long millis = Long.MAX_VALUE;
        CsvExport export = null;
        for (int run = 0; run < 3; run++) {
            long start = System.nanoTime();
            export = csv(all, flags);
            millis = Math.min(millis, (System.nanoTime() - start) / 1_000_000);
        }

        assertThat(export.count()).isEqualTo(10_000);
        assertThat(millis).isLessThan(5000);
    }

    @Test
    void tenThousandRowsAnswerInUnderOneSecond() {
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            batch.add(new Object[] {UUID.randomUUID(), userId, account, i % 2 == 0 ? food : fun,
                DAY.plusDays(i % 28), "Compra " + i, "Compra " + i,
                BigDecimal.valueOf(i + 1, 2)});
        }
        jdbc.batchUpdate("INSERT INTO transactions (id, user_id, account_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind, review_status, is_future_installment) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DEBIT', 'REGULAR', 'CONFIRMED', FALSE)", batch);
        DetailsFilter byCategory = filter(null, false, null, null, "compra", "category,asc");
        detailsService.details(userId, byCategory, 0); // warm-up

        // best of 3 runs: one slow run on a busy CI runner is noise, not the query
        long millis = Long.MAX_VALUE;
        DashboardLayer3Response response = null;
        for (int run = 0; run < 3; run++) {
            long start = System.nanoTime();
            response = detailsService.details(userId, byCategory, 100);
            millis = Math.min(millis, (System.nanoTime() - start) / 1_000_000);
        }

        assertThat(response.totalItems()).isEqualTo(10_000);
        assertThat(response.items()).hasSize(50);
        assertThat(millis).isLessThan(1000);
    }

    /**
     * Grupo C (pre-10-6): 100k linhas no "Tudo" com as duas flags. Mede tempo, bytes alocados pela thread e o pico de
     * heap vivo (amostrado após GCs forçados durante o export). Fica fora da suíte normal (~15 s): rode com
     * -DexcludedGroups= -Dgroups=slow. Medido em 2026-11-19, antes do streaming: ~1,0-1,8 s, 476 MB alocados, pico vivo
     * +166 MB; depois: ~1,1 s, 349 MB alocados, pico vivo +1 MB (CSV de 10 MB).
     */
    @Test
    @org.junit.jupiter.api.Tag("slow")
    void hundredThousandRowsExportMeasured() throws Exception {
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            batch.add(new Object[] {UUID.randomUUID(), userId, account, i % 2 == 0 ? food : fun,
                DAY.minusDays(i % 3000), "Compra no mercado número " + i, "Compra " + i, BigDecimal.valueOf(i + 1, 2)});
        }
        jdbc.batchUpdate("INSERT INTO transactions (id, user_id, account_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind, review_status, is_future_installment) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DEBIT', 'REGULAR', 'CONFIRMED', FALSE)", batch);
        batch = null;
        DetailsFilter all = new DetailsFilter("0001-01-01", "9999-12-31", null, false, null, null, null, "category,asc");
        ExportOptions flags = new ExportOptions(true, true);
        long[] bytes = new long[1];
        java.io.OutputStream sink = new java.io.OutputStream() {
            @Override public void write(int b) { bytes[0]++; }
            @Override public void write(byte[] b, int off, int len) { bytes[0] += len; }
        };
        exportTo(all, flags, sink); // warm-up
        long millis = Long.MAX_VALUE; // best of 3, without the GC sampler
        for (int run = 0; run < 3; run++) {
            long start = System.nanoTime();
            exportTo(all, flags, sink);
            millis = Math.min(millis, (System.nanoTime() - start) / 1_000_000);
        }

        var threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        var pools = java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(p -> p.getType() == java.lang.management.MemoryType.HEAP).toList();
        java.util.function.LongSupplier liveAfterGc = () -> {
            System.gc();
            return pools.stream().mapToLong(p -> p.getCollectionUsage() == null ? 0 : p.getCollectionUsage().getUsed()).sum();
        };
        long baseline = liveAfterGc.getAsLong();
        long[] peak = {0};
        var running = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread sampler = Thread.ofPlatform().start(() -> {
            while (running.get()) {
                peak[0] = Math.max(peak[0], liveAfterGc.getAsLong());
                try { Thread.sleep(50); } catch (InterruptedException e) { return; }
            }
        });
        bytes[0] = 0;
        long allocatedBefore = threads.getCurrentThreadAllocatedBytes();
        long count = exportTo(all, flags, sink);
        long allocated = threads.getCurrentThreadAllocatedBytes() - allocatedBefore;
        running.set(false);
        sampler.join();

        System.out.printf("CSV 100k: %d linhas, %d MB de CSV, %d ms, %d MB alocados, pico vivo +%d MB%n", count,
            bytes[0] >> 20, millis, allocated >> 20, Math.max(0, peak[0] - baseline) >> 20);
        assertThat(count).isEqualTo(100_000);
        assertThat(peak[0] - baseline).as("pico de heap vivo").isLessThan(32L << 20);
    }

    private long exportTo(DetailsFilter filter, ExportOptions options, java.io.OutputStream out) throws Exception {
        return detailsService.detailsCsv(userId, filter, options, count -> out);
    }

    private record CsvExport(String csv, long count) {}

    /** The streamed CSV as text, as the browser gets it. */
    private CsvExport csv(DetailsFilter filter, ExportOptions options) {
        var out = new java.io.ByteArrayOutputStream();
        try {
            long count = detailsService.detailsCsv(userId, filter, options, n -> out);
            return new CsvExport(out.toString(java.nio.charset.StandardCharsets.UTF_8), count);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
