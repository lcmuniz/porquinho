package com.porquinho.repository;

import com.porquinho.entity.TransactionImport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for TransactionImport (Story 5.1). Every lookup is scoped by user_id.
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
}
