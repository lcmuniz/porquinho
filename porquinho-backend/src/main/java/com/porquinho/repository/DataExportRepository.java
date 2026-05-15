package com.porquinho.repository;

import com.porquinho.entity.DataExport;
import com.porquinho.entity.DataExport.ExportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for DataExport entity.
 * Provides CRUD operations and custom query methods for data export tracking.
 */
@Repository
public interface DataExportRepository extends JpaRepository<DataExport, UUID> {

    Optional<DataExport> findByDownloadToken(UUID downloadToken);

    Optional<DataExport> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    List<DataExport> findByStatusAndExpiresAtBefore(ExportStatus status, LocalDateTime dateTime);

    /**
     * Find all exports for a user (Story 1.7 - needed to iterate file paths before purge).
     */
    List<DataExport> findAllByUserId(UUID userId);

    /**
     * Hard-delete all exports for a user (Story 1.7 - account purge).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DataExport d WHERE d.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
