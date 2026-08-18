package com.codegym.mathclass.auth.repository;

import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTwoFactorAuthRepository extends JpaRepository<UserTwoFactorAuth, Long> {
    Optional<UserTwoFactorAuth> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
