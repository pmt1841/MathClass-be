package com.codegym.mathclass.aiconfig.service.impl;

import com.codegym.mathclass.aiconfig.dto.request.ApiKeyCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.ApiKeyStatusPatchRequest;
import com.codegym.mathclass.aiconfig.dto.response.ApiKeyResponse;
import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.repository.ApiKeyRepository;
import com.codegym.mathclass.aiconfig.repository.ProviderRepository;
import com.codegym.mathclass.aiconfig.service.ApiKeyService;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final ProviderRepository providerRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> getKeysByProviderId(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ResourceNotFoundException("Không tìm thấy Provider với ID: " + providerId);
        }
        return apiKeyRepository.findByProviderId(providerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "ai_providers_cache", allEntries = true)
    public ApiKeyResponse addKey(Long providerId, ApiKeyCreateRequest request) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Provider với ID: " + providerId));

        ApiKey apiKey = ApiKey.builder()
                .provider(provider)
                .name(request.getName())
                .encryptedKey(request.getApiKey()) // Auto encrypted via JPA AttributeConverter
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        ApiKeyResponse response = mapToResponse(saved);
        log.info("[AI_AUDIT] Thêm API Key mới cho Provider ID={} (code='{}'): KeyID={}, name='{}', maskedKey='{}'", providerId, provider.getCode(), saved.getId(), saved.getName(), response.getMaskedApiKey());
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = "ai_providers_cache", allEntries = true)
    public void deleteKey(Long keyId) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy API Key với ID: " + keyId));
        String masked = maskKey(apiKey.getEncryptedKey());
        Long providerId = apiKey.getProvider() != null ? apiKey.getProvider().getId() : null;
        apiKeyRepository.delete(apiKey);
        log.warn("[AI_AUDIT] Xóa vĩnh viễn API Key ID={} (ProviderID={}, maskedKey='{}')", keyId, providerId, masked);
    }

    @Override
    @Transactional
    @CacheEvict(value = "ai_providers_cache", allEntries = true)
    public ApiKeyResponse updateKeyStatus(Long keyId, ApiKeyStatusPatchRequest request) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy API Key với ID: " + keyId));

        apiKey.setStatus(request.getStatus());
        ApiKey updated = apiKeyRepository.save(apiKey);
        log.info("[AI_AUDIT] Đổi trạng thái API Key ID={} sang {}", keyId, request.getStatus());
        return mapToResponse(updated);
    }

    private ApiKeyResponse mapToResponse(ApiKey apiKey) {
        String plainKey = apiKey.getEncryptedKey();
        return ApiKeyResponse.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .maskedApiKey(maskKey(plainKey))
                .priority(apiKey.getPriority())
                .status(apiKey.getStatus())
                .lastUsed(apiKey.getLastUsed())
                .createdAt(apiKey.getCreatedAt())
                .updatedAt(apiKey.getUpdatedAt())
                .build();
    }

    private String maskKey(String rawKey) {
        if (rawKey == null || rawKey.trim().isEmpty()) {
            return "";
        }
        String clean = rawKey.trim();
        if (clean.length() <= 12) {
            return clean.substring(0, Math.min(2, clean.length())) + "***";
        }
        return clean.substring(0, 8) + "***" + clean.substring(clean.length() - 4);
    }
}
