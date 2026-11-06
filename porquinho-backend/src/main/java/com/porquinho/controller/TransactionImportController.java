package com.porquinho.controller;

import com.porquinho.dto.ImportReviewActionResponse;
import com.porquinho.dto.ImportTransactionResponse;
import com.porquinho.dto.TransactionImportResponse;
import com.porquinho.dto.UpdateImportCategoryRequest;
import com.porquinho.service.TransactionImportReviewService;
import com.porquinho.service.TransactionImportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Bank statement uploads (Story 5.1), used by the /importar page, and their review and conclusion (Stories 5.9 and
 * 5.10). The user always comes from the JWT.
 */
@RestController
@RequestMapping("/api/v1/imports")
public class TransactionImportController {

    private final TransactionImportService transactionImportService;
    private final TransactionImportReviewService transactionImportReviewService;

    public TransactionImportController(TransactionImportService transactionImportService,
                                       TransactionImportReviewService transactionImportReviewService) {
        this.transactionImportService = transactionImportService;
        this.transactionImportReviewService = transactionImportReviewService;
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

    /** One of the user's imports, polled every 2 s by /importar while it is processing (Story 5.3). 404 if not the user's. */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionImportResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(transactionImportService.get(UUID.fromString(jwt.getSubject()), id));
    }

    /** The rows of an import for the review, without the future installments (Story 5.9). 404 if not the user's. */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<ImportTransactionResponse>> transactions(@AuthenticationPrincipal Jwt jwt,
                                                                        @PathVariable UUID id) {
        return ResponseEntity.ok(transactionImportReviewService.list(UUID.fromString(jwt.getSubject()), id));
    }

    /** Approves the high-confidence rows (Story 5.9). 404 if not the user's; 409 outside REVIEW. */
    @PostMapping("/{id}/approve-high-confidence")
    public ResponseEntity<ImportReviewActionResponse> approveHighConfidence(@AuthenticationPrincipal Jwt jwt,
                                                                            @PathVariable UUID id,
                                                                            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(transactionImportReviewService.approveHighConfidence(
            UUID.fromString(jwt.getSubject()), id, httpRequest.getRemoteAddr()));
    }

    /** Discards an import under review (Story 5.9). 404 if not the user's; 409 outside REVIEW. */
    @PostMapping("/{id}/discard")
    public ResponseEntity<ImportReviewActionResponse> discard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                              HttpServletRequest httpRequest) {
        return ResponseEntity.ok(transactionImportReviewService.discard(
            UUID.fromString(jwt.getSubject()), id, httpRequest.getRemoteAddr()));
    }

    /**
     * Sets the category of one or several rows, and of the future installments of their purchases (Story 5.10). 400
     * for a group or an invalid body; 404 for an import, row or category that is not available; 409 outside REVIEW.
     */
    @PatchMapping("/{id}/transactions/category")
    public ResponseEntity<ImportReviewActionResponse> updateCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                                     @Valid @RequestBody UpdateImportCategoryRequest request,
                                                                     HttpServletRequest httpRequest) {
        return ResponseEntity.ok(transactionImportReviewService.updateCategory(
            UUID.fromString(jwt.getSubject()), id, request, httpRequest.getRemoteAddr()));
    }

    /**
     * Finishes an import under review (Story 5.10). 200 with nothing changed if it is already COMPLETED; 400 if the
     * balance would go out of range; 404 if not the user's; 409 in any other status or with the target removed.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<ImportReviewActionResponse> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(transactionImportReviewService.complete(
            UUID.fromString(jwt.getSubject()), id, httpRequest.getRemoteAddr()));
    }
}
