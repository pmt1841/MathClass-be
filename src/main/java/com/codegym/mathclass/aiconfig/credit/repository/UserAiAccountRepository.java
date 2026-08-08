package com.codegym.mathclass.aiconfig.credit.repository;

import com.codegym.mathclass.aiconfig.credit.entity.UserAiAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAiAccountRepository extends JpaRepository<UserAiAccount, Long> {

    Optional<UserAiAccount> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM UserAiAccount a WHERE a.userId = :userId")
    Optional<UserAiAccount> findByUserIdForUpdate(@Param("userId") Long userId);
}
