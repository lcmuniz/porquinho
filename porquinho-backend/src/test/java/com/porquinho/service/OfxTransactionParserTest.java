package com.porquinho.service;

import com.porquinho.entity.Transaction;
import com.porquinho.service.OfxTransactionParser.Entry;
import com.porquinho.service.OfxTransactionParser.Result;
import com.porquinho.service.OfxTransactionParser.SkipReason;
import com.porquinho.service.OfxTransactionParser.SkippedRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Story 5.4: reading the STMTTRN blocks of the OFX samples and the edge cases of the spec. */
class OfxTransactionParserTest {

    private static final Charset CP1252 = Charset.forName("windows-1252");

    private static byte[] sample(String name) throws IOException {
        return new ClassPathResource("import-samples/" + name).getInputStream().readAllBytes();
    }

    private static int blocks(byte[] content) {
        return (int) Pattern.compile("<STMTTRN>").matcher(new String(content, CP1252)).results().count();
    }

    @ParameterizedTest
    @CsvSource({"nubank-conta.ofx, 10", "bb-conta.ofx, 10", "itau-conta.ofx, 10", "bradesco-conta.ofx, 9",
        "caixa-conta.ofx, 10"})
    void everyEntryOfEachAccountSampleIsRead(String name, int expected) throws IOException {
        byte[] content = sample(name);

        Result result = OfxTransactionParser.parse(content, false);

        assertThat(blocks(content)).isEqualTo(expected);
        assertThat(result.entries()).hasSize(expected);
        assertThat(result.skippedRows()).isEmpty();
        assertThat(result.totalRows()).isEqualTo(expected);
        assertThat(result.entries()).allSatisfy(entry -> {
            assertThat(entry.amount().signum()).isPositive();
            assertThat(entry.amount().scale()).isEqualTo(2);
            assertThat(entry.date()).hasYear(2026).hasMonthValue(10);
            assertThat(entry.externalId()).isNotBlank();
        });
    }

    @Test
    void nubankAccountEntriesKeepTheirFieldsAndCp1252Accents() throws IOException {
        List<Entry> entries = OfxTransactionParser.parse(sample("nubank-conta.ofx"), false).entries();

        Entry first = entries.get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(first.amount()).isEqualByComparingTo("2000.00");
        assertThat(first.type()).isEqualTo(Transaction.Type.CREDIT);
        assertThat(first.externalId()).isEqualTo("bdd640fb-0667-1ad1-1c80-317fa3b1799d");
        assertThat(first.description()).startsWith("Transferência recebida pelo Pix - JOANA PEREIRA LIMA - •••.432.198-••");
        Entry second = entries.get(1);
        assertThat(second.type()).isEqualTo(Transaction.Type.DEBIT);
        assertThat(second.amount()).isEqualTo(new BigDecimal("54.90"));
        assertThat(second.description()).isEqualTo("Compra no débito - IFOOD *RESTAURANTE SABOR");
    }

    @Test
    void cardPurchasesAreDebitAndThePaymentIsCredit() throws IOException {
        List<Entry> entries = OfxTransactionParser.parse(sample("itau-cartao.ofx"), true).entries();

        assertThat(entries).hasSize(8);
        assertThat(entries).filteredOn(e -> e.type() == Transaction.Type.CREDIT)
            .extracting(Entry::description, Entry::amount, Entry::cardPayment)
            .containsExactly(tuple("PAGAMENTO EFETUADO", new BigDecimal("1876.40"), true));
        assertThat(entries).filteredOn(e -> e.type() == Transaction.Type.DEBIT).hasSize(7)
            .allSatisfy(e -> assertThat(e.cardPayment()).isFalse());
    }

    @Test
    void repeatedSpacesAreCollapsedOnlyInTheDescription() throws IOException {
        Entry magalu = OfxTransactionParser.parse(sample("itau-cartao.ofx"), true).entries().get(0);

        assertThat(magalu.originalDescription()).isEqualTo("MAGAZINELUIZA  03/10");
        assertThat(magalu.description()).isEqualTo("MAGAZINELUIZA 03/10");
    }

