package com.codegym.mathclass.aiconfig.credit.repository;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CreditPurchaseOrderRepository extends JpaRepository<CreditPurchaseOrder, Long> {

    Optional<CreditPurchaseOrder> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CreditPurchaseOrder o WHERE o.id = :id")
    Optional<CreditPurchaseOrder> findByIdForUpdate(@Param("id") Long id);
}
