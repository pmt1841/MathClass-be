package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderStrategy;
import com.codegym.mathclass.aiconfig.repository.ApiKeyRepository;
import com.codegym.mathclass.common.ratelimit.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeySelectionService {

    private final ApiKeyRepository apiKeyRepository;
    private final RateLimiterService rateLimiterService;
    private final RedissonClient redissonClient;

    private static final String KEY_COOLDOWN_PREFIX = "ai:key:cooldown:";

    // Con trỏ AtomicInteger cho từng Provider đối với Round-Robin strategy
    private final Map<Long, AtomicInteger> roundRobinPointers = new ConcurrentHashMap<>();

    @Transactional
    public ApiKey selectKeyForProvider(Provider provider) {
        if (provider == null || provider.getId() <= 0) {
            throw new IllegalArgumentException("Provider không hợp lệ");
        }

        // Lấy tất cả Key active của Provider từ DB
        List<ApiKey> activeKeys = apiKeyRepository.findByProviderIdAndStatusOrderByPriorityDesc(
                provider.getId(), ApiKeyStatus.ACTIVE
        );

        // Lọc các key không nằm trong thời gian Cooldown (lỗi 429) phân tán trên Redis
        List<ApiKey> availableKeys = activeKeys.stream()
                .filter(key -> {
                    long remaining = rateLimiterService.getRemainingCooldownSeconds(KEY_COOLDOWN_PREFIX + key.getId());
                    if (remaining > 0) {
                        log.warn("Key ID {} đang trong thời gian cooldown phân tán (còn {} giây, lỗi 429), tạm thời bỏ qua",
                                key.getId(), remaining);
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (availableKeys.isEmpty()) {
            throw new IllegalStateException("Tính năng AI hiện đang được bảo trì, vui lòng quay lại sau.");
        }

        ApiKey selectedKey;
        if (provider.getStrategy() == ProviderStrategy.ROUND_ROBIN) {
            AtomicInteger pointer = roundRobinPointers.computeIfAbsent(provider.getId(), k -> new AtomicInteger(0));
            int index = Math.abs(pointer.getAndIncrement() % availableKeys.size());
            selectedKey = availableKeys.get(index);
        } else {
            // PRIORITY strategy: Lấy key có priority lớn nhất (đã được sort desc)
            selectedKey = availableKeys.stream()
                    .max(Comparator.comparingInt(ApiKey::getPriority))
                    .orElse(availableKeys.get(0));
        }

        // Cập nhật last_used
        selectedKey.setLastUsed(LocalDateTime.now());
        apiKeyRepository.save(selectedKey);

        return selectedKey;
    }

    @Transactional
    public void markKeyAsInactive(Long keyId) {
        apiKeyRepository.findById(keyId).ifPresent(key -> {
            key.setStatus(ApiKeyStatus.INACTIVE);
            apiKeyRepository.save(key);
            log.error("API Key ID {} bị lỗi 401 Unauthorized, đã tự động chuyển sang INACTIVE", keyId);
        });
    }

    public void cooldownKey(Long keyId, long durationSeconds) {
        if (keyId != null && durationSeconds > 0) {
            rateLimiterService.setCooldown(KEY_COOLDOWN_PREFIX + keyId, Duration.ofSeconds(durationSeconds));
            log.warn("API Key ID {} dính lỗi 429 Quota Exceeded, đưa vào danh sách Cooldown phân tán {} giây",
                    keyId, durationSeconds);
        }
    }

    public void clearCooldown(Long keyId) {
        if (keyId != null) {
            redissonClient.getBucket("ratelimit:" + KEY_COOLDOWN_PREFIX + keyId).delete();
            log.info("Đã xóa thời gian Cooldown cho API Key ID {}", keyId);
        }
    }

    public Long getCooldownRemainingSeconds(Long keyId) {
        if (keyId == null) {
            return null;
        }
        long remaining = rateLimiterService.getRemainingCooldownSeconds(KEY_COOLDOWN_PREFIX + keyId);
        return remaining > 0 ? remaining : null;
    }

    public Instant getCooldownExpiresAt(Long keyId) {
        Long remaining = getCooldownRemainingSeconds(keyId);
        return remaining != null ? Instant.now().plusSeconds(remaining) : null;
    }
}
