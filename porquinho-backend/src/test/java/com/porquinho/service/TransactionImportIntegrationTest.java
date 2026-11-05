package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.CreateCreditCardRequest;
import com.porquinho.dto.TransactionImportResponse;
import com.porquinho.entity.TransactionImport;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/**
 * Stories 5.1, 5.3 and 5.4: uploads against the real schema (PostgreSQL via Testcontainers, Flyway V25/V26): the row
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
            jdbc.update("DELETE FROM transactions WHERE user_id = ?", id);
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

    private List<Map<String, Object>> transactionsOf(UUID importId) {
        return jdbc.queryForList("SELECT * FROM transactions WHERE import_id = ? ORDER BY transaction_date, external_id",
            importId);
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
            assertThat(transactionRepository.deleteByUserId(userId)).isEqualTo(18);
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
            assertThat(t.get("is_pix")).isEqualTo(false);
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

    @Test
    void uploadedCsvGetsItsBankAndTheSameTransactionsAsTheOfx() throws Exception {
        UUID nubank = account(userId, "Nubank");

        TransactionImportResponse fromCsv = transactionImportService.create(userId, nubank, null,
            sample("nubank-conta.csv"), "127.0.0.1");
        TransactionImportResponse fromOfx = transactionImportService.create(userId, nubank, null,
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
            transactionManager, Clock.systemDefaultZone());

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
        jdbc.update("UPDATE transaction_imports SET status = 'FAILED', error_message = ? WHERE id = ?",
            TransactionImportProcessor.INTERRUPTED_MESSAGE, importId);
        TransactionImportProcessor processor = new TransactionImportProcessor(transactionImportRepository,
            transactionRepository, transactionManager, Clock.systemDefaultZone());

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx").getBytes());  // not the proxy: runs right here
        // the guard of the final update itself, against the real database
        assertThat(transactionImportRepository.markReadyForReview(importId, 10, 10, 0, java.time.OffsetDateTime.now()))
            .isZero();

        Map<String, Object> row = row(importId);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("error_message")).isEqualTo(TransactionImportProcessor.INTERRUPTED_MESSAGE);
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

    private UUID insertProcessing(UUID accountId, String age, String bank) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transaction_imports (id, user_id, account_id, file_name, file_type, file_size_kb, "
            + "bank_name, status, created_at, updated_at) "
            + "VALUES (?, ?, ?, 'x.ofx', 'OFX', 1, ?, 'PROCESSING', now() - ?::interval, now())",
            id, userId, accountId, bank, age);
        return id;
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT * FROM transaction_imports WHERE id = ?", id);
    }
}
