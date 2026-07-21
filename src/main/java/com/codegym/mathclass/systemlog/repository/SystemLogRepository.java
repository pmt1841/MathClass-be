package com.codegym.mathclass.systemlog.repository;

import com.codegym.mathclass.systemlog.entity.SystemLog;
import com.codegym.mathclass.systemlog.entity.SystemLogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    
    @Query("SELECT l FROM SystemLog l WHERE " +
           "(:level IS NULL OR l.level = :level) AND " +
           "(:actor IS NULL OR LOWER(l.actor) LIKE :actor) AND " +
           "(:startDate IS NULL OR l.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR l.timestamp <= :endDate)")
    Page<SystemLog> findByFilters(@Param("level") SystemLogLevel level,
                                  @Param("actor") String actor,
                                  @Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate,
                                  Pageable pageable);
}
