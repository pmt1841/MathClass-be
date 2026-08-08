package com.codegym.mathclass.aiconfig.credit.repository;

import com.codegym.mathclass.aiconfig.credit.entity.CreditTransaction;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    List<CreditTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<CreditTransaction> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, CreditTransactionType type);

    List<CreditTransaction> findByTypeOrderByCreatedAtDesc(CreditTransactionType type);
}
