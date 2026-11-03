package com.porquinho.repository;

import com.porquinho.entity.CreditCard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CreditCard (Story 3.3). Every lookup is scoped by user_id; "active" means deleted_at IS NULL.
 */
@Repository
public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {

    /** The user's active cards, oldest first (id breaks ties for rows created in the same instant). */
    @Query("SELECT c FROM CreditCard c WHERE c.userId = :userId AND c.deletedAt IS NULL ORDER BY c.createdAt ASC, c.id ASC")
    List<CreditCard> findActiveByUserId(@Param("userId") UUID userId);

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    /** The user's removed rows, most recently removed first (Story 4.4). */
    @Query("SELECT c FROM CreditCard c WHERE c.userId = :userId AND c.deletedAt IS NOT NULL ORDER BY c.deletedAt DESC, c.id ASC")
    List<CreditCard> findDeletedByUserId(@Param("userId") UUID userId);

    /**
     * The user's active row, row-locked (Story 4.4): an edit, a removal and a restore of the same row are serialized,
     * so an edit loaded before a concurrent removal cannot write deleted_at/deleted_by back to NULL.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreditCard c WHERE c.id = :id AND c.userId = :userId AND c.deletedAt IS NULL")
    Optional<CreditCard> findActiveByIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    /** The user's removed row, row-locked (Story 4.4): two concurrent restores cannot both restore and audit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreditCard c WHERE c.id = :id AND c.userId = :userId AND c.deletedAt IS NOT NULL")
    Optional<CreditCard> findDeletedByIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Every row of the user, removed ones included (LGPD export). */
    List<CreditCard> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /**
     * Hard-delete the user's cards (LGPD account purge, Story 3.3).
     * Must run BEFORE deleting the user row (FK credit_cards.user_id -> users.id).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM credit_cards WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
