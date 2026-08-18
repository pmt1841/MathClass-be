package com.codegym.mathclass.auth.repository;

import com.codegym.mathclass.auth.entity.UserBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBackupCodeRepository extends JpaRepository<UserBackupCode, Long> {
    List<UserBackupCode> findByUserIdAndIsUsedFalse(Long userId);
    void deleteByUserId(Long userId);
}
