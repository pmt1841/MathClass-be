package com.codegym.mathclass.aiconfig.credit.repository;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiCreditConfigRepository extends JpaRepository<AiCreditConfig, Long> {

    Optional<AiCreditConfig> findByTask(String task);

    List<AiCreditConfig> findAllByOrderByTaskAsc();
}
