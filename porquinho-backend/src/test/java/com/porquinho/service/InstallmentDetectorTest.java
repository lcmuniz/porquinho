package com.porquinho.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 5.7: the installment markers of card descriptions. */
class InstallmentDetectorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Magazine Luiza - Parcela 3/10 | 3  | 10 | Magazine Luiza",
        "Smart Fit - Parcela 2/12      | 2  | 12 | Smart Fit",
        "MAGAZINELUIZA  03/10          | 3  | 10 | MAGAZINELUIZA",
        "CASASBAHIA     07/12          | 7  | 12 | CASASBAHIA",
        "MERCADOLIVRE*LOJA 01/03       | 1  | 3  | MERCADOLIVRE*LOJA",
        "ANUIDADE DIFERENCIADA 05/12   | 5  | 12 | ANUIDADE DIFERENCIADA",
        "LOJA X PARC 03/10             | 3  | 10 | LOJA X",
        "PARC 3/10 LOJA X              | 3  | 10 | LOJA X",
        "LOJA X PARC. 3/10             | 3  | 10 | LOJA X",
        "loja x parcela 12/12          | 12 | 12 | loja x",
        "LOJA 48/48                    | 48 | 48 | LOJA",
    })
    void recognizesTheMarkers(String description, int number, int total, String base) {
        assertThat(InstallmentDetector.detect(description))
            .contains(new InstallmentDetector.Installment(number, total, base));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "LOJA 15/10", "LOJA 0/10", "LOJA 00/10", "LOJA 3/1", "LOJA 01/01", "LOJA 05/50", "LOJA Parcela 3/49",
        "LOJA 03/10 CENTRO", "LOJA03/10", "LOJA 3/10", "UBER *TRIP HELP.UBER.COM", "RAIA DROGASIL",
        "LOJA 03/10/26", "Pagamento recebido", "LOJA PARC 03/10/26", "LOJA Parcela 3/10/2026", "PARC 3/105",
        "SPARC 3/10", "XPARCELA 3/10",
    })
    void ignoresWhatIsNotAValidMarker(String description) {
        assertThat(InstallmentDetector.detect(description)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void nullOrBlankIsNotAnInstallment(String description) {
        assertThat(InstallmentDetector.detect(description)).isEmpty();
        assertThat(InstallmentDetector.withNumber(description, 4)).isEqualTo(description);
    }

    @Test
    void replacesTheNumberKeepingTheDigitsTheBankUsed() {
        assertThat(InstallmentDetector.withNumber("MAGAZINELUIZA  03/10", 4)).isEqualTo("MAGAZINELUIZA  04/10");
        assertThat(InstallmentDetector.withNumber("MAGAZINELUIZA  03/10", 10)).isEqualTo("MAGAZINELUIZA  10/10");
        assertThat(InstallmentDetector.withNumber("Magazine Luiza - Parcela 3/10", 4))
            .isEqualTo("Magazine Luiza - Parcela 4/10");
        assertThat(InstallmentDetector.withNumber("Magazine Luiza - Parcela 9/10", 10))
            .isEqualTo("Magazine Luiza - Parcela 10/10");
        assertThat(InstallmentDetector.withNumber("PARC 03/10 LOJA X", 7)).isEqualTo("PARC 07/10 LOJA X");
        assertThat(InstallmentDetector.withNumber("RAIA DROGASIL", 2)).isEqualTo("RAIA DROGASIL");
    }

    @Test
    void theExplicitWordWinsOverATrailingNumber() {
        assertThat(InstallmentDetector.detect("Parcela 2/5 LOJA 03/10"))
            .contains(new InstallmentDetector.Installment(2, 5, "LOJA 03/10"));
    }
}
