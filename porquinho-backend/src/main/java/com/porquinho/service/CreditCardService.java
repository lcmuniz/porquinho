package com.porquinho.service;

import com.porquinho.dto.CreateCreditCardRequest;
import com.porquinho.entity.CreditCard;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.CreditCardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Credit cards (Story 3.3: create, list and remove; Story 4.3: edit; Story 4.4: audited removal, removed list and
 * restore; closing day and invoices come later). Unlike accounts, there is no minimum number of active cards.
 * The user id always comes from the JWT; another user's card behaves as if it did not exist.
 */
@Service
public class CreditCardService {

    public static final String NOT_FOUND_MESSAGE = "Cartão não encontrado";
    public static final String AUDIT_DELETED = "card_deleted";
    public static final String AUDIT_RESTORED = "card_restored";

    private final CreditCardRepository creditCardRepository;
    private final AuditLogService auditLogService;
    private final BudgetBalanceCache budgetBalanceCache;

    public CreditCardService(CreditCardRepository creditCardRepository, AuditLogService auditLogService,
                             BudgetBalanceCache budgetBalanceCache) {
        this.creditCardRepository = creditCardRepository;
        this.auditLogService = auditLogService;
        this.budgetBalanceCache = budgetBalanceCache;
    }

    @Transactional
    public CreditCard create(UUID userId, CreateCreditCardRequest request) {
        return creditCardRepository.save(new CreditCard(userId, request.cardName(), request.bankName(),
            request.cardLimit().setScale(2, RoundingMode.UNNECESSARY), request.dueDayValue()));
    }

    /**
     * Edits name, bank, limit and due day (Story 4.3), with the same rules as the creation.
     * 404 when the card does not exist, belongs to someone else or was removed.
     */
    @Transactional
    public CreditCard update(UUID userId, UUID cardId, CreateCreditCardRequest request) {
        CreditCard card = creditCardRepository.findActiveByIdForUpdate(cardId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        card.setCardName(request.cardName());
        card.setBankName(request.bankName());
        card.setCardLimit(request.cardLimit().setScale(2, RoundingMode.UNNECESSARY));
        card.setDueDay(request.dueDayValue());
        return creditCardRepository.save(card);
    }

    /** The user's active cards, oldest first. */
    @Transactional(readOnly = true)
    public List<CreditCard> listActive(UUID userId) {
        return creditCardRepository.findActiveByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long countActive(UUID userId) {
        return creditCardRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    /** The user's removed cards, most recently removed first (Story 4.4). */
    @Transactional(readOnly = true)
    public List<CreditCard> listDeleted(UUID userId) {
        return creditCardRepository.findDeletedByUserId(userId);
    }

    /**
     * Soft delete (Story 4.4): fills deleted_at and deleted_by and writes the "card_deleted" audit.
     * 404 when the card does not exist, belongs to someone else or was already removed.
     */
    @Transactional
    public void delete(UUID userId, UUID cardId, String ipAddress) {
        CreditCard card = creditCardRepository.findActiveByIdForUpdate(cardId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        card.setDeletedAt(OffsetDateTime.now());
        card.setDeletedBy(userId);
        creditCardRepository.save(card);
        auditLogService.log(AUDIT_DELETED, userId, ipAddress, AccountService.idMetadata(cardId));
        budgetBalanceCache.evict(userId);
    }

    /**
     * Restore (Story 4.4): clears deleted_at and deleted_by and writes the "card_restored" audit.
     * 404 when the card does not exist, belongs to someone else or is not removed.
     */
    @Transactional
    public CreditCard restore(UUID userId, UUID cardId, String ipAddress) {
        CreditCard card = creditCardRepository.findDeletedByIdForUpdate(cardId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        card.setDeletedAt(null);
        card.setDeletedBy(null);
        CreditCard saved = creditCardRepository.save(card);
        auditLogService.log(AUDIT_RESTORED, userId, ipAddress, AccountService.idMetadata(cardId));
        budgetBalanceCache.evict(userId);
        return saved;
    }
}
