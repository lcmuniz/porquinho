package com.porquinho.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvPtBrTest {

    @Test
    void textQuotesSeparatorsAndNeutralizesFormulas() {
        assertEquals("", CsvPtBr.text(null));
        assertEquals("Mercado", CsvPtBr.text("Mercado"));
        assertEquals("\"a;b\"", CsvPtBr.text("a;b"));
        assertEquals("\"diz \"\"oi\"\"\"", CsvPtBr.text("diz \"oi\""));
        assertEquals("\"'=SOMA(1;2)\"", CsvPtBr.text("=SOMA(1;2)"));
        assertEquals("'-10", CsvPtBr.text("-10"));
    }

    @Test
    void moneyUsesTwoPlacesAndDecimalComma() {
        assertEquals("", CsvPtBr.money(null));
        assertEquals("10,00", CsvPtBr.money(BigDecimal.TEN));
        assertEquals("-1234,57", CsvPtBr.money(new BigDecimal("-1234.565")));
        assertEquals("0,50", CsvPtBr.money(new BigDecimal("0.5")));
    }
}
