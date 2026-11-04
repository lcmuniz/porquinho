package com.porquinho.service;

import com.porquinho.dto.TransactionImportResponse;
import com.porquinho.entity.Account;
import com.porquinho.entity.CreditCard;
import com.porquinho.entity.TransactionImport;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.TransactionImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Story 5.1: the whole I/O matrix of the upload with the fictitious samples of src/test/resources/import-samples.
 */
@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    @Mock
    private TransactionImportRepository transactionImportRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private TransactionImportService service;
    private UUID userId;
    private UUID accountId;
    private UUID cardId;

    @BeforeEach
    void setUp() {
        service = new TransactionImportService(transactionImportRepository, accountRepository, creditCardRepository,
            auditLogService, transactionManager);
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        cardId = UUID.randomUUID();
    }

    private static MockMultipartFile sample(String name) throws IOException {
        byte[] bytes = new ClassPathResource("import-samples/" + name).getInputStream().readAllBytes();
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content.getBytes(StandardCharsets.UTF_8));
    }

    private void accountExists() {
        Account account = new Account();
        account.setId(accountId);
        account.setAccountName("Nubank");
        when(accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)).thenReturn(Optional.of(account));
    }

    private void cardExists() {
        CreditCard card = new CreditCard();
        card.setId(cardId);
        card.setCardName("Itaú Visa");
        when(creditCardRepository.findByIdAndUserIdAndDeletedAtIsNull(cardId, userId)).thenReturn(Optional.of(card));
    }

    private void saveAssignsId() {
        when(transactionImportRepository.save(any(TransactionImport.class))).thenAnswer(inv -> {
            TransactionImport saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(OffsetDateTime.now());
            return saved;
        });
    }

    private void assertRejected(UUID account, UUID card, MultipartFile upload, String message) {
        assertThatThrownBy(() -> service.create(userId, account, card, upload, "127.0.0.1"))
            .isInstanceOf(BadRequestException.class)
            .hasMessage(message);
        verify(transactionImportRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nubank-conta.ofx", "bb-conta.ofx", "itau-conta.ofx", "bradesco-conta.ofx", "caixa-conta.ofx"})
    void accountStatementOfEachBankToAnAccountIsRecordedAsProcessing(String name) throws IOException {
        accountExists();
        saveAssignsId();
        MockMultipartFile upload = sample(name);

        TransactionImportResponse response = service.create(userId, accountId, null, upload, "127.0.0.1");

        ArgumentCaptor<TransactionImport> captor = ArgumentCaptor.forClass(TransactionImport.class);
        verify(transactionImportRepository).save(captor.capture());
        TransactionImport saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getAccountId()).isEqualTo(accountId);
        assertThat(saved.getCreditCardId()).isNull();
        assertThat(saved.getFileName()).isEqualTo(name);
        assertThat(saved.getFileType()).isEqualTo(TransactionImport.FileType.OFX);
        assertThat(saved.getBankName()).isNull();
        assertThat(saved.getStatus()).isEqualTo(TransactionImport.Status.PROCESSING);
        assertThat(saved.getFileSizeKb()).isEqualTo((int) ((upload.getSize() + 1023) / 1024));
        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.targetName()).isEqualTo("Nubank");
        assertThat(response.fileName()).isEqualTo(name);
    }

    @Test
    void auditHasIdTypeAndSizeButNotTheFileName() throws IOException {
        accountExists();
        saveAssignsId();

        TransactionImportResponse response = service.create(userId, accountId, null, sample("nubank-conta.ofx"), "10.0.0.1");

        ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq("import_created"), eq(userId), eq("10.0.0.1"), metadata.capture());
        assertThat(metadata.getValue())
            .contains("\"import_id\":\"" + response.id() + "\"", "\"file_type\":\"OFX\"", "\"file_size_kb\":4")
            .doesNotContain("nubank");
    }

    @Test
    void cardStatementToACardIsRecorded() throws IOException {
        cardExists();
        saveAssignsId();

        TransactionImportResponse response = service.create(userId, null, cardId, sample("itau-cartao.ofx"), "127.0.0.1");

        assertThat(response.creditCardId()).isEqualTo(cardId);
        assertThat(response.accountId()).isNull();
        assertThat(response.targetName()).isEqualTo("Itaú Visa");
        verifyNoInteractions(accountRepository);
    }

    @Test
    void cardStatementToAnAccountIs400() throws IOException {
        assertRejected(accountId, null, sample("itau-cartao.ofx"), TransactionImportService.CARD_FILE_TO_ACCOUNT_MESSAGE);
        assertThat(TransactionImportService.CARD_FILE_TO_ACCOUNT_MESSAGE)
            .isEqualTo("Este arquivo é de cartão de crédito. Escolha um cartão como destino.");
    }

    @Test
    void accountStatementToACardIs400() throws IOException {
        assertRejected(null, cardId, sample("nubank-conta.ofx"), TransactionImportService.ACCOUNT_FILE_TO_CARD_MESSAGE);
    }

    @Test
    void fileAbove10MbIs400() {
        MultipartFile upload = mock(MultipartFile.class);
        when(upload.isEmpty()).thenReturn(false);
        when(upload.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        assertRejected(accountId, null, upload, "Arquivo muito grande (máximo 10MB)");
    }

    @Test
    void fileOfExactly10MbPassesTheSizeCheck() {
        byte[] content = new byte[10 * 1024 * 1024];
        byte[] header = "OFXHEADER:100\n<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS>".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, content, 0, header.length);
        accountExists();
        saveAssignsId();

        TransactionImportResponse response = service.create(userId, accountId, null,
            new MockMultipartFile("file", "grande.ofx", null, content), "127.0.0.1");

        assertThat(response.fileSizeKb()).isEqualTo(10240);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nubank-conta.csv", "extrato.pdf", "extrato", "extrato.ofx.txt"})
    void wrongExtensionIs400(String name) {
        assertRejected(accountId, null, file(name, "OFXHEADER:100\n<OFX><STMTRS>"), "Formato de arquivo não suportado");
    }

    @Test
    void ofxExtensionWithPlainTextIs400() throws IOException {
        assertRejected(accountId, null, sample("erro-nao-e-extrato.ofx"), "Formato de arquivo não suportado");
    }

    @Test
    void emptyFileIs400() {
        assertRejected(accountId, null, file("vazio.ofx", ""), "Formato de arquivo não suportado");
    }

    @Test
    void missingFileIs400() {
        assertRejected(accountId, null, null, "Formato de arquivo não suportado");
    }

    @Test
    void ofxWithoutAStatementBlockIs400() {
        assertRejected(accountId, null, file("x.ofx", "OFXHEADER:100\n<OFX><SIGNONMSGSRSV1></SIGNONMSGSRSV1></OFX>"),
            "Formato de arquivo não suportado");
    }

    @Test
    void headerWithoutOfxRootIs400() {
        assertRejected(accountId, null, file("x.ofx", "OFXHEADER:100\n<STMTRS>"), "Formato de arquivo não suportado");
    }

    @Test
    void missingTargetIs400() throws IOException {
        assertRejected(null, null, sample("nubank-conta.ofx"), TransactionImportService.TARGET_REQUIRED_MESSAGE);
    }

    @Test
    void bothTargetsIs400() throws IOException {
        assertRejected(accountId, cardId, sample("nubank-conta.ofx"), TransactionImportService.TARGET_REQUIRED_MESSAGE);
    }

    @Test
    void accountOfAnotherUserOrRemovedIs404() throws IOException {
        when(accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)).thenReturn(Optional.empty());
        MockMultipartFile upload = sample("nubank-conta.ofx");

        assertThatThrownBy(() -> service.create(userId, accountId, null, upload, "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Conta não encontrada");
        verify(transactionImportRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void cardOfAnotherUserOrRemovedIs404() throws IOException {
        when(creditCardRepository.findByIdAndUserIdAndDeletedAtIsNull(cardId, userId)).thenReturn(Optional.empty());
        MockMultipartFile upload = sample("itau-cartao.ofx");

        assertThatThrownBy(() -> service.create(userId, null, cardId, upload, "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Cartão não encontrado");
        verify(transactionImportRepository, never()).save(any());
    }

    @Test
    void statementKindSkipsBomAndWhitespaceAndAcceptsXml() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "\r\n  OFXHEADER:100\n<OFX><CREDITCARDMSGSRSV1><CCSTMTRS>".getBytes(StandardCharsets.US_ASCII);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);
        assertThat(TransactionImportService.statementKind(content)).isEqualTo(TransactionImportService.StatementKind.CARD);

        byte[] xml = "<?xml version=\"1.0\"?><?OFX OFXHEADER=\"200\"?><ofx><stmtrs>".getBytes(StandardCharsets.UTF_8);
        assertThat(TransactionImportService.statementKind(xml)).isEqualTo(TransactionImportService.StatementKind.ACCOUNT);
    }

    @Test
    void whenBothBlocksExistTheFirstWins() {
        byte[] content = "OFXHEADER:100\n<OFX><CCSTMTRS></CCSTMTRS><STMTRS>".getBytes(StandardCharsets.US_ASCII);
        assertThat(TransactionImportService.statementKind(content)).isEqualTo(TransactionImportService.StatementKind.CARD);
    }

    @Test
    void fileNameKeepsOnlyTheNameWithoutPathAndAtMost255Characters() {
        assertThat(TransactionImportService.sanitizeFileName("C:\\Users\\ana\\extrato.ofx")).isEqualTo("extrato.ofx");
        assertThat(TransactionImportService.sanitizeFileName("../../etc/extrato.ofx")).isEqualTo("extrato.ofx");
        assertThat(TransactionImportService.sanitizeFileName("ex\ntrato.ofx")).isEqualTo("extrato.ofx");
        String longName = "a".repeat(300) + ".ofx";
        String sanitized = TransactionImportService.sanitizeFileName(longName);
        assertThat(sanitized).hasSize(255).endsWith(".ofx");
    }

    @Test
    void listRecentReturnsTheTargetNamesIncludingRemovedOnes() {
        TransactionImport toAccount = new TransactionImport(userId, accountId, null, "a.ofx", TransactionImport.FileType.OFX, 1);
        toAccount.setId(UUID.randomUUID());
        TransactionImport toCard = new TransactionImport(userId, null, cardId, "b.ofx", TransactionImport.FileType.OFX, 2);
        toCard.setId(UUID.randomUUID());
        when(transactionImportRepository.findRecentByUserId(eq(userId), any())).thenReturn(List.of(toCard, toAccount));
        Account account = new Account();
        account.setId(accountId);
        account.setAccountName("Nubank");
        when(accountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(account));
        CreditCard card = new CreditCard();
        card.setId(cardId);
        card.setCardName("Itaú Visa");
        when(creditCardRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(card));

        List<TransactionImportResponse> list = service.listRecent(userId);

        assertThat(list).extracting(TransactionImportResponse::targetName).containsExactly("Itaú Visa", "Nubank");
    }

    @Test
    void listRecentWithoutImportsSkipsTheLookups() {
        when(transactionImportRepository.findRecentByUserId(eq(userId), any())).thenReturn(List.of());
        assertThat(service.listRecent(userId)).isEmpty();
        verifyNoInteractions(accountRepository, creditCardRepository);
    }

    @Test
    void auditIpIsPassedThrough() throws IOException {
        accountExists();
        saveAssignsId();
        service.create(userId, accountId, null, sample("caixa-conta.ofx"), "192.168.0.9");
        verify(auditLogService).log(eq("import_created"), eq(userId), eq("192.168.0.9"), anyString());
    }
}
