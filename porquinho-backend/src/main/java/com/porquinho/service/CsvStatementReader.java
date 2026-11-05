package com.porquinho.service;

import com.porquinho.entity.Transaction;
import com.porquinho.service.OfxTransactionParser.Entry;
import com.porquinho.service.OfxTransactionParser.Result;
import com.porquinho.service.OfxTransactionParser.SkipReason;
import com.porquinho.service.OfxTransactionParser.SkippedRow;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads a CSV statement of one of the six known layouts (Story 5.2). Pure rule, no Spring: bytes in, layout (bank and
 * account or card) or entries and skipped rows out, with the value rules and the card payment rule of the OFX reader
 * ({@link OfxTransactionParser}).
 *
 * <ul>
 *   <li>Encoding: UTF-8 BOM or valid UTF-8 is UTF-8, anything else cp1252 (covers ISO-8859-1).</li>
 *   <li>Separator: comma, semicolon or tab; fields may be quoted (a doubled quote inside is one quote).</li>
 *   <li>Layout: by the header (quotes, accents, case, surrounding spaces and trailing empty columns ignored), which may
 *       come after title lines; Itaú has no header and is recognized by its rows ({@code DD/MM/AAAA;descrição;valor}).</li>
 *   <li>Lines that are not entries are ignored and not counted: blank lines, lines with fewer columns than the layout
 *       and no valid date (titles, footers), "Total" lines and balance lines ("SALDO ANTERIOR", "SALDO DO DIA"...).
 *       An entry line with a missing or invalid date or amount, or a zero amount, is skipped and reported with its
 *       line number (1-based) and reason only; a short line with a valid date counts as a missing amount.</li>
 * </ul>
 */
public final class CsvStatementReader {

    /** The header is looked for among the first this many records (title lines plus the header itself). */
    private static final int MAX_HEADER_SEARCH = 30;
    private static final char[] SEPARATORS = {',', ';', '\t'};
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern ITAU_DATE = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");
    private static final Set<String> BALANCE_ROWS = Set.of("SALDO ANTERIOR", "S A L D O", "SALDO", "SALDO DO DIA");
    private static final Pattern ONLY_ZEROS = Pattern.compile("0+");

