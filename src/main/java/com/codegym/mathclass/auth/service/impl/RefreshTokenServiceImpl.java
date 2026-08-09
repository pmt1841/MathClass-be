package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.repository.RefreshTokenRepository;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    @Value("${mathclass.app.jwtRefreshExpirationMs}")
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Override
    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy User"));

        // Giới hạn tối đa 5 thiết bị đồng thời
        List<RefreshToken> existingTokens = refreshTokenRepository.findAllByUserOrderByExpiryDateAsc(user);
        if (existingTokens.size() >= 5) {
            // Xóa các token cũ nhất, chỉ giữ lại 4 token mới nhất
            int tokensToDelete = existingTokens.size() - 4;
            refreshTokenRepository.deleteAll(existingTokens.subList(0, tokensToDelete));
        }

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .token(UUID.randomUUID().toString())
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new BadRequestException("Refresh token đã hết hạn. Vui lòng đăng nhập lại.");
        }
        return token;
    }

    @Override
    @Transactional
    public int deleteByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy User"));
        return refreshTokenRepository.deleteByUser(user);
    }

    @Override
    @Transactional
    public void deleteToken(RefreshToken token) {
        refreshTokenRepository.delete(token);
    }
}
