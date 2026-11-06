package com.porquinho.service;

import java.util.regex.Pattern;

/**
 * Hides CPFs and CNPJs from a text about to leave the backend (Story 5.8, decision D5): punctuated, digits only,
 * alphanumeric CNPJs, CPFs split by spaces and the partial CPFs banks show ({@code •••.432.198-••}).
 * Unlike {@link PixDetector#maskFullCpf}, nothing of the document stays. Applies only to what is sent: the stored
 * descriptions do not change.
 */
final class SensitiveDataMasker {

    static final String MASK = "***";

    /**
     * Whole documents only: a longer run of digits is left alone. Without punctuation, an alphanumeric CNPJ needs a
     * letter and a digit in its first 12 positions, so that a word ending in two digits ({@code SUPERMERCADO12}) stays.
     */
    private static final Pattern DOCUMENT = Pattern.compile(String.join("|",
        "(?<![A-Z0-9])[A-Z0-9]{2}\\.[A-Z0-9]{3}\\.[A-Z0-9]{3}/[A-Z0-9]{4}-\\d{2}(?![A-Z0-9])",
        "(?<![A-Z0-9])(?=[A-Z0-9]{0,11}[A-Z])(?=[A-Z0-9]{0,11}\\d)[A-Z0-9]{12}\\d{2}(?![A-Z0-9])",
        "(?<!\\d)(?:\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}|\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}"
            + "|\\d{3} \\d{3} \\d{3} \\d{2})(?!\\d)",
        "\\u2022{3}\\.\\d{3}\\.\\d{3}-\\u2022{2}"));

    private SensitiveDataMasker() {
    }

    static String mask(String text) {
        return DOCUMENT.matcher(text).replaceAll(MASK);
    }
}
