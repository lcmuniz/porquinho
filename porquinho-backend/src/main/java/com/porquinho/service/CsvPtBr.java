package com.porquinho.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one CSV format of the exports (Epic 9): Excel pt-BR, UTF-8 with BOM, ";" between fields, CRLF lines,
 * decimal comma with 2 places. Used by the transactions CSV (9.1) and the budget report (9.2).
 */
final class CsvPtBr {

    static final String BOM = "﻿";
    static final String CRLF = "\r\n";

    private CsvPtBr() {
    }

    /** Quotes a field with ; " or a line break; a leading = + - @ gets a ' so a spreadsheet does not run it. */
    static String text(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** 2 places, HALF_UP, decimal comma; null is an empty field. */
    static String money(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
