package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategy;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptExecutionService {

    private final TaskConfigRepository taskConfigRepository;
    private final KeySelectionService keySelectionService;
    private final AiProviderStrategyFactory aiProviderStrategyFactory;

    public String executePrompt(String taskCode, String prompt) {
        Optional<TaskConfig> configOpt = taskConfigRepository.findByTask(taskCode);
        if (configOpt.isEmpty()) {
            log.warn("TaskConfig '{}' chưa được cấu hình.", taskCode);
            throw new RuntimeException("Tác vụ AI '" + taskCode + "' chưa được hệ thống cấu hình.");
        }

        TaskConfig config = configOpt.get();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            log.warn("Tác vụ AI '{}' hiện đang bị vô hiệu hóa.", taskCode);
            throw new RuntimeException("Tác vụ AI '" + taskCode + "' hiện đang bị tạm khóa.");
        }

        Provider provider = config.getProvider();
        if (provider == null || provider.getStatus() != com.codegym.mathclass.aiconfig.entity.ProviderStatus.ACTIVE) {
            log.warn("Provider AI cho tác vụ '{}' không khả dụng.", taskCode);
            throw new RuntimeException("Dịch vụ AI Provider hiện không khả dụng.");
        }

        try {
            ApiKey apiKeyObj = keySelectionService.selectKeyForProvider(provider);
            String apiKey = apiKeyObj.getEncryptedKey();

            AiProviderStrategy strategy = aiProviderStrategyFactory.getStrategy(provider.getProtocol());
            return strategy.executePrompt(provider, config, apiKey, prompt);
        } catch (Exception e) {
            log.error("Lỗi khi thực thi prompt AI cho task '{}': {}", taskCode, e.getMessage());
            throw new RuntimeException("Dịch vụ AI phản hồi lỗi hoặc gặp sự cố kết nối: " + e.getMessage(), e);
        }
    }
}
