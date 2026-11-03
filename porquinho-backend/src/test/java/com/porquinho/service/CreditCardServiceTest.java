package com.porquinho.service;

import com.porquinho.dto.CreateCreditCardRequest;
import com.porquinho.entity.CreditCard;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.CreditCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    @Mock
    private CreditCardRepository creditCardRepository;

    private CreditCardService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new CreditCardService(creditCardRepository);
        userId = UUID.randomUUID();
    }

    private CreditCard savedCard() {
        ArgumentCaptor<CreditCard> captor = ArgumentCaptor.forClass(CreditCard.class);
        verify(creditCardRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createStoresTheCardOfTheUserWithTheLimitScaledToTwoDecimals() {
        when(creditCardRepository.save(any(CreditCard.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, new CreateCreditCardRequest("  Nubank Roxinho ", "  ", new BigDecimal("5000"), new BigDecimal("10")));

        CreditCard saved = savedCard();
        assertEquals(userId, saved.getUserId());
        assertEquals("Nubank Roxinho", saved.getCardName());
        assertNull(saved.getBankName());
        assertEquals(new BigDecimal("5000.00"), saved.getCardLimit());
        assertEquals(10, saved.getDueDay());
        assertNull(saved.getDeletedAt());
    }

    @Test
    void unicodeSpacesAroundNamesAreStripped() {
        when(creditCardRepository.save(any(CreditCard.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, new CreateCreditCardRequest(" Inter  ", " Inter ", BigDecimal.ZERO, new BigDecimal("31.0")));

        CreditCard saved = savedCard();
        assertEquals("Inter", saved.getCardName());
        assertEquals("Inter", saved.getBankName());
        assertEquals(new BigDecimal("0.00"), saved.getCardLimit());
        assertEquals(31, saved.getDueDay());
    }

    @Test
    void deleteSoftDeletesTheUsersCard() {
        UUID cardId = UUID.randomUUID();
        CreditCard card = new CreditCard(userId, "Nubank", null, BigDecimal.TEN, 5);
        when(creditCardRepository.findByIdAndUserIdAndDeletedAtIsNull(cardId, userId)).thenReturn(Optional.of(card));

        service.delete(userId, cardId);

        assertNotNull(card.getDeletedAt());
        verify(creditCardRepository).save(card);
        verify(creditCardRepository, never()).delete(any());
    }

    @Test
    void deletingSomeoneElsesMissingOrRemovedCardIsNotFound() {
        UUID cardId = UUID.randomUUID();
        when(creditCardRepository.findByIdAndUserIdAndDeletedAtIsNull(cardId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(userId, cardId));
        verify(creditCardRepository, never()).save(any());
    }

    @Test
    void updateChangesNameBankLimitAndDueDay() {
        UUID cardId = UUID.randomUUID();
        CreditCard card = new CreditCard(userId, "Nubank", "Nubank", new BigDecimal("5000.00"), 10);
        when(creditCardRepository.findByIdAndUserIdAndDeletedAtIsNull(cardId, userId)).thenReturn(Optional.of(card));
        when(creditCardRepository.save(any(CreditCard.class))).thenAnswer(inv -> inv.getArgument(0));

        CreditCard updated = service.update(userId, cardId,
            new CreateCreditCardRequest(" Nubank Ultravioleta ", "  ", new BigDecimal("6000"), new BigDecimal("20")));

        assertSame(card, updated);
        assertEquals("Nubank Ultravioleta", updated.getCardName());
        assertNull(updated.getBankName());
        assertEquals(new BigDecimal("6000.00"), updated.getCardLimit());
        assertEquals(20, updated.getDueDay());
        assertEquals(userId, updated.getUserId());
        verify(creditCardRepository).save(card);
    }

    @Test
    void updatingSomeoneElsesMissingOrRemovedCardIsNotFound() {
        UUID cardId = UUID.randomUUID();
        when(creditCardRepository.findByIdAndUserIdAndDeletedAtIsNull(cardId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(userId, cardId,
            new CreateCreditCardRequest("Nubank", null, BigDecimal.ONE, BigDecimal.ONE)));
        verify(creditCardRepository, never()).save(any());
    }
}
