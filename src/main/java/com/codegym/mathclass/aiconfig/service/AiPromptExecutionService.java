package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.strategy.AiExecutionResult;

public interface AiPromptExecutionService {

    String executePrompt(String taskCode, String prompt, Long userId);

    AiExecutionResult executePromptWithResult(String taskCode, String prompt, Long userId, boolean chargeCredits);

    String executePromptWithImage(String taskCode, String prompt, String base64Image, String mimeType, Long userId);

    AiExecutionResult executePromptWithImageWithResult(String taskCode, String prompt, String base64Image, String mimeType, Long userId, boolean chargeCredits);
}
