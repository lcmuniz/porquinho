package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.CreateCreditCardRequest;
import com.porquinho.dto.TransactionImportResponse;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 5.1: uploads against the real schema (PostgreSQL via Testcontainers, Flyway V25): the row and the audit,
 * nothing written on 400/404, the list with the target names, the CHECKs, purge order and export.
 * Not @Transactional: each service call commits. Cleans up after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionImportIntegrationTest {

    @Autowired
    private TransactionImportService transactionImportService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private CreditCardService creditCardService;
    @Autowired
    private TransactionImportRepository transactionImportRepository;
    @Autowired
    private AccountBalanceHistoryRepository balanceHistoryRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CreditCardRepository creditCardRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DataExportService dataExportService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> users = new ArrayList<>();
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = newUser();
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transaction_imports WHERE user_id = ?", id);
            jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", id);
            jdbc.update("DELETE FROM credit_cards WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, id + "@example.com");
        users.add(id);
        return id;
    }

    private UUID account(UUID owner, String name) {
        return accountService.create(owner, new CreateAccountRequest(name, null, "CHECKING", new BigDecimal("100.00"))).getId();
    }

    private UUID card(UUID owner, String name) {
        return creditCardService.create(owner, new CreateCreditCardRequest(name, null, new BigDecimal("1000.00"),
            new BigDecimal("10"))).getId();
    }

    private static MockMultipartFile sample(String name) throws IOException {
        byte[] bytes = new ClassPathResource("import-samples/" + name).getInputStream().readAllBytes();
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    private long imports(UUID owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transaction_imports WHERE user_id = ?", Long.class, owner);
    }

    @Test
    void accountUploadIsStoredAsProcessingWithTheAudit() throws IOException {
        UUID nubank = account(userId, "Nubank");

        TransactionImportResponse created = transactionImportService.create(userId, nubank, null,
            sample("nubank-conta.ofx"), "127.0.0.1");

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM transaction_imports WHERE id = ?", created.id());
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat(row.get("account_id")).isEqualTo(nubank);
        assertThat(row.get("credit_card_id")).isNull();
        assertThat(row.get("file_name")).isEqualTo("nubank-conta.ofx");
        assertThat(row.get("file_type")).isEqualTo("OFX");
        assertThat(row.get("bank_name")).isNull();
        assertThat(row.get("status")).isEqualTo("PROCESSING");
        assertThat(row.get("file_size_kb")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event_type = 'import_created'",
            Long.class, userId)).isEqualTo(1);
        assertThat(created.targetName()).isEqualTo("Nubank");
    }

    @Test
    void cardUploadIsStored() throws IOException {
        UUID itau = card(userId, "Itaú Visa");

        TransactionImportResponse created = transactionImportService.create(userId, null, itau,
            sample("itau-cartao.ofx"), "127.0.0.1");

        assertThat(jdbc.queryForObject("SELECT credit_card_id FROM transaction_imports WHERE id = ?", UUID.class,
            created.id())).isEqualTo(itau);
    }

    @Test
    void mismatchedStatementWritesNothing() throws IOException {
        UUID nubank = account(userId, "Nubank");
        MockMultipartFile upload = sample("itau-cartao.ofx");

        assertThatThrownBy(() -> transactionImportService.create(userId, nubank, null, upload, "127.0.0.1"))
            .isInstanceOf(BadRequestException.class);
        assertThat(imports(userId)).isZero();
    }

    @Test
    void anotherUsersOrRemovedTargetIs404AndWritesNothing() throws IOException {
        UUID other = newUser();
        UUID othersAccount = account(other, "Alheia");
        UUID othersCard = card(other, "Alheio");
        UUID removed = account(userId, "Removida");
        accountService.delete(userId, removed, "127.0.0.1");
        MockMultipartFile accountFile = sample("nubank-conta.ofx");
        MockMultipartFile cardFile = sample("itau-cartao.ofx");

        assertThatThrownBy(() -> transactionImportService.create(userId, othersAccount, null, accountFile, "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class).hasMessage("Conta não encontrada");
        assertThatThrownBy(() -> transactionImportService.create(userId, null, othersCard, cardFile, "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class).hasMessage("Cartão não encontrado");
        assertThatThrownBy(() -> transactionImportService.create(userId, removed, null, accountFile, "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThat(imports(userId)).isZero();
        assertThat(imports(other)).isZero();
    }

    @Test
    void listShowsOnlyTheUsersImportsMostRecentFirstWithTargetNames() throws IOException {
        UUID nubank = account(userId, "Nubank");
        UUID itau = card(userId, "Itaú Visa");
        transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1");
        transactionImportService.create(userId, null, itau, sample("itau-cartao.ofx"), "127.0.0.1");
        UUID other = newUser();
        transactionImportService.create(other, account(other, "Outra"), null, sample("bb-conta.ofx"), "127.0.0.1");

        List<TransactionImportResponse> list = transactionImportService.listRecent(userId);

        assertThat(list).extracting(TransactionImportResponse::fileName).containsExactly("itau-cartao.ofx", "nubank-conta.ofx");
        assertThat(list).extracting(TransactionImportResponse::targetName).containsExactly("Itaú Visa", "Nubank");
    }

    @Test
    void checksRejectBothTargetsAndUnknownStatus() {
        UUID nubank = account(userId, "Nubank");
        UUID itau = card(userId, "Itaú Visa");
        String insert = "INSERT INTO transaction_imports (user_id, account_id, credit_card_id, file_name, file_type, "
            + "file_size_kb, status) VALUES (?, ?, ?, 'x.ofx', ?, 1, ?)";

        assertThatThrownBy(() -> jdbc.update(insert, userId, nubank, itau, "OFX", "PROCESSING"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, userId, null, null, "OFX", "PROCESSING"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, userId, nubank, null, "OFX", "PENDING"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, userId, nubank, null, "PDF", "PROCESSING"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void purgeOrderDeletesImportsBeforeAccountsCardsAndTheUser() throws IOException {
        UUID nubank = account(userId, "Nubank");
        UUID itau = card(userId, "Itaú Visa");
        transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1");
        transactionImportService.create(userId, null, itau, sample("itau-cartao.ofx"), "127.0.0.1");

        // The FKs from transaction_imports block deleting the accounts and cards first
        assertThatThrownBy(() -> jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM credit_cards WHERE user_id = ?", userId))
            .isInstanceOf(DataIntegrityViolationException.class);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(transactionImportRepository.deleteByUserId(userId)).isEqualTo(2);
            balanceHistoryRepository.deleteByUserId(userId);
            assertThat(accountRepository.deleteByUserId(userId)).isEqualTo(1);
            assertThat(creditCardRepository.deleteByUserId(userId)).isEqualTo(1);
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
            assertThat(userRepository.deleteByIdNative(userId)).isEqualTo(1);
        });
        assertThat(imports(userId)).isZero();
    }

    @Test
    void exportIncludesTheImports() throws IOException {
        UUID nubank = account(userId, "Nubank");
        TransactionImportResponse created = transactionImportService.create(userId, nubank, null,
            sample("nubank-conta.ofx"), "127.0.0.1");

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("transaction_imports");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("id")).isEqualTo(created.id().toString());
        assertThat(rows.get(0).get("account_id")).isEqualTo(nubank.toString());
        assertThat(rows.get(0).get("status")).isEqualTo("PROCESSING");
    }
}
