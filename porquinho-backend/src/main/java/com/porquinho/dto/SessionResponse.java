package com.porquinho.dto;

import com.porquinho.entity.UserSession;

import java.time.OffsetDateTime;
import java.util.UUID;

public class SessionResponse {

    private UUID sessionId;
    private String deviceInfo;
    private String ipAddress;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastActivity;
    private boolean isCurrent;

    public SessionResponse() {
    }

    public SessionResponse(UUID sessionId, String deviceInfo, String ipAddress, OffsetDateTime createdAt, OffsetDateTime lastActivity, boolean isCurrent) {
        this.sessionId = sessionId;
        this.deviceInfo = deviceInfo;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
        this.lastActivity = lastActivity;
        this.isCurrent = isCurrent;
    }

    public static SessionResponse fromEntity(UserSession session, String currentTokenHash) {
        return new SessionResponse(
            session.getId(),
            session.getDeviceInfo(),
            session.getIpAddress(),
            session.getCreatedAt(),
            session.getLastActivity(),
            session.getJwtTokenHash().equals(currentTokenHash)
        );
    }

    // Getters and Setters
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getLastActivity() { return lastActivity; }
    public void setLastActivity(OffsetDateTime lastActivity) { this.lastActivity = lastActivity; }

    public boolean isCurrent() { return isCurrent; }
    public void setCurrent(boolean current) { isCurrent = current; }
}
