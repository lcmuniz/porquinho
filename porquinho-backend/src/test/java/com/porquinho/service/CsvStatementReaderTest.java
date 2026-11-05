package com.porquinho.service;

import com.porquinho.entity.Transaction;
import com.porquinho.service.CsvStatementReader.Layout;
import com.porquinho.service.OfxTransactionParser.Entry;
import com.porquinho.service.OfxTransactionParser.Result;
import com.porquinho.service.OfxTransactionParser.SkipReason;
import com.porquinho.service.OfxTransactionParser.SkippedRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Story 5.2: layouts, encodings, separators and rows of the CSV samples, plus the edge cases of the spec. */
class CsvStatementReaderTest {

    private static final Charset CP1252 = Charset.forName("windows-1252");

    private static byte[] sample(String name) throws IOException {
        return new ClassPathResource("import-samples/" + name).getInputStream().readAllBytes();
    }

    private static List<String> descriptions(Result result) {
        return result.entries().stream().map(Entry::description).toList();
    }

    @ParameterizedTest
    @CsvSource({
        "nubank-conta.csv, NUBANK_ACCOUNT, NUBANK, false",
        "nubank-cartao.csv, NUBANK_CARD, NUBANK, true",
        "bb-conta.csv, BANCO_DO_BRASIL, BANCO_DO_BRASIL, false",
        "itau-conta.csv, ITAU, ITAU, false",
        "bradesco-conta.csv, BRADESCO, BRADESCO, false",
        "caixa-conta.csv, CAIXA, CAIXA, false"})
    void eachSampleHasItsLayout(String name, Layout layout, String bank, boolean card) throws IOException {
        assertThat(CsvStatementReader.detect(sample(name))).contains(layout);
        assertThat(layout.bankName()).isEqualTo(bank);
        assertThat(layout.card()).isEqualTo(card);
    }

    @ParameterizedTest
    @CsvSource({
        "nubank-conta.csv, nubank-conta.ofx",
        "bb-conta.csv, bb-conta.ofx",
        "itau-conta.csv, itau-conta.ofx",
        "bradesco-conta.csv, bradesco-conta.ofx",
        "caixa-conta.csv, caixa-conta.ofx"})
    void eachAccountCsvHasTheSameEntriesAsTheOfxOfTheSameBank(String csv, String ofx) throws IOException {
        Result fromCsv = CsvStatementReader.parse(sample(csv), false);
        Result fromOfx = OfxTransactionParser.parse(sample(ofx), false);

        assertThat(fromCsv.skippedRows()).isEmpty();
        assertThat(fromCsv.totalRows()).isEqualTo(fromOfx.totalRows());
        assertThat(fromCsv.entries())
            .extracting(Entry::date, Entry::amount, Entry::type, Entry::cardPayment)
            .containsExactlyElementsOf(fromOfx.entries().stream()
                .map(e -> tuple(e.date(), e.amount(), e.type(), e.cardPayment())).toList());
    }

    @Test
    void nubankAccountKeepsIdentifierAndAccents() throws IOException {
        List<Entry> entries = CsvStatementReader.parse(sample("nubank-conta.csv"), false).entries();

        Entry first = entries.get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(first.amount()).isEqualByComparingTo("2000.00");
        assertThat(first.type()).isEqualTo(Transaction.Type.CREDIT);
        assertThat(first.externalId()).isEqualTo("bdd640fb-0667-1ad1-1c80-317fa3b1799d");
        assertThat(first.description()).startsWith("Transferência recebida pelo Pix - JOANA PEREIRA LIMA - •••");
        assertThat(entries).filteredOn(Entry::cardPayment).extracting(Entry::description)
            .containsExactly("Pagamento da fatura - Cartão Nubank");
    }

