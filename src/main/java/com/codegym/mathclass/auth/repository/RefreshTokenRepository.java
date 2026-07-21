package com.codegym.mathclass.auth.repository;

import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findAllByUserOrderByExpiryDateAsc(User user);
    int deleteByUser(User user);
}
