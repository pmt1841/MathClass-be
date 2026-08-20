package com.codegym.mathclass.user.repository;

import com.codegym.mathclass.user.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    List<PasswordHistory> findTop3ByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserId(Long userId);
}
