package com.codegym.mathclass.aiconfig.credit.repository;

import com.codegym.mathclass.aiconfig.credit.entity.PaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentConfigRepository extends JpaRepository<PaymentConfig, Long> {

    Optional<PaymentConfig> findFirstByOrderByIdAsc();
}
