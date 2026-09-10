package com.codegym.mathclass.ai.strategy.parser.impl;

import com.codegym.mathclass.ai.strategy.parser.AbstractAiResponseParser;
import com.codegym.mathclass.ai.strategy.parser.AiResponseType;
import com.codegym.mathclass.classroom.dto.response.AiRemarkJsonResult;
import com.codegym.mathclass.utils.AiResponseUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy Parser bóc tách dữ liệu phản hồi AI cho tính năng Đánh giá / Nhận xét tiến độ học sinh (AiRemarkJsonResult).
 */
@Component
public class AiRemarkResponseParser extends AbstractAiResponseParser<AiRemarkJsonResult> {

    private static final Pattern STRENGTHS_PATTERN =
            Pattern.compile("\"strengths\"\\s*:\\s*\"([\\s\\S]*?)(?:\"\\s*,|\"\\s*\\}|$)");
    private static final Pattern WEAKNESSES_PATTERN =
            Pattern.compile("\"weaknesses\"\\s*:\\s*\"([\\s\\S]*?)(?:\"\\s*,|\"\\s*\\}|$)");
    private static final Pattern GENERAL_PATTERN =
            Pattern.compile("\"generalAssessment\"\\s*:\\s*\"([\\s\\S]*?)(?:\"\\s*,|\"\\s*\\}|$)");

    public AiRemarkResponseParser(ObjectMapper objectMapper) {
        super(objectMapper, AiRemarkJsonResult.class);
    }

    @Override
    public boolean supports(AiResponseType type) {
        return type == AiResponseType.REMARK;
    }

    @Override
    protected AiRemarkJsonResult parseFallbackRegex(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        String strengths = extractField(STRENGTHS_PATTERN, rawResponse, "Chưa có thông tin điểm mạnh cụ thể.");
        String weaknesses = extractField(WEAKNESSES_PATTERN, rawResponse, "Chưa có thông tin điểm yếu cụ thể.");
        String generalAssessment = extractField(GENERAL_PATTERN, rawResponse, AiResponseUtils.stripMarkdownFences(rawResponse));

        AiRemarkJsonResult result = new AiRemarkJsonResult();
        result.setStrengths(strengths);
        result.setWeaknesses(weaknesses);
        result.setGeneralAssessment(generalAssessment);
        return result;
    }

    private String extractField(Pattern pattern, String raw, String defaultValue) {
        Matcher matcher = pattern.matcher(raw);
        if (matcher.find()) {
            String val = matcher.group(1).trim();
            while (val.endsWith("\\")) {
                val = val.substring(0, val.length() - 1).trim();
            }
            if (!val.isBlank()) {
                return val;
            }
        }
        return defaultValue;
    }
}
