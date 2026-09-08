package com.codegym.mathclass.auth.repository;

import com.codegym.mathclass.auth.entity.AuthAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long> {
    List<AuthAuditLog> findByEmailOrderByCreatedAtDesc(String email);
    List<AuthAuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);
}
