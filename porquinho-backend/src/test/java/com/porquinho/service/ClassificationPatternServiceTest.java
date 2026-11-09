package com.porquinho.service;

import com.porquinho.entity.UserClassificationPattern;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.TransactionRepository;
import com.porquinho.repository.UserClassificationPatternRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Story 5.11: the normalization of the spec's matrix and what a saved category teaches, over mocked repositories. */
@ExtendWith(MockitoExtension.class)
class ClassificationPatternServiceTest {

    @Mock
    private UserClassificationPatternRepository patternRepository;
    @Mock
    private AiClassificationHistoryRepository historyRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @InjectMocks
    private ClassificationPatternService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID category = UUID.randomUUID();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "UBER *TRIP 12/03 PARC 02/05 | uber trip",
        "Uber Trip 15/04             | uber trip",
        "Magazine Luiza - Parcela 3/10 | magazine luiza",
        "MAGAZINELUIZA  03/10        | magazineluiza",
        "Padaria São João            | padaria sao joao",
        "PIX ENVIADO 123.456.789-09 MARIA | pix enviado maria",
        "AÇOUGUE   DO   ZÉ           | acougue do ze",
        "123456                      | ''",
        "'   '                       | ''",
    })
    void normalizesTheDescription(String description, String expected) {
        assertThat(ClassificationPatternService.normalize(description)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"PIX 98765, false", "PAGAMENTO BOLETO 3421, false", "123456, false",
        "PIX TRANSF JOAO SILVA, true", "Uber Trip, true"})
    void onlyGenericBankingTermsAreNotUsable(String description, boolean usable) {
        assertThat(ClassificationPatternService.isUsable(ClassificationPatternService.normalize(description)))
            .isEqualTo(usable);
    }

    @Test
    void genericTermsTeachNothingButANameKeepsTheWholePattern() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(transactionRepository.findDescriptions(userId, ids))
            .thenReturn(List.of("PIX 98765", "PAGAMENTO BOLETO 3421", "PIX TRANSF JOAO SILVA"));

        service.learn(userId, ids, category);

        verify(patternRepository).upsert(eq(userId), eq("pix transf joao silva"), eq(category), any());
        verify(patternRepository, org.mockito.Mockito.times(1)).upsert(any(), anyString(), any(), any());
    }

    @Test
    void nullIsEmpty() {
        assertThat(ClassificationPatternService.normalize(null)).isEmpty();
    }

    @Test
    void equalDescriptionsOfABatchMakeOnePatternAndAnEmptyOneTeachesNothing() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(transactionRepository.findDescriptions(userId, ids))
            .thenReturn(List.of("UBER TRIP 01/11", "Uber Trip", "uber *trip 7", "123456"));

        service.learn(userId, ids, category);

        verify(historyRepository).markUserChoice(userId, ids, category);
        verify(patternRepository).upsert(eq(userId), eq("uber trip"), eq(category), any());
        verify(patternRepository, never()).upsert(eq(userId), eq(""), any(), any());
    }

    @Test
    void anEmptyPatternIsNeverWritten() {
        List<UUID> ids = List.of(UUID.randomUUID());
        when(transactionRepository.findDescriptions(userId, ids)).thenReturn(List.of("123456"));

        service.learn(userId, ids, category);

        verify(patternRepository, never()).upsert(any(), anyString(), any(), any());
    }

    @Test
    void matchMapsEachUsablePatternToItsCategory() {
        UserClassificationPattern pattern = new UserClassificationPattern();
        pattern.setDescriptionPattern("uber trip");
        pattern.setPreferredCategoryId(category);
        when(patternRepository.findUsableByUserId(userId)).thenReturn(List.of(pattern));

        assertThat(service.match(userId)).isEqualTo(Map.of("uber trip", category));
    }
}
