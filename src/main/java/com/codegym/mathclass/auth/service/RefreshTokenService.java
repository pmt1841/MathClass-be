package com.codegym.mathclass.auth.service;

import com.codegym.mathclass.auth.entity.RefreshToken;

import java.util.Optional;

public interface RefreshTokenService {
    Optional<RefreshToken> findByToken(String token);
    RefreshToken createRefreshToken(Long userId);
    RefreshToken verifyExpiration(RefreshToken token);
    int deleteByUserId(Long userId);
    void deleteToken(RefreshToken token);
}
