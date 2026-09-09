package com.codegym.mathclass.aiconfig.credit.repository;

import com.codegym.mathclass.aiconfig.credit.entity.CreditTransaction;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

@Repository
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    List<CreditTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<CreditTransaction> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, CreditTransactionType type);

    List<CreditTransaction> findByTypeOrderByCreatedAtDesc(CreditTransactionType type);

    Page<CreditTransaction> findByUserId(Long userId, Pageable pageable);

    Page<CreditTransaction> findByUserIdAndType(Long userId, CreditTransactionType type, Pageable pageable);

    Page<CreditTransaction> findByType(CreditTransactionType type, Pageable pageable);

    @Query("SELECT t.task, COUNT(t) FROM CreditTransaction t WHERE t.type = :type AND t.task IS NOT NULL GROUP BY t.task")
    List<Object[]> countUsageByTask(@Param("type") CreditTransactionType type);

    @Query("SELECT t.task, COUNT(t) FROM CreditTransaction t WHERE t.type = :type AND t.task IS NOT NULL AND t.createdAt >= :startDate AND t.createdAt < :endDate GROUP BY t.task")
    List<Object[]> countUsageByTaskAndCreatedAtBetween(@Param("type") CreditTransactionType type,
                                                       @Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    @Query("""
        SELECT t.task, t.type, COUNT(t)
        FROM CreditTransaction t
        WHERE t.type IN (:types)
          AND t.task IS NOT NULL
          AND t.createdAt >= :startDate
          AND t.createdAt < :endDate
        GROUP BY t.task, t.type
    """)
    List<Object[]> countTaskTransactionsByTypesAndCreatedAtBetween(
            @Param("types") List<CreditTransactionType> types,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("""
        SELECT t.task,
               SUM(CASE WHEN t.type = com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType.CONSUME THEN 1L ELSE 0L END),
               SUM(CASE WHEN t.type = com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType.REFUND
                        AND (t.description LIKE '%lỗi%' OR t.description LIKE '%hủy%') THEN 1L ELSE 0L END)
        FROM CreditTransaction t
        WHERE t.task IS NOT NULL
          AND t.createdAt >= :startDate
          AND t.createdAt < :endDate
        GROUP BY t.task
    """)
    List<Object[]> countAiCallsAndFailuresByTaskAndCreatedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
