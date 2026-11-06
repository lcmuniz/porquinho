package com.porquinho.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Classifies one batch of transactions into the user's categories (Story 5.8). Transactions and categories go by short
 * numbers, never by UUID: fewer output tokens, and an invented id is easy to spot. Implementations never throw: on any
 * failure they return an empty list, and the caller leaves the batch "Não classificado".
 */
public interface TransactionClassifier {

    /** {@code description} already masked; {@code type} is DEBIT or CREDIT; {@code target} is "conta" or "cartão". */
    record Item(int index, String description, BigDecimal amount, String type, String target) {
    }

    /** A child category of the user. {@code id} starts at 1 and {@code group} is the name of its parent. */
    record CategoryChoice(int id, String name, String group) {
    }

    /** {@code categoryId} is null when no category fits. Not validated: the caller checks it against what was sent. */
    record Result(int index, Integer categoryId, int confidence) {
    }

    List<Result> classify(List<Item> items, List<CategoryChoice> categories);
}
