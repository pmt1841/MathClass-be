package com.codegym.mathclass.aiconfig.repository;

import com.codegym.mathclass.aiconfig.entity.SystemPromptHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemPromptHistoryRepository extends JpaRepository<SystemPromptHistory, Long> {

    List<SystemPromptHistory> findByPromptIdOrderByVersionDesc(Long promptId);

    Optional<SystemPromptHistory> findTopByPromptIdOrderByVersionDesc(Long promptId);
}
