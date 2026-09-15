package com.codegym.mathclass.security.jwt.impl;

import com.codegym.mathclass.security.jwt.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedissonTokenBlacklistServiceImplTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RBucket<String> bucket;

    @InjectMocks
    private RedissonTokenBlacklistServiceImpl blacklistService;

    @Test
    @DisplayName("blacklistToken: Token hợp lệ với thời gian còn lại > 0 -> Lưu vào Redis với TTL")
    void blacklistToken_ValidTokenWithRemainingTime_SavesToRedis() {
        String token = "valid.jwt.token";
        doReturn(bucket).when(redissonClient).getBucket(startsWith("jwt:blacklist:"), any(StringCodec.class));
        when(jwtUtils.getRemainingExpirationMs(token)).thenReturn(180000L); // 3 phút

        blacklistService.blacklistToken(token);

        verify(bucket, times(1)).set(eq("1"), eq(Duration.ofMillis(180000L)));
    }

    @Test
    @DisplayName("blacklistToken: Token đã hết hạn (remainingMs <= 0) -> Không lưu vào Redis")
    void blacklistToken_ExpiredToken_DoesNotSaveToRedis() {
        String token = "expired.jwt.token";
        when(jwtUtils.getRemainingExpirationMs(token)).thenReturn(0L);

        blacklistService.blacklistToken(token);

        verify(redissonClient, never()).getBucket(anyString(), any(StringCodec.class));
    }

    @Test
    @DisplayName("blacklistToken: Token null hoặc rỗng -> Bỏ qua không làm gì")
    void blacklistToken_NullOrEmptyToken_DoesNothing() {
        blacklistService.blacklistToken(null);
        blacklistService.blacklistToken("   ");

        verifyNoInteractions(jwtUtils);
        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("isBlacklisted: Key tồn tại trên Redis -> Trả về true")
    void isBlacklisted_KeyExists_ReturnsTrue() {
        String token = "blacklisted.jwt.token";
        doReturn(bucket).when(redissonClient).getBucket(startsWith("jwt:blacklist:"), any(StringCodec.class));
        when(bucket.isExists()).thenReturn(true);

        boolean result = blacklistService.isBlacklisted(token);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isBlacklisted: Key không tồn tại trên Redis -> Trả về false")
    void isBlacklisted_KeyDoesNotExist_ReturnsFalse() {
        String token = "fresh.jwt.token";
        doReturn(bucket).when(redissonClient).getBucket(startsWith("jwt:blacklist:"), any(StringCodec.class));
        when(bucket.isExists()).thenReturn(false);

        boolean result = blacklistService.isBlacklisted(token);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isBlacklisted: Token null hoặc rỗng -> Trả về false ngay lập tức")
    void isBlacklisted_NullOrEmptyToken_ReturnsFalse() {
        assertThat(blacklistService.isBlacklisted(null)).isFalse();
        assertThat(blacklistService.isBlacklisted("   ")).isFalse();

        verifyNoInteractions(redissonClient);
    }
}
