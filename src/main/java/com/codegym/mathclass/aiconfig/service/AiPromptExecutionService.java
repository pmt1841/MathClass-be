package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.strategy.AiExecutionResult;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategy;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategyFactory;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Cổng thực thi prompt AI tập trung.
 *
 * <p>Từ MAT-255: khi truyền {@code userId}, hệ thống kiểm tra & trừ credit theo
 * cấu hình {@link AiCreditConfig}. Mô hình Reserve-then-Refund: trừ credit trước
 * khi gọi AI, hoàn lại (refund) nếu AI lỗi để không phạt người dùng.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptExecutionService {

    private final TaskConfigRepository taskConfigRepository;
    private final KeySelectionService keySelectionService;
    private final AiProviderStrategyFactory aiProviderStrategyFactory;
    private final AiCreditService aiCreditService;
    private final UserRepository userRepository;

    /** Giữ nguyên API cũ: không thu phí credit (tương thích ngược). */
    public String executePrompt(String taskCode, String prompt) {
        return executePrompt(taskCode, prompt, null);
    }

    public String executePrompt(String taskCode, String prompt, Long userId) {
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

        Optional<AiCreditConfig> creditCfg = aiCreditService.getCreditConfig(taskCode);
        boolean charge = creditCfg.isPresent()
                && Boolean.TRUE.equals(creditCfg.get().getEnabled())
                && userId != null
                && !isAdmin(userId);

        int costPerCall = 0;
        Integer tokensPerCredit = null;
        int reserved = 0;
        if (charge) {
            costPerCall = creditCfg.get().getCostPerCall() != null ? creditCfg.get().getCostPerCall() : 0;
            tokensPerCredit = creditCfg.get().getTokensPerCredit();
            int maxToken = config.getMaxToken() != null ? config.getMaxToken() : 512;
            // Đặt chỗ ước lượng theo trần maxToken; sau khi AI trả kết quả sẽ settle theo token thực tế.
            reserved = AiCreditService.estimateCredits(maxToken, costPerCall, tokensPerCredit);
            if (reserved > 0) {
                aiCreditService.reserve(userId, taskCode, reserved);
            }
        }

        try {
            ApiKey apiKeyObj = keySelectionService.selectKeyForProvider(provider);
            String apiKey = apiKeyObj.getEncryptedKey();

            AiProviderStrategy strategy = aiProviderStrategyFactory.getStrategy(provider.getProtocol());
            AiExecutionResult result = strategy.executePrompt(provider, config, apiKey, prompt);

            if (reserved > 0) {
                int actual = AiCreditService.computeCredits(result.completionTokens(), costPerCall, tokensPerCredit);
                aiCreditService.settle(userId, taskCode, reserved, actual);
            }
            return result.content();
        } catch (Exception e) {
            if (reserved > 0) {
                aiCreditService.refund(userId, taskCode, reserved);
            }
            log.error("Lỗi khi thực thi prompt AI cho task '{}': {}", taskCode, e.getMessage());
            throw new RuntimeException("Dịch vụ AI phản hồi lỗi hoặc gặp sự cố kết nối: " + e.getMessage(), e);
        }
    }

    /** ADMIN không bị trừ credit khi gọi AI (BR-4 / AC-9). */
    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> user.getRole() == Role.ADMIN)
                .orElse(false);
    }
}

