package com.codegym.mathclass.ai.strategy.parser.impl;

import com.codegym.mathclass.ai.strategy.parser.AbstractAiResponseParser;
import com.codegym.mathclass.ai.strategy.parser.AiResponseType;
import com.codegym.mathclass.submission.dto.response.StudentHintResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy Parser bóc tách dữ liệu phản hồi AI cho tính năng Gợi ý giải toán từng bước (StudentHintResponse).
 */
@Component
public class AiHintResponseParser extends AbstractAiResponseParser<StudentHintResponse> {

    private static final Pattern HINT_TEXT_PATTERN =
            Pattern.compile("\"hintText\"\\s*:\\s*\"([\\s\\S]*?)(?:\"\\s*,|\"\\s*\\}|$)");
    private static final Pattern FINAL_STEP_PATTERN =
            Pattern.compile("\"isFinalStep\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STEP_INDEX_PATTERN =
            Pattern.compile("\"stepIndex\"\\s*:\\s*([0-9]+)");

    public AiHintResponseParser(ObjectMapper objectMapper) {
        super(objectMapper, StudentHintResponse.class);
    }

    @Override
    public boolean supports(AiResponseType type) {
        return type == AiResponseType.HINT;
    }

    @Override
    protected StudentHintResponse parseFallbackRegex(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        String hintText = "";
        Matcher textMatcher = HINT_TEXT_PATTERN.matcher(rawResponse);
        if (textMatcher.find()) {
            hintText = textMatcher.group(1).trim();
            while (hintText.endsWith("\\")) {
                hintText = hintText.substring(0, hintText.length() - 1).trim();
            }
        }

        boolean isFinalStep = false;
        Matcher finalMatcher = FINAL_STEP_PATTERN.matcher(rawResponse);
        if (finalMatcher.find()) {
            isFinalStep = Boolean.parseBoolean(finalMatcher.group(1));
        }

        Integer stepIndex = null;
        Matcher stepMatcher = STEP_INDEX_PATTERN.matcher(rawResponse);
        if (stepMatcher.find()) {
            try {
                stepIndex = Integer.parseInt(stepMatcher.group(1));
            } catch (NumberFormatException ignored) {
                // Ignore parse error
            }
        }

        if (hintText.isBlank() && stepIndex == null) {
            return null;
        }

        return StudentHintResponse.builder()
                .hintContent(hintText)
                .hintNumber(stepIndex != null ? stepIndex : 1)
                .build();
    }
}
