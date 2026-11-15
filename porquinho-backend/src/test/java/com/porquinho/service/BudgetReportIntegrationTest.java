package com.porquinho.service;

import com.porquinho.controller.BudgetController;
import com.porquinho.exception.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Story 9.2: the budget report CSV against the real schema (PostgreSQL via Testcontainers). */
@SpringBootTest
@ActiveProfiles("test")
class BudgetReportIntegrationTest {

    private static final String HEADER = "﻿Categoria;Grupo;Alocado;Gasto;Restante;% usado;Status";
    private static final YearMonth NOV = YearMonth.of(2026, 11);

    @Autowired
    private BudgetReportService service;
    @Autowired
    private BudgetController controller;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> users = new ArrayList<>();
    private UUID userId;
    private UUID account;
    private UUID home;
    private UUID food;
    private UUID transport;
    private UUID rent;

    @BeforeEach
    void setUp() {
        userId = newUser();
        account = newAccount(userId);
        home = newCategory(userId, "Moradia", null);
        UUID essentials = newCategory(userId, "Essenciais", null);
        // created out of order: the CSV sorts by group, then name; "Água" before "Transporte" only with pt-BR collation
        rent = newCategory(userId, "Aluguel", home);
        transport = newCategory(userId, "Transporte", essentials);
        food = newCategory(userId, "Água", essentials);
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", id);
            jdbc.update("DELETE FROM budget_allocations WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transactions WHERE user_id = ?", id);
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

    private UUID newAccount(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
            + "VALUES (?, ?, 'Nubank', 'CHECKING', 0, 0)", id, owner);
        return id;
    }