    @Test
    void nubankCardHasTheSignInverted() throws IOException {
        Result result = CsvStatementReader.parse(sample("nubank-cartao.csv"), true);

        assertThat(result.entries()).hasSize(9);
        assertThat(result.skippedRows()).isEmpty();
        assertThat(result.entries()).filteredOn(e -> e.type() == Transaction.Type.CREDIT)
            .extracting(Entry::description, Entry::amount, Entry::cardPayment)
            .containsExactly(
                tuple("Pagamento recebido", new java.math.BigDecimal("1243.67"), true),
                tuple("Estorno - Amazon Marketplace", new java.math.BigDecimal("129.90"), false));
        assertThat(result.entries()).filteredOn(e -> e.type() == Transaction.Type.DEBIT).hasSize(7)
            .allSatisfy(e -> {
                assertThat(e.cardPayment()).isFalse();
                assertThat(e.externalId()).isNull();
            });
        assertThat(result.entries().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    void bbIgnoresTheBalanceRowsAndReadsIso88591() throws IOException {
        Result result = CsvStatementReader.parse(sample("bb-conta.csv"), false);

        assertThat(result.entries()).hasSize(10);
        assertThat(result.skippedRows()).isEmpty();
        assertThat(descriptions(result)).noneMatch(d -> d.contains("Saldo") || d.contains("S A L D O"))
            .contains("Tarifa Pacote de Serviços - Cobrança referente 10/2026", "BB Rende Fácil - Rende Facil");
        assertThat(result.entries()).filteredOn(Entry::cardPayment).extracting(Entry::description)
            .containsExactly("Pagamento Cartão de Crédito - OUROCARD VISA");
        assertThat(result.entries()).extracting(Entry::externalId).doesNotContainNull().doesNotContain("0");
    }

    @Test
    void bradescoIgnoresTitleBalanceTotalAndFooterAndUsesTheFilledColumn() throws IOException {
        Result result = CsvStatementReader.parse(sample("bradesco-conta.csv"), false);

        assertThat(result.entries()).hasSize(9);
        assertThat(result.skippedRows()).isEmpty();
        Entry first = result.entries().get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(first.type()).isEqualTo(Transaction.Type.DEBIT);
        assertThat(first.amount()).isEqualByComparingTo("150.00");
        assertThat(first.externalId()).isEqualTo("1001");
        assertThat(result.entries()).filteredOn(e -> e.type() == Transaction.Type.CREDIT)
            .extracting(Entry::amount).map(java.math.BigDecimal::toPlainString)
            .containsExactly("6100.00", "250.00", "5.33");
        assertThat(result.entries()).filteredOn(Entry::cardPayment).extracting(Entry::amount)
            .map(java.math.BigDecimal::toPlainString).containsExactly("1534.22");
    }

    @Test
    void caixaUsesTheDebitCreditColumn() throws IOException {
        Result result = CsvStatementReader.parse(sample("caixa-conta.csv"), false);

        assertThat(result.entries()).hasSize(10);
        assertThat(result.entries()).filteredOn(e -> e.type() == Transaction.Type.CREDIT)
            .extracting(Entry::description).containsExactly("CRED SALARIO", "PIX RECEBIDO", "CRED JUROS POUP");
        assertThat(result.entries().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(result.entries().get(0).externalId()).isEqualTo("700000");
    }

    @Test
    void itauHasNoHeaderAndReadsCp1252() throws IOException {
        Result result = CsvStatementReader.parse(sample("itau-conta.csv"), false);

        assertThat(result.entries()).hasSize(10);
        assertThat(result.entries()).allSatisfy(e -> assertThat(e.externalId()).isNull());
        assertThat(result.entries().get(0).amount()).isEqualByComparingTo("7420.00");
        assertThat(result.entries()).filteredOn(Entry::cardPayment).extracting(Entry::description)
            .containsExactly("ITAU UNIBANCO CARTAO VISA");
    }

    @Test
    void cp1252AccentsAreRead() {
        byte[] content = "Data;Histórico;Docto.;Crédito (R$);Débito (R$);Saldo (R$)\r\n05/10/26;PADARIA SÃO JOSÉ;1;;10,00;0\r\n"
            .getBytes(CP1252);

        Result result = CsvStatementReader.parse(content, false);

        assertThat(CsvStatementReader.detect(content)).contains(Layout.BRADESCO);
        assertThat(descriptions(result)).containsExactly("PADARIA SÃO JOSÉ");
    }

    @Test
    void utf8WithBomIsRead() throws IOException {
        byte[] body = sample("nubank-conta.csv");
        byte[] content = new byte[body.length + 3];
        content[0] = (byte) 0xEF;
        content[1] = (byte) 0xBB;
        content[2] = (byte) 0xBF;
        System.arraycopy(body, 0, content, 3, body.length);

        Result result = CsvStatementReader.parse(content, false);

        assertThat(CsvStatementReader.detect(content)).contains(Layout.NUBANK_ACCOUNT);
        assertThat(result.entries()).hasSize(10);
        assertThat(result.entries().get(0).description()).startsWith("Transferência");
    }

    @Test
    void utf8BomWithACp1252BodyIsReadAsCp1252() {
        byte[] body = "Data;Histórico;Docto.;Crédito (R$);Débito (R$);Saldo (R$)\r\n05/10/26;PADARIA SÃO JOSÉ;1;;10,00;0\r\n"
            .getBytes(CP1252);
        byte[] content = new byte[body.length + 3];
        content[0] = (byte) 0xEF;
        content[1] = (byte) 0xBB;
        content[2] = (byte) 0xBF;
        System.arraycopy(body, 0, content, 3, body.length);

        assertThat(OfxBankDetector.decode(content)).startsWith("Data;Histórico").doesNotContain("\uFFFD");
        assertThat(descriptions(CsvStatementReader.parse(content, false))).containsExactly("PADARIA SÃO JOSÉ");
    }

    @Test
    void caixaWithAnotherDebCredIsSkipped() {
        String csv = "\"Conta\";\"Data_Mov\";\"Nr_Doc\";\"Historico\";\"Valor\";\"Deb_Cred\"\n"
            + "\"1\";\"20261002\";\"7\";\"X\";\"10.00\";\"Z\"\n"
            + "\"1\";\"20261003\";\"8\";\"Y\";\"10.00\";\"D\"\n";

        Result result = CsvStatementReader.parse(csv.getBytes(StandardCharsets.UTF_8), false);

        assertThat(result.skippedRows()).containsExactly(new SkippedRow(2, SkipReason.INVALID_AMOUNT));
        assertThat(descriptions(result)).containsExactly("Y");
    }

    @Test
    void bradescoCreditAndDebitColumns() {
        String csv = "Data;Histórico;Docto.;Crédito (R$);Débito (R$);Saldo (R$)\n"
            + "05/10/26;AMBOS;1;10,00;5,00;0\n"
            + "06/10/26;ZERO OPOSTO;2;0,00;7,50;0\n"
            + "07/10/26;ZERO NO DEBITO;3;8,00;0,00;0\n"
            + "08/10/26;OS DOIS ZERO;4;0,00;0,00;0\n";

        Result result = CsvStatementReader.parse(csv.getBytes(CP1252), false);

        assertThat(result.skippedRows()).containsExactly(new SkippedRow(2, SkipReason.INVALID_AMOUNT),
            new SkippedRow(5, SkipReason.ZERO_AMOUNT));
        assertThat(result.entries()).extracting(Entry::description, Entry::type)
            .containsExactly(tuple("ZERO OPOSTO", Transaction.Type.DEBIT), tuple("ZERO NO DEBITO", Transaction.Type.CREDIT));
        assertThat(result.entries().get(0).amount()).isEqualByComparingTo("7.50");
    }

    @Test
    void shortLineWithAValidDateIsSkippedButTitlesAreIgnored() {
        String csv = "Data,Valor,Identificador,Descrição\n01/10/2026,-10.00\nRodapé qualquer\n02/10/2026,-5.00,x,Ok\n";

        Result result = CsvStatementReader.parse(csv.getBytes(StandardCharsets.UTF_8), false);

        assertThat(result.skippedRows()).containsExactly(new SkippedRow(2, SkipReason.MISSING_AMOUNT));
        assertThat(descriptions(result)).containsExactly("Ok");
    }

    @Test
    void balanceRowsAreIgnoredInEveryLayout() {
        String csv = "30/09/2026;SALDO ANTERIOR;1000,00\n01/10/2026;PIX QRS IFOOD;-10,00\n01/10/2026;SALDO DO DIA;990,00\n";

        Result result = CsvStatementReader.parse(csv.getBytes(CP1252), false);

        assertThat(CsvStatementReader.detect(csv.getBytes(CP1252))).contains(Layout.ITAU);
        assertThat(result.skippedRows()).isEmpty();
        assertThat(descriptions(result)).containsExactly("PIX QRS IFOOD");
    }

    @Test
    void documentWithOnlyZerosHasNoExternalId() {
        String csv = "Data;Histórico;Docto.;Crédito (R$);Débito (R$);Saldo (R$)\n05/10/26;A;000000;;10,00;0\n"
            + "06/10/26;B;000123;;10,00;0\n";

        Result result = CsvStatementReader.parse(csv.getBytes(CP1252), false);

        assertThat(result.entries()).extracting(Entry::externalId).containsExactly(null, "000123");
    }

    @Test
    void invalidRowsAreSkippedWithPositionAndReasonOnly() throws IOException {
        Result result = CsvStatementReader.parse(sample("erro-csv-linhas-invalidas.csv"), false);

        assertThat(result.entries()).hasSize(2);
        assertThat(result.skippedRows()).containsExactly(
            new SkippedRow(3, SkipReason.INVALID_DATE),
            new SkippedRow(4, SkipReason.INVALID_AMOUNT),
            new SkippedRow(5, SkipReason.MISSING_AMOUNT),
            new SkippedRow(6, SkipReason.MISSING_DATE));
        assertThat(result.totalRows()).isEqualTo(6);
    }

    @Test
    void zeroAndTooPreciseAmountsAreSkipped() {
        String csv = "Data,Valor,Identificador,Descrição\n01/10/2026,0.00,a,Zero\n02/10/2026,1.234,b,Tres casas\n"
            + "03/10/2026,1e3,c,Exp\n04/10/2026,-10.00,d,Ok\n";

        Result result = CsvStatementReader.parse(csv.getBytes(StandardCharsets.UTF_8), false);

        assertThat(result.skippedRows()).extracting(SkippedRow::reason)
            .containsExactly(SkipReason.ZERO_AMOUNT, SkipReason.INVALID_AMOUNT, SkipReason.INVALID_AMOUNT);
        assertThat(descriptions(result)).containsExactly("Ok");
    }

    @Test
    void unknownLayoutIsEmpty() throws IOException {
        assertThat(CsvStatementReader.detect(sample("erro-csv-banco-desconhecido.csv"))).isEmpty();
        assertThat(CsvStatementReader.detect(new byte[0])).isEmpty();
        assertThat(CsvStatementReader.detect("a,b,c\n1,2,3\n".getBytes(StandardCharsets.UTF_8))).isEmpty();
        Result result = CsvStatementReader.parse(sample("erro-csv-banco-desconhecido.csv"), false);
        assertThat(result.entries()).isEmpty();
        assertThat(result.skippedRows()).isEmpty();
    }

    @Test
    void headerIgnoresQuotesCaseAccentsAndTheSeparator() {
        String tab = "\"DATA\"\t\"VALOR\"\t\"IDENTIFICADOR\"\t\"DESCRICAO\"\n01/10/2026\t-5,50\tx\tCafé\n";
        String semicolon = "Título do extrato\n\ndata;valor;identificador;descrição\n01/10/2026;-5,50;x;Café\n";

        for (String csv : List.of(tab, semicolon)) {
            Result result = CsvStatementReader.parse(csv.getBytes(StandardCharsets.UTF_8), false);
            assertThat(result.entries()).extracting(Entry::description, Entry::type)
                .containsExactly(tuple("Café", Transaction.Type.DEBIT));
            assertThat(result.entries().get(0).amount()).isEqualByComparingTo("5.50");
        }
    }

    @Test
    void quotedFieldsKeepSeparatorsDoubledQuotesAndLineBreaks() {
        String csv = "Data,Valor,Identificador,Descrição\n"
            + "01/10/2026,\"-1.234,56\",x,\"Loja \"\"Boa\"\", Centro\"\n"
            + "02/10/2026,-1.00,y,\"duas\nlinhas\"\n"
            + "32/10/2026,-1.00,z,ruim\n";

        Result result = CsvStatementReader.parse(csv.getBytes(StandardCharsets.UTF_8), false);

        assertThat(result.entries()).extracting(Entry::description)
            .containsExactly("Loja \"Boa\", Centro", "duas linhas");
        assertThat(result.entries().get(0).amount()).isEqualByComparingTo("1234.56");
        assertThat(result.entries().get(1).originalDescription()).isEqualTo("duas\nlinhas");
        // the line count follows the line break inside the quoted field
        assertThat(result.skippedRows()).containsExactly(new SkippedRow(5, SkipReason.INVALID_DATE));
    }

    @Test
    void datesFollowTheLayout() {
        assertThat(CsvStatementReader.parseDate("03/10/26", Layout.BRADESCO)).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(CsvStatementReader.parseDate("30/09/2026", Layout.BRADESCO)).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(CsvStatementReader.parseDate("2026-10-02", Layout.NUBANK_CARD)).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(CsvStatementReader.parseDate("20261002", Layout.CAIXA)).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(CsvStatementReader.parseDate("31/02/2026", Layout.BANCO_DO_BRASIL)).isNull();
        assertThat(CsvStatementReader.parseDate("2026-10-02", Layout.NUBANK_ACCOUNT)).isNull();
        assertThat(CsvStatementReader.parseDate("02/10/2026", Layout.CAIXA)).isNull();
    }
}
