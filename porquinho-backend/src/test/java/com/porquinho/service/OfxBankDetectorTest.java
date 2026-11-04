package com.porquinho.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 5.3: bank and truncation of every OFX sample of src/test/resources/import-samples and the edge cases. */
class OfxBankDetectorTest {

    private static byte[] sample(String name) throws IOException {
        return new ClassPathResource("import-samples/" + name).getInputStream().readAllBytes();
    }

    private static byte[] ofx(String signOn, String statement) {
        return ("OFXHEADER:100\nDATA:OFXSGML\n\n<OFX>\n<SIGNONMSGSRSV1><SONRS>" + signOn + "</SONRS></SIGNONMSGSRSV1>\n"
            + "<BANKMSGSRSV1><STMTTRNRS><STMTRS>" + statement + "</STMTRS></STMTTRNRS></BANKMSGSRSV1>\n</OFX>\n")
            .getBytes(StandardCharsets.US_ASCII);
    }

    @ParameterizedTest
    @CsvSource({
        "nubank-conta.ofx, NUBANK",
        "bb-conta.ofx, BANCO_DO_BRASIL",
        "itau-conta.ofx, ITAU",
        "bradesco-conta.ofx, BRADESCO",
        "caixa-conta.ofx, CAIXA",
        "itau-cartao.ofx, ITAU",
    })
    void everySampleHasItsBankAndIsComplete(String name, String bank) throws IOException {
        byte[] content = sample(name);
        assertThat(OfxBankDetector.isTruncated(content)).isFalse();
        assertThat(OfxBankDetector.detectBank(content)).isEqualTo(bank);
    }

    @Test
    void cardWithoutBankIdUsesFid() throws IOException {
        byte[] content = sample("itau-cartao.ofx");
        assertThat(OfxBankDetector.tagValue(content, "<BANKID>")).isEmpty();
        assertThat(OfxBankDetector.detectBank(content)).isEqualTo(OfxBankDetector.ITAU);
    }

    @Test
    void bankIdWinsOverADifferentFid() {
        byte[] content = ofx("<FI><ORG>Itau<FID>341</FI>", "<BANKACCTFROM><BANKID>0260<ACCTID>1</BANKACCTFROM>");
        assertThat(OfxBankDetector.detectBank(content)).isEqualTo(OfxBankDetector.NUBANK);
    }

    @Test
    void unknownBankWithoutKnownOrgIsOther() {
        byte[] content = ofx("<FI><ORG>Banco Qualquer S.A.<FID>077</FI>", "<BANKACCTFROM><BANKID>077<ACCTID>1</BANKACCTFROM>");
        assertThat(OfxBankDetector.detectBank(content)).isEqualTo(OfxBankDetector.OTHER);
    }

    @Test
    void unknownBankIdDecidesAloneEvenWithAKnownFidOrOrg() {
        byte[] knownFid = ofx("<FI><ORG>Banco Qualquer<FID>341</FI>", "<BANKACCTFROM><BANKID>077<ACCTID>1</BANKACCTFROM>");
        byte[] knownOrg = ofx("<FI><ORG>CAIXA ECONOMICA FEDERAL</FI>", "<BANKACCTFROM><BANKID>077<ACCTID>1</BANKACCTFROM>");
        assertThat(OfxBankDetector.detectBank(knownFid)).isEqualTo(OfxBankDetector.OTHER);
        assertThat(OfxBankDetector.detectBank(knownOrg)).isEqualTo(OfxBankDetector.OTHER);
    }

    @Test
    void withoutBankIdAndFidTheOrgKeywordDecides() {
        assertThat(OfxBankDetector.detectBank(ofx("<FI><ORG>NU PAGAMENTOS S.A.</FI>", ""))).isEqualTo("NUBANK");
        assertThat(OfxBankDetector.detectBank(ofx("<FI><ORG>Banco do Brasil</FI>", ""))).isEqualTo("BANCO_DO_BRASIL");
        assertThat(OfxBankDetector.detectBank(ofx("<FI><ORG>BANCO BRADESCO S/A</FI>", ""))).isEqualTo("BRADESCO");
        assertThat(OfxBankDetector.detectBank(ofx("<FI><ORG>CAIXA ECONOMICA FEDERAL</FI>", ""))).isEqualTo("CAIXA");
        assertThat(OfxBankDetector.detectBank(ofx("", ""))).isEqualTo("OTHER");
    }

    @Test
    void orgWithAccentWorksInUtf8AndCp1252() {
        String text = "OFXHEADER:100\n<OFX><SONRS><FI><ORG>Itaú Unibanco S.A.</FI></SONRS></OFX>";
        assertThat(OfxBankDetector.detectBank(text.getBytes(StandardCharsets.UTF_8))).isEqualTo("ITAU");
        assertThat(OfxBankDetector.detectBank(text.getBytes(Charset.forName("windows-1252")))).isEqualTo("ITAU");
    }

    @Test
    void xmlOfxWithClosingTagsAndLowerCase() {
        byte[] content = ("<?xml version=\"1.0\"?><ofx><signonmsgsrsv1><sonrs><fi><org>X</org><fid>999</fid></fi></sonrs>"
            + "</signonmsgsrsv1><bankmsgsrsv1><stmttrnrs><stmtrs><bankacctfrom><bankid>0237</bankid></bankacctfrom>"
            + "</stmtrs></stmttrnrs></bankmsgsrsv1></ofx>").getBytes(StandardCharsets.UTF_8);
        assertThat(OfxBankDetector.isTruncated(content)).isFalse();
        assertThat(OfxBankDetector.detectBank(content)).isEqualTo("BRADESCO");
    }

    @Test
    void zeroPaddedCodesAreNormalized() {
        assertThat(OfxBankDetector.detectBank(ofx("", "<BANKID>001"))).isEqualTo("BANCO_DO_BRASIL");
        assertThat(OfxBankDetector.detectBank(ofx("", "<BANKID>0104"))).isEqualTo("CAIXA");
        assertThat(OfxBankDetector.detectBank(ofx("", "<BANKID>0"))).isEqualTo("OTHER");
    }

    @Test
    void truncatedSampleIsDetected() throws IOException {
        assertThat(OfxBankDetector.isTruncated(sample("erro-ofx-truncado.ofx"))).isTrue();
    }
}
