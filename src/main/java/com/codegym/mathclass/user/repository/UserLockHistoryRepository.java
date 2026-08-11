package com.codegym.mathclass.user.repository;

import com.codegym.mathclass.user.entity.UserLockHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserLockHistoryRepository extends JpaRepository<UserLockHistory, Long> {
    List<UserLockHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
