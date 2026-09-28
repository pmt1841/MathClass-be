package com.codegym.mathclass.aiconfig.credit.repository;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import com.codegym.mathclass.bugreport.entity.BugErrorType;
import com.codegym.mathclass.bugreport.entity.BugReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditPurchaseOrderRepository extends JpaRepository<CreditPurchaseOrder, Long> {

    Optional<CreditPurchaseOrder> findByIdAndUserId(Long id, Long userId);

    Optional<CreditPurchaseOrder> findByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CreditPurchaseOrder o WHERE o.id = :id")
    Optional<CreditPurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CreditPurchaseOrder o WHERE o.orderCode = :orderCode")
    Optional<CreditPurchaseOrder> findByOrderCodeForUpdate(@Param("orderCode") String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CreditPurchaseOrder o WHERE o.orderCode = :orderCode OR o.orderCode = :cleanCode")
    Optional<CreditPurchaseOrder> findByOrderCodeOrCleanCodeForUpdate(@Param("orderCode") String orderCode, @Param("cleanCode") String cleanCode);

    boolean existsByOrderCode(String orderCode);

    boolean existsByTransactionRef(String transactionRef);

    Optional<CreditPurchaseOrder> findByTransactionRef(String transactionRef);

    List<CreditPurchaseOrder> findTop5ByStatusOrderByCreatedAtDesc(CreditPurchaseOrderStatus status);

    List<CreditPurchaseOrder> findByStatusOrderByCreatedAtDesc(CreditPurchaseOrderStatus status);

    @Query("SELECT COALESCE(SUM(o.price), 0) FROM CreditPurchaseOrder o WHERE o.status = :status AND o.paidAt >= :startDate AND o.paidAt < :endDate")
    Long sumPriceByStatusAndPaidAtBetween(@Param("status") CreditPurchaseOrderStatus status,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    long countByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(CreditPurchaseOrderStatus status,
                                                                 LocalDateTime startDate,
                                                                 LocalDateTime endDate);

    @Query("SELECT o.packageId, COUNT(o) FROM CreditPurchaseOrder o WHERE o.status = :status GROUP BY o.packageId")
    List<Object[]> countPurchasesByPackage(@Param("status") CreditPurchaseOrderStatus status);

    @Query("SELECT o.packageId, COUNT(o) FROM CreditPurchaseOrder o WHERE o.status = :status AND o.paidAt >= :startDate AND o.paidAt < :endDate GROUP BY o.packageId")
    List<Object[]> countPurchasesByPackageAndPaidAtBetween(@Param("status") CreditPurchaseOrderStatus status,
                                                           @Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate);

    @Query("SELECT o FROM CreditPurchaseOrder o WHERE o.status = :status AND o.paidAt >= :startDate AND o.paidAt < :endDate ORDER BY o.paidAt DESC, o.createdAt DESC")
    List<CreditPurchaseOrder> findByStatusAndPaidAtBetweenOrderByPaidAtDesc(@Param("status") CreditPurchaseOrderStatus status,
                                                                           @Param("startDate") LocalDateTime startDate,
                                                                           @Param("endDate") LocalDateTime endDate,
                                                                           Pageable pageable);

    @Query(value = """
        SELECT EXTRACT(MONTH FROM paid_at)::int AS month_num, COALESCE(SUM(price), 0) AS total_rev
        FROM credit_purchase_orders
        WHERE status = 'SUCCESS'
          AND paid_at >= :startOfYear AND paid_at < :endOfYear
        GROUP BY EXTRACT(MONTH FROM paid_at)
    """, nativeQuery = true)
    List<Object[]> sumRevenueByMonthOfYear(@Param("startOfYear") LocalDateTime startOfYear,
                                           @Param("endOfYear") LocalDateTime endOfYear);

    @Query("""
        SELECT o FROM CreditPurchaseOrder o
        WHERE (:search IS NULL OR :search = '' OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (
            (:status IS NOT NULL AND o.status = :status AND (
                (o.status != 'EXPIRED_PAID' AND o.status != 'DUPLICATE_PAYMENT')
                OR EXISTS (
                    SELECT 1 FROM BugReport b
                    WHERE b.errorType = 'PAYMENT_REFUND'
                      AND (b.orderCode = o.orderCode OR o.orderCode LIKE CONCAT(b.orderCode, '-%') OR o.orderCode LIKE CONCAT(b.orderCode, 'DUP%'))
                )
            ))
            OR (:status IS NULL AND (
                o.status = 'SUCCESS'
                OR o.status = 'REFUNDED'
                OR (
                    (o.status = 'EXPIRED_PAID' OR o.status = 'DUPLICATE_PAYMENT')
                    AND EXISTS (
                        SELECT 1 FROM BugReport b
                        WHERE b.errorType = 'PAYMENT_REFUND'
                          AND (b.orderCode = o.orderCode OR o.orderCode LIKE CONCAT(b.orderCode, '-%') OR o.orderCode LIKE CONCAT(b.orderCode, 'DUP%'))
                    )
                )
            ))
          )
        ORDER BY o.createdAt DESC
    """)
    Page<CreditPurchaseOrder> findOrdersForAdmin(@Param("status") CreditPurchaseOrderStatus status,
                                                @Param("search") String search,
                                                Pageable pageable);
}
