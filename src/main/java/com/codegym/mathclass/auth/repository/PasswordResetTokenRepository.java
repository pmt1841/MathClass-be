package com.codegym.mathclass.auth.repository;

import com.codegym.mathclass.auth.entity.PasswordResetToken;
import com.codegym.mathclass.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndIsUsedFalse(String tokenHash);
    Optional<PasswordResetToken> findByUserAndIsUsedFalse(User user);
}
