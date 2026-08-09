package com.codegym.mathclass.aiconfig.service.impl;

import com.codegym.mathclass.aiconfig.dto.request.RenderPromptRequest;
import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;
import com.codegym.mathclass.aiconfig.entity.SystemPrompt;
import com.codegym.mathclass.aiconfig.entity.SystemPromptStatus;
import com.codegym.mathclass.aiconfig.repository.SystemPromptRepository;
import com.codegym.mathclass.aiconfig.service.PromptRenderService;
import com.codegym.mathclass.aiconfig.validator.SystemPromptValidator;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.PromptNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromptRenderServiceImpl implements PromptRenderService {

    private final SystemPromptRepository systemPromptRepository;
    private final SystemPromptValidator systemPromptValidator;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "systemPromptsRender", key = "#request.promptCode + '_' + #request.variables.hashCode()", unless = "#result == null")
    public RenderPromptResponse renderPrompt(RenderPromptRequest request) {
        SystemPrompt prompt = systemPromptRepository.findByCode(request.getPromptCode())
                .orElseThrow(() -> new PromptNotFoundException("Không tìm thấy System Prompt với mã code: " + request.getPromptCode()));

        if (prompt.getStatus() != SystemPromptStatus.ACTIVE) {
            throw new BadRequestException("System Prompt " + request.getPromptCode() + " đang ở trạng thái INACTIVE và không thể sử dụng.");
        }

        List<String> allowedVariables = systemPromptValidator.parseAllowedVariables(prompt.getAllowedVariables());
        Map<String, Object> inputVariables = request.getVariables() != null ? request.getVariables() : Collections.emptyMap();

        String rawContent = prompt.getCurrentContent();
        Matcher matcher = VARIABLE_PATTERN.matcher(rawContent);

        StringBuilder renderedBuilder = new StringBuilder();
        List<String> usedVariables = new ArrayList<>();

        while (matcher.find()) {
            String varName = matcher.group(1);
            usedVariables.add(varName);

            Object val = inputVariables.get(varName);
            String replacement;
            if (val != null) {
                replacement = Matcher.quoteReplacement(val.toString());
            } else {
                log.warn("[PromptRenderService] Missing variable value for key '{}' in prompt '{}'", varName, request.getPromptCode());
                replacement = "";
            }
            matcher.appendReplacement(renderedBuilder, replacement);
        }
        matcher.appendTail(renderedBuilder);

        return RenderPromptResponse.builder()
                .promptCode(prompt.getCode())
                .renderedPrompt(renderedBuilder.toString())
                .usedVariables(usedVariables)
                .build();
    }
}
