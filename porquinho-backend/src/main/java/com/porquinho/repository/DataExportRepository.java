package com.porquinho.repository;

import com.porquinho.entity.DataExport;
import com.porquinho.entity.DataExport.ExportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
