package com.porquinho.service;

import com.porquinho.entity.AuditLog;
import com.porquinho.entity.DataExport;
import com.porquinho.entity.DataExport.ExportStatus;
import com.porquinho.entity.User;
import com.porquinho.entity.User.AuthProvider;
import com.porquinho.entity.UserSession;
import com.porquinho.exception.RateLimitExceededException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AuditLogRepository;
import com.porquinho.repository.DataExportRepository;
import com.porquinho.repository.UserRepository;
import com.porquinho.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataExportServiceTest {

    @Mock
    private DataExportRepository dataExportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private EmailService emailService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DataExportService dataExportService;

    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new User("test@example.com", AuthProvider.EMAIL, null);
        testUser.setId(userId);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());

        ReflectionTestUtils.setField(dataExportService, "exportDirectory", System.getProperty("java.io.tmpdir") + "/porquinho-test-exports");
        ReflectionTestUtils.setField(dataExportService, "expirationHours", 24);
        ReflectionTestUtils.setField(dataExportService, "baseUrl", "http://localhost:8080");
    }

    @Test
    void requestExportShouldCreateExportAndReturnResponse() {
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(dataExportRepository.save(any(DataExport.class))).thenAnswer(inv -> {
            DataExport export = inv.getArgument(0);
            export.setId(UUID.randomUUID());
            export.setCreatedAt(LocalDateTime.now());
            return export;
        });
        when(dataExportRepository.findById(any())).thenReturn(Optional.of(createTestExport()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userSessionRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(auditLogRepository.findByUserIdOrderByTimestampDesc(userId)).thenReturn(List.of());

        var response = dataExportService.requestExport(userId, "127.0.0.1");

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals("PROCESSING", response.getStatus());
        verify(dataExportRepository, atLeastOnce()).save(any(DataExport.class));
    }

    @Test
    void requestExportShouldThrowWhenRateLimited() {
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(false);

        assertThrows(RateLimitExceededException.class, () ->
            dataExportService.requestExport(userId, "127.0.0.1"));

        verify(dataExportRepository, never()).save(any());
    }

    @Test
    void collectUserDataShouldSanitizeSensitiveFields() {
        testUser.setFailedLoginAttempts(3);
        testUser.setLockedUntil(LocalDateTime.now());
        testUser.setLgpdConsentAt(LocalDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        UserSession session = new UserSession(userId, "hash123", "Chrome", "192.168.1.1", OffsetDateTime.now().plusDays(7));
        session.setId(UUID.randomUUID());
        when(userSessionRepository.findAllByUserId(userId)).thenReturn(List.of(session));

        AuditLog log = new AuditLog(userId, "user_login", "192.168.1.1", null);
        log.setId(UUID.randomUUID());
        when(auditLogRepository.findByUserIdOrderByTimestampDesc(userId)).thenReturn(List.of(log));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        // Verify metadata
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertEquals("1.0", metadata.get("data_version"));
        assertEquals(userId.toString(), metadata.get("user_id"));

        // Verify user profile - sensitive fields excluded
        @SuppressWarnings("unchecked")
        Map<String, Object> userProfile = (Map<String, Object>) data.get("user_profile");
        assertEquals("test@example.com", userProfile.get("email"));
        assertFalse(userProfile.containsKey("failed_login_attempts"));
        assertFalse(userProfile.containsKey("locked_until"));
        assertFalse(userProfile.containsKey("deleted_at"));
        assertFalse(userProfile.containsKey("deleted_by"));
        assertTrue(userProfile.containsKey("lgpd_consent_at"));

        // Verify sessions - jwt_token_hash excluded
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) data.get("sessions");
        assertEquals(1, sessions.size());
        assertFalse(sessions.get(0).containsKey("jwt_token_hash"));
        assertEquals("Chrome", sessions.get(0).get("device_info"));

        // Verify audit logs present
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> auditLogs = (List<Map<String, Object>>) data.get("audit_logs");
        assertEquals(1, auditLogs.size());
        assertEquals("user_login", auditLogs.get(0).get("event_type"));
    }

    @Test
    void downloadExportShouldReturnResourceAndMarkDownloadedAndDeleteFile() throws IOException {
        DataExport export = createTestExport();
        export.setStatus(ExportStatus.READY);
        export.setExpiresAt(LocalDateTime.now().plusHours(12));

        Path tempFile = Files.createTempFile("test-export", ".json");
        Files.writeString(tempFile, "{\"test\": true}");
        export.setFilePath(tempFile.toString());

        UUID token = export.getDownloadToken();
        when(dataExportRepository.findByDownloadToken(token)).thenReturn(Optional.of(export));
        when(dataExportRepository.save(any(DataExport.class))).thenAnswer(inv -> inv.getArgument(0));

        Resource resource = dataExportService.downloadExport(token.toString(), "127.0.0.1");

        assertNotNull(resource);
        assertEquals(ExportStatus.DOWNLOADED, export.getStatus());
        assertNotNull(export.getDownloadedAt());
        verify(dataExportRepository).save(export);
        verify(auditLogService).log("data_export_downloaded", export.getUserId(), "127.0.0.1");

        // File should be deleted after download (AC4: single-use)
        assertFalse(Files.exists(tempFile));
    }

    @Test
    void downloadExportShouldThrowWhenExpired() {
        DataExport export = createTestExport();
        export.setStatus(ExportStatus.READY);
        export.setExpiresAt(LocalDateTime.now().minusHours(1));

        UUID token = export.getDownloadToken();
        when(dataExportRepository.findByDownloadToken(token)).thenReturn(Optional.of(export));
        when(dataExportRepository.save(any(DataExport.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(ResourceNotFoundException.class, () ->
            dataExportService.downloadExport(token.toString(), "127.0.0.1"));
    }

    @Test
    void downloadExportShouldThrowWhenAlreadyDownloaded() {
        DataExport export = createTestExport();
        export.setStatus(ExportStatus.DOWNLOADED);

        UUID token = export.getDownloadToken();
        when(dataExportRepository.findByDownloadToken(token)).thenReturn(Optional.of(export));

        assertThrows(ResourceNotFoundException.class, () ->
            dataExportService.downloadExport(token.toString(), "127.0.0.1"));
    }

    @Test
    void getExportStatusShouldReturnLatestExport() {
        DataExport export = createTestExport();
        export.setStatus(ExportStatus.READY);

        when(dataExportRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(export));

        var response = dataExportService.getExportStatus(userId);

        assertNotNull(response);
        assertEquals("READY", response.getStatus());
    }

    @Test
    void getExportStatusShouldReturnNullWhenNoExport() {
        when(dataExportRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        var response = dataExportService.getExportStatus(userId);

        assertNull(response);
    }

    @Test
    void cleanupExpiredExportsShouldDeleteFilesAndUpdateStatus() throws IOException {
        DataExport export = createTestExport();
        export.setStatus(ExportStatus.READY);
        Path tempFile = Files.createTempFile("test-expired", ".json");
        Files.writeString(tempFile, "{}");
        export.setFilePath(tempFile.toString());

        when(dataExportRepository.findByStatusAndExpiresAtBefore(eq(ExportStatus.READY), any()))
            .thenReturn(List.of(export));
        when(dataExportRepository.save(any(DataExport.class))).thenAnswer(inv -> inv.getArgument(0));

        dataExportService.cleanupExpiredExports();

        assertEquals(ExportStatus.EXPIRED, export.getStatus());
        assertFalse(Files.exists(tempFile));
        verify(dataExportRepository).save(export);
    }

    private DataExport createTestExport() {
        DataExport export = new DataExport(userId);
        export.setId(UUID.randomUUID());
        export.setDownloadToken(UUID.randomUUID());
        export.setCreatedAt(LocalDateTime.now());
        export.setExpiresAt(LocalDateTime.now().plusHours(24));
        return export;
    }
}
