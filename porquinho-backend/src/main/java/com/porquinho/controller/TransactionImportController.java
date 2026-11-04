package com.porquinho.controller;

import com.porquinho.dto.TransactionImportResponse;
import com.porquinho.service.TransactionImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Bank statement uploads (Story 5.1), used by the /importar page. The user always comes from the JWT.
 */
@RestController
@RequestMapping("/api/v1/imports")
public class TransactionImportController {

    private final TransactionImportService transactionImportService;

    public TransactionImportController(TransactionImportService transactionImportService) {
        this.transactionImportService = transactionImportService;
    }

    /** The user's 20 most recent imports, with the target name. */
    @GetMapping
    public ResponseEntity<List<TransactionImportResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(transactionImportService.listRecent(UUID.fromString(jwt.getSubject())));
    }

    /**
     * Multipart: {@code file} plus exactly one of {@code accountId} / {@code creditCardId}. 201 with the import in
     * PROCESSING; 400 for an invalid target or file (ProblemDetail, pt-BR); 404 when the target is not one of the
     * user's active accounts or cards.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TransactionImportResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                            @RequestPart(value = "file", required = false) MultipartFile file,
                                                            @RequestParam(value = "accountId", required = false) UUID accountId,
                                                            @RequestParam(value = "creditCardId", required = false) UUID creditCardId,
                                                            HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(jwt.getSubject());
        TransactionImportResponse created = transactionImportService.create(userId, accountId, creditCardId, file,
            httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
