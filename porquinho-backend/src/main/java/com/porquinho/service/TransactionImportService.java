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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Upload of a bank statement to one of the user's accounts or cards (Story 5.1). Validates the target (exactly one,
 * active, owned by the JWT user; otherwise 404 as if it did not exist) and the file (extension .ofx, at most 10MB,
 * content that looks like OFX, statement type matching the target), then records the import as PROCESSING and logs
 * "import_created". The file content is never stored nor logged: after the commit it is handed, in memory only, to
 * {@link TransactionImportProcessor} (Story 5.3).
 */
@Service
public class TransactionImportService {

    private static final Logger log = LoggerFactory.getLogger(TransactionImportService.class);

    public static final String AUDIT_CREATED = "import_created";

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final int RECENT_LIMIT = 20;
    static final int MAX_FILE_NAME_LENGTH = 255;

    public static final String NOT_FOUND_MESSAGE = "Importação não encontrada";
    public static final String TARGET_REQUIRED_MESSAGE = "Escolha uma conta ou um cartão de destino";
    public static final String FILE_TOO_LARGE_MESSAGE = "Arquivo muito grande (máximo 10MB)";
    public static final String UNSUPPORTED_FORMAT_MESSAGE = "Formato de arquivo não suportado";
    public static final String CARD_FILE_TO_ACCOUNT_MESSAGE =
        "Este arquivo é de cartão de crédito. Escolha um cartão como destino.";
    public static final String ACCOUNT_FILE_TO_CARD_MESSAGE =
        "Este arquivo é de conta bancária. Escolha uma conta como destino.";

    /** The kind of statement found in an OFX file. */
    enum StatementKind { ACCOUNT, CARD }

    private final TransactionImportRepository transactionImportRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final AuditLogService auditLogService;
    private final TransactionImportProcessor transactionImportProcessor;
    private final TransactionTemplate transactionTemplate;

    public TransactionImportService(TransactionImportRepository transactionImportRepository,
                                    AccountRepository accountRepository, CreditCardRepository creditCardRepository,
                                    AuditLogService auditLogService, TransactionImportProcessor transactionImportProcessor,
                                    PlatformTransactionManager transactionManager) {
        this.transactionImportRepository = transactionImportRepository;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.auditLogService = auditLogService;
        this.transactionImportProcessor = transactionImportProcessor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Records the upload. 400 when the target is missing or doubled, the file is empty, too large, not .ofx, not OFX
     * content, or of the other statement type; 404 "Conta não encontrada" / "Cartão não encontrado" when the target is
     * not one of the user's active rows. Nothing is written on error. The file is validated before the transaction
     * starts, so reading up to 10MB never holds a pooled connection. After the commit, the content (only in memory) goes
     * to the background processing (Story 5.3).
     */
    public TransactionImportResponse create(UUID userId, UUID accountId, UUID creditCardId, MultipartFile file,
                                            String ipAddress) {
        if ((accountId == null) == (creditCardId == null)) {
            throw new BadRequestException(TARGET_REQUIRED_MESSAGE);
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(UNSUPPORTED_FORMAT_MESSAGE);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException(FILE_TOO_LARGE_MESSAGE);
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".ofx")) {
            throw new BadRequestException(UNSUPPORTED_FORMAT_MESSAGE);
        }
        byte[] content = read(file);
        StatementKind kind = statementKind(content);
        if (accountId != null && kind == StatementKind.CARD) {
            throw new BadRequestException(CARD_FILE_TO_ACCOUNT_MESSAGE);
        }
        if (creditCardId != null && kind == StatementKind.ACCOUNT) {
            throw new BadRequestException(ACCOUNT_FILE_TO_CARD_MESSAGE);
        }
        int fileSizeKb = sizeKb(file.getSize());
        TransactionImportResponse created = transactionTemplate.execute(status ->
            record(userId, accountId, creditCardId, fileName, fileSizeKb, ipAddress));
        // execute returned: the row is committed, so the background processing always finds it (Story 5.3)
        try {
            transactionImportProcessor.process(created.id(), content);
        } catch (RuntimeException e) {
            // e.g. executor rejected the task: the upload itself succeeded, the import just fails now
            log.error("Import {} could not start processing: {}", created.id(), e.getClass().getName());
            transactionImportRepository.markFailed(created.id(), TransactionImportProcessor.UNEXPECTED_ERROR_MESSAGE,
                OffsetDateTime.now());
            return get(userId, created.id());
        }
        return created;
    }

    private TransactionImportResponse record(UUID userId, UUID accountId, UUID creditCardId, String fileName,
                                             int fileSizeKb, String ipAddress) {
        String targetName;
        if (accountId != null) {
            targetName = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(AccountService.NOT_FOUND_MESSAGE))
                .getAccountName();
        } else {
            targetName = creditCardRepository.findByIdAndUserIdAndDeletedAtIsNull(creditCardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(CreditCardService.NOT_FOUND_MESSAGE))
                .getCardName();
        }

