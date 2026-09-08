package com.codegym.mathclass.ai.strategy.parser.impl;

import com.codegym.mathclass.ai.strategy.parser.AbstractAiResponseParser;
import com.codegym.mathclass.ai.strategy.parser.AiResponseType;
import com.codegym.mathclass.submission.dto.response.AiGradingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy Parser bóc tách dữ liệu phản hồi AI cho tính năng Chấm bài tự luận (AiGradingResponse).
 */
@Component
public class AiGradingResponseParser extends AbstractAiResponseParser<AiGradingResponse> {

    private static final Pattern SCORE_PATTERN =
            Pattern.compile("\"suggestedScore\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern FEEDBACK_PATTERN =
            Pattern.compile("\"draftFeedback\"\\s*:\\s*\"([\\s\\S]*?)(?:\"\\s*,|\"\\s*\\}|$)");

    public AiGradingResponseParser(ObjectMapper objectMapper) {
        super(objectMapper, AiGradingResponse.class);
    }

    @Override
    public boolean supports(AiResponseType type) {
        return type == AiResponseType.GRADING;
    }

    @Override
    protected AiGradingResponse parseFallbackRegex(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        Double suggestedScore = null;
        Matcher scoreMatcher = SCORE_PATTERN.matcher(rawResponse);
        if (scoreMatcher.find()) {
            try {
                suggestedScore = Double.parseDouble(scoreMatcher.group(1));
            } catch (NumberFormatException ignored) {
                // Ignore parse error
            }
        }

        String draftFeedback = "";
        Matcher feedbackMatcher = FEEDBACK_PATTERN.matcher(rawResponse);
        if (feedbackMatcher.find()) {
            draftFeedback = feedbackMatcher.group(1).trim();
            while (draftFeedback.endsWith("\\")) {
                draftFeedback = draftFeedback.substring(0, draftFeedback.length() - 1).trim();
            }
        } else if (rawResponse.contains("\"draftFeedback\"")) {
            int idx = rawResponse.indexOf("\"draftFeedback\"");
            int colonIdx = rawResponse.indexOf(':', idx);
            if (colonIdx != -1) {
                String sub = rawResponse.substring(colonIdx + 1).trim();
                if (sub.startsWith("\"")) {
                    sub = sub.substring(1);
                }
                sub = sub.replaceAll("[\"}\\s]+$", "");
                while (sub.endsWith("\\")) {
                    sub = sub.substring(0, sub.length() - 1).trim();
                }
                draftFeedback = sub;
            }
        }

        if (suggestedScore == null && draftFeedback.isBlank()) {
            return null;
        }

        return AiGradingResponse.builder()
                .suggestedScore(suggestedScore)
                .draftFeedback(draftFeedback)
                .build();
    }
}
