package com.porquinho.repository;

import com.porquinho.entity.TransactionImport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TransactionImport (Stories 5.1 and 5.3). Every user-facing lookup is scoped by user_id; the
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

    /** One of the user's imports; empty when it does not exist or belongs to someone else (Story 5.3). */
    Optional<TransactionImport> findByIdAndUserId(UUID id, UUID userId);

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
     * Marks as FAILED every PROCESSING import with no bank yet created before {@code createdBefore}, whatever the user:
     * imports with no processing running (backend restarted, upload interrupted). An import with its bank detected has
     * finished this stage and waits for the parsing (decision 1A), so it is left alone. Story 5.3.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionImport i SET i.status = com.porquinho.entity.TransactionImport.Status.FAILED, "
        + "i.errorMessage = :message, i.updatedAt = :now "
        + "WHERE i.status = com.porquinho.entity.TransactionImport.Status.PROCESSING AND i.bankName IS NULL "
        + "AND i.createdAt < :createdBefore")
    int failProcessingCreatedBefore(@Param("createdBefore") OffsetDateTime createdBefore,
                                    @Param("message") String message, @Param("now") OffsetDateTime now);
}
