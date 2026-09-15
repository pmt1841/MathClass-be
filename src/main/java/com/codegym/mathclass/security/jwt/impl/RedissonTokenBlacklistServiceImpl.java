package com.codegym.mathclass.security.jwt.impl;

import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.jwt.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonTokenBlacklistServiceImpl implements TokenBlacklistService {

    public static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    private final RedissonClient redissonClient;
    private final JwtUtils jwtUtils;

    @Override
    public void blacklistToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        long remainingMs = jwtUtils.getRemainingExpirationMs(token);
        if (remainingMs <= 0) {
            log.debug("Token đã hết hạn tự nhiên, không cần đưa vào blacklist.");
            return;
        }

        String tokenHash = hashToken(token);
        String redisKey = BLACKLIST_KEY_PREFIX + tokenHash;

        RBucket<String> bucket = redissonClient.getBucket(redisKey, StringCodec.INSTANCE);
        bucket.set("1", Duration.ofMillis(remainingMs));
        log.info("Đã đưa token (hash: {}) vào Redis blacklist với TTL: {} ms", tokenHash, remainingMs);
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String tokenHash = hashToken(token);
        String redisKey = BLACKLIST_KEY_PREFIX + tokenHash;

        RBucket<String> bucket = redissonClient.getBucket(redisKey, StringCodec.INSTANCE);
        return bucket.isExists();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuật toán SHA-256 không khả dụng trên hệ thống", e);
        }
    }
}
