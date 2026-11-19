package com.porquinho.dto;

import com.porquinho.entity.DataExport;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

public class DataExportResponse {

    private UUID id;
    private String status;
    // Com offset: LocalDateTime sem fuso era lido pelo navegador como hora local (UTC do servidor aparecia como Brasília).
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private String downloadUrl;

    public DataExportResponse() {
    }

    public DataExportResponse(UUID id, String status, LocalDateTime createdAt, LocalDateTime expiresAt, String downloadUrl) {
        this.id = id;
        this.status = status;
        this.createdAt = withOffset(createdAt);
        this.expiresAt = withOffset(expiresAt);
        this.downloadUrl = downloadUrl;
    }

    /** As datas da entidade são LocalDateTime.now() no fuso da JVM. */
    private static OffsetDateTime withOffset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    public static DataExportResponse fromEntity(DataExport export) {
        String downloadUrl = null;
        if (export.getStatus() == DataExport.ExportStatus.READY && export.getDownloadToken() != null) {
            downloadUrl = "/api/v1/data-export/download/" + export.getDownloadToken();
        }
        return new DataExportResponse(
            export.getId(),
            export.getStatus().name(),
            export.getCreatedAt(),
            export.getExpiresAt(),
            downloadUrl
        );
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = withOffset(createdAt);
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = withOffset(expiresAt);
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
