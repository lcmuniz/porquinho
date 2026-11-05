package com.porquinho.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.entity.Transaction;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Story 5.6: recognizes a Pix entry by its description and builds the {@code pix_details} JSON. Every bank pattern is
 * tried on every import, whatever the detected bank (so OTHER and the card are covered too), and the word "pix" alone
 * is the last resort, without extraction. Pure logic with precompiled patterns, no database access (NFR6).
 *
 * <p>The JSON always has {@code detected: true} and {@code direction}, which comes from the type (DEBIT → SENT,
 * CREDIT → RECEIVED), never from the words of the description. {@code counterpart_name}, {@code document} and
 * {@code counterpart_bank} ({@code name}, {@code code}, {@code agency}, {@code account}) appear only when present.
 * Extracted text is kept as the description shows it (NFC), only trimmed; every full CPF in it is masked before it
 * is stored.
 */
public final class PixDetector {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** Masked CPF (as the bank shows it) or CNPJ: the anchor between the name and the bank in the Nubank text. */
    private static final String DOCUMENT = "[•*\\d]{3}\\.\\d{3}\\.\\d{3}-[•*\\d]{2}|\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}";
    /** Day/month (optionally /yy or /yyyy) the banks put in the text. */
    private static final String DATE = "\\d{2}/\\d{2}(?:/(?:\\d{4}|\\d{2}))?";
    /** The date the banks append (Itaú, Bradesco), possibly glued to the name ("MARIA S11/10"). */
    private static final String TRAILING_DATE = "\\s*(?:" + DATE + ")?\\s*$";

    // Nubank: "Transferência enviada pelo Pix - NAME - DOC - BANK (0001) Agência: 1234 Conta: 98765-4"
    private static final Pattern NUBANK = Pattern.compile("^Transfer[êe]ncia (?:enviada|recebida) pelo Pix - (?<rest>.*)$", FLAGS);
    private static final Pattern NUBANK_ANCHORED =
        Pattern.compile("^(?<name>.*?) - (?<document>" + DOCUMENT + ")(?: - (?<bank>.*))?$", FLAGS);
    private static final Pattern NUBANK_BANK_CODE = Pattern.compile("^(?<name>.*)\\((?<code>\\d+)\\)(?<tail>.*)$");
    private static final Pattern AGENCY = Pattern.compile("Ag[êe]ncia:\\s*(?!Conta:)(?<value>\\S+)", FLAGS);
    private static final Pattern ACCOUNT = Pattern.compile("Conta:\\s*(?<value>\\S+)", FLAGS);
    // Itaú: "PIX TRANSF CARLOS 03/10", "PIX QRS IFOOD 04/10"
    private static final Pattern ITAU = Pattern.compile("^PIX (?:TRANSF|QRS)\\b\\s*(?<name>.*?)" + TRAILING_DATE, FLAGS);
    // BB: "Pix - Enviado - 01/10 09:15 Joana Pereira Lima" (CSV), "PIX - ENVIADO 01/10 09:15 JOANA" (OFX)
    private static final Pattern BB = Pattern.compile(
        "^Pix - (?:Enviado|Recebido)\\b(?:\\s*-)?\\s*(?:" + DATE + "(?:\\s+\\d{2}:\\d{2})?)?\\s*(?<name>.*)$", FLAGS);
    // Bradesco: "TRANSFERENCIA PIX DES: CARLOS EDUARDO SOUZA 03/10", "... REM: ..."
    private static final Pattern BRADESCO =
        Pattern.compile("^TRANSFER[ÊE]NCIA PIX (?:DES|REM):\\s*(?<name>.*?)" + TRAILING_DATE, FLAGS);
    // Caixa: "ENVIO PIX", "PIX RECEBIDO" (nothing to extract)
    private static final Pattern CAIXA = Pattern.compile("^(?:ENVIO PIX|PIX RECEBIDO)\\b", FLAGS);
    /** The word "pix" alone (not "PIXEL"). */
    private static final Pattern FALLBACK = Pattern.compile("(?<![\\p{L}\\p{N}])pix(?![\\p{L}\\p{N}])", FLAGS);

    /** A CPF with all 11 digits shown, anywhere in an extracted text. */
    private static final Pattern FULL_CPF = Pattern.compile("(?<!\\d)\\d{3}\\.(\\d{3})\\.(\\d{3})-\\d{2}(?!\\d)");

    private PixDetector() {
    }

    /**
     * The {@code pix_details} JSON when {@code description} is a Pix, empty otherwise. {@code type} gives the
     * direction.
     */
    public static Optional<String> detect(String description, Transaction.Type type) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        Objects.requireNonNull(type, "type");
        // NFC: a decomposed "ê" (NFD) must match the patterns like the composed one
        String text = Normalizer.normalize(description, Normalizer.Form.NFC).trim();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("detected", true);
        details.put("direction", type == Transaction.Type.DEBIT ? "SENT" : "RECEIVED");

        Matcher matcher;
        if ((matcher = NUBANK.matcher(text)).matches()) {
            nubank(matcher.group("rest"), details);
        } else if ((matcher = ITAU.matcher(text)).matches()
            || (matcher = BB.matcher(text)).matches()
            || (matcher = BRADESCO.matcher(text)).matches()) {
            put(details, "counterpart_name", matcher.group("name"));
        } else if (!CAIXA.matcher(text).find() && !FALLBACK.matcher(text).find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(JSON.writeValueAsString(details));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("pix_details could not be written", e);
        }
    }

    /** After the prefix: the document splits the name from the bank; without it, the name runs to the first " - ". */
    private static void nubank(String rest, Map<String, Object> details) {
        Matcher anchored = NUBANK_ANCHORED.matcher(rest);
        if (!anchored.matches()) {
            int dash = rest.indexOf(" - ");
            put(details, "counterpart_name", dash >= 0 ? rest.substring(0, dash) : rest);
            return;
        }
        put(details, "counterpart_name", anchored.group("name"));
        put(details, "document", anchored.group("document"));
        String bankText = anchored.group("bank");
        if (bankText == null || bankText.isBlank()) {
            return;
        }
        Map<String, Object> bank = new LinkedHashMap<>();
        Matcher code = NUBANK_BANK_CODE.matcher(bankText.trim());
        String tail;
        if (code.matches()) {
            put(bank, "name", code.group("name"));
            put(bank, "code", code.group("code"));
            tail = code.group("tail");
        } else {
            Matcher agencyStart = AGENCY.matcher(bankText);
            int end = agencyStart.find() ? agencyStart.start() : bankText.length();
            put(bank, "name", bankText.substring(0, end));
            tail = bankText.substring(end);
        }
        Matcher agency = AGENCY.matcher(tail);
        if (agency.find()) {
            put(bank, "agency", agency.group("value"));
        }
        Matcher account = ACCOUNT.matcher(tail);
        if (account.find()) {
            put(bank, "account", account.group("value"));
        }
        if (!bank.isEmpty()) {
            details.put("counterpart_bank", bank);
        }
    }

    /** Every CPF with all 11 digits shown becomes {@code •••.XXX.XXX-••}; masked CPFs and CNPJs stay as shown. */
    static String maskFullCpf(String text) {
        return FULL_CPF.matcher(text).replaceAll("•••.$1.$2-••");
    }

    /** Trimmed value with full CPFs masked; blank ones are left out (never null in the JSON). */
    private static void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, maskFullCpf(value.trim()));
        }
    }
}
