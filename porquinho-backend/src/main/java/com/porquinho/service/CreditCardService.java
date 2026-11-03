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
 * Credit cards (Story 3.3: create, list and remove; Story 4.3: edit; closing day and invoices come later).
 * The user id always comes from the JWT; another user's card behaves as if it did not exist.
 */
@Service
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;

    public CreditCardService(CreditCardRepository creditCardRepository) {
        this.creditCardRepository = creditCardRepository;
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
        CreditCard card = creditCardRepository.findByIdAndUserIdAndDeletedAtIsNull(cardId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));
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

    /** Soft delete. 404 when the card does not exist, belongs to someone else or was already removed. */
    @Transactional
    public void delete(UUID userId, UUID cardId) {
        CreditCard card = creditCardRepository.findByIdAndUserIdAndDeletedAtIsNull(cardId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));
        card.setDeletedAt(OffsetDateTime.now());
        creditCardRepository.save(card);
    }
}
