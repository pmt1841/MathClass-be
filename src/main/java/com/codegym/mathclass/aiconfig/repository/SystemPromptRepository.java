package com.codegym.mathclass.aiconfig.repository;

import com.codegym.mathclass.aiconfig.entity.SystemPrompt;
import com.codegym.mathclass.aiconfig.entity.SystemPromptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemPromptRepository extends JpaRepository<SystemPrompt, Long>, JpaSpecificationExecutor<SystemPrompt> {

    Optional<SystemPrompt> findByCode(String code);

    boolean existsByCode(String code);

    List<SystemPrompt> findByTaskCode(String taskCode);

    List<SystemPrompt> findByStatus(SystemPromptStatus status);
}
