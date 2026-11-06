package com.porquinho.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Story 5.7: recognizes the installment marker of a card purchase in its description: "Parcela 3/10", "PARC 03/10" or a
 * trailing "03/10" after a space. Valid only when {@code 1 ≤ number ≤ total} and {@code 2 ≤ total ≤ 48}. The caller
 * decides when to apply it (card purchases only, so that a date such as "05/10" in an account description is never
 * taken as an installment). Pure logic with precompiled patterns, no database access (NFR6).
 */
public final class InstallmentDetector {

    static final int MIN_TOTAL = 2;
    static final int MAX_TOTAL = 48;

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** In order: the explicit words first, the bare trailing "dd/dd" last. A further "/" makes it a date (03/10/26). */
    private static final Pattern[] MARKERS = {
        Pattern.compile("(?<![\\p{L}\\p{N}])Parcela\\s*(?<number>\\d{1,2})\\s*/\\s*(?<total>\\d{1,2})(?![\\d/])",
            FLAGS),
        Pattern.compile("(?<![\\p{L}\\p{N}])PARC\\.?\\s*(?<number>\\d{1,2})\\s*/\\s*(?<total>\\d{1,2})(?![\\d/])",
            FLAGS),
        Pattern.compile("(?<=\\s)(?<number>\\d{2})/(?<total>\\d{2})\\s*$"),
    };
    /** Separators left at the ends once the marker is removed ("Magazine Luiza - " → "Magazine Luiza"). */
    private static final Pattern EDGE_SEPARATORS = Pattern.compile("^[\\s\\-–—:]+|[\\s\\-–—:]+$");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * An installment read from a description.
     *
     * @param baseDescription the description without the marker (trimmed, spaces collapsed): what identifies the
     *                        purchase across the months
     */
    public record Installment(int number, int total, String baseDescription) {
    }

    private InstallmentDetector() {
    }

    /** The installment in {@code description}, or empty (null, blank, no marker or invalid numbers). */
    public static Optional<Installment> detect(String description) {
        Matcher matcher = find(description);
        if (matcher == null) {
            return Optional.empty();
        }
        String rest = description.substring(0, matcher.start()) + " " + description.substring(matcher.end());
        String base = EDGE_SEPARATORS.matcher(SPACES.matcher(rest).replaceAll(" ").trim()).replaceAll("");
        return Optional.of(new Installment(Integer.parseInt(matcher.group("number")),
            Integer.parseInt(matcher.group("total")), base));
    }

    /**
     * {@code description} with the installment number replaced by {@code number}, zero-padded to the digits the bank
     * used ("MAGAZINELUIZA  03/10" → "MAGAZINELUIZA  04/10", "Parcela 3/10" → "Parcela 4/10"). Unchanged when the
     * description has no valid marker.
     */
    public static String withNumber(String description, int number) {
        Matcher matcher = find(description);
        if (matcher == null) {
            return description;
        }
        int digits = matcher.end("number") - matcher.start("number");
        String formatted = String.format(Locale.ROOT, "%0" + digits + "d", number);
        return description.substring(0, matcher.start("number")) + formatted
            + description.substring(matcher.end("number"));
    }

    /** The first valid marker, or null. */
    private static Matcher find(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        for (Pattern marker : MARKERS) {
            Matcher matcher = marker.matcher(description);
            while (matcher.find()) {
                int number = Integer.parseInt(matcher.group("number"));
                int total = Integer.parseInt(matcher.group("total"));
                if (total >= MIN_TOTAL && total <= MAX_TOTAL && number >= 1 && number <= total) {
                    return matcher;
                }
            }
        }
        return null;
    }
}