    private UUID newCategory(UUID owner, String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)", id, owner, name, parent);
        return id;
    }

    private void allocate(UUID owner, UUID category, YearMonth month, String amount) {
        jdbc.update("INSERT INTO budget_allocations (user_id, category_id, month_year, allocated_amount) VALUES (?, ?, ?, ?)",
            owner, category, month.atDay(1), new BigDecimal(amount));
    }

    private void spend(UUID owner, UUID acc, UUID category, YearMonth month, String amount, String type, String status,
                       boolean future, boolean deleted) {
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind, review_status, is_future_installment, deleted_at) "
            + "VALUES (?, ?, ?, ?, ?, 'X', 'X', ?, ?, 'REGULAR', ?, ?, " + (deleted ? "NOW()" : "NULL") + ")",
            UUID.randomUUID(), owner, acc, category, month.atDay(10), new BigDecimal(amount), type, status, future);
    }

    private void spend(UUID category, YearMonth month, String amount) {
        spend(userId, account, category, month, amount, "DEBIT", "CONFIRMED", false, false);
    }

    private static List<String> lines(String csv) {
        assertThat(csv).endsWith("\r\n");
        return List.of(csv.split("\r\n"));
    }

    @Test
    void oneMonthHasEveryChildByGroupThenNameWithTheEpic6SpentRule() {
        allocate(userId, food, NOV, "100.00");
        allocate(userId, rent, NOV, "1000.00");
        spend(food, NOV, "120.00");
        spend(rent, NOV, "950.00");
        // left out: credit, pending, future installment, deleted, uncategorized, another month, another user
        spend(userId, account, food, NOV, "1.00", "CREDIT", "CONFIRMED", false, false);
        spend(userId, account, food, NOV, "2.00", "DEBIT", "PENDING", false, false);
        spend(userId, account, food, NOV, "3.00", "DEBIT", "CONFIRMED", true, false);
        spend(userId, account, food, NOV, "4.00", "DEBIT", "CONFIRMED", false, true);
        spend(userId, account, null, NOV, "5.00", "DEBIT", "CONFIRMED", false, false);
        spend(food, NOV.plusMonths(1), "6.00");
        UUID other = newUser();
        UUID otherGroup = newCategory(other, "Outro", null);
        UUID otherFood = newCategory(other, "Alimentação", otherGroup);
        allocate(other, otherFood, NOV, "50.00");
        spend(other, newAccount(other), otherFood, NOV, "7.00", "DEBIT", "CONFIRMED", false, false);
        // a removed child is left out
        UUID removed = newCategory(userId, "Antiga", home);
        jdbc.update("UPDATE categories SET deleted_at = NOW() WHERE id = ?", removed);

        String csv = service.csv(userId, "2026-11", "2026-11", false, false);

        assertThat(lines(csv)).containsExactly(HEADER,
            "Água;Essenciais;100,00;120,00;-20,00;120;Estourada",
            "Transporte;Essenciais;;0,00;;;",
            "Aluguel;Moradia;1000,00;950,00;50,00;95;Saldo baixo");
    }

    @Test
    void aRangeSumsAllocatedAndSpentAndTheStatusComesFromTheSums() {
        allocate(userId, food, YearMonth.of(2026, 9), "100.00");
        allocate(userId, food, YearMonth.of(2026, 10), "100.00");
        allocate(userId, food, NOV, "100.00");
        spend(food, YearMonth.of(2026, 9), "10.00");
        spend(food, YearMonth.of(2026, 10), "290.00");
        spend(food, NOV, "0.50");
        spend(transport, YearMonth.of(2026, 8), "999.00"); // before the range

        String csv = service.csv(userId, "2026-09", "2026-11", false, false);

        assertThat(lines(csv)).containsExactly(HEADER,
            "Água;Essenciais;300,00;300,50;-0,50;100;Estourada",
            "Transporte;Essenciais;;0,00;;;",
            "Aluguel;Moradia;;0,00;;;");
    }

    @Test
    void hierarchyAddsAGroupTotalWithThe69RuleBeforeItsChildren() {
        allocate(userId, food, NOV, "100.00");
        spend(food, NOV, "50.00");
        // unbudgeted child: its spent goes to "Gasto sem orçamento", so the group is not overspent
        spend(transport, NOV, "200.00");
        // a group with no budgeted child: spent sums every child, the column stays empty
        spend(rent, NOV, "800.00");

        String csv = service.csv(userId, "2026-11", "2026-11", true, false);

        assertThat(lines(csv)).containsExactly(HEADER + ";Gasto sem orçamento",
            "Essenciais;;100,00;50,00;50,00;50;Saudável;200,00",
            "Água;Essenciais;100,00;50,00;50,00;50;Saudável;",
            "Transporte;Essenciais;;200,00;;;;",
            "Moradia;;;800,00;;;;",
            "Aluguel;Moradia;;800,00;;;;");
    }

    @Test
    void hierarchyWithAveragesSumsEveryChildInTheGroupAverages() {
        allocate(userId, food, NOV, "100.00");
        spend(food, NOV, "30.00");
        spend(transport, NOV, "40.00");
        for (int m = 1; m <= 3; m++) spend(food, NOV.minusMonths(m), "300.00");
        for (int m = 4; m <= 6; m++) spend(transport, NOV.minusMonths(m), "60.00");

        String csv = service.csv(userId, "2026-11", "2026-11", true, true);

        // group: win3 900 (food), win6 900 + 180 -> 300,00 and 180,00
        assertThat(lines(csv)).containsExactly(
            HEADER + ";Gasto sem orçamento;Média 3 meses;Média 6 meses;Tendência",
            "Essenciais;;100,00;30,00;70,00;30;Saudável;40,00;300,00;180,00;Subindo",
            "Água;Essenciais;100,00;30,00;70,00;30;Saudável;;300,00;150,00;Subindo",
            "Transporte;Essenciais;;40,00;;;;;0,00;30,00;Caindo",
            "Moradia;;;0,00;;;;;0,00;0,00;Estável",
            "Aluguel;Moradia;;0,00;;;;;0,00;0,00;Estável");
    }

    @Test
    void namesAreEscapedOnChildAndGroupRows() {
        UUID other = newUser();
        UUID group = newCategory(other, "Casa \"nova\"", null);
        newCategory(other, "=SOMA(1;2)", group);

        String csv = service.csv(other, "2026-11", "2026-11", true, false);

        assertThat(lines(csv)).containsExactly(HEADER + ";Gasto sem orçamento",
            "\"Casa \"\"nova\"\"\";;;0,00;;;;",
            "\"'=SOMA(1;2)\";\"Casa \"\"nova\"\"\";;0,00;;;;");
    }

    @Test
    void overARangeTheAverageWindowsEndBeforeFrom() {
        // the 6 months before September: 100 each; February (7 back) and the range itself stay out of the averages
        for (int m = 1; m <= 6; m++) spend(rent, YearMonth.of(2026, 9).minusMonths(m), "100.00");
        spend(rent, YearMonth.of(2026, 2), "5000.00");
        for (int m = 9; m <= 11; m++) spend(rent, YearMonth.of(2026, m), "9999.00");

        String csv = service.csv(userId, "2026-09", "2026-11", false, true);

        assertThat(lines(csv)).contains("Aluguel;Moradia;;29997,00;;;;100,00;100,00;Estável");
    }

    @Test
    void averagesUseTheMonthsBeforeTheRangeAndTheTrendComparesThem() {
        // food: last 3 months 300 each, the 3 before 0 -> avg3 300, avg6 150: rising
        for (int m = 1; m <= 3; m++) spend(food, NOV.minusMonths(m), "300.00");
        // transport: 3 before 100 each, last 3 0 -> avg3 0, avg6 50: falling
        for (int m = 4; m <= 6; m++) spend(transport, NOV.minusMonths(m), "100.00");
        // rent: 100 every month -> stable; the month 7 back and the range itself do not count
        for (int m = 1; m <= 6; m++) spend(rent, NOV.minusMonths(m), "100.00");
        spend(rent, NOV.minusMonths(7), "5000.00");
        spend(rent, NOV, "5000.00");

        String csv = service.csv(userId, "2026-11", "2026-11", false, true);

        assertThat(lines(csv)).containsExactly(
            HEADER + ";Média 3 meses;Média 6 meses;Tendência",
            "Água;Essenciais;;0,00;;;;300,00;150,00;Subindo",
            "Transporte;Essenciais;;0,00;;;;0,00;50,00;Caindo",
            "Aluguel;Moradia;;5000,00;;;;100,00;100,00;Estável");
    }

    @Test
    void trendRule() {
        assertThat(BudgetReportService.trend(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualTo("Estável");
        assertThat(BudgetReportService.trend(BigDecimal.ONE, BigDecimal.ZERO)).isEqualTo("Subindo");
        // avg3 = 2 x win3 / 6-window: exactly +10% and -10% hit the edges
        assertThat(BudgetReportService.trend(new BigDecimal("55"), new BigDecimal("100"))).isEqualTo("Subindo");
        assertThat(BudgetReportService.trend(new BigDecimal("54.99"), new BigDecimal("100"))).isEqualTo("Estável");
        assertThat(BudgetReportService.trend(new BigDecimal("45"), new BigDecimal("100"))).isEqualTo("Caindo");
        assertThat(BudgetReportService.trend(new BigDecimal("45.01"), new BigDecimal("100"))).isEqualTo("Estável");
    }

    @Test
    void aUserWithoutChildrenGetsOnlyTheHeader() {
        UUID lonely = newUser();
        newCategory(lonely, "Só grupo", null);

        assertThat(service.csv(lonely, "2026-11", "2026-11", true, false)).isEqualTo(HEADER + ";Gasto sem orçamento\r\n");
    }

    @Test
    void invalidRangesAre400() {
        for (String[] range : List.of(new String[] {"2026-12", "2026-11"}, new String[] {"2025-11", "2026-11"},
                new String[] {"2026-13", "2026-13"}, new String[] {"11/2026", "2026-11"}, new String[] {null, "2026-11"},
                new String[] {"2026-11", null}, new String[] {"2026-11", "2026/11"})) {
            assertThatThrownBy(() -> service.csv(userId, range[0], range[1], false, false))
                .isInstanceOf(BadRequestException.class).hasMessage(BudgetReportService.INVALID_RANGE_MESSAGE);
        }
        // 12 months is the limit
        assertThat(service.csv(userId, "2025-12", "2026-11", false, false)).startsWith(HEADER);
    }

    @Test
    void theEndpointAuditsTheExportWithTheIpAndTheRange() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "ES256").subject(userId.toString())
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");

        ResponseEntity<String> response = controller.export(jwt, "2026-09", "2026-11", false, false, request);

        assertThat(response.getHeaders().getFirst("Content-Disposition"))
            .isEqualTo("attachment; filename=\"orcamento_2026-09_2026-11.csv\"");
        List<Map<String, Object>> logs = jdbc.queryForList(
            "SELECT event_type, ip_address, metadata::text AS metadata FROM audit_logs WHERE user_id = ?", userId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).get("event_type")).isEqualTo("budget_export");
        assertThat(String.valueOf(logs.get(0).get("ip_address"))).contains("10.0.0.7");
        assertThat(String.valueOf(logs.get(0).get("metadata"))).contains("\"from\"", "2026-09", "\"to\"", "2026-11");
    }

    @Test
    void twelveMonthsOfFiftyCategoriesWithAveragesIsUnderThreeSeconds() {
        UUID group = newCategory(userId, "Muitas", null);
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < 50; i++) many.add(newCategory(userId, "Categoria " + i, group));
        YearMonth from = NOV.minusMonths(11);
        List<Object[]> allocations = new ArrayList<>();
        List<Object[]> transactions = new ArrayList<>();
        for (YearMonth m = from.minusMonths(6); !m.isAfter(NOV); m = m.plusMonths(1)) {
            for (UUID c : many) {
                if (!m.isBefore(from)) allocations.add(new Object[] {userId, c, m.atDay(1), new BigDecimal("500.00")});
                for (int t = 0; t < 10; t++) {
                    transactions.add(new Object[] {UUID.randomUUID(), userId, account, c, m.atDay(1 + t),
                        new BigDecimal("12.34")});
                }
            }
        }
        jdbc.batchUpdate("INSERT INTO budget_allocations (user_id, category_id, month_year, allocated_amount) "
            + "VALUES (?, ?, ?, ?)", allocations);
        jdbc.batchUpdate("INSERT INTO transactions (id, user_id, account_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind, review_status, is_future_installment) "
            + "VALUES (?, ?, ?, ?, ?, 'X', 'X', ?, 'DEBIT', 'REGULAR', 'CONFIRMED', FALSE)", transactions);

        service.csv(userId, from.toString(), NOV.toString(), true, true); // warm-up
        long start = System.nanoTime();
        String csv = service.csv(userId, from.toString(), NOV.toString(), true, true);
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("Story 9.2: 12 meses x 50 categorias em %d ms%n", millis);

        assertThat(lines(csv)).hasSize(1 + 3 + 2 + 51); // header, Essenciais + 2, Moradia + 1, Muitas + 50
        assertThat(csv).contains("Categoria 0;Muitas;6000,00;1480,80;4519,20;25;Saudável;;123,40;123,40;Estável");
        assertThat(millis).isLessThan(3000);
    }
}
