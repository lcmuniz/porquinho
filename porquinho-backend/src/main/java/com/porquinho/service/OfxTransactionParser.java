package com.porquinho.service;

import com.porquinho.entity.Transaction;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the entries ({@code <STMTTRN>} blocks) of an OFX file (Story 5.4). Pure rule, no Spring: bytes in, entries
 * and skipped rows out. Works on SGML (leaf tags closed or not) and XML, cp1252 or UTF-8 (decoded as in Story 5.3).
 *
 * <ul>
 *   <li>Date: first 8 digits of DTPOSTED (AAAAMMDD), time zone ignored.</li>
 *   <li>Amount: TRNAMT with dot or comma decimal; negative is DEBIT, positive CREDIT, stored as absolute value
 *       (account and card alike). TRNTYPE is ignored.</li>
 *   <li>Description: MEMO, else NAME, else "Sem descrição"; the original trimmed, the description with repeated
 *       whitespace collapsed. FITID is the optional external id.</li>
 *   <li>A block without a valid date or amount, or with a zero amount, is skipped and reported with its position
 *       (1-based) and reason only.</li>
 *   <li>Card payment (decision 3A): keyword in the description (no accents, upper case); on an account only for
 *       DEBIT, on a card only for CREDIT.</li>
 * </ul>
 */
public final class OfxTransactionParser {

    public static final String NO_DESCRIPTION = "Sem descrição";

    /** Column sizes of V26. */
    static final int MAX_DESCRIPTION_LENGTH = 500;
    static final int MAX_EXTERNAL_ID_LENGTH = 255;
    /** DECIMAL(15,2): at most 13 digits before the point. */
    private static final int MAX_INTEGER_DIGITS = 13;
    /** Optional sign, digits and separators only (no exponent, no other character). */
    private static final Pattern AMOUNT = Pattern.compile("[+-]?[0-9.,]+");

    /** Decision 3A: card payment keywords (upper case, without accents). */
    static final List<String> CARD_PAYMENT_KEYWORDS = List.of(
        "PAGAMENTO DA FATURA",
        "PAGAMENTO EFETUADO",
        "PAGAMENTO RECEBIDO",
        "ITAU UNIBANCO CARTAO",
        "GASTOS CARTAO DE CREDITO"
    );

