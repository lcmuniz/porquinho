package com.porquinho.service;

import com.porquinho.entity.AiClassificationHistory;
import com.porquinho.entity.Category;
import com.porquinho.entity.Transaction;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static com.porquinho.entity.Transaction.ClassificationSource.AI;
import static com.porquinho.entity.Transaction.ClassificationSource.PATTERN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Story 5.8: the I/O matrix of the spec over mocked repositories and classifier. */
@ExtendWith(MockitoExtension.class)
class TransactionClassificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionImportRepository importRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AiClassificationHistoryRepository historyRepository;
    @Mock
    private TransactionClassifier classifier;
    @Mock
    private ClassificationPatternService patternService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private TransactionClassificationService service;
    private final UUID importId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final UUID mercado = UUID.randomUUID();
    private final UUID lazer = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(importRepository.startClassification(eq(importId), anyInt(), any())).thenReturn(1);
        lenient().when(importRepository.addClassified(eq(importId), anyInt(), any())).thenReturn(1);
        // by name, as the query orders them: Lazer is 1, Mercado is 2
        lenient().when(categoryRepository.findActiveByUserId(userId)).thenReturn(List.of(
            category(UUID.randomUUID(), "Alimentação", null), category(lazer, "Lazer", null),
            category(mercado, "Mercado", null)));
        service = new TransactionClassificationService(transactionRepository, importRepository, categoryRepository,
            historyRepository, classifier, patternService, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void withGroupedCategories() {
        UUID group = UUID.randomUUID();
        when(categoryRepository.findActiveByUserId(userId)).thenReturn(List.of(
            category(group, "Alimentação", null), category(lazer, "Lazer", group), category(mercado, "Mercado", group)));
    }

    private static Category category(UUID id, String name, UUID parent) {
        Category category = new Category();
        category.setId(id);
        category.setCategoryName(name);
        category.setParentCategoryId(parent);
        return category;
    }

    private static Transaction row(String description) {
        Transaction row = new Transaction();
        row.setId(UUID.randomUUID());
        row.setDescription(description);
        row.setOriginalDescription(description);
        row.setAmount(new BigDecimal("10.00"));
        row.setTransactionType(Transaction.Type.DEBIT);
        row.setTransactionDate(LocalDate.of(2026, 11, 1));
        return row;
    }

    private List<Transaction> rows(int count) {
        List<Transaction> rows = IntStream.range(0, count).mapToObj(i -> row("COMPRA " + i)).toList();
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(rows);
        return rows;
    }

    private static TransactionClassifier.Result result(int index, Integer category, int confidence) {
        return new TransactionClassifier.Result(index, category, confidence);
    }

    @SuppressWarnings("unchecked")
    private List<AiClassificationHistory> history() {
        ArgumentCaptor<List<AiClassificationHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(historyRepository, org.mockito.Mockito.atLeast(0)).saveAll(captor.capture());
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    private void assertNothingWrittenTo(Transaction row) {
        verify(transactionRepository, never()).applyClassification(eq(row.getId()), any(), anyInt(), any(), any());
    }

    @Test
    void highConfidenceAppliesTheCategoryAndKeepsHistory() {
        withGroupedCategories();
        Transaction row = rows(1).get(0);
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 2, 92)));

        service.classify(importId, userId, false);

        verify(transactionRepository).applyClassification(row.getId(), mercado, 92, AI, now);
        assertThat(history()).singleElement().satisfies(h -> {
            assertThat(h.getUserId()).isEqualTo(userId);
            assertThat(h.getTransactionId()).isEqualTo(row.getId());
            assertThat(h.getPredictedCategoryId()).isEqualTo(mercado);
            assertThat(h.getConfidence()).isEqualTo(92);
            assertThat(h.isWasCorrected()).isFalse();
        });
    }

    @Test
    void lowConfidenceKeepsTheSuggestionInTheCategory() {
        withGroupedCategories();
        Transaction row = rows(1).get(0);
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 1, 60)));

        service.classify(importId, userId, false);

        verify(transactionRepository).applyClassification(row.getId(), lazer, 60, AI, now);
        assertThat(history()).singleElement().satisfies(h -> assertThat(h.getConfidence()).isEqualTo(60));
    }

    @Test
    void noCategoryLeavesTheRowUnclassifiedWithHistory() {
        withGroupedCategories();
        Transaction row = rows(1).get(0);
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, null, 30)));

        service.classify(importId, userId, false);

        assertNothingWrittenTo(row);
        assertThat(history()).singleElement().satisfies(h -> {
            assertThat(h.getPredictedCategoryId()).isNull();
            assertThat(h.getConfidence()).isEqualTo(30);
        });
    }

    @Test
    void categoryOutsideTheListDiscardsTheItem() {
        withGroupedCategories();
        Transaction row = rows(1).get(0);
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 3, 95)));

        service.classify(importId, userId, false);

        assertNothingWrittenTo(row);
        assertThat(history()).isEmpty();
    }

    @Test
    void unknownIndexOrConfidenceOutOfRangeDiscardsOnlyThatItem() {
        withGroupedCategories();
        List<Transaction> rows = rows(3);
        when(classifier.classify(any(), any())).thenReturn(List.of(result(-1, 1, 90), result(3, 1, 90), result(0, 1, 101),
            result(1, 1, -1), result(2, 1, 100)));

        service.classify(importId, userId, false);

        assertNothingWrittenTo(rows.get(0));
        assertNothingWrittenTo(rows.get(1));
        verify(transactionRepository).applyClassification(rows.get(2).getId(), lazer, 100, AI, now);
        assertThat(history()).hasSize(1);
    }

    @Test
    void repeatedIndexDiscardsEveryItemOfThatIndex() {
        withGroupedCategories();
        List<Transaction> rows = rows(2);
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 1, 90), result(0, 2, 91), result(1, 2, 85)));

        service.classify(importId, userId, false);

        assertNothingWrittenTo(rows.get(0));
        verify(transactionRepository).applyClassification(rows.get(1).getId(), mercado, 85, AI, now);
        assertThat(history()).hasSize(1);
    }

    @Test
    void aMissingItemLeavesOnlyThatRowUnclassified() {
        withGroupedCategories();
        List<Transaction> rows = rows(100);
        List<TransactionClassifier.Result> answer = new ArrayList<>();
        IntStream.range(0, 99).forEach(i -> answer.add(result(i, 2, 90)));
        when(classifier.classify(any(), any())).thenReturn(answer);

        service.classify(importId, userId, false);

        assertNothingWrittenTo(rows.get(99));
        verify(transactionRepository, times(99)).applyClassification(any(), eq(mercado), eq(90), eq(AI), eq(now));
        verify(importRepository).addClassified(importId, 100, now);
    }

    @Test
    void twoHundredAndFiftyRowsGoInThreeBatchesOfCallsOf25AndTheProgressFollows() {
        withGroupedCategories();
        rows(250);
        when(classifier.classify(any(), any())).thenReturn(List.of());

        service.classify(importId, userId, false);

        verify(classifier, times(10)).classify(any(), any());
        InOrder order = inOrder(importRepository);
        order.verify(importRepository).startClassification(importId, 250, now);
        order.verify(importRepository, times(2)).addClassified(importId, 100, now);
        order.verify(importRepository).addClassified(importId, 50, now);
    }

    @Test
    void aFailedBatchDoesNotStopTheNextOnes() {
        withGroupedCategories();
        List<Transaction> rows = rows(250);
        // the calls of a batch run at the same time, so the answer goes by the rows of the call
        when(classifier.classify(any(), any())).thenAnswer(invocation -> {
            List<TransactionClassifier.Item> items = invocation.getArgument(0);
            String description = items.get(0).description();
            if (description.equals("COMPRA 100")) {
                throw new IllegalStateException("timeout");
            }
            if (description.equals("COMPRA 0")) {
                return List.of(result(0, 2, 90));
            }
            return description.equals("COMPRA 200") ? List.of(result(0, 1, 85)) : List.of();
        });

        service.classify(importId, userId, false);

        verify(transactionRepository).applyClassification(rows.get(0).getId(), mercado, 90, AI, now);
        assertNothingWrittenTo(rows.get(100));
        verify(transactionRepository).applyClassification(rows.get(200).getId(), lazer, 85, AI, now);
        verify(importRepository, times(2)).addClassified(importId, 100, now);
        verify(importRepository).addClassified(importId, 50, now);
    }

    @Test
    void aDatabaseFailureInOneBatchDoesNotStopTheNextOnes() {
        withGroupedCategories();
        List<Transaction> rows = rows(250);
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 2, 90)));
        when(importRepository.addClassified(eq(importId), anyInt(), any()))
            .thenReturn(1)
            .thenThrow(new org.springframework.dao.QueryTimeoutException("database down"))
            .thenReturn(1);

        assertThatCode(() -> service.classify(importId, userId, false)).doesNotThrowAnyException();

        verify(classifier, times(10)).classify(any(), any());
        verify(transactionRepository).applyClassification(rows.get(0).getId(), mercado, 90, AI, now);
        assertNothingWrittenTo(rows.get(100));
        verify(transactionRepository).applyClassification(rows.get(200).getId(), mercado, 90, AI, now);
        verify(transactionManager).rollback(any());
        verify(transactionManager, times(2)).commit(any());
    }

    @Test
    void theFourCallsOfABatchRunAtTheSameTime() {
        withGroupedCategories();
        rows(100);
        CountDownLatch allStarted = new CountDownLatch(4);
        AtomicInteger together = new AtomicInteger();
        when(classifier.classify(any(), any())).thenAnswer(invocation -> {
            allStarted.countDown();
            if (allStarted.await(5, TimeUnit.SECONDS)) {
                together.incrementAndGet();
            }
            return List.of();
        });

        service.classify(importId, userId, false);

        assertThat(together).hasValue(4);
    }

    @Test
    void aFailedCallLeavesOnlyItsRowsUnclassified() {
        withGroupedCategories();
        List<Transaction> rows = rows(100);
        when(classifier.classify(any(), any())).thenAnswer(invocation -> {
            List<TransactionClassifier.Item> items = invocation.getArgument(0);
            int first = items.get(0).index();
            if (first == 25) {
                throw new IllegalStateException("timeout");
            }
            return List.of(result(first, 2, 90));
        });

        service.classify(importId, userId, false);

        verify(transactionRepository).applyClassification(rows.get(0).getId(), mercado, 90, AI, now);
        assertNothingWrittenTo(rows.get(25));
        verify(transactionRepository).applyClassification(rows.get(50).getId(), mercado, 90, AI, now);
        verify(transactionRepository).applyClassification(rows.get(75).getId(), mercado, 90, AI, now);
        verify(importRepository).addClassified(importId, 100, now);
    }

    @Test
    void anIndexOfAnotherCallIsDiscarded() {
        withGroupedCategories();
        List<Transaction> rows = rows(50);
        when(classifier.classify(any(), any())).thenAnswer(invocation -> {
            List<TransactionClassifier.Item> items = invocation.getArgument(0);
            return items.get(0).index() == 0 ? List.of(result(30, 2, 90)) : List.of();
        });

        service.classify(importId, userId, false);

        assertNothingWrittenTo(rows.get(30));
    }

    @Test
    void anEmptyAnswerStillCountsInTheProgressAndWritesNothing() {
        withGroupedCategories();
        rows(2);
        when(classifier.classify(any(), any())).thenReturn(List.of());

        service.classify(importId, userId, false);

        verify(importRepository).startClassification(importId, 2, now);
        verify(importRepository).addClassified(importId, 2, now);
        verify(transactionRepository, never()).applyClassification(any(), any(), anyInt(), any(), any());
        verify(historyRepository, never()).saveAll(any());
    }

    @Test
    void nothingToSendMeansNoCallAndNoTotal() {
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of());

        service.classify(importId, userId, false);

        verifyNoInteractions(classifier);
        verify(importRepository, never()).startClassification(any(), anyInt(), any());
    }

    @Test
    void withoutChildCategoriesThereIsNoCallAndGroupsAreNotOffered() {
        rows(2);  // the categories of setUp are all groups

        service.classify(importId, userId, false);

        verifyNoInteractions(classifier);
        verify(importRepository, never()).startClassification(any(), anyInt(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsMaskedDescriptionsAndOnlyChildCategoriesWithTheirGroup() {
        withGroupedCategories();
        Transaction row = row("PIX 123.456.789-09");
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of(row));
        when(classifier.classify(any(), any())).thenReturn(List.of());

        service.classify(importId, userId, true);

        ArgumentCaptor<List<TransactionClassifier.Item>> items = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<TransactionClassifier.CategoryChoice>> categories = ArgumentCaptor.forClass(List.class);
        verify(classifier).classify(items.capture(), categories.capture());
        assertThat(items.getValue()).containsExactly(
            new TransactionClassifier.Item(0, "PIX ***", new BigDecimal("10.00"), "DEBIT", "cartão"));
        assertThat(categories.getValue()).containsExactly(
            new TransactionClassifier.CategoryChoice(1, "Lazer", "Alimentação"),
            new TransactionClassifier.CategoryChoice(2, "Mercado", "Alimentação"));
        assertThat(row.getDescription()).isEqualTo("PIX 123.456.789-09");
        assertThat(row.getOriginalDescription()).isEqualTo("PIX 123.456.789-09");
    }

    @Test
    void theFuturesOfAnInstallmentInheritWithoutHistoryOfTheirOwn() {
        withGroupedCategories();
        Transaction row = row("LOJA 03/10");
        row.setInstallmentGroupId(groupId);
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of(row));
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 2, 88)));

        service.classify(importId, userId, true);

        verify(transactionRepository).applyClassificationToFutures(importId, groupId, mercado, 88, AI, now);
        assertThat(history()).hasSize(1);
    }

    @Test
    void anInstallmentWithoutAnAnswerLeavesItsFuturesAlone() {
        withGroupedCategories();
        Transaction row = row("LOJA 03/10");
        row.setInstallmentGroupId(groupId);
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of(row));
        when(classifier.classify(any(), any())).thenReturn(List.of());

        service.classify(importId, userId, true);

        verify(transactionRepository, never()).applyClassificationToFutures(any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void anInstallmentWithNoCategoryLeavesItsFuturesAlone() {
        withGroupedCategories();
        Transaction row = row("LOJA 03/10");
        row.setInstallmentGroupId(groupId);
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of(row));
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, null, 20)));

        service.classify(importId, userId, true);

        verify(transactionRepository, never()).applyClassificationToFutures(any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void anImportThatLeftProcessingStopsTheLoopAndWritesNothing() {
        withGroupedCategories();
        rows(250);
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 2, 90)));
        when(importRepository.addClassified(eq(importId), anyInt(), any())).thenReturn(0);

        service.classify(importId, userId, false);

        verify(classifier, times(4)).classify(any(), any());
        verify(transactionRepository, never()).applyClassification(any(), any(), anyInt(), any(), any());
    }

    @Test
    void anImportThatAlreadyLeftProcessingIsNotSent() {
        withGroupedCategories();
        rows(2);
        when(importRepository.startClassification(eq(importId), anyInt(), any())).thenReturn(0);

        service.classify(importId, userId, false);

        verifyNoInteractions(classifier);
    }

    @Test
    void nothingEscapes() {
        when(transactionRepository.findClassifiableByImport(userId, importId))
            .thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> service.classify(importId, userId, false)).doesNotThrowAnyException();
    }

    // Story 5.11

    @Test
    void whenEveryRowMatchesAPatternTheAiIsNotCalledAndTheFuturesInherit() {
        withGroupedCategories();
        Transaction installment = row("UBER *TRIP 12/03 PARC 02/05");
        installment.setInstallmentGroupId(groupId);
        Transaction plain = row("Uber Trip 15/04");
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of(installment, plain));
        when(patternService.match(userId)).thenReturn(Map.of("uber trip", lazer));

        service.classify(importId, userId, true);

        verify(importRepository).addClassified(importId, 0, now);
        verify(transactionRepository).applyClassification(installment.getId(), lazer, 100, PATTERN, now);
        verify(transactionRepository).applyClassification(plain.getId(), lazer, 100, PATTERN, now);
        verify(transactionRepository).applyClassificationToFutures(importId, groupId, lazer, 100, PATTERN, now);
        verify(patternService).touch(userId, Set.of("uber trip"), now);
        verifyNoInteractions(classifier);
        verify(importRepository, never()).startClassification(any(), anyInt(), any());
        assertThat(history()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyTheRowsWithoutAPatternGoToTheAiAndCountInTheTotal() {
        withGroupedCategories();
        Transaction uber = row("UBER TRIP");
        Transaction other = row("PADARIA");
        Transaction empty = row("123456");
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of(uber, other, empty));
        when(patternService.match(userId)).thenReturn(Map.of("uber trip", lazer, "", mercado));
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 2, 90), result(1, 2, 70)));

        service.classify(importId, userId, false);

        verify(transactionRepository).applyClassification(uber.getId(), lazer, 100, PATTERN, now);
        verify(importRepository).startClassification(importId, 2, now);
        ArgumentCaptor<List<TransactionClassifier.Item>> items = ArgumentCaptor.forClass(List.class);
        verify(classifier).classify(items.capture(), any());
        assertThat(items.getValue()).extracting(TransactionClassifier.Item::description)
            .containsExactly("PADARIA", "123456");
        verify(transactionRepository).applyClassification(other.getId(), mercado, 90, AI, now);
        verify(transactionRepository).applyClassification(empty.getId(), mercado, 70, AI, now);
        assertThat(history()).extracting(AiClassificationHistory::getTransactionId)
            .containsExactly(other.getId(), empty.getId());
    }

    @Test
    void withoutAUsablePatternEveryRowGoesToTheAi() {
        // a removed category, one turned into a group or a pattern of another user never comes back from match
        withGroupedCategories();
        Transaction uber = row("UBER TRIP");
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of(uber));
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 2, 90)));

        service.classify(importId, userId, false);

        verify(transactionRepository).applyClassification(uber.getId(), mercado, 90, AI, now);
        verify(transactionRepository, never()).applyClassification(any(), any(), anyInt(), eq(PATTERN), any());
        verify(importRepository, never()).addClassified(importId, 0, now);
    }

    @Test
    void anImportThatLeftProcessingGetsNoPatternAndNoAi() {
        withGroupedCategories();
        Transaction uber = row("UBER TRIP");
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of(uber, row("PADARIA")));
        when(patternService.match(userId)).thenReturn(Map.of("uber trip", lazer));
        when(importRepository.addClassified(eq(importId), eq(0), any())).thenReturn(0);

        service.classify(importId, userId, false);

        verify(transactionRepository, never()).applyClassification(any(), any(), anyInt(), any(), any());
        verifyNoInteractions(classifier);
    }

    @Test
    void genericTermsNeverMatchAndGoToTheAi() {
        withGroupedCategories();
        Transaction pix = row("PIX 98765");
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of(pix));
        when(patternService.match(userId)).thenReturn(Map.of("pix", lazer));
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 2, 90)));

        service.classify(importId, userId, false);

        verify(transactionRepository).applyClassification(pix.getId(), mercado, 90, AI, now);
        verify(transactionRepository, never()).applyClassification(any(), any(), anyInt(), eq(PATTERN), any());
    }

    @Test
    void whenSavingThePatternsFailsEveryRowGoesToTheAi() {
        withGroupedCategories();
        Transaction uber = row("UBER TRIP");
        Transaction other = row("PADARIA");
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of(uber, other));
        when(patternService.match(userId)).thenReturn(Map.of("uber trip", lazer));
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(patternService).touch(any(), any(), any());
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 2, 90), result(1, 2, 80)));

        service.classify(importId, userId, false);

        assertEverythingWentToTheAi(uber, other);
    }

    @Test
    void whenReadingThePatternsFailsEveryRowGoesToTheAi() {
        withGroupedCategories();
        Transaction uber = row("UBER TRIP");
        Transaction other = row("PADARIA");
        when(transactionRepository.findClassifiableByImport(userId, importId)).thenReturn(List.of(uber, other));
        when(patternService.match(userId)).thenThrow(new IllegalStateException("database down"));
        when(classifier.classify(any(), any())).thenReturn(List.of(result(0, 2, 90), result(1, 2, 80)));

        service.classify(importId, userId, false);

        assertEverythingWentToTheAi(uber, other);
    }

    @SuppressWarnings("unchecked")
    private void assertEverythingWentToTheAi(Transaction first, Transaction second) {
        ArgumentCaptor<List<TransactionClassifier.Item>> items = ArgumentCaptor.forClass(List.class);
        verify(classifier).classify(items.capture(), any());
        assertThat(items.getValue()).extracting(TransactionClassifier.Item::description)
            .containsExactly(first.getDescription(), second.getDescription());
        verify(importRepository).startClassification(importId, 2, now);
        verify(transactionRepository).applyClassification(first.getId(), mercado, 90, AI, now);
        verify(transactionRepository).applyClassification(second.getId(), mercado, 80, AI, now);
    }
}
