package com.codegym.mathclass.aiconfig.service.impl;

import com.codegym.mathclass.aiconfig.dto.request.ProviderCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.ProviderUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.ProviderResponse;
import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.ApiKeyRepository;
import com.codegym.mathclass.aiconfig.repository.ProviderRepository;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.service.ProviderService;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderRepository providerRepository;
    private final TaskConfigRepository taskConfigRepository;
    private final ApiKeyRepository apiKeyRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "ai_providers_cache")
    public List<ProviderResponse> getAllProviders() {
        return providerRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderResponse getProviderById(Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Provider với ID: " + id));
        return mapToResponse(provider);
    }

    @Override
    @Transactional
    @CacheEvict(value = "ai_providers_cache", allEntries = true)
    public ProviderResponse createProvider(ProviderCreateRequest request) {
        if (providerRepository.existsByCode(request.getCode())) {
            throw new IllegalStateException("Mã Provider '" + request.getCode() + "' đã tồn tại trong hệ thống");
        }

        Provider provider = Provider.builder()
                .code(request.getCode().toUpperCase())
                .name(request.getName())
                .baseUrl(request.getBaseUrl())
                .protocol(request.getProtocol() != null ? request.getProtocol() : ProviderProtocol.OPENAI_COMPATIBLE)
                .authHeaderName(request.getAuthHeaderName())
                .authHeaderPrefix(request.getAuthHeaderPrefix())
                .authQueryParam(request.getAuthQueryParam())
                .healthCheckPath(request.getHealthCheckPath())
                .strategy(request.getStrategy())
                .build();

        Provider saved = providerRepository.save(provider);

        if (request.getApiKey() != null && !request.getApiKey().trim().isEmpty()) {
            ApiKey key = ApiKey.builder()
                    .provider(saved)
                    .name(saved.getName() + " Initial Key")
                    .encryptedKey(request.getApiKey().trim())
                    .priority(10)
                    .build();
            apiKeyRepository.save(key);
        }

        log.info("[AI_AUDIT] Tạo Provider thành công: ID={}, code='{}', name='{}', protocol='{}'", saved.getId(), saved.getCode(), saved.getName(), saved.getProtocol());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "ai_providers_cache", allEntries = true)
    public ProviderResponse updateProvider(Long id, ProviderUpdateRequest request) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Provider với ID: " + id));

        ProviderStatus oldStatus = provider.getStatus();
        ProviderStatus newStatus = request.getStatus();

        provider.setName(request.getName());
        provider.setBaseUrl(request.getBaseUrl());
        if (request.getProtocol() != null) {
            provider.setProtocol(request.getProtocol());
        }
        provider.setAuthHeaderName(request.getAuthHeaderName());
        provider.setAuthHeaderPrefix(request.getAuthHeaderPrefix());
        provider.setAuthQueryParam(request.getAuthQueryParam());
        provider.setHealthCheckPath(request.getHealthCheckPath());
        provider.setStrategy(request.getStrategy());
        if (newStatus != null) {
            provider.setStatus(newStatus);
        }

        Provider updated = providerRepository.save(provider);

        if (newStatus != null && newStatus != oldStatus) {
            ApiKeyStatus targetKeyStatus = (newStatus == ProviderStatus.ACTIVE) ? ApiKeyStatus.ACTIVE : ApiKeyStatus.INACTIVE;
            apiKeyRepository.updateStatusByProviderId(id, targetKeyStatus);
            log.info("[AI_AUDIT] Đổi trạng thái Provider ID={} (code='{}') từ {} sang {}. Đã đồng bộ API Keys sang {}", id, provider.getCode(), oldStatus, newStatus, targetKeyStatus);
        } else {
            log.info("[AI_AUDIT] Cập nhật thông tin Provider ID={} (code='{}')", id, provider.getCode());
        }

        return mapToResponse(updated);
    }

    @Override
    @Transactional
    @CacheEvict(value = "ai_providers_cache", allEntries = true)
    public void deleteProvider(Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Provider với ID: " + id));

        List<TaskConfig> usingTasks = taskConfigRepository.findByProviderId(id);
        if (!usingTasks.isEmpty()) {
            String taskList = usingTasks.stream().map(TaskConfig::getTask).collect(Collectors.joining(", "));
            throw new IllegalStateException("Không thể xóa Provider đang được các Task sử dụng: [" + taskList + "]");
        }

        providerRepository.delete(provider);
        log.warn("[AI_AUDIT] Xóa vĩnh viễn Provider ID={} (code='{}', name='{}')", id, provider.getCode(), provider.getName());
    }

    private ProviderResponse mapToResponse(Provider provider) {
        return ProviderResponse.builder()
                .id(provider.getId())
                .code(provider.getCode())
                .name(provider.getName())
                .baseUrl(provider.getBaseUrl())
                .protocol(provider.getProtocol())
                .authHeaderName(provider.getAuthHeaderName())
                .authHeaderPrefix(provider.getAuthHeaderPrefix())
                .authQueryParam(provider.getAuthQueryParam())
                .healthCheckPath(provider.getHealthCheckPath())
                .strategy(provider.getStrategy())
                .status(provider.getStatus())
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .build();
    }
}