    private static final DateTimeFormatter DD_MM_YYYY = strict("dd/MM/uuuu");
    private static final DateTimeFormatter YYYY_MM_DD = strict("uuuu-MM-dd");
    private static final DateTimeFormatter YYYYMMDD = strict("uuuuMMdd");
    private static final Pattern DD_MM_YY = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{2})");

    /** How the amount and the direction (DEBIT/CREDIT) are read. */
    private enum AmountRule {
        /** One signed column: negative is DEBIT. */
        SIGNED,
        /** One signed column, card statement: positive is a purchase (DEBIT), negative a payment or refund (CREDIT). */
        INVERTED,
        /** A credit column and a debit column; the filled one tells the direction. */
        CREDIT_DEBIT_COLUMNS,
        /** An unsigned amount and a D/C column. */
        DC_COLUMN
    }

    /** The six layouts of the samples ({@code import-samples/README.md}). */
    public enum Layout {
        NUBANK_ACCOUNT(OfxBankDetector.NUBANK, false, List.of("data", "valor", "identificador", "descricao"),
            0, 3, 1, -1, 2, AmountRule.SIGNED),
        NUBANK_CARD(OfxBankDetector.NUBANK, true, List.of("date", "title", "amount"),
            0, 1, 2, -1, -1, AmountRule.INVERTED),
        BANCO_DO_BRASIL(OfxBankDetector.BANCO_DO_BRASIL, false, List.of("data", "dependencia origem", "historico",
            "data do balancete", "numero do documento", "valor"),
            0, 2, 5, -1, 4, AmountRule.SIGNED),
        ITAU(OfxBankDetector.ITAU, false, null,
            0, 1, 2, -1, -1, AmountRule.SIGNED),
        BRADESCO(OfxBankDetector.BRADESCO, false, List.of("data", "historico", "docto.", "credito (r$)",
            "debito (r$)", "saldo (r$)"),
            0, 1, 3, 4, 2, AmountRule.CREDIT_DEBIT_COLUMNS),
        CAIXA(OfxBankDetector.CAIXA, false, List.of("conta", "data_mov", "nr_doc", "historico", "valor", "deb_cred"),
            1, 3, 4, 5, 2, AmountRule.DC_COLUMN);

        private final String bankName;
        private final boolean card;
        /** Normalized header; null when the layout has none (Itaú). */
        private final List<String> header;
        private final int dateColumn;
        private final int descriptionColumn;
        /** The amount, or the credit column for {@link AmountRule#CREDIT_DEBIT_COLUMNS}. */
        private final int amountColumn;
        /** The debit column ({@link AmountRule#CREDIT_DEBIT_COLUMNS}) or the D/C one ({@link AmountRule#DC_COLUMN}). */
        private final int secondColumn;
        private final int externalIdColumn;
        private final AmountRule amountRule;
        private final int minColumns;

        Layout(String bankName, boolean card, List<String> header, int dateColumn, int descriptionColumn,
               int amountColumn, int secondColumn, int externalIdColumn, AmountRule amountRule) {
            this.bankName = bankName;
            this.card = card;
            this.header = header;
            this.dateColumn = dateColumn;
            this.descriptionColumn = descriptionColumn;
            this.amountColumn = amountColumn;
            this.secondColumn = secondColumn;
            this.externalIdColumn = externalIdColumn;
            this.amountRule = amountRule;
            this.minColumns = 1 + Math.max(Math.max(dateColumn, descriptionColumn),
                Math.max(Math.max(amountColumn, secondColumn), externalIdColumn));
        }

        /** One of the bank codes of {@link OfxBankDetector}. */
        public String bankName() {
            return bankName;
        }

        /** True for a credit card statement, false for a bank account one. */
        public boolean card() {
            return card;
        }
    }

    /** A CSV record: its fields and the line (1-based) where it starts. */
    record Row(List<String> fields, int line) {
    }

    /** Where the entries start: the layout, the separator and the index of the first record after the header. */
    private record Detection(Layout layout, char separator, int firstRow) {
    }

    private CsvStatementReader() {
    }

    /** The layout of {@code content}, or empty when it is none of the six. */
    public static Optional<Layout> detect(byte[] content) {
        return detect(OfxBankDetector.decode(content)).map(Detection::layout);
    }

    /**
     * Reads the entries of {@code content}; {@code creditCard} tells the target (for the card payment rule). A content
     * of no known layout gives no entries and no skipped rows.
     */
    public static Result parse(byte[] content, boolean creditCard) {
        String text = OfxBankDetector.decode(content);
        Optional<Detection> detection = detect(text);
        List<Entry> entries = new ArrayList<>();
        List<SkippedRow> skipped = new ArrayList<>();
        if (detection.isEmpty()) {
            return new Result(entries, skipped);
        }
        Layout layout = detection.get().layout();
        List<Row> rows = records(text, detection.get().separator(), Integer.MAX_VALUE);
        for (int i = detection.get().firstRow(); i < rows.size(); i++) {
            readRow(rows.get(i), layout, creditCard, entries, skipped);
        }
        return new Result(entries, skipped);
    }

    private static Optional<Detection> detect(String text) {
        for (char separator : SEPARATORS) {
            List<Row> rows = records(text, separator, MAX_HEADER_SEARCH);
            for (int i = 0; i < rows.size(); i++) {
                List<String> normalized = normalizedHeader(rows.get(i).fields());
                for (Layout layout : Layout.values()) {
                    if (normalized.equals(layout.header)) {
                        return Optional.of(new Detection(layout, separator, i + 1));
                    }
                }
            }
        }
        // Itaú: no header, the first non-blank line is DD/MM/AAAA;descrição;valor
        List<Row> rows = records(text, ';', MAX_HEADER_SEARCH);
        Optional<Row> first = rows.stream().filter(row -> !isBlank(row)).findFirst();
        if (first.isPresent()) {
            List<String> fields = trimmedWithoutTrailingEmpties(first.get().fields());
            if (fields.size() == 3 && ITAU_DATE.matcher(fields.get(0)).matches() && parseDate(fields.get(0), Layout.ITAU) != null
                && OfxTransactionParser.parseAmount(fields.get(2)) != null) {
                return Optional.of(new Detection(Layout.ITAU, ';', 0));
            }
        }
        return Optional.empty();
    }

    private static void readRow(Row row, Layout layout, boolean creditCard, List<Entry> entries,
                                List<SkippedRow> skipped) {
        List<String> fields = row.fields().stream().map(String::trim).toList();
        if (isBlank(row)) {
            return;
        }
        if (fields.size() < layout.minColumns) {
            // an entry cut short (valid date) is skipped; anything else is a title or footer
            boolean entry = layout.dateColumn < fields.size() && parseDate(fields.get(layout.dateColumn), layout) != null
                && !(layout.descriptionColumn < fields.size() && isBalance(fields.get(layout.descriptionColumn)));
            if (entry) {
                skipped.add(new SkippedRow(row.line(), SkipReason.MISSING_AMOUNT));
            }
            return;
        }
        if ("TOTAL".equals(normalize(fields.get(0)).toUpperCase(Locale.ROOT))) {
            return;
        }
        String description = fields.get(layout.descriptionColumn);
        if (isBalance(description)) {
            return;
        }
        String rawDate = fields.get(layout.dateColumn);
        if (rawDate.isEmpty()) {
            skipped.add(new SkippedRow(row.line(), SkipReason.MISSING_DATE));
            return;
        }
        LocalDate date = parseDate(rawDate, layout);
        if (date == null) {
            skipped.add(new SkippedRow(row.line(), SkipReason.INVALID_DATE));
            return;
        }
        String rawAmount;
        Transaction.Type debitOrCredit = null;  // set when a column, not the sign, tells the direction
        switch (layout.amountRule) {
            case CREDIT_DEBIT_COLUMNS -> {
                // a zero in the other column counts as empty
                String credit = zeroAsEmpty(fields.get(layout.amountColumn));
                String debit = zeroAsEmpty(fields.get(layout.secondColumn));
                if (!credit.isEmpty() && !debit.isEmpty()) {
                    skipped.add(new SkippedRow(row.line(), SkipReason.INVALID_AMOUNT));
                    return;
                }
                rawAmount = credit.isEmpty() ? debit : credit;
                if (rawAmount.isEmpty()) {
                    // both zero (or one zero, one empty): a zero amount, not a missing one
                    rawAmount = fields.get(layout.amountColumn).isEmpty()
                        ? fields.get(layout.secondColumn) : fields.get(layout.amountColumn);
                }
                debitOrCredit = credit.isEmpty() ? Transaction.Type.DEBIT : Transaction.Type.CREDIT;
            }
            case DC_COLUMN -> {
                rawAmount = fields.get(layout.amountColumn);
                String dc = fields.get(layout.secondColumn).toUpperCase(Locale.ROOT);
                debitOrCredit = "D".equals(dc) ? Transaction.Type.DEBIT
                    : "C".equals(dc) ? Transaction.Type.CREDIT : null;
            }
            default -> rawAmount = fields.get(layout.amountColumn);
        }
        if (rawAmount.isEmpty()) {
            skipped.add(new SkippedRow(row.line(), SkipReason.MISSING_AMOUNT));
            return;
        }
        BigDecimal signed = OfxTransactionParser.parseAmount(rawAmount);
        if (signed == null || (layout.amountRule == AmountRule.DC_COLUMN && debitOrCredit == null)) {
            skipped.add(new SkippedRow(row.line(), SkipReason.INVALID_AMOUNT));
            return;
        }
        if (signed.signum() == 0) {
            skipped.add(new SkippedRow(row.line(), SkipReason.ZERO_AMOUNT));
            return;
        }
        Transaction.Type type = switch (layout.amountRule) {
            case SIGNED -> signed.signum() < 0 ? Transaction.Type.DEBIT : Transaction.Type.CREDIT;
            case INVERTED -> signed.signum() > 0 ? Transaction.Type.DEBIT : Transaction.Type.CREDIT;
            default -> debitOrCredit;
        };
        String externalId = layout.externalIdColumn < 0 ? null : fields.get(layout.externalIdColumn);
        if (externalId != null && ONLY_ZEROS.matcher(externalId).matches()) {
            externalId = null;
        }
        entries.add(OfxTransactionParser.entry(date, description, signed.abs(), type, externalId, creditCard));
    }

    /** The date in the format(s) of the layout; null when it is not a real date. */
    static LocalDate parseDate(String raw, Layout layout) {
        try {
            return switch (layout) {
                case NUBANK_CARD -> LocalDate.parse(raw, YYYY_MM_DD);
                case CAIXA -> LocalDate.parse(raw, YYYYMMDD);
                case BRADESCO -> {
                    var shortYear = DD_MM_YY.matcher(raw);
                    yield shortYear.matches()
                        ? LocalDate.parse(shortYear.group(1) + "/" + shortYear.group(2) + "/20" + shortYear.group(3),
                            DD_MM_YYYY)
                        : LocalDate.parse(raw, DD_MM_YYYY);
                }
                default -> LocalDate.parse(raw, DD_MM_YYYY);
            };
        } catch (DateTimeException e) {
            return null;
        }
    }

    /**
     * Splits {@code text} into at most {@code limit} records. A field that starts with a quote runs to the closing
     * quote (it may hold separators and line breaks; a doubled quote is one quote); anything after the closing quote
     * up to the separator is kept as is.
     */
    static List<Row> records(String text, char separator, int limit) {
        List<Row> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        int line = 1;
        int rowLine = 1;
        boolean quoted = false;
        boolean atFieldStart = true;
        int i = 0;
        int n = text.length();
        while (i < n && rows.size() < limit) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    quoted = false;
                } else {
                    if (c == '\n' || (c == '\r' && (i + 1 >= n || text.charAt(i + 1) != '\n'))) {
                        line++;
                    }
                    field.append(c);
                }
                i++;
                continue;
            }
            if (c == '"' && atFieldStart) {
                quoted = true;
                atFieldStart = false;
                i++;
            } else if (c == separator) {
                fields.add(field.toString());
                field.setLength(0);
                atFieldStart = true;
                i++;
            } else if (c == '\r' || c == '\n') {
                fields.add(field.toString());
                field.setLength(0);
                rows.add(new Row(List.copyOf(fields), rowLine));
                fields.clear();
                atFieldStart = true;
                i += (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') ? 2 : 1;
                line++;
                rowLine = line;
            } else {
                field.append(c);
                atFieldStart = false;
                i++;
            }
        }
        if (rows.size() < limit && (!fields.isEmpty() || !field.isEmpty())) {
            fields.add(field.toString());
            rows.add(new Row(List.copyOf(fields), rowLine));
        }
        return rows;
    }

    private static boolean isBalance(String description) {
        return BALANCE_ROWS.contains(normalize(description).toUpperCase(Locale.ROOT).replaceAll("\\s+", " "));
    }

    private static String zeroAsEmpty(String value) {
        BigDecimal amount = value.isEmpty() ? null : OfxTransactionParser.parseAmount(value);
        return amount != null && amount.signum() == 0 ? "" : value;
    }

    private static boolean isBlank(Row row) {
        return row.fields().stream().allMatch(String::isBlank);
    }

    private static List<String> normalizedHeader(List<String> fields) {
        return trimmedWithoutTrailingEmpties(fields).stream()
            .map(f -> normalize(f).toLowerCase(Locale.ROOT).replaceAll("\\s+", " "))
            .toList();
    }

    private static List<String> trimmedWithoutTrailingEmpties(List<String> fields) {
        List<String> trimmed = new ArrayList<>(fields.stream().map(String::trim).toList());
        while (!trimmed.isEmpty() && trimmed.get(trimmed.size() - 1).isEmpty()) {
            trimmed.remove(trimmed.size() - 1);
        }
        return trimmed;
    }

    /** Without accents and surrounding spaces. */
    private static String normalize(String value) {
        return DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("").trim();
    }

    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }
}
