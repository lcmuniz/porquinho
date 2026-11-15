package com.porquinho.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/v1/data-export (Story 9.3): password of the AES-256 ZIP. Never stored or logged; only the job's char[] copy is zeroed. */
public record DataExportRequest(
    @NotBlank(message = "Informe a senha do arquivo")
    @Size(min = 8, max = 128, message = "A senha precisa ter entre 8 e 128 caracteres")
    String password
) {
    @Override
    public String toString() {
        return "DataExportRequest[password=***]";
    }
}
