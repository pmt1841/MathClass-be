package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderStrategy;
import com.codegym.mathclass.aiconfig.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // Con trỏ AtomicInteger cho từng Provider đối với Round-Robin strategy
    private final Map<Long, AtomicInteger> roundRobinPointers = new ConcurrentHashMap<>();

    // Map lưu mốc thời gian hết hạn Cooldown (dành cho lỗi 429)
    private final Map<Long, Instant> keyCooldowns = new ConcurrentHashMap<>();

    @Transactional
    public ApiKey selectKeyForProvider(Provider provider) {
        if (provider == null || provider.getId() <= 0) {
            throw new IllegalArgumentException("Provider không hợp lệ");
        }

        // Lấy tất cả Key active của Provider từ DB
        List<ApiKey> activeKeys = apiKeyRepository.findByProviderIdAndStatusOrderByPriorityDesc(
                provider.getId(), ApiKeyStatus.ACTIVE
        );

        // Lọc các key không nằm trong thời gian Cooldown (lỗi 429)
        Instant now = Instant.now();
        List<ApiKey> availableKeys = activeKeys.stream()
                .filter(key -> {
                    Instant cooldownEnd = keyCooldowns.get(key.getId());
                    if (cooldownEnd != null) {
                        if (now.isBefore(cooldownEnd)) {
                            log.warn("Key ID {} đang trong thời gian cooldown 5 phút (lỗi 429), tạm thời bỏ qua", key.getId());
                            return false;
                        } else {
                            // Đã hết cooldown 5 phút
                            keyCooldowns.remove(key.getId());
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (availableKeys.isEmpty()) {
            throw new IllegalStateException("Không có API Key nào khả dụng cho Provider: " + provider.getName());
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
        keyCooldowns.put(keyId, Instant.now().plusSeconds(durationSeconds));
        log.warn("API Key ID {} dính lỗi 429 Quota Exceeded, đưa vào danh sách Cooldown {} giây", keyId, durationSeconds);
    }

    public void clearCooldown(Long keyId) {
        if (keyId != null) {
            keyCooldowns.remove(keyId);
            log.info("Đã xóa thời gian Cooldown cho API Key ID {}", keyId);
        }
    }

    public Long getCooldownRemainingSeconds(Long keyId) {
        if (keyId == null) {
            return null;
        }
        Instant cooldownEnd = keyCooldowns.get(keyId);
        if (cooldownEnd == null) {
            return null;
        }
        Instant now = Instant.now();
        if (now.isBefore(cooldownEnd)) {
            return java.time.Duration.between(now, cooldownEnd).getSeconds();
        } else {
            keyCooldowns.remove(keyId);
            return null;
        }
    }

    public Instant getCooldownExpiresAt(Long keyId) {
        if (keyId == null) {
            return null;
        }
        Instant cooldownEnd = keyCooldowns.get(keyId);
        if (cooldownEnd == null) {
            return null;
        }
        if (Instant.now().isBefore(cooldownEnd)) {
            return cooldownEnd;
        } else {
            keyCooldowns.remove(keyId);
            return null;
        }
    }
}
