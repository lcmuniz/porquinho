package com.porquinho.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 5.8: what leaves the backend has no CPF nor CNPJ, whatever the format. */
class SensitiveDataMaskerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "PIX 123.456.789-09 | PIX ***",
        "PIX 12345678909 | PIX ***",
        "PIX 123456789-09 | PIX ***",
        "PIX RECEBIDO 12.345.678/0001-95 LTDA | PIX RECEBIDO *** LTDA",
        "TED 12345678000195 | TED ***",
        "TED 12345678/000195 | TED ***",
        "CPF:12345678909,CNPJ:12345678000195 | CPF:***,CNPJ:***",
        "REF12345678909X | REF***X",
        "CNPJ 12.ABC.345/01DE-35 LTDA | CNPJ *** LTDA",
        "CNPJ 12ABC34501DE35 LTDA | CNPJ *** LTDA",
        "PIX 123 456 789 09 RECEBIDO | PIX *** RECEBIDO",
        "PIX •••.432.198-•• RECEBIDO | PIX *** RECEBIDO",
    })
    void masksDocuments(String text, String expected) {
        assertThat(SensitiveDataMasker.mask(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "MERCADO EXTRA 1234",
        "COMPRA 03/10",
        "BOLETO 123456789012",
        "CODIGO 123456789012345",
        "VALOR 1.234,56",
        "SUPERMERCADO12",
    })
    void leavesEverythingElse(String text) {
        assertThat(SensitiveDataMasker.mask(text)).isEqualTo(text);
    }

    @Test
    void masksEveryDocumentOfTheText() {
        assertThat(SensitiveDataMasker.mask("DE 123.456.789-09 PARA 98765432100")).isEqualTo("DE *** PARA ***");
    }
}