    @Test
    void badRowsAreSkippedWithPositionAndReasonOnly() throws IOException {
        Result result = OfxTransactionParser.parse(sample("erro-ofx-linhas-invalidas.ofx"), false);

        assertThat(result.totalRows()).isEqualTo(6);
        assertThat(result.skippedRows()).containsExactly(
            new SkippedRow(2, SkipReason.INVALID_DATE),
            new SkippedRow(3, SkipReason.MISSING_AMOUNT),
            new SkippedRow(4, SkipReason.INVALID_AMOUNT),
            new SkippedRow(5, SkipReason.ZERO_AMOUNT));
        assertThat(result.entries()).extracting(Entry::date, Entry::amount, Entry::type, Entry::description)
            .containsExactly(
                tuple(LocalDate.of(2026, 10, 2), new BigDecimal("150.00"), Transaction.Type.DEBIT, "PIX TRANSF CARLOS 02/10"),
                tuple(LocalDate.of(2026, 10, 9), new BigDecimal("23.70"), Transaction.Type.DEBIT, "PADARIA SÃO JOSÉ"));
    }

    @Test
    void ofxWithoutEntriesHasNoRows() throws IOException {
        String withoutEntries = new String(sample("itau-conta.ofx"), CP1252)
            .replaceAll("(?s)<STMTTRN>.*?</STMTTRN>\\s*", "");

        Result result = OfxTransactionParser.parse(withoutEntries.getBytes(CP1252), false);

        assertThat(result.entries()).isEmpty();
        assertThat(result.totalRows()).isZero();
    }

