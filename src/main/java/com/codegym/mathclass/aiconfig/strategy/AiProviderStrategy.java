package com.codegym.mathclass.aiconfig.strategy;

import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;

public interface AiProviderStrategy {
    boolean supports(ProviderProtocol protocol);

    /**
     * Thực thi prompt với provider. Trả về kết quả kèm số token đầu ra
     * (completionTokens) để hệ thống credit tính phí theo token (MAT-255).
     */
    AiExecutionResult executePrompt(Provider provider, TaskConfig config, String apiKey, String prompt) throws Exception;

    /**
     * Thực thi prompt kèm theo dữ liệu hình ảnh (Multimodal Vision).
     * Mặc định gọi lại {@link #executePrompt(Provider, TaskConfig, String, String)}.
     */
    default AiExecutionResult executePromptWithImage(Provider provider, TaskConfig config, String apiKey,
                                                    String prompt, String base64Image, String mimeType) throws Exception {
        return executePrompt(provider, config, apiKey, prompt);
    }
}
