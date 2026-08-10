package com.codegym.mathclass.aiconfig.service.impl;

import com.codegym.mathclass.aiconfig.dto.request.SystemPromptCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.SystemPromptResetRequest;
import com.codegym.mathclass.aiconfig.dto.request.SystemPromptUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.SystemPromptHistoryResponse;
import com.codegym.mathclass.aiconfig.dto.response.SystemPromptResponse;
import com.codegym.mathclass.aiconfig.entity.SystemPrompt;
import com.codegym.mathclass.aiconfig.entity.SystemPromptHistory;
import com.codegym.mathclass.aiconfig.entity.SystemPromptStatus;
import com.codegym.mathclass.aiconfig.repository.SystemPromptHistoryRepository;
import com.codegym.mathclass.aiconfig.repository.SystemPromptRepository;
import com.codegym.mathclass.aiconfig.service.SystemPromptService;
import com.codegym.mathclass.aiconfig.validator.SystemPromptValidator;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.PromptNotFoundException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemPromptServiceImpl implements SystemPromptService {

    private final SystemPromptRepository systemPromptRepository;
    private final SystemPromptHistoryRepository systemPromptHistoryRepository;
    private final SystemPromptValidator systemPromptValidator;
    private final SystemLogService systemLogService;

    @Override
    @Transactional(readOnly = true)
    public List<SystemPromptResponse> getAllPrompts(String taskCode, String statusStr, String search) {
        Specification<SystemPrompt> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(taskCode)) {
                predicates.add(cb.equal(root.get("taskCode"), taskCode));
            }
            if (StringUtils.hasText(statusStr)) {
                try {
                    SystemPromptStatus statusEnum = SystemPromptStatus.valueOf(statusStr.toUpperCase());
                    predicates.add(cb.equal(root.get("status"), statusEnum));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (StringUtils.hasText(search)) {
                String searchLike = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), searchLike),
                        cb.like(cb.lower(root.get("code")), searchLike)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return systemPromptRepository.findAll(spec).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SystemPromptResponse getPromptById(Long id) {
        SystemPrompt prompt = findPromptById(id);
        return mapToResponse(prompt);
    }

    @Override
    @Transactional
    public SystemPromptResponse createPrompt(SystemPromptCreateRequest request, String adminEmail, String ipAddress) {
        if (systemPromptRepository.existsByCode(request.getCode())) {
            throw new BadRequestException("Mã prompt code '" + request.getCode() + "' đã tồn tại trong hệ thống.");
        }

        // Validate prompt variables
        systemPromptValidator.validateVariables(request.getDefaultContent(), request.getAllowedVariables());

        String allowedVarsStr = String.join(",", request.getAllowedVariables());

        SystemPrompt prompt = SystemPrompt.builder()
                .code(request.getCode().toUpperCase())
                .name(request.getName())
                .taskCode(request.getTaskCode())
                .defaultContent(request.getDefaultContent())
                .currentContent(request.getDefaultContent())
                .allowedVariables(allowedVarsStr)
                .description(request.getDescription())
                .status(SystemPromptStatus.ACTIVE)
                .build();

        SystemPrompt savedPrompt = systemPromptRepository.save(prompt);

        // Save initial history version 1
        saveHistory(savedPrompt, 1, savedPrompt.getDefaultContent(), "Khởi tạo Prompt ban đầu", adminEmail);

        // System Audit Log
        systemLogService.log(adminEmail, "CREATE_PROMPT",
                com.codegym.mathclass.systemlog.entity.SystemLogLevel.INFO,
                "SYSTEM_PROMPT", savedPrompt.getCode(), ipAddress, null, "SUCCESS");

        return mapToResponse(savedPrompt);
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemPromptsRender", allEntries = true)
    public SystemPromptResponse updatePrompt(Long id, SystemPromptUpdateRequest request, String adminEmail, String ipAddress) {
        SystemPrompt prompt = findPromptById(id);
        List<String> allowedVars = systemPromptValidator.parseAllowedVariables(prompt.getAllowedVariables());

        // Strict validation for variables in currentContent
        systemPromptValidator.validateVariables(request.getCurrentContent(), allowedVars);

        String oldContent = prompt.getCurrentContent();
        prompt.setName(request.getName());
        prompt.setCurrentContent(request.getCurrentContent());
        prompt.setDescription(request.getDescription());
        prompt.setStatus(request.getStatus());

        SystemPrompt updatedPrompt = systemPromptRepository.save(prompt);

        // Save new history version if content changed
        if (!oldContent.equals(request.getCurrentContent())) {
            int nextVersion = getNextVersion(prompt.getId());
            String reason = StringUtils.hasText(request.getChangeReason()) ? request.getChangeReason() : "Chỉnh sửa nội dung Prompt";
            saveHistory(updatedPrompt, nextVersion, updatedPrompt.getCurrentContent(), reason, adminEmail);
        }

        // System Audit Log
        systemLogService.log(adminEmail, "UPDATE_PROMPT",
                com.codegym.mathclass.systemlog.entity.SystemLogLevel.INFO,
                "SYSTEM_PROMPT", updatedPrompt.getCode(), ipAddress, null, "SUCCESS");

        return mapToResponse(updatedPrompt);
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemPromptsRender", allEntries = true)
    public SystemPromptResponse resetToDefault(Long id, SystemPromptResetRequest request, String adminEmail, String ipAddress) {
        SystemPrompt prompt = findPromptById(id);

        prompt.setCurrentContent(prompt.getDefaultContent());
        SystemPrompt updatedPrompt = systemPromptRepository.save(prompt);

        int nextVersion = getNextVersion(prompt.getId());
        String reason = (request != null && StringUtils.hasText(request.getReason()))
                ? request.getReason() : "Khôi phục về bản mặc định gốc";
        saveHistory(updatedPrompt, nextVersion, updatedPrompt.getCurrentContent(), reason, adminEmail);

        // System Audit Log
        systemLogService.log(adminEmail, "RESET_PROMPT",
                com.codegym.mathclass.systemlog.entity.SystemLogLevel.WARNING,
                "SYSTEM_PROMPT", updatedPrompt.getCode(), ipAddress, null, "SUCCESS");

        return mapToResponse(updatedPrompt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SystemPromptHistoryResponse> getPromptHistory(Long id) {
        findPromptById(id); // Ensure prompt exists
        return systemPromptHistoryRepository.findByPromptIdOrderByVersionDesc(id).stream()
                .map(this::mapToHistoryResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemPromptsRender", allEntries = true)
    public SystemPromptResponse rollbackToVersion(Long id, Long historyId, String adminEmail, String ipAddress) {
        SystemPrompt prompt = findPromptById(id);

        SystemPromptHistory history = systemPromptHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi lịch sử với ID: " + historyId));

        if (history.getPrompt().getId() != prompt.getId()) {
            throw new BadRequestException("Bản ghi lịch sử không thuộc về System Prompt này.");
        }

        prompt.setCurrentContent(history.getContent());
        SystemPrompt updatedPrompt = systemPromptRepository.save(prompt);

        int nextVersion = getNextVersion(prompt.getId());
        String reason = "Rollback về phiên bản v" + history.getVersion();
        saveHistory(updatedPrompt, nextVersion, updatedPrompt.getCurrentContent(), reason, adminEmail);

        // System Audit Log
        systemLogService.log(adminEmail, "ROLLBACK_PROMPT",
                com.codegym.mathclass.systemlog.entity.SystemLogLevel.WARNING,
                "SYSTEM_PROMPT", updatedPrompt.getCode(), ipAddress, null, "SUCCESS");

        return mapToResponse(updatedPrompt);
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemPromptsRender", allEntries = true)
    public void deletePrompt(Long id, String adminEmail, String ipAddress) {
        SystemPrompt prompt = findPromptById(id);
        String code = prompt.getCode();

        systemPromptRepository.delete(prompt);

        // System Audit Log
        systemLogService.log(adminEmail, "DELETE_PROMPT",
                com.codegym.mathclass.systemlog.entity.SystemLogLevel.WARNING,
                "SYSTEM_PROMPT", code, ipAddress, null, "SUCCESS");
    }

    // Helper methods
    private SystemPrompt findPromptById(Long id) {
        return systemPromptRepository.findById(id)
                .orElseThrow(() -> new PromptNotFoundException("Không tìm thấy System Prompt với ID: " + id));
    }

    private int getNextVersion(Long promptId) {
        Optional<SystemPromptHistory> latest = systemPromptHistoryRepository.findTopByPromptIdOrderByVersionDesc(promptId);
        return latest.map(h -> h.getVersion() + 1).orElse(1);
    }

    private void saveHistory(SystemPrompt prompt, int version, String content, String reason, String createdBy) {
        SystemPromptHistory history = SystemPromptHistory.builder()
                .prompt(prompt)
                .version(version)
                .content(content)
                .changeReason(reason)
                .createdBy(createdBy != null ? createdBy : "SYSTEM")
                .build();
        systemPromptHistoryRepository.save(history);
    }

    private SystemPromptResponse mapToResponse(SystemPrompt prompt) {
        List<String> allowedVars = systemPromptValidator.parseAllowedVariables(prompt.getAllowedVariables());
        return SystemPromptResponse.builder()
                .id(prompt.getId())
                .code(prompt.getCode())
                .name(prompt.getName())
                .taskCode(prompt.getTaskCode())
                .defaultContent(prompt.getDefaultContent())
                .currentContent(prompt.getCurrentContent())
                .allowedVariables(allowedVars)
                .description(prompt.getDescription())
                .status(prompt.getStatus())
                .createdAt(prompt.getCreatedAt())
                .updatedAt(prompt.getUpdatedAt())
                .build();
    }

    private SystemPromptHistoryResponse mapToHistoryResponse(SystemPromptHistory history) {
        return SystemPromptHistoryResponse.builder()
                .id(history.getId())
                .promptId(history.getPrompt().getId())
                .version(history.getVersion())
                .content(history.getContent())
                .changeReason(history.getChangeReason())
                .createdBy(history.getCreatedBy())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