    @Test
    void xmlOfxInUtf8IsRead() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <?OFX OFXHEADER="200" VERSION="220"?>
            <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
            <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20261103</DTPOSTED><TRNAMT>-10.5</TRNAMT><FITID>X1</FITID><NAME>Açaí &amp; Cia</NAME></STMTTRN>
            <STMTTRN><TRNTYPE>CREDIT</TRNTYPE><DTPOSTED>20261104120000.000[-3:BRT]</DTPOSTED><TRNAMT>1.234,56</TRNAMT></STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
            """;

        List<Entry> entries = OfxTransactionParser.parse(xml.getBytes(StandardCharsets.UTF_8), false).entries();

        assertThat(entries).extracting(Entry::date, Entry::amount, Entry::type, Entry::description, Entry::externalId)
            .containsExactly(
                tuple(LocalDate.of(2026, 11, 3), new BigDecimal("10.50"), Transaction.Type.DEBIT, "Açaí & Cia", "X1"),
                tuple(LocalDate.of(2026, 11, 4), new BigDecimal("1234.56"), Transaction.Type.CREDIT, "Sem descrição", null));
    }

    @Test
    void memoWinsOverNameAndBlankMemoFallsBackToName() {
        String sgml = "<OFX><BANKTRANLIST>\n"
            + "<STMTTRN>\n<DTPOSTED>20261001\n<TRNAMT>-1.00\n<NAME>NOME\n<MEMO>MEMO\n</STMTTRN>\n"
            + "<STMTTRN>\n<DTPOSTED>20261001\n<TRNAMT>-1.00\n<NAME>  NOME  \n<MEMO>   \n</STMTTRN>\n"
            + "</BANKTRANLIST></OFX>\n";

        List<Entry> entries = OfxTransactionParser.parse(sgml.getBytes(StandardCharsets.US_ASCII), false).entries();

        assertThat(entries).extracting(Entry::originalDescription).containsExactly("MEMO", "NOME");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"20261001|2026-10-01", "20261001120000[-3:BRT]|2026-10-01",
        "20240229|2024-02-29", "20230229|", "20261301|", "2026100|", "2026-10-01|", "|"})
    void dateComesFromTheFirstEightDigits(String raw, String expected) {
        LocalDate parsed = raw == null ? null : OfxTransactionParser.parseDate(raw);
        assertThat(parsed).isEqualTo(expected == null ? null : LocalDate.parse(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"-54.90|-54.90", "54,90|54.90", "+10|10.00", "-1.234,56|-1234.56",
        "1,234.56|1234.56", "0.00|0.00", "12.500|12.50", "9999999999999.99|9999999999999.99", "0.005|", "1.234|",
        "1E200000000|", "1e2|", "abc|", "-|", "1.2.3|", "R$10|", "99999999999999.00|"})
    void amountAcceptsDotOrCommaDecimal(String raw, String expected) {
        BigDecimal parsed = OfxTransactionParser.parseAmount(raw);
        assertThat(parsed).isEqualTo(expected == null ? null : new BigDecimal(expected));
    }

    @Test
    void nonBreakingSpacesInTheAmountAreIgnored() {
        assertThat(OfxTransactionParser.parseAmount("1\u00A0234,56")).isEqualTo(new BigDecimal("1234.56"));
        assertThat(OfxTransactionParser.parseAmount("-\u00A01 234,56")).isEqualTo(new BigDecimal("-1234.56"));
    }

    @Test
    void longMemoAndFitidAreCutToTheColumnSizes() {
        String memo = "A".repeat(600);
        String fitid = "F".repeat(300);
        String sgml = "<OFX><BANKTRANLIST>\n<STMTTRN>\n<DTPOSTED>20261001\n<TRNAMT>-1.00\n<FITID>" + fitid
            + "\n<MEMO>" + memo + "\n</STMTTRN>\n</BANKTRANLIST></OFX>\n";

        Entry entry = OfxTransactionParser.parse(sgml.getBytes(StandardCharsets.US_ASCII), false).entries().get(0);

        assertThat(entry.originalDescription()).hasSize(500).isEqualTo(memo.substring(0, 500));
        assertThat(entry.description()).hasSize(500).isEqualTo(memo.substring(0, 500));
        assertThat(entry.externalId()).hasSize(255).isEqualTo(fitid.substring(0, 255));
    }

    @Test
    void cardPaymentsOfTheSamplesAreFlagged() throws IOException {
        assertThat(cardPayments("nubank-conta.ofx", false)).containsExactly("Pagamento da fatura - Cartão Nubank");
        assertThat(cardPayments("itau-conta.ofx", false)).containsExactly("ITAU UNIBANCO CARTAO VISA");
        assertThat(cardPayments("bradesco-conta.ofx", false)).containsExactly("GASTOS CARTAO DE CREDITO BRADESCARD");
        assertThat(cardPayments("itau-cartao.ofx", true)).containsExactly("PAGAMENTO EFETUADO");
        assertThat(cardPayments("bb-conta.ofx", false)).isEmpty();
        assertThat(cardPayments("caixa-conta.ofx", false)).isEmpty();
    }

    @Test
    void cardPaymentDependsOnTheDirectionOfTheTarget() {
        assertThat(OfxTransactionParser.isCardPayment("Pagamento recebido", Transaction.Type.CREDIT, true)).isTrue();
        assertThat(OfxTransactionParser.isCardPayment("Pagamento recebido", Transaction.Type.CREDIT, false)).isFalse();
        assertThat(OfxTransactionParser.isCardPayment("PAGAMENTO DA FATURA", Transaction.Type.DEBIT, true)).isFalse();
        assertThat(OfxTransactionParser.isCardPayment("pagamento da fatura", Transaction.Type.DEBIT, false)).isTrue();
        assertThat(OfxTransactionParser.isCardPayment("Itaú Unibanco Cartão", Transaction.Type.DEBIT, false)).isTrue();
    }

    @Test
    void aWholeYearIsParsedWellUnderThirtySeconds() {
        byte[] content = yearOfEntries(3000).getBytes(CP1252);

        long start = System.nanoTime();
        Result result = OfxTransactionParser.parse(content, false);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result.entries()).hasSize(3000);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(30));
    }

    /** SGML OFX of an account with {@code count} entries spread over 2026. */
    static String yearOfEntries(int count) {
        StringBuilder sb = new StringBuilder("OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\nCHARSET:1252\n\n<OFX>\n"
            + "<BANKMSGSRSV1>\n<STMTTRNRS>\n<STMTRS>\n<BANKACCTFROM>\n<BANKID>0341\n</BANKACCTFROM>\n<BANKTRANLIST>\n");
        LocalDate first = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            LocalDate date = first.plusDays(i % 365);
            sb.append("<STMTTRN>\n<TRNTYPE>DEBIT\n<DTPOSTED>")
                .append(date.toString().replace("-", "")).append("000000[-3:BRT]\n<TRNAMT>-")
                .append(1 + i % 500).append('.').append(String.format("%02d", i % 100))
                .append("\n<FITID>").append(100000 + i).append("\n<MEMO>COMPRA NÚMERO ").append(i)
                .append("\n</STMTTRN>\n");
        }
        sb.append("</BANKTRANLIST>\n</STMTRS>\n</STMTTRNRS>\n</BANKMSGSRSV1>\n</OFX>\n");
        return sb.toString();
    }

    private static List<String> cardPayments(String name, boolean creditCard) throws IOException {
        return OfxTransactionParser.parse(sample(name), creditCard).entries().stream()
            .filter(Entry::cardPayment).map(Entry::description).toList();
    }
}
