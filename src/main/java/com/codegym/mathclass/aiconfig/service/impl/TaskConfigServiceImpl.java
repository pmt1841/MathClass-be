package com.codegym.mathclass.aiconfig.service.impl;

import com.codegym.mathclass.aiconfig.dto.request.TaskConfigUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.TaskConfigResponse;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.ProviderRepository;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.service.TaskConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskConfigServiceImpl implements TaskConfigService {

    private final TaskConfigRepository taskConfigRepository;
    private final ProviderRepository providerRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "ai_task_configs_cache", key = "#task")
    public TaskConfigResponse getTaskConfig(String task) {
        TaskConfig taskConfig = taskConfigRepository.findByTask(task.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Chưa có cấu hình cho Task: " + task));
        return mapToResponse(taskConfig);
    }

    @Override
    @Transactional
    @CacheEvict(value = "ai_task_configs_cache", key = "#task")
    public TaskConfigResponse updateTaskConfig(String task, TaskConfigUpdateRequest request) {
        Provider provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Provider với ID: " + request.getProviderId()));

        TaskConfig taskConfig = taskConfigRepository.findByTask(task.toUpperCase())
                .orElse(TaskConfig.builder().task(task.toUpperCase()).build());

        taskConfig.setProvider(provider);
        taskConfig.setModel(request.getModel());
        taskConfig.setTemperature(request.getTemperature());
        taskConfig.setMaxToken(request.getMaxToken());
        taskConfig.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);

        TaskConfig saved = taskConfigRepository.save(taskConfig);
        return mapToResponse(saved);
    }

    private TaskConfigResponse mapToResponse(TaskConfig config) {
        return TaskConfigResponse.builder()
                .task(config.getTask())
                .providerId(config.getProvider() != null ? config.getProvider().getId() : null)
                .model(config.getModel())
                .temperature(config.getTemperature())
                .maxToken(config.getMaxToken())
                .enabled(config.getEnabled())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
