package com.codegym.mathclass.common.ratelimit.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedissonRateLimiterServiceImplTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    @Mock
    private RAtomicLong atomicLong;

    @InjectMocks
    private RedissonRateLimiterServiceImpl rateLimiterService;

    @Nested
    @DisplayName("tryAcquire Tests")
    class TryAcquireTests {

        @Test
        @DisplayName("tryAcquire trả về true khi key chưa tồn tại và ghi nhận cooldown thành công")
        void tryAcquire_Success() {
            String key = "test:user:1";
            Duration cooldown = Duration.ofSeconds(60);

            when(redissonClient.<String>getBucket(eq("ratelimit:" + key), any(StringCodec.class)))
                    .thenReturn(bucket);
            when(bucket.setIfAbsent("1", cooldown)).thenReturn(true);

            boolean acquired = rateLimiterService.tryAcquire(key, cooldown);

            assertThat(acquired).isTrue();
            verify(bucket).setIfAbsent("1", cooldown);
        }

        @Test
        @DisplayName("tryAcquire trả về false khi key đã tồn tại (đang trong cooldown)")
        void tryAcquire_AlreadyLocked_ReturnsFalse() {
            String key = "test:user:1";
            Duration cooldown = Duration.ofSeconds(60);

            when(redissonClient.<String>getBucket(eq("ratelimit:" + key), any(StringCodec.class)))
                    .thenReturn(bucket);
            when(bucket.setIfAbsent("1", cooldown)).thenReturn(false);

            boolean acquired = rateLimiterService.tryAcquire(key, cooldown);

            assertThat(acquired).isFalse();
        }
    }

    @Nested
    @DisplayName("getRemainingCooldownSeconds Tests")
    class GetRemainingCooldownSecondsTests {

        @Test
        @DisplayName("getRemainingCooldownSeconds trả về số giây làm tròn lên khi TTL > 0")
        void getRemainingCooldownSeconds_PositiveTtl() {
            String key = "test:user:1";
            when(redissonClient.<String>getBucket(eq("ratelimit:" + key), any(StringCodec.class)))
                    .thenReturn(bucket);
            when(bucket.remainTimeToLive()).thenReturn(45200L); // 45.2s -> 46s

            long remaining = rateLimiterService.getRemainingCooldownSeconds(key);

            assertThat(remaining).isEqualTo(46L);
        }

        @Test
        @DisplayName("getRemainingCooldownSeconds trả về 0 khi key đã hết hạn hoặc không tồn tại")
        void getRemainingCooldownSeconds_Expired_ReturnsZero() {
            String key = "test:user:1";
            when(redissonClient.<String>getBucket(eq("ratelimit:" + key), any(StringCodec.class)))
                    .thenReturn(bucket);
            when(bucket.remainTimeToLive()).thenReturn(-2L);

            long remaining = rateLimiterService.getRemainingCooldownSeconds(key);

            assertThat(remaining).isZero();
        }
    }

    @Nested
    @DisplayName("setCooldown Tests")
    class SetCooldownTests {

        @Test
        @DisplayName("setCooldown ghi nhận key kèm thời hạn cooldown")
        void setCooldown_Success() {
            String key = "test:email:abc@test.com";
            Duration cooldown = Duration.ofSeconds(30);

            when(redissonClient.<String>getBucket(eq("ratelimit:" + key), any(StringCodec.class)))
                    .thenReturn(bucket);

            rateLimiterService.setCooldown(key, cooldown);

            verify(bucket).set("1", cooldown);
        }
    }

    @Nested
    @DisplayName("Window Rate Limit Tests")
    class WindowRateLimitTests {

        @Test
        @DisplayName("isWindowLimitExceeded trả về true khi counter >= maxRequests")
        void isWindowLimitExceeded_TrueWhenExceeded() {
            String key = "test:ip:127.0.0.1";
            when(redissonClient.getAtomicLong("ratelimit:" + key)).thenReturn(atomicLong);
            when(atomicLong.isExists()).thenReturn(true);
            when(atomicLong.get()).thenReturn(5L);

            boolean exceeded = rateLimiterService.isWindowLimitExceeded(key, 5);

            assertThat(exceeded).isTrue();
        }

        @Test
        @DisplayName("isWindowLimitExceeded trả về false khi key chưa tồn tại")
        void isWindowLimitExceeded_FalseWhenNotExists() {
            String key = "test:ip:127.0.0.1";
            when(redissonClient.getAtomicLong("ratelimit:" + key)).thenReturn(atomicLong);
            when(atomicLong.isExists()).thenReturn(false);

            boolean exceeded = rateLimiterService.isWindowLimitExceeded(key, 5);

            assertThat(exceeded).isFalse();
        }

        @Test
        @DisplayName("recordHit tăng counter và set expire ở lần gọi đầu tiên")
        void recordHit_FirstCall_SetsExpire() {
            String key = "test:ip:127.0.0.1";
            Duration window = Duration.ofMinutes(10);

            when(redissonClient.getAtomicLong("ratelimit:" + key)).thenReturn(atomicLong);
            when(atomicLong.incrementAndGet()).thenReturn(1L);

            rateLimiterService.recordHit(key, window);

            verify(atomicLong).expire(window);
        }

        @Test
        @DisplayName("getHitCount trả về giá trị counter nếu tồn tại")
        void getHitCount_ReturnsCount() {
            String key = "test:ip:127.0.0.1";
            when(redissonClient.getAtomicLong("ratelimit:" + key)).thenReturn(atomicLong);
            when(atomicLong.isExists()).thenReturn(true);
            when(atomicLong.get()).thenReturn(3L);

            long count = rateLimiterService.getHitCount(key);

            assertThat(count).isEqualTo(3L);
        }
    }
}