    private static final Pattern BLOCK_START = Pattern.compile("<STMTTRN>", Pattern.CASE_INSENSITIVE);
    /** Where a block ends when its closing tag is missing: the next block or the end of the list. */
    private static final Pattern BLOCK_END =
        Pattern.compile("</STMTTRN>|<STMTTRN>|</BANKTRANLIST>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DTPOSTED = field("DTPOSTED");
    private static final Pattern TRNAMT = field("TRNAMT");
    private static final Pattern FITID = field("FITID");
    private static final Pattern MEMO = field("MEMO");
    private static final Pattern NAME = field("NAME");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    public enum SkipReason { MISSING_DATE, INVALID_DATE, MISSING_AMOUNT, INVALID_AMOUNT, ZERO_AMOUNT }

    /** One valid entry. {@code amount} is positive with 2 decimals. */
    public record Entry(LocalDate date, String description, String originalDescription, BigDecimal amount,
                        Transaction.Type type, String externalId, boolean cardPayment) {
    }

    /** A skipped block: its position among the blocks (1-based) and why. Never its content. */
    public record SkippedRow(int position, SkipReason reason) {
    }

    /** {@code totalRows} is the number of {@code <STMTTRN>} blocks: entries plus skipped rows. */
    public record Result(List<Entry> entries, List<SkippedRow> skippedRows) {
        public int totalRows() {
            return entries.size() + skippedRows.size();
        }
    }

    private OfxTransactionParser() {
    }

    /** Parses {@code content}; {@code creditCard} tells the target (for the card payment rule). */
    public static Result parse(byte[] content, boolean creditCard) {
        String text = OfxBankDetector.decode(content);
        List<Entry> entries = new ArrayList<>();
        List<SkippedRow> skipped = new ArrayList<>();
        Matcher start = BLOCK_START.matcher(text);
        Matcher end = BLOCK_END.matcher(text);
        int position = 0;
        int from = 0;
        while (start.find(from)) {
            position++;
            int blockStart = start.end();
            int blockEnd = end.find(blockStart) ? end.start() : text.length();
            String block = text.substring(blockStart, blockEnd);
            from = blockEnd;
            readEntry(block, creditCard, position, entries, skipped);
        }
        return new Result(entries, skipped);
    }

    private static void readEntry(String block, boolean creditCard, int position, List<Entry> entries,
                                  List<SkippedRow> skipped) {
        String rawDate = value(block, DTPOSTED);
        if (rawDate == null) {
            skipped.add(new SkippedRow(position, SkipReason.MISSING_DATE));
            return;
        }
        LocalDate date = parseDate(rawDate);
        if (date == null) {
            skipped.add(new SkippedRow(position, SkipReason.INVALID_DATE));
            return;
        }
        String rawAmount = value(block, TRNAMT);
        if (rawAmount == null) {
            skipped.add(new SkippedRow(position, SkipReason.MISSING_AMOUNT));
            return;
        }
        BigDecimal signed = parseAmount(rawAmount);
        if (signed == null) {
            skipped.add(new SkippedRow(position, SkipReason.INVALID_AMOUNT));
            return;
        }
        if (signed.signum() == 0) {
            skipped.add(new SkippedRow(position, SkipReason.ZERO_AMOUNT));
            return;
        }
        Transaction.Type type = signed.signum() < 0 ? Transaction.Type.DEBIT : Transaction.Type.CREDIT;

        String original = value(block, MEMO);
        if (original == null) {
            original = value(block, NAME);
        }
        if (original == null) {
            original = NO_DESCRIPTION;
        }
        original = truncate(original, MAX_DESCRIPTION_LENGTH);
        String description = WHITESPACE.matcher(original).replaceAll(" ");
        String externalId = value(block, FITID);
        if (externalId != null) {
            externalId = truncate(externalId, MAX_EXTERNAL_ID_LENGTH);
        }
        entries.add(new Entry(date, description, original, signed.abs(), type, externalId,
            isCardPayment(description, type, creditCard)));
    }

    /** Decision 3A. */
    static boolean isCardPayment(String description, Transaction.Type type, boolean creditCard) {
        if (type != (creditCard ? Transaction.Type.CREDIT : Transaction.Type.DEBIT)) {
            return false;
        }
        String normalized = DIACRITICS.matcher(Normalizer.normalize(description, Normalizer.Form.NFD)).replaceAll("")
            .toUpperCase(Locale.ROOT);
        return CARD_PAYMENT_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /** AAAAMMDD from the first 8 characters; null when they are not a real date. */
    static LocalDate parseDate(String raw) {
        if (raw.length() < 8) {
            return null;
        }
        String digits = raw.substring(0, 8);
        for (int i = 0; i < digits.length(); i++) {
            if (digits.charAt(i) < '0' || digits.charAt(i) > '9') {
                return null;
            }
        }
        try {
            return LocalDate.parse(digits, DATE);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /**
     * Signed amount with 2 decimals; null when not a plain number (exponent or any other character), with more than 2
     * significant decimals or too large for DECIMAL(15,2). Spaces (also non-breaking) are ignored. Dot or comma
     * decimal; when both appear, the last one is the decimal separator and the other groups thousands.
     */
    static BigDecimal parseAmount(String raw) {
        String s = raw.replace(" ", "").replace("\u00A0", "");
        if (!AMOUNT.matcher(s).matches()) {
            return null;
        }
        int dot = s.lastIndexOf('.');
        int comma = s.lastIndexOf(',');
        if (dot >= 0 && comma >= 0) {
            s = dot > comma ? s.replace(",", "") : s.replace(".", "").replace(',', '.');
        } else {
            s = s.replace(',', '.');
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(s).stripTrailingZeros();
        } catch (NumberFormatException e) {
            return null;
        }
        // checked before any rescale: no silent rounding
        if (amount.scale() > 2 || amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            return null;
        }
        return amount.setScale(2);
    }

    /** Text right after {@code <TAG>} up to the next '&lt;' or line break, trimmed, XML entities decoded; null when empty. */
    private static String value(String block, Pattern tag) {
        Matcher m = tag.matcher(block);
        if (!m.find()) {
            return null;
        }
        String value = unescape(m.group(1)).trim();
        return value.isEmpty() ? null : value;
    }

    private static String unescape(String value) {
        if (value.indexOf('&') < 0) {
            return value;
        }
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&amp;", "&");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).trim();
    }

    private static Pattern field(String tag) {
        return Pattern.compile("<" + tag + ">([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE);
    }
}
