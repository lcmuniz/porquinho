package com.porquinho.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bank and truncation of an OFX file, read straight from the bytes without a full parser (Story 5.3). The bank comes
 * from BANKID (leading zeros ignored) when the file has one; without it, from FID (same) and then ORG (by keyword);
 * {@link #OTHER} when that source names none of the five supported banks. A file without "&lt;/OFX&gt;" is truncated. Tags are matched on
 * the ASCII bytes ignoring case, so cp1252 and UTF-8 files alike work and no copy of the content is made.
 */
public final class OfxBankDetector {

    public static final String NUBANK = "NUBANK";
    public static final String BANCO_DO_BRASIL = "BANCO_DO_BRASIL";
    public static final String ITAU = "ITAU";
    public static final String BRADESCO = "BRADESCO";
    public static final String CAIXA = "CAIXA";
    public static final String OTHER = "OTHER";

    private static final Map<String, String> BY_CODE = Map.of(
        "260", NUBANK,
        "1", BANCO_DO_BRASIL,
        "341", ITAU,
        "237", BRADESCO,
        "104", CAIXA
    );

    /** ORG keywords (upper case, without accents), checked in this order. */
    private static final List<Map.Entry<String, String>> BY_ORG_KEYWORD = List.of(
        Map.entry("NUBANK", NUBANK),
        Map.entry("NU PAGAMENTOS", NUBANK),
        Map.entry("BANCO DO BRASIL", BANCO_DO_BRASIL),
        Map.entry("ITAU", ITAU),
        Map.entry("BRADESCO", BRADESCO),
        Map.entry("CAIXA ECONOMICA", CAIXA),
        Map.entry("CAIXA", CAIXA)
    );

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final int MAX_VALUE_LENGTH = 200;

    private OfxBankDetector() {
    }

    /** True when the file has no "&lt;/OFX&gt;" closing tag (an export cut short). */
    public static boolean isTruncated(byte[] content) {
        return indexOf(content, "</OFX>", 0) < 0;
    }

    /** One of the five bank codes, or {@link #OTHER}. */
    public static String detectBank(byte[] content) {
        Optional<String> bankId = tagValue(content, "<BANKID>");
        if (bankId.isPresent()) {
            // a BANKID outside the five decides alone: OTHER, never a FID or ORG of another bank
            return byCode(bankId.get()).orElse(OTHER);
        }
        return tagValue(content, "<FID>").flatMap(OfxBankDetector::byCode)
            .or(() -> tagValue(content, "<ORG>").flatMap(OfxBankDetector::byOrg))
            .orElse(OTHER);
    }

    private static Optional<String> byCode(String value) {
        String digits = value.replaceFirst("^0+(?=\\d)", "");
        return Optional.ofNullable(BY_CODE.get(digits));
    }

    private static Optional<String> byOrg(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT);
        return BY_ORG_KEYWORD.stream()
            .filter(entry -> normalized.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst();
    }

    /**
     * Text right after the first {@code tag}, up to the next '&lt;' or line break, trimmed (SGML leaf tags have no
     * closing tag; XML ones do). Empty when the tag is absent or has no value.
     */
    static Optional<String> tagValue(byte[] content, String tag) {
        int start = indexOf(content, tag, 0);
        if (start < 0) {
            return Optional.empty();
        }
        start += tag.length();
        int end = start;
        while (end < content.length && end - start < MAX_VALUE_LENGTH
            && content[end] != '<' && content[end] != '\n' && content[end] != '\r') {
            end++;
        }
        String value = decode(Arrays.copyOfRange(content, start, end)).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    /** UTF-8 when the bytes are valid UTF-8, otherwise cp1252 (the usual charset of Brazilian OFX). */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, WINDOWS_1252);
        }
    }

    /** First position of the upper-case ASCII {@code tag} at or after {@code from}, ignoring ASCII case; -1 if absent. */
    private static int indexOf(byte[] content, String tag, int from) {
        for (int i = from; i + tag.length() <= content.length; i++) {
            if (matchesAt(content, i, tag)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesAt(byte[] content, int offset, String tag) {
        for (int k = 0; k < tag.length(); k++) {
            int b = content[offset + k];
            if (b >= 'a' && b <= 'z') {
                b -= 'a' - 'A';
            }
            if (b != tag.charAt(k)) {
                return false;
            }
        }
        return true;
    }
}
