package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.dto.request.SystemPromptCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.SystemPromptResetRequest;
import com.codegym.mathclass.aiconfig.dto.request.SystemPromptUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.SystemPromptHistoryResponse;
import com.codegym.mathclass.aiconfig.dto.response.SystemPromptResponse;

import java.util.List;

public interface SystemPromptService {
    List<SystemPromptResponse> getAllPrompts(String taskCode, String status, String search);

    SystemPromptResponse getPromptById(Long id);

    SystemPromptResponse createPrompt(SystemPromptCreateRequest request, String adminEmail, String ipAddress);

    SystemPromptResponse updatePrompt(Long id, SystemPromptUpdateRequest request, String adminEmail, String ipAddress);

    SystemPromptResponse resetToDefault(Long id, SystemPromptResetRequest request, String adminEmail, String ipAddress);

    List<SystemPromptHistoryResponse> getPromptHistory(Long id);

    SystemPromptResponse rollbackToVersion(Long id, Long historyId, String adminEmail, String ipAddress);

    void deletePrompt(Long id, String adminEmail, String ipAddress);
}
