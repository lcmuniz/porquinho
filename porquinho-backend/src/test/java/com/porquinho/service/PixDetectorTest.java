package com.porquinho.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.entity.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static com.porquinho.entity.Transaction.Type.CREDIT;
import static com.porquinho.entity.Transaction.Type.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;

/** Story 5.6: one case per row of the spec matrix, plus the full CPF, quotes in the name and empty descriptions. */
class PixDetectorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(String description, Transaction.Type type) throws Exception {
        String json = PixDetector.detect(description, type).orElseThrow();
        return JSON.readValue(json, Map.class);
    }

    @Test
    void nubankReceivedWithMaskedCpfAndBank() throws Exception {
        assertThat(details("Transferência recebida pelo Pix - JOANA PEREIRA LIMA - •••.432.198-•• - BCO DO BRASIL S.A. "
            + "(0001) Agência: 1234 Conta: 98765-4", CREDIT)).containsExactly(
            Map.entry("detected", true),
            Map.entry("direction", "RECEIVED"),
            Map.entry("counterpart_name", "JOANA PEREIRA LIMA"),
            Map.entry("document", "•••.432.198-••"),
            Map.entry("counterpart_bank",
                Map.of("name", "BCO DO BRASIL S.A.", "code", "0001", "agency", "1234", "account", "98765-4")));
    }

    @Test
    void nubankSentWithCnpj() throws Exception {
        Map<String, Object> details = details("Transferência enviada pelo Pix - MERCADO BOM PRECO LTDA - "
            + "12.345.678/0001-90 - CAIXA ECONOMICA FEDERAL (0104) Agência: 0001 Conta: 4455-6", DEBIT);
        assertThat(details).containsEntry("direction", "SENT")
            .containsEntry("counterpart_name", "MERCADO BOM PRECO LTDA")
            .containsEntry("document", "12.345.678/0001-90")
            .containsEntry("counterpart_bank",
                Map.of("name", "CAIXA ECONOMICA FEDERAL", "code", "0104", "agency", "0001", "account", "4455-6"));
    }

    @Test
    void nubankBankNameWithDashIsNotSplit() throws Exception {
        assertThat(details("Transferência recebida pelo Pix - ANA BEATRIZ COSTA - •••.111.222-•• - NU PAGAMENTOS - IP "
            + "(0260) Agência: 1 Conta: 7788990-1", CREDIT)).containsEntry("counterpart_name", "ANA BEATRIZ COSTA")
            .containsEntry("counterpart_bank",
                Map.of("name", "NU PAGAMENTOS - IP", "code", "0260", "agency", "1", "account", "7788990-1"));
    }

    @Test
    void nubankFullCpfIsMasked() throws Exception {
        assertThat(details("Transferência enviada pelo Pix - CARLOS SOUZA - 123.876.543-21 - ITAÚ UNIBANCO S.A. (0341) "
            + "Agência: 4321 Conta: 11223-3", DEBIT)).containsEntry("document", "•••.876.543-••");
    }

    @Test
    void nubankAsteriskMaskIsKeptAsShown() throws Exception {
        assertThat(details("Transferência enviada pelo Pix - CARLOS - ***.876.543-** - X (1)", DEBIT))
            .containsEntry("document", "***.876.543-**");
    }

    @Test
    void nubankWithoutDocumentKeepsOnlyTheName() throws Exception {
        assertThat(details("Transferência enviada pelo Pix - FULANO DE TAL - BANCO X", DEBIT)).containsOnlyKeys(
            "detected", "direction", "counterpart_name").containsEntry("counterpart_name", "FULANO DE TAL");
    }

    @Test
    void nubankNameWithDashBeforeTheDocumentIsKept() throws Exception {
        assertThat(details("Transferência enviada pelo Pix - LOJA A - B - 12.345.678/0001-90 - BANCO X (0001) "
            + "Agência: 1 Conta: 2-3", DEBIT)).containsEntry("counterpart_name", "LOJA A - B")
            .containsEntry("document", "12.345.678/0001-90");
    }

    @Test
    void nubankBankWithoutCodeButWithAgency() throws Exception {
        assertThat(details("Transferência enviada pelo Pix - FULANO - •••.111.222-•• - BANCO SEM CODIGO "
            + "Agência: 0012 Conta: 345-6", DEBIT)).containsEntry("counterpart_bank",
            Map.of("name", "BANCO SEM CODIGO", "agency", "0012", "account", "345-6"));
    }

    @Test
    void nubankCodeIsTheLastParenthesizedNumber() throws Exception {
        assertThat(details("Transferência enviada pelo Pix - FULANO - •••.111.222-•• - BANCO (2) SA (0001) "
            + "Agência: 1 Conta: 2-3", DEBIT)).containsEntry("counterpart_bank",
            Map.of("name", "BANCO (2) SA", "code", "0001", "agency", "1", "account", "2-3"));
    }

    @Test
    void emptyAgencyDoesNotTakeTheAccountLabel() throws Exception {
        assertThat(details("Transferência enviada pelo Pix - FULANO - •••.111.222-•• - BANCO X (0001) "
            + "Agência: Conta: 123", DEBIT)).containsEntry("counterpart_bank",
            Map.of("name", "BANCO X", "code", "0001", "account", "123"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Transferência enviada pelo Pix - JOSE 123.456.789-01 - BANCO X|JOSE •••.456.789-••",
        "PIX TRANSF JOSE 123.456.789-01 03/10|JOSE •••.456.789-••",
        "Pix - Enviado - 01/10 09:15 Jose 123.456.789-01|Jose •••.456.789-••",
        "TRANSFERENCIA PIX DES: JOSE 123.456.789-01 03/10|JOSE •••.456.789-••",
    })
    void fullCpfInTheNameIsMasked(String description, String name) throws Exception {
        assertThat(details(description, DEBIT)).containsEntry("counterpart_name", name);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "PIX TRANSF CARLOS 03/10/26|CARLOS",
        "PIX TRANSF CARLOS 03/10/2026|CARLOS",
        "TRANSFERENCIA PIX REM: LUCIA 12/10/2026|LUCIA",
        "Pix - Enviado - 01/10/2026 09:15 Joana Lima|Joana Lima",
        "PIX - RECEBIDO 07/10/26 18:03 ROBER|ROBER",
    })
    void dateWithYearStaysOutOfTheName(String description, String name) throws Exception {
        assertThat(details(description, DEBIT)).containsEntry("counterpart_name", name);
    }

    @Test
    void bbNeedsAWordBoundaryAfterTheDirection() throws Exception {
        // only the "pix" fallback: no name taken from "XYZ ..."
        assertThat(details("Pix - EnviadoXYZ Fulano", DEBIT)).containsOnlyKeys("detected", "direction");
    }

    @Test
    void decomposedAccentsMatchLikeComposedOnes() throws Exception {
        String nfd = java.text.Normalizer.normalize("Transferência enviada pelo Pix - FULANO - •••.111.222-•• - "
            + "BANCO X (0001) Agência: 1 Conta: 2-3", java.text.Normalizer.Form.NFD);
        assertThat(details(nfd, DEBIT)).containsEntry("counterpart_name", "FULANO")
            .containsEntry("counterpart_bank", Map.of("name", "BANCO X", "code", "0001", "agency", "1", "account", "2-3"));
        String bradesco = java.text.Normalizer.normalize("TRANSFERÊNCIA PIX DES: FULANO 03/10",
            java.text.Normalizer.Form.NFD);
        assertThat(details(bradesco, DEBIT)).containsEntry("counterpart_name", "FULANO");
    }

    @Test
    void quotesInTheNameStayValidJson() throws Exception {
        String json = PixDetector.detect("Transferência enviada pelo Pix - LOJA \"BOA\" - 12.345.678/0001-90 - X (1)",
            DEBIT).orElseThrow();
        JsonNode node = JSON.readTree(json);
        assertThat(node.get("counterpart_name").asText()).isEqualTo("LOJA \"BOA\"");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "PIX TRANSF CARLOS 03/10|CARLOS",
        "PIX QRS IFOOD 04/10|IFOOD",
        "PIX TRANSF MARIA S11/10|MARIA S",
        "Pix - Enviado - 01/10 09:15 Joana Pereira Lima|Joana Pereira Lima",
        "PIX - ENVIADO 01/10 09:15 JOANA|JOANA",
        "Pix - Recebido - 07/10 18:03 Roberto Alves|Roberto Alves",
        "PIX - RECEBIDO 07/10 18:03 ROBER|ROBER",
        "TRANSFERENCIA PIX DES: CARLOS EDUARDO SOUZA 03/10|CARLOS EDUARDO SOUZA",
        "TRANSFERENCIA PIX REM: LUCIA HELENA MOTA 12/10|LUCIA HELENA MOTA",
    })
    void itauBbAndBradescoGiveTheNameWithoutTheDate(String description, String name) throws Exception {
        assertThat(details(description, DEBIT)).containsOnlyKeys("detected", "direction", "counterpart_name")
            .containsEntry("detected", true).containsEntry("counterpart_name", name);
    }

    @ParameterizedTest
    @CsvSource({"ENVIO PIX,DEBIT,SENT", "PIX RECEBIDO,CREDIT,RECEIVED", "ESTORNO PIX LOJA X,CREDIT,RECEIVED",
        "pagamento via pix,DEBIT,SENT"})
    void caixaAndFallbackOnlyDetect(String description, Transaction.Type type, String direction) throws Exception {
        assertThat(details(description, type)).containsExactly(Map.entry("detected", true),
            Map.entry("direction", direction));
    }

    @Test
    void directionComesFromTheTypeNotTheWords() throws Exception {
        assertThat(details("PIX - ENVIADO 01/10 09:15 JOANA", CREDIT)).containsEntry("direction", "RECEIVED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Compra no débito - UBER *TRIP", "PIXEL STORE", "Compra no débito - IFOOD *RESTAURANTE SABOR",
        "COMPRA COM CARTÃO 05/10 13:42 PA", "TED 237.1234JOAO P SILVA"})
    void notPix(String description) {
        assertThat(PixDetector.detect(description, DEBIT)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nullOrEmptyIsNotPix(String description) {
        assertThat(PixDetector.detect(description, DEBIT)).isEmpty();
    }

    @Test
    void neverWritesNull() {
        for (String description : List.of("PIX TRANSF 03/10", "Pix - Enviado - 01/10 09:15", "ENVIO PIX")) {
            assertThat(PixDetector.detect(description, DEBIT).orElseThrow()).doesNotContain("null")
                .isEqualTo("{\"detected\":true,\"direction\":\"SENT\"}");
        }
    }
}
