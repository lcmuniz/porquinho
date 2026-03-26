package com.porquinho.dto;

import com.porquinho.entity.DataExport;

import java.time.LocalDateTime;
import java.util.UUID;

public class DataExportResponse {

    private UUID id;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String downloadUrl;

    public DataExportResponse() {
    }

    public DataExportResponse(UUID id, String status, LocalDateTime createdAt, LocalDateTime expiresAt, String downloadUrl) {
        this.id = id;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.downloadUrl = downloadUrl;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
