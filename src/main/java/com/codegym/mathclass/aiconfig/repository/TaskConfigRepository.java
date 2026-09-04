package com.codegym.mathclass.aiconfig.repository;

import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskConfigRepository extends JpaRepository<TaskConfig, Long> {

    @EntityGraph(attributePaths = {"provider"})
    Optional<TaskConfig> findByTask(String task);

    boolean existsByProviderId(Long providerId);
    List<TaskConfig> findByProviderId(Long providerId);
}