        TransactionImport saved = transactionImportRepository.save(new TransactionImport(userId, accountId,
            creditCardId, fileName, TransactionImport.FileType.OFX, fileSizeKb));
        auditLogService.log(AUDIT_CREATED, userId, ipAddress, auditMetadata(saved));
        return TransactionImportResponse.of(saved, targetName);
    }

    /** The user's 20 most recent imports, with the target name (removed accounts and cards included). */
    @Transactional(readOnly = true)
    public List<TransactionImportResponse> listRecent(UUID userId) {
        List<TransactionImport> imports =
            transactionImportRepository.findRecentByUserId(userId, PageRequest.of(0, RECENT_LIMIT));
        if (imports.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> accounts = accountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
            .collect(Collectors.toMap(Account::getId, Account::getAccountName));
        Map<UUID, String> cards = creditCardRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
            .collect(Collectors.toMap(CreditCard::getId, CreditCard::getCardName));
        return imports.stream()
            .map(i -> TransactionImportResponse.of(i,
                i.getAccountId() != null ? accounts.get(i.getAccountId()) : cards.get(i.getCreditCardId())))
            .toList();
    }

    /** One of the user's imports, for the status polling of /importar (Story 5.3); 404 when not the user's. */
    @Transactional(readOnly = true)
    public TransactionImportResponse get(UUID userId, UUID importId) {
        TransactionImport transactionImport = transactionImportRepository.findByIdAndUserId(importId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        String targetName = transactionImport.getAccountId() != null
            ? accountRepository.findById(transactionImport.getAccountId()).map(Account::getAccountName).orElse(null)
            : creditCardRepository.findById(transactionImport.getCreditCardId()).map(CreditCard::getCardName)
                .orElse(null);
        return TransactionImportResponse.of(transactionImport, targetName);
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException(UNSUPPORTED_FORMAT_MESSAGE);
        }
    }

    /**
     * ACCOUNT or CARD for content that looks like OFX: after a UTF-8 BOM and whitespace it starts with
     * "OFXHEADER:" (SGML) or "&lt;?xml", contains "&lt;OFX&gt;" and a statement block. When both blocks exist, the
     * first one wins. Anything else is 400 "Formato de arquivo não suportado". The ASCII tags are matched on the
     * bytes, ignoring case, so cp1252 and UTF-8 files alike work and no copy of the (up to 10MB) content is made.
     */
    static StatementKind statementKind(byte[] content) {
        boolean bom = content.length >= 3 && content[0] == (byte) 0xEF && content[1] == (byte) 0xBB
            && content[2] == (byte) 0xBF;
        int start = bom ? 3 : 0;
        while (start < content.length && Character.isWhitespace((char) (content[start] & 0xFF))) {
            start++;
        }
        boolean header = matchesAt(content, start, "OFXHEADER:") || matchesAt(content, start, "<?XML");
        if (!header || indexOf(content, "<OFX>") < 0) {
            throw new BadRequestException(UNSUPPORTED_FORMAT_MESSAGE);
        }
        int card = indexOf(content, "<CCSTMTRS>");
        int account = indexOf(content, "<STMTRS>");
        if (card < 0 && account < 0) {
            throw new BadRequestException(UNSUPPORTED_FORMAT_MESSAGE);
        }
        if (card < 0) {
            return StatementKind.ACCOUNT;
        }
        if (account < 0) {
            return StatementKind.CARD;
        }
        return card < account ? StatementKind.CARD : StatementKind.ACCOUNT;
    }

    /** First position of the upper-case ASCII {@code tag} in {@code content}, ignoring ASCII case; -1 if absent. */
    private static int indexOf(byte[] content, String tag) {
        for (int i = 0; i + tag.length() <= content.length; i++) {
            if (matchesAt(content, i, tag)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesAt(byte[] content, int offset, String tag) {
        if (offset + tag.length() > content.length) {
            return false;
        }
        for (int k = 0; k < tag.length(); k++) {
            int b = content[offset + k];
            if (b >= 'a' && b <= 'z') {
                b -= 'a' - 'A';
            }
            if (b != tag.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    /** Only the name (no path from either OS), without control characters, at most 255 characters. */
    static String sanitizeFileName(String original) {
        String name = original == null ? "" : original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            name = name.substring(name.length() - MAX_FILE_NAME_LENGTH);
        }
        return name;
    }

    static int sizeKb(long bytes) {
        return (int) ((bytes + 1023) / 1024);
    }

    static String auditMetadata(TransactionImport transactionImport) {
        return "{\"import_id\":\"" + transactionImport.getId() + "\",\"file_type\":\""
            + transactionImport.getFileType().name() + "\",\"file_size_kb\":" + transactionImport.getFileSizeKb() + "}";
    }
}
