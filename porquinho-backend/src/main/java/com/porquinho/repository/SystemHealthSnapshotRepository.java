package com.porquinho.repository;

import com.porquinho.entity.SystemHealthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

public interface SystemHealthSnapshotRepository extends JpaRepository<SystemHealthSnapshot, Long> {

    /** One row: [minutes with a snapshot, requests, errors, total ms, 2xx requests]; the sums are null without rows. */
    @Query("SELECT COUNT(s), SUM(s.requestCount), SUM(s.errorCount), SUM(s.totalTimeMs), SUM(s.okRequests) "
        + "FROM SystemHealthSnapshot s WHERE s.recordedAt >= :from")
    List<Object[]> totalsSince(@Param("from") OffsetDateTime from);

    @Query("SELECT MIN(s.recordedAt) FROM SystemHealthSnapshot s")
    OffsetDateTime firstRecordedAt();

    List<SystemHealthSnapshot> findByRecordedAtGreaterThanEqualOrderByRecordedAt(OffsetDateTime from);

    /** Own transaction: a failed purge never rolls back the snapshot just saved. */
    @Modifying
    @Transactional
    @Query("DELETE FROM SystemHealthSnapshot s WHERE s.recordedAt < :before")
    int deleteRecordedBefore(@Param("before") OffsetDateTime before);
}
