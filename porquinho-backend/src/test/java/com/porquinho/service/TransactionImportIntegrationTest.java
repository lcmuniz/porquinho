package com.porquinho.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.CreateCreditCardRequest;
import com.porquinho.dto.TransactionImportResponse;
import com.porquinho.entity.TransactionImport;
import com.porquinho.exception.BadRequestException;
import com.porquinho.dto.UpdateImportCategoryRequest;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import com.porquinho.repository.TransferRepository;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/**
 * Stories 5.1, 5.3, 5.4 and 5.8: uploads against the real schema (PostgreSQL via Testcontainers, Flyway V25/V26): the row
 * and the audit, nothing written on 400/404, the list with the target names, the background processing up to the
 * transactions in REVIEW, the CHECKs, purge order and export. Not @Transactional: each service call commits. Cleans
 * up after each test.
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
    @Autowired
    private TransactionImportProcessor transactionImportProcessor;
    @Autowired
    private ConfigurableApplicationContext context;
    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private TransferRepository transferRepository;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private TransactionClassificationService classificationService;
    @Autowired
    private AiClassificationHistoryRepository aiClassificationHistoryRepository;
    @Autowired
    private TransactionImportReviewService reviewService;
    /** Never the real client: every test here runs without network. Answers nothing unless stubbed. */
    @MockitoBean
    private TransactionClassifier classifier;

    private final List<UUID> users = new ArrayList<>();
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = newUser();
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            // stops any processing still running: its guarded updates find no PROCESSING row and write nothing
            jdbc.update("UPDATE transaction_imports SET status = 'FAILED' WHERE user_id = ? AND status = 'PROCESSING'", id);
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", id);
            jdbc.update("DELETE FROM ai_classification_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transactions WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transfers WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transaction_imports WHERE user_id = ?", id);
            jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", id);
            jdbc.update("DELETE FROM credit_cards WHERE user_id = ?", id);
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

    /** Polls the import row until {@code done} holds (the processing is asynchronous), at most 10 s. */
    private Map<String, Object> awaitRow(UUID importId, java.util.function.Predicate<Map<String, Object>> done)
        throws InterruptedException {
        return awaitRow(importId, done, Duration.ofSeconds(10));
    }

    private Map<String, Object> awaitRow(UUID importId, java.util.function.Predicate<Map<String, Object>> done,
                                         Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM transaction_imports WHERE id = ?", importId);
        while (!done.test(row) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            row = jdbc.queryForMap("SELECT * FROM transaction_imports WHERE id = ?", importId);
        }
        return row;
    }

    private Map<String, Object> awaitDone(UUID importId) throws InterruptedException {
        return awaitRow(importId, r -> !"PROCESSING".equals(r.get("status")));
    }

    /** The rows that came from the file of {@code importId} (the future installments it created are left out). */
    private List<Map<String, Object>> transactionsOf(UUID importId) {
        return jdbc.queryForList("SELECT * FROM transactions WHERE import_id = ? AND is_future_installment = FALSE "
            + "ORDER BY transaction_date, external_id", importId);
    }

    /** The future installments created by {@code importId} (Story 5.7). */
    private List<Map<String, Object>> futuresOf(UUID importId) {
        return jdbc.queryForList("SELECT * FROM transactions WHERE import_id = ? AND is_future_installment = TRUE "
            + "ORDER BY installment_group_id, installment_number", importId);
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
        assertThat(created.status()).isEqualTo("PROCESSING");
        assertThat(row.get("status")).isIn("PROCESSING", "REVIEW");  // the background processing may be done already
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
    void purgeOrderDeletesTransactionsAndImportsBeforeAccountsCardsAndTheUser() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID itau = card(userId, "Itaú Visa");
        UUID toAccount = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        UUID toCard = transactionImportService.create(userId, null, itau, sample("itau-cartao.ofx"), "127.0.0.1").id();
        assertThat(awaitDone(toAccount).get("status")).isEqualTo("REVIEW");
        assertThat(awaitDone(toCard).get("status")).isEqualTo("REVIEW");

        // The FKs from transactions block deleting the imports first; those from both block the accounts and cards
        assertThatThrownBy(() -> jdbc.update("DELETE FROM transaction_imports WHERE user_id = ?", userId))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM credit_cards WHERE user_id = ?", userId))
            .isInstanceOf(DataIntegrityViolationException.class);

        transactionTemplate.executeWithoutResult(status -> {
            // 10 + 8 entries and the 21 future installments of the Itaú card (Story 5.7)
            assertThat(transactionRepository.deleteByUserId(userId)).isEqualTo(39);
            assertThat(transactionImportRepository.deleteByUserId(userId)).isEqualTo(2);
            balanceHistoryRepository.deleteByUserId(userId);
            assertThat(accountRepository.deleteByUserId(userId)).isEqualTo(1);
            assertThat(creditCardRepository.deleteByUserId(userId)).isEqualTo(1);
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
            assertThat(userRepository.deleteByIdNative(userId)).isEqualTo(1);
        });
        assertThat(imports(userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId)).isZero();
    }

    @Test
    void exportIncludesTheImportsAndTheirTransactions() throws Exception {
        UUID nubank = account(userId, "Nubank");
        TransactionImportResponse created = transactionImportService.create(userId, nubank, null,
            sample("nubank-conta.ofx"), "127.0.0.1");

        awaitDone(created.id());

        Map<String, Object> data = dataExportService.collectUserData(userId);
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) data.get("transaction_imports");
        @SuppressWarnings("unchecked")
        var transactions = (List<Map<String, Object>>) data.get("transactions");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("id")).isEqualTo(created.id().toString());
        assertThat(rows.get(0).get("account_id")).isEqualTo(nubank.toString());
        assertThat(rows.get(0).get("status")).isEqualTo("REVIEW");
        assertThat(rows.get(0).get("total_transactions")).isEqualTo(10);
        assertThat(transactions).hasSize(10).allSatisfy(t -> {
            assertThat(t.get("import_id")).isEqualTo(created.id().toString());
            assertThat(t.get("review_status")).isEqualTo("PENDING");
        });
    }

    @Test
    void uploadedOfxIsReadInTheBackgroundAndItsTransactionsWaitForReview() throws Exception {
        UUID nubank = account(userId, "Nubank");

        TransactionImportResponse created = transactionImportService.create(userId, nubank, null,
            sample("nubank-conta.ofx"), "127.0.0.1");

        Map<String, Object> row = awaitDone(created.id());
        assertThat(row.get("bank_name")).isEqualTo("NUBANK");
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("total_transactions")).isEqualTo(10);
        assertThat(row.get("imported_transactions")).isEqualTo(10);
        assertThat(row.get("skipped_rows")).isEqualTo(0);
        assertThat(row.get("error_message")).isNull();
        List<Map<String, Object>> transactions = transactionsOf(created.id());
        assertThat(transactions).hasSize(10).allSatisfy(t -> {
            assertThat(t.get("user_id")).isEqualTo(userId);
            assertThat(t.get("account_id")).isEqualTo(nubank);
            assertThat(t.get("credit_card_id")).isNull();
            assertThat(t.get("review_status")).isEqualTo("PENDING");
            assertThat(t.get("classification_source")).isEqualTo("NONE");
            assertThat(t.get("affects_balance")).isEqualTo(false);
            assertThat(t.get("is_installment")).isEqualTo(false);
            assertThat(t.get("is_future_installment")).isEqualTo(false);
            assertThat(t.get("deleted_at")).isNull();
        });
        Map<String, Object> first = transactions.get(0);
        assertThat(first.get("transaction_date")).isEqualTo(java.sql.Date.valueOf("2026-10-01"));
        assertThat((BigDecimal) first.get("amount")).isEqualByComparingTo("2000.00");
        assertThat(first.get("transaction_type")).isEqualTo("CREDIT");
        assertThat(first.get("external_id")).isEqualTo("bdd640fb-0667-1ad1-1c80-317fa3b1799d");
        assertThat((String) first.get("description")).startsWith("Transferência recebida pelo Pix - JOANA PEREIRA LIMA - •••");
        assertThat(transactions).filteredOn(t -> "CARD_PAYMENT".equals(t.get("kind")))
            .extracting(t -> t.get("description")).containsExactly("Pagamento da fatura - Cartão Nubank");
        // balance untouched (D3 is applied in Story 5.10)
        assertThat(jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id = ?", BigDecimal.class, nubank))
            .isEqualByComparingTo("100.00");
        TransactionImportResponse fetched = transactionImportService.get(userId, created.id());
        assertThat(fetched.status()).isEqualTo("REVIEW");
        assertThat(fetched.totalTransactions()).isEqualTo(10);
        assertThat(fetched.importedTransactions()).isEqualTo(10);
        assertThat(fetched.skippedRows()).isZero();
        assertThat(fetched.targetName()).isEqualTo("Nubank");
    }

    @ParameterizedTest
    @CsvSource({"nubank-conta,4", "bb-conta,3", "itau-conta,3", "bradesco-conta,2", "caixa-conta,3"})
    void pixEntriesAreFlaggedWithTheirDetailsInOfxAndCsv(String sample, int pixCount) throws Exception {
        for (String extension : List.of("ofx", "csv")) {
            // each file in its own account: in the same one the second would be all duplicates (Story 5.5)
            UUID target = account(userId, sample + " " + extension);
            TransactionImportResponse created = transactionImportService.create(userId, target, null,
                sample(sample + "." + extension), "127.0.0.1");
            assertThat(awaitDone(created.id()).get("status")).as(extension).isEqualTo("REVIEW");

            List<Map<String, Object>> rows = jdbc.queryForList("SELECT is_pix, pix_details::text AS details, "
                + "transaction_type FROM transactions WHERE import_id = ?", created.id());
            assertThat(rows).as(sample + "." + extension).filteredOn(r -> Boolean.TRUE.equals(r.get("is_pix")))
                .hasSize(pixCount).allSatisfy(r -> {
                    JsonNode details = new ObjectMapper().readTree((String) r.get("details"));
                    assertThat(details.get("detected").asBoolean()).isTrue();
                    assertThat(details.get("direction").asText())
                        .isEqualTo("DEBIT".equals(r.get("transaction_type")) ? "SENT" : "RECEIVED");
                });
            assertThat(rows).filteredOn(r -> Boolean.FALSE.equals(r.get("is_pix")))
                .allSatisfy(r -> assertThat(r.get("details")).isNull());
        }
    }

    @Test
    void nubankPixDetailsAreReadBackFromTheDatabase() throws Exception {
        UUID nubank = account(userId, "Nubank");
        TransactionImportResponse created = transactionImportService.create(userId, nubank, null,
            sample("nubank-conta.ofx"), "127.0.0.1");
        awaitDone(created.id());

        String json = jdbc.queryForObject("SELECT pix_details::text FROM transactions WHERE import_id = ? "
            + "AND transaction_date = '2026-10-15'", String.class, created.id());
        JsonNode details = new ObjectMapper().readTree(json);
        assertThat(details.get("direction").asText()).isEqualTo("RECEIVED");
        assertThat(details.get("counterpart_name").asText()).isEqualTo("ANA BEATRIZ COSTA");
        assertThat(details.get("document").asText()).isEqualTo("•••.111.222-••");
        assertThat(details.get("counterpart_bank").get("name").asText()).isEqualTo("NU PAGAMENTOS - IP");
        assertThat(details.get("counterpart_bank").get("code").asText()).isEqualTo("0260");
        assertThat(details.get("counterpart_bank").get("agency").asText()).isEqualTo("1");
        assertThat(details.get("counterpart_bank").get("account").asText()).isEqualTo("7788990-1");

        // the export carries it as an object, not as an escaped string
        @SuppressWarnings("unchecked")
        var exported = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("transactions");
        assertThat(exported).filteredOn(t -> Boolean.TRUE.equals(t.get("is_pix"))).hasSize(4)
            .allSatisfy(t -> assertThat(t.get("pix_details")).isInstanceOf(JsonNode.class));
    }

    @Test
    void uploadedCsvGetsItsBankAndTheSameTransactionsAsTheOfx() throws Exception {
        UUID nubank = account(userId, "Nubank");
        // another account: in the same one the second file would be all duplicates (Story 5.5)
        UUID nubankOfx = account(userId, "Nubank OFX");

        TransactionImportResponse fromCsv = transactionImportService.create(userId, nubank, null,
            sample("nubank-conta.csv"), "127.0.0.1");
        TransactionImportResponse fromOfx = transactionImportService.create(userId, nubankOfx, null,
            sample("nubank-conta.ofx"), "127.0.0.1");

        Map<String, Object> row = awaitDone(fromCsv.id());
        assertThat(awaitDone(fromOfx.id()).get("status")).isEqualTo("REVIEW");
        assertThat(row.get("file_type")).isEqualTo("CSV");
        assertThat(row.get("bank_name")).isEqualTo("NUBANK");
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("total_transactions")).isEqualTo(10);
        assertThat(row.get("imported_transactions")).isEqualTo(10);
        assertThat(row.get("skipped_rows")).isEqualTo(0);
        java.util.function.Function<Map<String, Object>, List<Object>> key = t -> List.of(t.get("transaction_date"),
            ((BigDecimal) t.get("amount")).toPlainString(), t.get("transaction_type"), t.get("kind"));
        assertThat(transactionsOf(fromCsv.id())).hasSize(10).map(key)
            .containsExactlyElementsOf(transactionsOf(fromOfx.id()).stream().map(key).toList());
        assertThat(transactionsOf(fromCsv.id())).allSatisfy(t -> {
            assertThat(t.get("account_id")).isEqualTo(nubank);
            assertThat(t.get("review_status")).isEqualTo("PENDING");
        });
    }

    @Test
    void nubankCardCsvGoesToTheCardWithPaymentAndRefundAsCredit() throws Exception {
        UUID nubank = card(userId, "Nubank");

        TransactionImportResponse created = transactionImportService.create(userId, null, nubank,
            sample("nubank-cartao.csv"), "127.0.0.1");

        Map<String, Object> row = awaitDone(created.id());
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("bank_name")).isEqualTo("NUBANK");
        assertThat(transactionsOf(created.id())).hasSize(9)
            .filteredOn(t -> "CREDIT".equals(t.get("transaction_type")))
            .extracting(t -> t.get("description"), t -> t.get("kind"))
            .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("Pagamento recebido", "CARD_PAYMENT"),
                org.assertj.core.groups.Tuple.tuple("Estorno - Amazon Marketplace", "REGULAR"));
    }

    @Test
    void csvBadRowsAreSkippedAndCounted() throws Exception {
        UUID nubank = account(userId, "Nubank");

        TransactionImportResponse created = transactionImportService.create(userId, nubank, null,
            sample("erro-csv-linhas-invalidas.csv"), "127.0.0.1");

        Map<String, Object> row = awaitDone(created.id());
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("imported_transactions")).isEqualTo(2);
        assertThat(row.get("skipped_rows")).isEqualTo(4);
    }

    @Test
    void unknownOrMismatchedCsvWritesNothing() throws IOException {
        UUID nubank = account(userId, "Nubank");
        UUID card = card(userId, "Nubank cartão");
        MockMultipartFile unknown = sample("erro-csv-banco-desconhecido.csv");
        MockMultipartFile cardCsv = sample("nubank-cartao.csv");
        MockMultipartFile accountCsv = sample("nubank-conta.csv");

        assertThatThrownBy(() -> transactionImportService.create(userId, nubank, null, unknown, "127.0.0.1"))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Não reconhecemos o formato deste CSV. Tente exportar o extrato em OFX.");
        assertThatThrownBy(() -> transactionImportService.create(userId, nubank, null, cardCsv, "127.0.0.1"))
            .isInstanceOf(BadRequestException.class).hasMessage(TransactionImportService.CARD_FILE_TO_ACCOUNT_MESSAGE);
        assertThatThrownBy(() -> transactionImportService.create(userId, null, card, accountCsv, "127.0.0.1"))
            .isInstanceOf(BadRequestException.class).hasMessage(TransactionImportService.ACCOUNT_FILE_TO_CARD_MESSAGE);
        assertThat(imports(userId)).isZero();
    }

    @Test
    void cardStatementTransactionsGoToTheCard() throws Exception {
        UUID itau = card(userId, "Itaú Visa");

        TransactionImportResponse created = transactionImportService.create(userId, null, itau,
            sample("itau-cartao.ofx"), "127.0.0.1");

        Map<String, Object> row = awaitDone(created.id());
        assertThat(row.get("bank_name")).isEqualTo("ITAU");
        assertThat(row.get("status")).isEqualTo("REVIEW");
        List<Map<String, Object>> transactions = transactionsOf(created.id());
        assertThat(transactions).hasSize(8).allSatisfy(t -> {
            assertThat(t.get("credit_card_id")).isEqualTo(itau);
            assertThat(t.get("account_id")).isNull();
        });
        assertThat(transactions).filteredOn(t -> "CREDIT".equals(t.get("transaction_type")))
            .extracting(t -> t.get("description"), t -> t.get("kind"))
            .containsExactly(org.assertj.core.groups.Tuple.tuple("PAGAMENTO EFETUADO", "CARD_PAYMENT"));
        assertThat(transactions).filteredOn(t -> "DEBIT".equals(t.get("transaction_type"))).hasSize(7)
            .allSatisfy(t -> assertThat(t.get("kind")).isEqualTo("REGULAR"));
    }

    @Test
    void badRowsAreSkippedAndCounted() throws Exception {
        UUID itau = account(userId, "Itaú");

        TransactionImportResponse created = transactionImportService.create(userId, itau, null,
            sample("erro-ofx-linhas-invalidas.ofx"), "127.0.0.1");

        Map<String, Object> row = awaitDone(created.id());
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("total_transactions")).isEqualTo(6);
        assertThat(row.get("imported_transactions")).isEqualTo(2);
        assertThat(row.get("skipped_rows")).isEqualTo(4);
        assertThat(transactionsOf(created.id())).extracting(t -> t.get("description"))
            .containsExactly("PIX TRANSF CARLOS 02/10", "PADARIA SÃO JOSÉ");
    }

    @Test
    void ofxWithoutTransactionsFails() throws Exception {
        UUID itau = account(userId, "Itaú");
        Charset cp1252 = Charset.forName("windows-1252");
        String withoutEntries = new String(sample("itau-conta.ofx").getBytes(), cp1252)
            .replaceAll("(?s)<STMTTRN>.*?</STMTTRN>\\s*", "");
        MockMultipartFile upload = new MockMultipartFile("file", "vazio.ofx", "application/octet-stream",
            withoutEntries.getBytes(cp1252));

        TransactionImportResponse created = transactionImportService.create(userId, itau, null, upload, "127.0.0.1");

        Map<String, Object> row = awaitDone(created.id());
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("error_message"))
            .isEqualTo("Não encontramos transações neste arquivo. Exporte o extrato de novo e envie outra vez.");
        assertThat(transactionsOf(created.id())).isEmpty();
    }

    @Test
    void failureWhileSavingWritesNothing() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID importId = insertProcessing(nubank, "0 seconds", null);
        // the real repository, but saveAll fails after the rows reached the database
        TransactionRepository failing = Mockito.mock(TransactionRepository.class,
            AdditionalAnswers.delegatesTo(transactionRepository));
        Mockito.doAnswer(invocation -> {
            transactionRepository.saveAllAndFlush(invocation.getArgument(0));
            throw new IllegalStateException("boom");
        }).when(failing).saveAll(any());
        TransactionImportProcessor processor = new TransactionImportProcessor(transactionImportRepository, failing,
            transferRepository, auditLogService, classificationService, transactionManager, Clock.systemDefaultZone());

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx").getBytes());  // not the proxy: runs right here

        Map<String, Object> row = row(importId);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("error_message")).isEqualTo(TransactionImportProcessor.UNEXPECTED_ERROR_MESSAGE);
        assertThat(row.get("total_transactions")).isEqualTo(0);
        assertThat(transactionsOf(importId)).isEmpty();
    }

    @Test
    void importThatAlreadyLeftProcessingGetsNoTransactions() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID importId = insertProcessing(nubank, "0 seconds", "NUBANK");
        jdbc.update("UPDATE transaction_imports SET status = 'FAILED', error_message = ?, classification_total = 9, "
            + "classified_count = 4 WHERE id = ?", TransactionImportProcessor.INTERRUPTED_MESSAGE, importId);
        TransactionImportProcessor processor = new TransactionImportProcessor(transactionImportRepository,
            transactionRepository, transferRepository, auditLogService, classificationService, transactionManager, Clock.systemDefaultZone());

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx").getBytes());  // not the proxy: runs right here
        // the guard of the final update itself, against the real database
        assertThat(transactionImportRepository.recordParsed(importId, 10, 10, 0, 0, 0, java.time.OffsetDateTime.now()))
            .isZero();
        assertThat(transactionImportRepository.markReadyForReview(importId, java.time.OffsetDateTime.now())).isZero();
        assertThat(transactionImportRepository.startClassification(importId, 20, java.time.OffsetDateTime.now())).isZero();
        assertThat(transactionImportRepository.addClassified(importId, 3, java.time.OffsetDateTime.now())).isZero();

        Map<String, Object> row = row(importId);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("error_message")).isEqualTo(TransactionImportProcessor.INTERRUPTED_MESSAGE);
        assertThat(row.get("classification_total")).isEqualTo(9);
        assertThat(row.get("classified_count")).isEqualTo(4);
        assertThat(row.get("total_transactions")).isEqualTo(0);
        assertThat(row.get("imported_transactions")).isEqualTo(0);
        assertThat(row.get("skipped_rows")).isEqualTo(0);
        assertThat(transactionsOf(importId)).isEmpty();
    }

    @Test
    void aWholeYearIsImportedWellUnderThirtySeconds() throws Exception {
        UUID itau = account(userId, "Itaú");
        MockMultipartFile upload = new MockMultipartFile("file", "ano.ofx", "application/octet-stream",
            OfxTransactionParserTest.yearOfEntries(3000).getBytes(Charset.forName("windows-1252")));

        long start = System.nanoTime();
        TransactionImportResponse created = transactionImportService.create(userId, itau, null, upload, "127.0.0.1");
        Map<String, Object> row = awaitRow(created.id(), r -> !"PROCESSING".equals(r.get("status")), Duration.ofSeconds(30));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("total_transactions")).isEqualTo(3000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE import_id = ?", Long.class, created.id()))
            .isEqualTo(3000);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(30));
    }

    @Test
    void transactionChecksRejectBothTargetsAndNonPositiveAmounts() {
        UUID nubank = account(userId, "Nubank");
        UUID itau = card(userId, "Itaú Visa");
        String insert = "INSERT INTO transactions (user_id, account_id, credit_card_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind) VALUES (?, ?, ?, DATE '2026-10-01', 'x', 'x', ?, ?, ?)";

        assertThatThrownBy(() -> jdbc.update(insert, userId, nubank, itau, new BigDecimal("1.00"), "DEBIT", "REGULAR"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, userId, null, null, new BigDecimal("1.00"), "DEBIT", "REGULAR"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, userId, nubank, null, BigDecimal.ZERO, "DEBIT", "REGULAR"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, userId, nubank, null, new BigDecimal("1.00"), "OUT", "REGULAR"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, userId, nubank, null, new BigDecimal("1.00"), "DEBIT", "TRANSFER"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update(insert, userId, null, itau, new BigDecimal("1.00"), "CREDIT", "CARD_PAYMENT")).isEqualTo(1);
    }

    @Test
    void truncatedOfxFailsWithTheIncompleteMessage() throws Exception {
        UUID nubank = account(userId, "Nubank");

        TransactionImportResponse created = transactionImportService.create(userId, nubank, null,
            sample("erro-ofx-truncado.ofx"), "127.0.0.1");

        Map<String, Object> row = awaitRow(created.id(), r -> "FAILED".equals(r.get("status")));
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("error_message")).isEqualTo(TransactionImportProcessor.TRUNCATED_MESSAGE);
        assertThat(row.get("bank_name")).isNull();
    }

    @Test
    void getOfAnotherUsersImportIs404() throws IOException {
        UUID nubank = account(userId, "Nubank");
        TransactionImportResponse created = transactionImportService.create(userId, nubank, null,
            sample("nubank-conta.ofx"), "127.0.0.1");
        UUID other = newUser();

        assertThatThrownBy(() -> transactionImportService.get(other, created.id()))
            .isInstanceOf(ResourceNotFoundException.class).hasMessage("Importação não encontrada");
    }

    @Test
    void atStartupEveryOldProcessingImportFailsWithOrWithoutBank() throws IOException {
        UUID nubank = account(userId, "Nubank");
        UUID old = insertProcessing(nubank, "1 day", null);
        UUID detected = insertProcessing(nubank, "1 day", "NUBANK");

        // the real trigger: the listener runs on ApplicationReadyEvent
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

        Map<String, Object> oldRow = row(old);
        assertThat(oldRow.get("status")).isEqualTo("FAILED");
        assertThat(oldRow.get("error_message")).isEqualTo("O processamento foi interrompido. Envie o arquivo de novo.");
        // PROCESSING means processing is running (decision 1A of Story 5.4): with a bank it is an orphan too
        assertThat(row(detected).get("status")).isEqualTo("FAILED");
        assertThat(row(detected).get("error_message")).isEqualTo(TransactionImportProcessor.INTERRUPTED_MESSAGE);
    }

    @Test
    void everyFiveMinutesProcessingImportsOlderThan15MinutesFail() throws IOException {
        UUID nubank = account(userId, "Nubank");
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
        // inserted after the startup sweep, so only the periodic one can fail them
        UUID stale = insertProcessing(nubank, "20 minutes", null);
        UUID recent = insertProcessing(nubank, "1 minute", null);
        UUID detected = insertProcessing(nubank, "20 minutes", "ITAU");

        transactionImportProcessor.failStaleImports();

        assertThat(row(stale).get("status")).isEqualTo("FAILED");
        assertThat(row(stale).get("error_message")).isEqualTo("O processamento foi interrompido. Envie o arquivo de novo.");
        assertThat(row(recent).get("status")).isEqualTo("PROCESSING");
        assertThat(row(detected).get("status")).isEqualTo("FAILED");
    }

    @Test
    void sweepIsScheduledAndProcessingRunsThroughTheAsyncProxy() throws NoSuchMethodException {
        assertThat(scheduledTaskHolder.getScheduledTasks())
            .anyMatch(task -> task.getTask().toString().endsWith("TransactionImportProcessor.failStaleImports"));
        // @Async only works through the proxy of a separate bean: a plain bean would run in the request thread
        assertThat(AopUtils.isAopProxy(transactionImportProcessor)).isTrue();
        assertThat(TransactionImportProcessor.class.getMethod("process", UUID.class, TransactionImport.FileType.class, byte[].class)
            .isAnnotationPresent(Async.class)).isTrue();
    }

    // Story 5.5

    @Test
    void sameFileTwiceSavesNothingTheSecondTimeAndCompletes() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID first = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");

        UUID second = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(second);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("total_transactions")).isEqualTo(10);
        assertThat(row.get("imported_transactions")).isEqualTo(0);
        assertThat(row.get("duplicates_found")).isEqualTo(10);
        assertThat(row.get("transfers_found")).isEqualTo(0);
        assertThat(transactionsOf(second)).isEmpty();
        assertThat(transactionImportService.get(userId, second).duplicatesFound()).isEqualTo(10);
        List<String> audits = jdbc.queryForList("SELECT metadata FROM audit_logs WHERE user_id = ? "
            + "AND event_type = 'import_duplicates_checked' AND ip_address IS NULL", String.class, userId);
        assertThat(audits).hasSize(2);
        assertThat(audits).filteredOn(m -> m.contains(second.toString())).singleElement().asString().isEqualTo("{\"import_id\":\"" + second + "\",\"total_transactions\":10,"
            + "\"imported_transactions\":0,\"duplicates_found\":10,\"transfers_found\":0,\"skipped_rows\":0,\"installments_found\":0,\"future_installments_created\":0,\"installments_confirmed\":0}");
    }

    @ParameterizedTest
    @CsvSource({"nubank-conta.ofx, nubank-conta.csv, 10", "nubank-conta.csv, nubank-conta.ofx, 10",
        "bb-conta.ofx, bb-conta.csv, 10", "bb-conta.csv, bb-conta.ofx, 10", "itau-conta.ofx, itau-conta.csv, 10",
        "itau-conta.csv, itau-conta.ofx, 10", "bradesco-conta.ofx, bradesco-conta.csv, 9",
        "bradesco-conta.csv, bradesco-conta.ofx, 9", "caixa-conta.ofx, caixa-conta.csv, 10",
        "caixa-conta.csv, caixa-conta.ofx, 10"})
    void ofxAndCsvOfTheSameMonthGiveOnlyDuplicates(String firstFile, String secondFile, int count) throws Exception {
        UUID target = account(userId, "Conta");
        UUID first = transactionImportService.create(userId, target, null, sample(firstFile), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");

        UUID second = transactionImportService.create(userId, target, null, sample(secondFile), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(second);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("duplicates_found")).isEqualTo(count);
        assertThat(row.get("imported_transactions")).isEqualTo(0);
        assertThat(transactionsOf(second)).isEmpty();
        assertThat(transactionsOf(first)).hasSize(count);
    }

    @Test
    void registeredTransferIsNotImportedAndIsCountedApart() throws Exception {
        UUID bb = account(userId, "BB");
        UUID nubank = account(userId, "Nubank");
        jdbc.update("INSERT INTO transfers (id, user_id, from_account_id, to_account_id, amount, transfer_date) "
            + "VALUES (?, ?, ?, ?, 2000.00, DATE '2026-10-01')", UUID.randomUUID(), userId, bb, nubank);

        UUID fromBb = transactionImportService.create(userId, bb, null, sample("bb-conta.ofx"), "127.0.0.1").id();
        UUID toNubank = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();

        Map<String, Object> bbRow = awaitDone(fromBb);
        assertThat(bbRow.get("status")).isEqualTo("REVIEW");
        assertThat(bbRow.get("transfers_found")).isEqualTo(1);
        assertThat(bbRow.get("duplicates_found")).isEqualTo(0);
        assertThat(bbRow.get("imported_transactions")).isEqualTo(9);
        assertThat(transactionsOf(fromBb)).noneMatch(t -> ((BigDecimal) t.get("amount")).compareTo(new BigDecimal("2000")) == 0);
        Map<String, Object> nubankRow = awaitDone(toNubank);
        assertThat(nubankRow.get("transfers_found")).isEqualTo(1);
        assertThat(nubankRow.get("imported_transactions")).isEqualTo(9);
        assertThat(transactionImportService.get(userId, fromBb).transfersFound()).isEqualTo(1);
    }

    @Test
    void removedTransactionDoesNotMakeADuplicateAndAnotherTargetIsIgnored() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID other = account(userId, "Outra");
        UUID first = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        awaitDone(first);
        jdbc.update("UPDATE transactions SET deleted_at = now() WHERE import_id = ? AND transaction_date = DATE '2026-10-01'",
            first);

        UUID again = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        UUID elsewhere = transactionImportService.create(userId, other, null, sample("nubank-conta.ofx"), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(again);
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("duplicates_found")).isEqualTo(9);
        assertThat(row.get("imported_transactions")).isEqualTo(1);
        assertThat(awaitDone(elsewhere).get("duplicates_found")).isEqualTo(0);
    }

    @Test
    void sameCardFileTwiceIsAllDuplicatesButAnotherCardIsNot() throws Exception {
        UUID itau = card(userId, "Itaú");
        UUID otherCard = card(userId, "Outro");
        UUID first = transactionImportService.create(userId, null, itau, sample("itau-cartao.ofx"), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");

        UUID again = transactionImportService.create(userId, null, itau, sample("itau-cartao.ofx"), "127.0.0.1").id();
        Map<String, Object> row = awaitDone(again);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("duplicates_found")).isEqualTo(row.get("total_transactions"));
        assertThat(row.get("imported_transactions")).isEqualTo(0);
        assertThat(transactionsOf(again)).isEmpty();

        UUID elsewhere = transactionImportService.create(userId, null, otherCard, sample("itau-cartao.ofx"),
            "127.0.0.1").id();
        Map<String, Object> otherRow = awaitDone(elsewhere);
        assertThat(otherRow.get("status")).isEqualTo("REVIEW");
        assertThat(otherRow.get("duplicates_found")).isEqualTo(0);
        assertThat(otherRow.get("imported_transactions")).isEqualTo(otherRow.get("total_transactions"));
    }

    @Test
    void removedTransferDoesNotAbsorbTheEntry() throws Exception {
        UUID bb = account(userId, "BB");
        UUID nubank = account(userId, "Nubank");
        jdbc.update("INSERT INTO transfers (id, user_id, from_account_id, to_account_id, amount, transfer_date, deleted_at) "
            + "VALUES (?, ?, ?, ?, 2000.00, DATE '2026-10-01', now())", UUID.randomUUID(), userId, bb, nubank);

        UUID fromBb = transactionImportService.create(userId, bb, null, sample("bb-conta.ofx"), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(fromBb);
        assertThat(row.get("transfers_found")).isEqualTo(0);
        assertThat(row.get("imported_transactions")).isEqualTo(10);
        assertThat(transactionsOf(fromBb)).anyMatch(t -> ((BigDecimal) t.get("amount")).compareTo(new BigDecimal("2000")) == 0);
    }

    @Test
    void twoImportsOfTheSameFileAtOnceSaveItOnlyOnce() throws Exception {
        UUID nubank = account(userId, "Nubank");

        UUID a = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        UUID b = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();

        awaitDone(a);
        awaitDone(b);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE account_id = ?", Long.class, nubank))
            .isEqualTo(10);
        assertThat(jdbc.queryForList("SELECT status FROM transaction_imports WHERE id IN (?, ?)", String.class, a, b))
            .containsExactlyInAnyOrder("REVIEW", "COMPLETED");
    }

    @Test
    void transfersFoundIsExportedAndChecked() throws Exception {
        UUID nubank = account(userId, "Nubank");
        awaitDone(transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id());

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("transaction_imports");
        assertThat(rows.get(0)).containsEntry("transfers_found", 0);
        assertThatThrownBy(() -> jdbc.update("UPDATE transaction_imports SET transfers_found = -1 WHERE user_id = ?", userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Story 5.7

    @Test
    void cardInstallmentsGetTheirGroupsAndFutureMonths() throws Exception {
        UUID nubank = card(userId, "Nubank");
        UUID created = transactionImportService.create(userId, null, nubank, sample("nubank-cartao.csv"), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(created);
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("imported_transactions")).isEqualTo(9);
        List<Map<String, Object>> installments = transactionsOf(created).stream()
            .filter(t -> Boolean.TRUE.equals(t.get("is_installment"))).toList();
        assertThat(installments).extracting(t -> t.get("description"), t -> t.get("installment_number"),
                t -> t.get("installment_total"))
            .containsExactly(org.assertj.core.groups.Tuple.tuple("Magazine Luiza - Parcela 3/10", 3, 10),
                org.assertj.core.groups.Tuple.tuple("Smart Fit - Parcela 2/12", 2, 12));
        List<Map<String, Object>> futures = futuresOf(created);
        assertThat(futures).hasSize(17).allSatisfy(t -> {
            assertThat(t.get("is_installment")).isEqualTo(true);
            assertThat(t.get("external_id")).isNull();
            assertThat(t.get("review_status")).isEqualTo("PENDING");
            assertThat(t.get("credit_card_id")).isEqualTo(nubank);
        });
        Object magazineGroup = installments.get(0).get("installment_group_id");
        assertThat(futures).filteredOn(t -> magazineGroup.equals(t.get("installment_group_id")))
            .extracting(t -> t.get("installment_number"), t -> t.get("transaction_date").toString(),
                t -> t.get("description"))
            .startsWith(org.assertj.core.groups.Tuple.tuple(4, "2026-11-02", "Magazine Luiza - Parcela 4/10"))
            .endsWith(org.assertj.core.groups.Tuple.tuple(10, "2027-05-02", "Magazine Luiza - Parcela 10/10"))
            .hasSize(7);
        assertThat(futures).filteredOn(t -> magazineGroup.equals(t.get("installment_group_id")))
            .allSatisfy(t -> assertThat((BigDecimal) t.get("amount")).isEqualByComparingTo("189.90"));
    }

    @Test
    void itauCardOfxHasFourInstallments() throws Exception {
        UUID itau = card(userId, "Itaú Visa");
        UUID created = transactionImportService.create(userId, null, itau, sample("itau-cartao.ofx"), "127.0.0.1").id();

        assertThat(awaitDone(created).get("status")).isEqualTo("REVIEW");
        assertThat(transactionsOf(created)).filteredOn(t -> Boolean.TRUE.equals(t.get("is_installment")))
            .extracting(t -> t.get("description"))
            .containsExactly("MAGAZINELUIZA 03/10", "CASASBAHIA 07/12", "MERCADOLIVRE*LOJA 01/03",
                "ANUIDADE DIFERENCIADA 05/12");
        assertThat(futuresOf(created)).hasSize(21);
        assertThat(futuresOf(created)).extracting(t -> t.get("original_description"))
            .contains("MAGAZINELUIZA  04/10", "CASASBAHIA     12/12");
    }

    @Test
    void accountImportHasNoInstallments() throws Exception {
        UUID itau = account(userId, "Itaú");
        UUID created = transactionImportService.create(userId, itau, null, sample("itau-conta.ofx"), "127.0.0.1").id();

        assertThat(awaitDone(created).get("status")).isEqualTo("REVIEW");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ? AND is_installment = TRUE",
            Long.class, userId)).isZero();
    }

    @Test
    void nextMonthConfirmsTheFutureInstallment() throws Exception {
        UUID nubank = card(userId, "Nubank");
        UUID first = transactionImportService.create(userId, null, nubank, sample("nubank-cartao.csv"), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");
        long rowsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId);
        MockMultipartFile november = new MockMultipartFile("file", "nov.csv", "text/csv",
            "date,title,amount\n2026-11-03,Magazine Luiza - Parcela 4/10,189.90\n2026-11-05,Padaria,12.00\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        UUID second = transactionImportService.create(userId, null, nubank, november, "127.0.0.1").id();

        Map<String, Object> row = awaitDone(second);
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("imported_transactions")).isEqualTo(1);
        assertThat(row.get("duplicates_found")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId))
            .isEqualTo(rowsBefore + 1);
        Map<String, Object> confirmed = jdbc.queryForMap("SELECT * FROM transactions WHERE user_id = ? "
            + "AND description = 'Magazine Luiza - Parcela 4/10'", userId);
        assertThat(confirmed.get("is_future_installment")).isEqualTo(false);
        assertThat(confirmed.get("import_id")).isEqualTo(first);
        assertThat(confirmed.get("transaction_date").toString()).isEqualTo("2026-11-03");
        assertThat(confirmed.get("external_id")).isNull(); // the Nubank card CSV has no id: kept as the file brings it
        assertThat(futuresOf(second)).isEmpty();
        assertThat(jdbc.queryForList("SELECT metadata FROM audit_logs WHERE user_id = ? "
            + "AND event_type = 'import_duplicates_checked' AND metadata LIKE ?",
            String.class, userId, "%" + second + "%")).singleElement().asString()
            .contains("\"installments_found\":1,\"future_installments_created\":0,\"installments_confirmed\":1");
    }

    @Test
    void cardFileWithOnlyConfirmationsCompletes() throws Exception {
        UUID nubank = card(userId, "Nubank");
        UUID first = transactionImportService.create(userId, null, nubank, sample("nubank-cartao.csv"), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");
        long rowsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId);
        MockMultipartFile november = new MockMultipartFile("file", "nov.csv", "text/csv",
            "date,title,amount\n2026-11-03,Magazine Luiza - Parcela 4/10,189.90\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        UUID second = transactionImportService.create(userId, null, nubank, november, "127.0.0.1").id();

        Map<String, Object> row = awaitDone(second);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("imported_transactions")).isEqualTo(0);
        assertThat(row.get("duplicates_found")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId))
            .isEqualTo(rowsBefore);
        Map<String, Object> confirmed = jdbc.queryForMap("SELECT * FROM transactions WHERE user_id = ? "
            + "AND description = 'Magazine Luiza - Parcela 4/10'", userId);
        assertThat(confirmed.get("is_future_installment")).isEqualTo(false);
        assertThat(confirmed.get("transaction_date").toString()).isEqualTo("2026-11-03");
    }

    @Test
    void sameCardFileAgainCreatesNoFutures() throws Exception {
        UUID itau = card(userId, "Itaú Visa");
        UUID first = transactionImportService.create(userId, null, itau, sample("itau-cartao.ofx"), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");

        UUID second = transactionImportService.create(userId, null, itau, sample("itau-cartao.ofx"), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(second);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("duplicates_found")).isEqualTo(8);
        assertThat(futuresOf(second)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId))
            .isEqualTo(29L);
    }

    @Test
    void confirmationKeepsTheOfxIdAndDescriptionsAndTheSameFileAgainChangesNothing() throws Exception {
        UUID itau = card(userId, "Itaú Visa");
        UUID first = transactionImportService.create(userId, null, itau, sample("itau-cartao.ofx"), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");
        long rowsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId);
        UUID future = jdbc.queryForObject("SELECT id FROM transactions WHERE user_id = ? "
            + "AND original_description = 'MAGAZINELUIZA  04/10'", UUID.class, userId);

        UUID second = transactionImportService.create(userId, null, itau, novemberOfx(), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(second);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("duplicates_found")).isEqualTo(1);
        Map<String, Object> confirmed = jdbc.queryForMap("SELECT * FROM transactions WHERE id = ?", future);
        assertThat(confirmed.get("is_future_installment")).isEqualTo(false);
        assertThat(confirmed.get("external_id")).isEqualTo("20261103000001");
        assertThat(confirmed.get("description")).isEqualTo("MAGAZINE LUIZA 04/10");
        assertThat(confirmed.get("original_description")).isEqualTo("MAGAZINE  LUIZA  04/10");
        assertThat(confirmed.get("import_id")).isEqualTo(first);

        UUID third = transactionImportService.create(userId, null, itau, novemberOfx(), "127.0.0.1").id();

        Map<String, Object> again = awaitDone(third);
        assertThat(again.get("status")).isEqualTo("COMPLETED");
        assertThat(again.get("duplicates_found")).isEqualTo(1);
        assertThat(futuresOf(third)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId))
            .isEqualTo(rowsBefore);
        assertThat(jdbc.queryForList("SELECT metadata FROM audit_logs WHERE user_id = ? "
            + "AND event_type = 'import_duplicates_checked' AND metadata LIKE ?",
            String.class, userId, "%" + third + "%")).singleElement().asString()
            .contains("\"installments_found\":0,\"future_installments_created\":0,\"installments_confirmed\":0");
    }

    @Test
    void sameFileWithoutIdAfterTheConfirmationIsADuplicate() throws Exception {
        UUID nubank = card(userId, "Nubank");
        UUID first = transactionImportService.create(userId, null, nubank, sample("nubank-cartao.csv"), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");
        byte[] csv = "date,title,amount\n2026-11-03,Magazine Luiza - Parcela 4/10,189.90\n"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        UUID second = transactionImportService.create(userId, null, nubank,
            new MockMultipartFile("file", "nov.csv", "text/csv", csv), "127.0.0.1").id();
        assertThat(awaitDone(second).get("status")).isEqualTo("COMPLETED");
        long rowsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId);

        UUID third = transactionImportService.create(userId, null, nubank,
            new MockMultipartFile("file", "nov.csv", "text/csv", csv), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(third);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("imported_transactions")).isEqualTo(0);
        assertThat(row.get("duplicates_found")).isEqualTo(1);
        assertThat(futuresOf(third)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId))
            .isEqualTo(rowsBefore);
    }

    /** The Itaú card statement with a single entry: the next month of its first installment, with an OFX id. */
    private static MockMultipartFile novemberOfx() throws IOException {
        Charset charset = Charset.forName("windows-1252");
        String ofx = new String(sample("itau-cartao.ofx").getBytes(), charset);
        int start = ofx.indexOf("<STMTTRN>");
        int end = ofx.lastIndexOf("</STMTTRN>") + "</STMTTRN>".length();
        String entry = "<STMTTRN>\n<TRNTYPE>DEBIT\n<DTPOSTED>20261103000000[-3:BRT]\n<TRNAMT>-189.90\n"
            + "<FITID>20261103000001\n<MEMO>MAGAZINE  LUIZA  04/10\n</STMTTRN>";
        return new MockMultipartFile("file", "nov.ofx", "application/octet-stream",
            (ofx.substring(0, start) + entry + ofx.substring(end)).getBytes(charset));
    }

    @Test
    void installmentOnAnotherCardDoesNotConfirmTheFirstCardsFuture() throws Exception {
        UUID nubank = card(userId, "Nubank");
        UUID other = card(userId, "Nubank Virtual");
        UUID first = transactionImportService.create(userId, null, nubank, sample("nubank-cartao.csv"), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");
        MockMultipartFile november = new MockMultipartFile("file", "nov.csv", "text/csv",
            "date,title,amount\n2026-11-03,Magazine Luiza - Parcela 4/10,189.90\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        UUID second = transactionImportService.create(userId, null, other, november, "127.0.0.1").id();

        Map<String, Object> row = awaitDone(second);
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("imported_transactions")).isEqualTo(1);
        assertThat(row.get("duplicates_found")).isEqualTo(0);
        assertThat(futuresOf(first)).hasSize(17);
        assertThat(futuresOf(second)).hasSize(6).allSatisfy(t -> assertThat(t.get("credit_card_id")).isEqualTo(other));
        assertThat(transactionsOf(second)).singleElement().satisfies(t -> {
            assertThat(t.get("credit_card_id")).isEqualTo(other);
            assertThat(t.get("installment_group_id")).isNotIn(futuresOf(first).stream()
                .map(future -> future.get("installment_group_id")).toList());
        });
    }

    @Test
    void deletedFutureIsNotConfirmed() throws Exception {
        UUID nubank = card(userId, "Nubank");
        UUID first = transactionImportService.create(userId, null, nubank, sample("nubank-cartao.csv"), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");
        UUID deleted = jdbc.queryForObject("SELECT id FROM transactions WHERE user_id = ? "
            + "AND description = 'Magazine Luiza - Parcela 4/10'", UUID.class, userId);
        jdbc.update("UPDATE transactions SET deleted_at = NOW() WHERE id = ?", deleted);
        MockMultipartFile november = new MockMultipartFile("file", "nov.csv", "text/csv",
            "date,title,amount\n2026-11-03,Magazine Luiza - Parcela 4/10,189.90\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        UUID second = transactionImportService.create(userId, null, nubank, november, "127.0.0.1").id();

        Map<String, Object> row = awaitDone(second);
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("imported_transactions")).isEqualTo(1);
        assertThat(row.get("duplicates_found")).isEqualTo(0);
        Map<String, Object> stillDeleted = jdbc.queryForMap("SELECT * FROM transactions WHERE id = ?", deleted);
        assertThat(stillDeleted.get("is_future_installment")).isEqualTo(true);
        assertThat(stillDeleted.get("transaction_date").toString()).isEqualTo("2026-11-02");
        // the free number joins the group of the purchase; 5..10 already exist there
        assertThat(futuresOf(second)).isEmpty();
        assertThat(transactionsOf(second)).singleElement().satisfies(t -> {
            assertThat(t.get("installment_number")).isEqualTo(4);
            assertThat(t.get("installment_group_id")).isEqualTo(stillDeleted.get("installment_group_id"));
        });
    }

    // Story 5.8

    /** A group of the user with one child. Returns the child. */
    private UUID childCategory(UUID owner, String name) {
        UUID group = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        String insert = "INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)";
        jdbc.update(insert, group, owner, "Grupo de " + name, null);
        jdbc.update(insert, child, owner, name, group);
        return child;
    }

    private void everyItemGetsTheFirstCategory(int confidence) {
        Mockito.when(classifier.classify(any(), any())).thenAnswer(invocation -> {
            List<TransactionClassifier.Item> items = invocation.getArgument(0);
            return items.stream()
                .map(item -> new TransactionClassifier.Result(item.index(), 1, confidence))
                .toList();
        });
    }

    /** doAnswer: stubbing again with when() would run the previous answer. */
    private void everyItemGets(String categoryName, int confidence) {
        Mockito.doAnswer(invocation -> {
            List<TransactionClassifier.Item> items = invocation.getArgument(0);
            List<TransactionClassifier.CategoryChoice> categories = invocation.getArgument(1);
            int category = categories.stream().filter(c -> c.name().equals(categoryName)).findFirst().orElseThrow().id();
            return items.stream()
                .map(item -> new TransactionClassifier.Result(item.index(), category, confidence))
                .toList();
        }).when(classifier).classify(any(), any());
    }

    @Test
    void theResponseCarriesTheClassificationProgress() {
        UUID importId = insertProcessing(account(userId, "Nubank"), "0 seconds", "NUBANK");
        transactionImportRepository.startClassification(importId, 9, java.time.OffsetDateTime.now());
        transactionImportRepository.addClassified(importId, 4, java.time.OffsetDateTime.now());

        TransactionImportResponse response = transactionImportService.get(userId, importId);

        assertThat(response.classificationTotal()).isEqualTo(9);
        assertThat(response.classifiedCount()).isEqualTo(4);
    }

    @Test
    void anotherImportOfThePurchaseLeavesTheClassificationOfTheFirstAlone() throws Exception {
        UUID casa = childCategory(userId, "Casa");
        UUID lazer = childCategory(userId, "Lazer");
        UUID nubank = card(userId, "Nubank");
        everyItemGets("Casa", 88);
        UUID first = transactionImportService.create(userId, null, nubank, sample("nubank-cartao.csv"), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");
        // parcel 2 is new in the group of the first import, so its classification reaches the futures query; 4 confirms
        MockMultipartFile second = new MockMultipartFile("file", "outro.csv", "text/csv",
            ("date,title,amount\n2026-09-02,Magazine Luiza - Parcela 2/10,189.90\n"
                + "2026-11-03,Magazine Luiza - Parcela 4/10,189.90\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        everyItemGets("Lazer", 55);

        UUID other = transactionImportService.create(userId, null, nubank, second, "127.0.0.1").id();

        assertThat(awaitDone(other).get("status")).isEqualTo("REVIEW");
        String byDescription = "SELECT * FROM transactions WHERE user_id = ? AND description = ?";
        Map<String, Object> confirmed = jdbc.queryForMap(byDescription, userId, "Magazine Luiza - Parcela 4/10");
        UUID group = (UUID) confirmed.get("installment_group_id");
        assertThat(transactionsOf(other)).singleElement().satisfies(t -> {
            assertThat(t.get("installment_group_id")).isEqualTo(group);
            assertThat(t.get("category_id")).isEqualTo(lazer);
            assertThat(t.get("classification_confidence")).isEqualTo(55);
        });
        assertThat(confirmed.get("import_id")).isEqualTo(first);
        assertThat(confirmed.get("is_future_installment")).isEqualTo(false);
        assertThat(confirmed.get("category_id")).isEqualTo(casa);
        assertThat(confirmed.get("classification_confidence")).isEqualTo(88);
        assertThat(futuresOf(first)).filteredOn(t -> group.equals(t.get("installment_group_id"))).hasSize(6)
            .allSatisfy(t -> {
                assertThat(t.get("category_id")).isEqualTo(casa);
                assertThat(t.get("classification_confidence")).isEqualTo(88);
            });

        // an answer of the first import arriving now: only what is still future changes
        transactionTemplate.executeWithoutResult(status -> transactionRepository.applyAiClassificationToFutures(first,
            group, lazer, 40, java.time.OffsetDateTime.now()));

        assertThat(futuresOf(first)).filteredOn(t -> group.equals(t.get("installment_group_id"))).hasSize(6)
            .allSatisfy(t -> assertThat(t.get("classification_confidence")).isEqualTo(40));
        for (String description : List.of("Magazine Luiza - Parcela 3/10", "Magazine Luiza - Parcela 4/10")) {
            Map<String, Object> kept = jdbc.queryForMap(byDescription, userId, description);
            assertThat(kept.get("category_id")).isEqualTo(casa);
            assertThat(kept.get("classification_confidence")).isEqualTo(88);
        }
    }

    @Test
    void cardImportIsClassifiedAndTheFuturesInheritBeforeReview() throws Exception {
        UUID category = childCategory(userId, "Casa");
        UUID itau = card(userId, "Itaú Visa");
        everyItemGetsTheFirstCategory(88);

        UUID created = transactionImportService.create(userId, null, itau, sample("itau-cartao.ofx"), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(created);
        assertThat(row.get("status")).isEqualTo("REVIEW");
        List<Map<String, Object>> rows = transactionsOf(created);
        List<Map<String, Object>> regular = rows.stream().filter(t -> "REGULAR".equals(t.get("kind"))).toList();
        List<Map<String, Object>> payments = rows.stream().filter(t -> "CARD_PAYMENT".equals(t.get("kind"))).toList();
        assertThat(payments).isNotEmpty().allSatisfy(t -> {
            assertThat(t.get("category_id")).isNull();
            assertThat(t.get("classification_source")).isEqualTo("NONE");
            assertThat(t.get("classification_confidence")).isNull();
        });
        assertThat(regular).isNotEmpty().allSatisfy(t -> {
            assertThat(t.get("category_id")).isEqualTo(category);
            assertThat(t.get("classification_source")).isEqualTo("AI");
            assertThat(t.get("classification_confidence")).isEqualTo(88);
        });
        assertThat(row.get("classification_total")).isEqualTo(regular.size());
        assertThat(row.get("classified_count")).isEqualTo(regular.size());
        assertThat(futuresOf(created)).hasSize(21).allSatisfy(t -> {
            assertThat(t.get("category_id")).isEqualTo(category);
            assertThat(t.get("classification_source")).isEqualTo("AI");
            assertThat(t.get("classification_confidence")).isEqualTo(88);
        });
        // only the rows the AI answered: neither the payments nor the futures
        assertThat(aiClassificationHistoryRepository.findByUserIdOrdered(userId)).hasSize(regular.size());
    }

    @Test
    void withTheAiDownTheImportStillReachesReviewAndNoRowIsLost() throws Exception {
        childCategory(userId, "Casa");
        UUID nubank = account(userId, "Nubank");
        Mockito.when(classifier.classify(any(), any())).thenThrow(new IllegalStateException("boom"));

        UUID created = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(created);
        assertThat(row.get("status")).isEqualTo("REVIEW");
        // 10 rows, one of them a card payment that does not go to the AI
        assertThat(row.get("classification_total")).isEqualTo(9);
        assertThat(row.get("classified_count")).isEqualTo(9);
        assertThat(transactionsOf(created)).hasSize(10).allSatisfy(t -> {
            assertThat(t.get("category_id")).isNull();
            assertThat(t.get("classification_source")).isEqualTo("NONE");
            assertThat(t.get("classification_confidence")).isEqualTo("REGULAR".equals(t.get("kind")) ? 0 : null);
        });
        assertThat(aiClassificationHistoryRepository.findByUserIdOrdered(userId)).isEmpty();
    }

    @Test
    void withoutChildCategoriesTheAiIsNotCalled() throws Exception {
        UUID nubank = account(userId, "Nubank");

        UUID created = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(created);
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("classification_total")).isEqualTo(0);
        Mockito.verifyNoInteractions(classifier);
    }

    @Test
    void anotherImportOfTheSameTargetDoesNotWaitForTheAi() throws Exception {
        childCategory(userId, "Casa");
        UUID nubank = account(userId, "Nubank");
        CountDownLatch classifying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Mockito.when(classifier.classify(any(), any())).thenAnswer(invocation -> {
            classifying.countDown();
            release.await(30, TimeUnit.SECONDS);
            return List.of();
        });
        try {
            UUID first = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
            assertThat(classifying.await(10, TimeUnit.SECONDS)).isTrue();

            UUID second = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();

            // the first one is still in the AI call: the second took the lock and saw its rows as duplicates
            assertThat(awaitDone(second).get("status")).isEqualTo("COMPLETED");
            Map<String, Object> running = row(first);
            assertThat(running.get("status")).isEqualTo("PROCESSING");
            assertThat(running.get("classification_total")).isEqualTo(9);
            assertThat(running.get("classified_count")).isEqualTo(0);
            release.countDown();
            assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");
        } finally {
            release.countDown();
        }
    }

    @Test
    void orphanWithSavedRowsGoesToReviewAndOneWithoutRowsFails() throws Exception {
        UUID nubank = account(userId, "Nubank");
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
        UUID withRows = insertProcessing(nubank, "20 minutes", "NUBANK");
        jdbc.update("UPDATE transaction_imports SET imported_transactions = 3, classification_total = 3 WHERE id = ?",
            withRows);
        UUID withoutRows = insertProcessing(nubank, "20 minutes", "NUBANK");

        transactionImportProcessor.failStaleImports();

        assertThat(row(withRows).get("status")).isEqualTo("REVIEW");
        assertThat(row(withRows).get("error_message")).isNull();
        assertThat(row(withoutRows).get("status")).isEqualTo("FAILED");
    }

    @Test
    void importAtStartupWithSavedRowsGoesToReview() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID withRows = insertProcessing(nubank, "1 day", "NUBANK");
        jdbc.update("UPDATE transaction_imports SET imported_transactions = 3 WHERE id = ?", withRows);

        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

        assertThat(row(withRows).get("status")).isEqualTo("REVIEW");
    }

    @Test
    void longImportWithFreshBatchesIsNotSwept() throws Exception {
        UUID nubank = account(userId, "Nubank");
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
        UUID running = insertProcessing(nubank, "1 hour", "NUBANK");
        jdbc.update("UPDATE transaction_imports SET classification_total = 3 WHERE id = ?", running);
        assertThat(transactionImportRepository.addClassified(running, 1, java.time.OffsetDateTime.now())).isEqualTo(1);

        transactionImportProcessor.failStaleImports();

        assertThat(row(running).get("status")).isEqualTo("PROCESSING");
        assertThat(row(running).get("classified_count")).isEqualTo(1);
    }

    @Test
    void historyIsPurgedBeforeTheTransactionsAndExportedWithEveryColumn() throws Exception {
        childCategory(userId, "Casa");
        UUID nubank = account(userId, "Nubank");
        everyItemGetsTheFirstCategory(92);
        UUID created = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        assertThat(awaitDone(created).get("status")).isEqualTo("REVIEW");

        @SuppressWarnings("unchecked")
        var exported = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("ai_classification_history");
        assertThat(exported).hasSize(9);  // the card payment does not go to the AI
        assertThat(exported.get(0)).containsOnlyKeys("id", "user_id", "transaction_id", "predicted_category_id",
            "confidence", "was_corrected", "corrected_category_id", "created_at");

        // the FK from the history blocks deleting the transactions first
        assertThatThrownBy(() -> jdbc.update("DELETE FROM transactions WHERE user_id = ?", userId))
            .isInstanceOf(DataIntegrityViolationException.class);
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(aiClassificationHistoryRepository.deleteByUserId(userId)).isEqualTo(9);
            assertThat(transactionRepository.deleteByUserId(userId)).isEqualTo(10);
        });
    }

    @Test
    void progressCannotPassTheTotal() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID running = insertProcessing(nubank, "0 seconds", "NUBANK");

        assertThatThrownBy(() -> transactionImportRepository.addClassified(running, 1, java.time.OffsetDateTime.now()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long activeRows(UUID owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ? AND deleted_at IS NULL", Long.class,
            owner);
    }

    /** Parcela 4/10 of the purchase in nubank-cartao.csv plus, when {@code extra}, a purchase of its own. */
    private static MockMultipartFile novemberCsv(boolean extra) {
        String content = "date,title,amount\n2026-11-03,Magazine Luiza - Parcela 4/10,189.90\n"
            + (extra ? "2026-11-04,Padaria do Bairro,12.50\n" : "");
        return new MockMultipartFile("file", "nov.csv", "text/csv", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Map<String, Object> fourthInstallment() {
        return jdbc.queryForMap("SELECT * FROM transactions WHERE user_id = ? AND deleted_at IS NULL "
            + "AND description = 'Magazine Luiza - Parcela 4/10'", userId);
    }

    /** Story 5.9: the discarded rows are no duplicates, so the same file comes in again, and only once. */
    @Test
    void discardedFileCanBeSentAgainWithoutDuplicates() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID first = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        Map<String, Object> reviewed = awaitDone(first);
        assertThat(reviewed.get("status")).isEqualTo("REVIEW");
        long rows = activeRows(userId);

        reviewService.discard(userId, first, "127.0.0.1");

        assertThat(activeRows(userId)).isZero();
        assertThat(transactionImportService.get(userId, first).status()).isEqualTo("DISCARDED");

        UUID second = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();

        Map<String, Object> again = awaitDone(second);
        assertThat(again.get("status")).isEqualTo("REVIEW");
        assertThat(again.get("imported_transactions")).isEqualTo(reviewed.get("imported_transactions"));
        assertThat(again.get("duplicates_found")).isEqualTo(0);
        assertThat(activeRows(userId)).isEqualTo(rows);
    }

    /** Story 5.9: a discarded card file takes its future installments with it, and the same file creates them again. */
    @Test
    void discardedCardFileTakesItsFuturesAndComesBackWhole() throws Exception {
        UUID nubank = card(userId, "Nubank");
        UUID first = transactionImportService.create(userId, null, nubank, sample("nubank-cartao.csv"), "127.0.0.1").id();
        assertThat(awaitDone(first).get("status")).isEqualTo("REVIEW");
        long rows = activeRows(userId);
        int futures = futuresOf(first).size();
        assertThat(futures).isPositive();

        reviewService.discard(userId, first, "127.0.0.1");

        assertThat(activeRows(userId)).isZero();

        UUID second = transactionImportService.create(userId, null, nubank, sample("nubank-cartao.csv"), "127.0.0.1").id();

        assertThat(awaitDone(second).get("duplicates_found")).isEqualTo(0);
        assertThat(futuresOf(second)).hasSize(futures);
        assertThat(activeRows(userId)).isEqualTo(rows);
    }

    /** Story 5.9, decision 3A: the real pipeline writes the mark, and the confirmed row survives its creator. */
    @Test
    void discardingTheCreatorLeavesTheConfirmedInstallmentWithTheCompletedConfirmer() throws Exception {
        UUID nubank = card(userId, "Nubank");
        UUID creator = transactionImportService.create(userId, null, nubank, sample("nubank-cartao.csv"), "127.0.0.1").id();
        assertThat(awaitDone(creator).get("status")).isEqualTo("REVIEW");
        UUID confirmer = transactionImportService.create(userId, null, nubank, novemberCsv(false), "127.0.0.1").id();
        assertThat(awaitDone(confirmer).get("status")).isEqualTo("COMPLETED");
        assertThat(fourthInstallment().get("confirmed_by_import_id")).isEqualTo(confirmer);

        reviewService.discard(userId, creator, "127.0.0.1");

        Map<String, Object> confirmed = fourthInstallment();
        assertThat(confirmed.get("import_id")).isEqualTo(confirmer);
        assertThat(confirmed.get("review_status")).isEqualTo("CONFIRMED");
        assertThat(activeRows(userId)).isEqualTo(1);
    }

    /** Story 5.9, decision 3A: discarding the confirmer gives the future back, and the same file confirms it again. */
    @Test
    void discardingTheConfirmerGivesTheFutureBackAndTheFileConfirmsItAgain() throws Exception {
        UUID nubank = card(userId, "Nubank");
        UUID creator = transactionImportService.create(userId, null, nubank, sample("nubank-cartao.csv"), "127.0.0.1").id();
        assertThat(awaitDone(creator).get("status")).isEqualTo("REVIEW");
        long rows = activeRows(userId);
        UUID confirmer = transactionImportService.create(userId, null, nubank, novemberCsv(true), "127.0.0.1").id();
        assertThat(awaitDone(confirmer).get("status")).isEqualTo("REVIEW");

        reviewService.discard(userId, confirmer, "127.0.0.1");

        Map<String, Object> reverted = fourthInstallment();
        assertThat(reverted.get("is_future_installment")).isEqualTo(true);
        assertThat(reverted.get("import_id")).isEqualTo(creator);
        assertThat(reverted.get("confirmed_by_import_id")).isNull();
        assertThat(reverted.get("transaction_date").toString()).isEqualTo("2026-11-03");
        assertThat(activeRows(userId)).isEqualTo(rows);

        UUID again = transactionImportService.create(userId, null, nubank, novemberCsv(true), "127.0.0.1").id();

        Map<String, Object> row = awaitDone(again);
        assertThat(row.get("status")).isEqualTo("REVIEW");
        assertThat(row.get("imported_transactions")).isEqualTo(1);
        assertThat(row.get("duplicates_found")).isEqualTo(1);
        Map<String, Object> confirmed = fourthInstallment();
        assertThat(confirmed.get("is_future_installment")).isEqualTo(false);
        assertThat(confirmed.get("import_id")).isEqualTo(creator);
        assertThat(confirmed.get("confirmed_by_import_id")).isEqualTo(again);
        assertThat(activeRows(userId)).isEqualTo(rows + 1);
    }

    /** Runs {@code lock} in a transaction of its own and returns with it held; commits after {@code release}. */
    private CompletableFuture<Void> holding(Runnable lock, CountDownLatch release, Runnable beforeCommit)
        throws InterruptedException {
        CountDownLatch held = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(status -> {
            lock.run();
            held.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            beforeCommit.run();
        }));
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        return holder;
    }

    /** Story 5.9: a file of the same target being saved holds the advisory lock, and the discard waits for it. */
    @Test
    void discardWaitsForTheAdvisoryLockOfTheTarget() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID id = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        assertThat(awaitDone(id).get("status")).isEqualTo("REVIEW");
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<Void> holder = holding(
                () -> transactionImportRepository.lockTarget(TransactionImportProcessor.lockKey(nubank)), release, () -> { });

            CompletableFuture<Void> discard = CompletableFuture.runAsync(() -> reviewService.discard(userId, id, "127.0.0.1"));

            assertThatThrownBy(() -> discard.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(row(id).get("status")).isEqualTo("REVIEW");
            release.countDown();
            discard.get(10, TimeUnit.SECONDS);
            holder.get(10, TimeUnit.SECONDS);
            assertThat(row(id).get("status")).isEqualTo("DISCARDED");
        } finally {
            release.countDown();
        }
    }

    /** Story 5.9: the approval waits for the import row and reads the status left there, here a discard: 409. */
    @Test
    void approvalWaitsForTheImportRowAndSeesTheStatusLeftThere() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID id = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        assertThat(awaitDone(id).get("status")).isEqualTo("REVIEW");
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<Void> holder = holding(
                () -> jdbc.queryForList("SELECT id FROM transaction_imports WHERE id = ? FOR UPDATE", id), release,
                () -> jdbc.update("UPDATE transaction_imports SET status = 'DISCARDED' WHERE id = ?", id));

            CompletableFuture<Void> approval = CompletableFuture.runAsync(
                () -> reviewService.approveHighConfidence(userId, id, "127.0.0.1"));

            assertThatThrownBy(() -> approval.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThatThrownBy(() -> approval.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ConflictException.class);
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
    }

    /** Story 5.10: a file of the same target being saved holds the advisory lock, and the conclusion waits for it. */
    @Test
    void completeWaitsForTheAdvisoryLockOfTheTarget() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID id = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        assertThat(awaitDone(id).get("status")).isEqualTo("REVIEW");
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<Void> holder = holding(
                () -> transactionImportRepository.lockTarget(TransactionImportProcessor.lockKey(nubank)), release, () -> { });

            CompletableFuture<Void> complete = CompletableFuture.runAsync(() -> reviewService.complete(userId, id, "127.0.0.1"));

            assertThatThrownBy(() -> complete.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(row(id).get("status")).isEqualTo("REVIEW");
            release.countDown();
            complete.get(10, TimeUnit.SECONDS);
            holder.get(10, TimeUnit.SECONDS);
            assertThat(row(id).get("status")).isEqualTo("COMPLETED");
        } finally {
            release.countDown();
        }
    }

    /** Story 5.10: the category change waits for the import row and reads the status left there, here a discard: 409. */
    @Test
    void categoryChangeWaitsForTheImportRowAndSeesTheStatusLeftThere() throws Exception {
        UUID nubank = account(userId, "Nubank");
        UUID id = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        assertThat(awaitDone(id).get("status")).isEqualTo("REVIEW");
        UUID transaction = jdbc.queryForObject("SELECT id FROM transactions WHERE import_id = ? AND kind = 'REGULAR' "
            + "LIMIT 1", UUID.class, id);
        UpdateImportCategoryRequest request = new UpdateImportCategoryRequest(List.of(transaction),
            childCategory(userId, "Mercado"));
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<Void> holder = holding(
                () -> jdbc.queryForList("SELECT id FROM transaction_imports WHERE id = ? FOR UPDATE", id), release,
                () -> jdbc.update("UPDATE transaction_imports SET status = 'DISCARDED' WHERE id = ?", id));

            CompletableFuture<Void> change = CompletableFuture.runAsync(
                () -> reviewService.updateCategory(userId, id, request, "127.0.0.1"));

            assertThatThrownBy(() -> change.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThatThrownBy(() -> change.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ConflictException.class);
            holder.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT classification_source FROM transactions WHERE id = ?", String.class,
                transaction)).isNotEqualTo("USER");
        } finally {
            release.countDown();
        }
    }

    private UUID insertProcessing(UUID accountId, String age, String bank) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transaction_imports (id, user_id, account_id, file_name, file_type, file_size_kb, "
            + "bank_name, status, created_at, updated_at) "
            + "VALUES (?, ?, ?, 'x.ofx', 'OFX', 1, ?, 'PROCESSING', now() - ?::interval, now() - ?::interval)",
            id, userId, accountId, bank, age, age);
        return id;
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT * FROM transaction_imports WHERE id = ?", id);
    }
}
