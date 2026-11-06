package com.porquinho.repository;

import com.porquinho.entity.TransactionImport;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TransactionImport (Stories 5.1, 5.3 and 5.4). Every user-facing lookup is scoped by user_id; the
 * processing updates go by import id.
 */
@Repository
public interface TransactionImportRepository extends JpaRepository<TransactionImport, UUID> {

    /** The user's imports, most recent first (id breaks ties), limited by {@code page}. Served by idx_transaction_imports_user_created. */
    @Query("SELECT i FROM TransactionImport i WHERE i.userId = :userId ORDER BY i.createdAt DESC, i.id DESC")
    List<TransactionImport> findRecentByUserId(@Param("userId") UUID userId, Pageable page);

    /** Every row of the user, oldest first (LGPD export). */
    @Query("SELECT i FROM TransactionImport i WHERE i.userId = :userId ORDER BY i.createdAt ASC, i.id ASC")
    List<TransactionImport> findByUserIdOrdered(@Param("userId") UUID userId);

    /**
     * Hard-delete the user's imports (LGPD account purge, Story 5.1).
     * Must run BEFORE deleting the accounts, the cards and the user row (FKs to all three).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM transaction_imports WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);

    /** The user's imports among {@code ids}: the format of the imports existing transactions came from (Story 5.5). */
    @Query("SELECT i FROM TransactionImport i WHERE i.userId = :userId AND i.id IN :ids")
    List<TransactionImport> findByUserIdAndIdIn(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    /** One of the user's imports; empty when it does not exist or belongs to someone else (Story 5.3). */
    Optional<TransactionImport> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Same as {@link #findByIdAndUserId}, with the row locked until the end of the transaction: the status read stays
     * true while the batch approval of Story 5.9 runs, even with a discard at the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM TransactionImport i WHERE i.id = :id AND i.userId = :userId")
    Optional<TransactionImport> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Records the detected bank while the import is still PROCESSING (Story 5.3, decision 1A: the status stays
     * PROCESSING). Returns 0 when the import is gone or already left PROCESSING (e.g. failed as orphan).
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionImport i SET i.bankName = :bankName, i.updatedAt = :now "
        + "WHERE i.id = :id AND i.status = com.porquinho.entity.TransactionImport.Status.PROCESSING")
    int recordBank(@Param("id") UUID id, @Param("bankName") String bankName, @Param("now") OffsetDateTime now);

    /** Marks a PROCESSING import as FAILED with {@code message} (Story 5.3). Returns 0 when it already left PROCESSING. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionImport i SET i.status = com.porquinho.entity.TransactionImport.Status.FAILED, "
        + "i.errorMessage = :message, i.updatedAt = :now "
        + "WHERE i.id = :id AND i.status = com.porquinho.entity.TransactionImport.Status.PROCESSING")
    int markFailed(@Param("id") UUID id, @Param("message") String message, @Param("now") OffsetDateTime now);

    /**
     * Ends the parsing (Story 5.4, decision 1A): records the counters ({@code imported} = transactions saved,
     * {@code duplicates} and {@code transfers} = entries not saved, Story 5.5). The import stays PROCESSING for the
     * classification (Story 5.8). Returns 0 when the import is gone or already left PROCESSING (e.g. failed as orphan);
     * the caller then rolls back.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionImport i SET "
        + "i.totalTransactions = :total, i.importedTransactions = :imported, i.duplicatesFound = :duplicates, "
        + "i.transfersFound = :transfers, i.skippedRows = :skipped, i.updatedAt = :now "
        + "WHERE i.id = :id AND i.status = com.porquinho.entity.TransactionImport.Status.PROCESSING")
    int recordParsed(@Param("id") UUID id, @Param("total") int total, @Param("imported") int imported,
                     @Param("duplicates") int duplicates, @Param("transfers") int transfers,
                     @Param("skipped") int skipped, @Param("now") OffsetDateTime now);

    /** Records how many rows go to the AI (Story 5.8). Returns 0 when the import already left PROCESSING. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionImport i SET i.classificationTotal = :total, i.updatedAt = :now "
        + "WHERE i.id = :id AND i.status = com.porquinho.entity.TransactionImport.Status.PROCESSING")
    int startClassification(@Param("id") UUID id, @Param("total") int total, @Param("now") OffsetDateTime now);

    /**
     * Adds the {@code count} rows of a finished batch to the progress and renews {@code updated_at}, which keeps the
     * orphan sweep away (Story 5.8). Returns 0 when the import already left PROCESSING.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionImport i SET i.classifiedCount = i.classifiedCount + :count, i.updatedAt = :now "
        + "WHERE i.id = :id AND i.status = com.porquinho.entity.TransactionImport.Status.PROCESSING")
    int addClassified(@Param("id") UUID id, @Param("count") int count, @Param("now") OffsetDateTime now);

    /** Ends the pipeline: a PROCESSING import goes to REVIEW (Story 5.8). Returns 0 when it already left PROCESSING. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionImport i SET i.status = com.porquinho.entity.TransactionImport.Status.REVIEW, "
        + "i.updatedAt = :now "
        + "WHERE i.id = :id AND i.status = com.porquinho.entity.TransactionImport.Status.PROCESSING")
    int markReadyForReview(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    /** Discards an import under review (Story 5.9). Returns 0 when it is not in REVIEW. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionImport i SET i.status = com.porquinho.entity.TransactionImport.Status.DISCARDED, "
        + "i.updatedAt = :now "
        + "WHERE i.id = :id AND i.status = com.porquinho.entity.TransactionImport.Status.REVIEW")
    int markDiscarded(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    /**
     * Story 5.5, decision 1A: every entry was a duplicate or an already registered transfer, so nothing is saved and
     * there is nothing to review. Records the counters ({@code imported} = 0) and moves a PROCESSING import straight to
     * COMPLETED. Returns 0 when the import is gone or already left PROCESSING.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionImport i SET i.status = com.porquinho.entity.TransactionImport.Status.COMPLETED, "
        + "i.totalTransactions = :total, i.importedTransactions = 0, i.duplicatesFound = :duplicates, "
        + "i.transfersFound = :transfers, i.skippedRows = :skipped, i.updatedAt = :now "
        + "WHERE i.id = :id AND i.status = com.porquinho.entity.TransactionImport.Status.PROCESSING")
    int markCompletedWithNothingNew(@Param("id") UUID id, @Param("total") int total,
                                    @Param("duplicates") int duplicates, @Param("transfers") int transfers,
                                    @Param("skipped") int skipped, @Param("now") OffsetDateTime now);

    /**
     * Serializes the imports of one target (Story 5.5): a transaction-scoped PostgreSQL advisory lock on {@code key},
     * released at commit or rollback. Taken before reading the existing transactions so that two imports into the same
     * account or card always see each other's rows. Must run inside a transaction (MANDATORY): outside one the lock
     * would be released right away.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS l", nativeQuery = true)
    Integer lockTarget(@Param("key") long key);

    /**
     * Moves to REVIEW every PROCESSING orphan (no change since {@code updatedBefore}) whose rows were already saved
     * (Story 5.8): as FAILED, the rows would stay active and a new upload of the file would see them as duplicates. The
     * rows not reached by the classification stay "Não classificado".
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionImport i SET i.status = com.porquinho.entity.TransactionImport.Status.REVIEW, "
        + "i.updatedAt = :now "
        + "WHERE i.status = com.porquinho.entity.TransactionImport.Status.PROCESSING "
        + "AND i.updatedAt < :updatedBefore AND i.importedTransactions > 0")
    int reviewOrphansWithRows(@Param("updatedBefore") OffsetDateTime updatedBefore, @Param("now") OffsetDateTime now);

    /**
     * Marks as FAILED every PROCESSING orphan (no change since {@code updatedBefore}) with no rows saved, whatever the
     * user and whether its bank was detected: PROCESSING means processing is running (Story 5.4, decision 1A), so an
     * old one has none running anymore (backend restarted, upload interrupted, processing stuck). Stories 5.3, 5.4, 5.8.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionImport i SET i.status = com.porquinho.entity.TransactionImport.Status.FAILED, "
        + "i.errorMessage = :message, i.updatedAt = :now "
        + "WHERE i.status = com.porquinho.entity.TransactionImport.Status.PROCESSING "
        + "AND i.updatedAt < :updatedBefore AND i.importedTransactions = 0")
    int failOrphansWithoutRows(@Param("updatedBefore") OffsetDateTime updatedBefore,
                               @Param("message") String message, @Param("now") OffsetDateTime now);
}
