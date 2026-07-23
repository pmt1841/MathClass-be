package com.codegym.mathclass.systemlog.repository;

import com.codegym.mathclass.systemlog.entity.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long>, JpaSpecificationExecutor<SystemLog> {

    @Modifying
    @Transactional
    void deleteByCreatedAtBefore(LocalDateTime cutoffDate);
}
