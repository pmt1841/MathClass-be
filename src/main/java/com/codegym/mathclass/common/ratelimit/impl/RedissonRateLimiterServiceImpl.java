package com.codegym.mathclass.common.ratelimit.impl;

import com.codegym.mathclass.common.ratelimit.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonRateLimiterServiceImpl implements RateLimiterService {

    public static final String RATE_LIMIT_PREFIX = "ratelimit:";

    private final RedissonClient redissonClient;

    @Override
    public boolean tryAcquire(String key, Duration cooldown) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        RBucket<String> bucket = redissonClient.getBucket(redisKey, StringCodec.INSTANCE);
        boolean acquired = bucket.setIfAbsent("1", cooldown);
        if (acquired) {
            log.debug("Rate limit acquired cho key: {}, cooldown: {}s", key, cooldown.toSeconds());
        } else {
            log.warn("Rate limit bị từ chối cho key: {}", key);
        }
        return acquired;
    }

    @Override
    public long getRemainingCooldownSeconds(String key) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        RBucket<String> bucket = redissonClient.getBucket(redisKey, StringCodec.INSTANCE);
        long ttlMs = bucket.remainTimeToLive();
        if (ttlMs <= 0) {
            return 0;
        }
        return (long) Math.ceil(ttlMs / 1000.0);
    }

    @Override
    public void setCooldown(String key, Duration cooldown) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        RBucket<String> bucket = redissonClient.getBucket(redisKey, StringCodec.INSTANCE);
        bucket.set("1", cooldown);
        log.debug("Đã thiết lập cooldown cho key: {}, thời hạn: {}s", key, cooldown.toSeconds());
    }

    @Override
    public boolean isWindowLimitExceeded(String key, long maxRequests) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        RAtomicLong counter = redissonClient.getAtomicLong(redisKey);
        return counter.isExists() && counter.get() >= maxRequests;
    }

    @Override
    public void recordHit(String key, Duration window) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        RAtomicLong counter = redissonClient.getAtomicLong(redisKey);
        long count = counter.incrementAndGet();
        if (count == 1 || counter.remainTimeToLive() < 0) {
            counter.expire(window);
        }
        log.debug("Ghi nhận hit cho key: {} (count: {}, window: {}s)", key, count, window.toSeconds());
    }

    @Override
    public long getHitCount(String key) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        RAtomicLong counter = redissonClient.getAtomicLong(redisKey);
        return counter.isExists() ? counter.get() : 0;
    }
}
