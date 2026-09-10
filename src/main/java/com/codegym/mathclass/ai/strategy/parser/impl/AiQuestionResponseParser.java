package com.codegym.mathclass.ai.strategy.parser.impl;

import com.codegym.mathclass.ai.strategy.parser.AbstractAiResponseParser;
import com.codegym.mathclass.ai.strategy.parser.AiResponseType;
import com.codegym.mathclass.assignment.dto.response.BatchGenerateQuestionsResponse;
import com.codegym.mathclass.assignment.dto.response.BatchQuestionItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy Parser bóc tách dữ liệu phản hồi AI cho tính năng Sinh câu hỏi/đề bài tự động (BatchGenerateQuestionsResponse).
 */
@Component
public class AiQuestionResponseParser extends AbstractAiResponseParser<BatchGenerateQuestionsResponse> {

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("\"suggestedTitle\"\\s*:\\s*\"([\\s\\S]*?)(?:\"\\s*,|\"\\s*\\}|$)");
    private static final Pattern DESC_PATTERN =
            Pattern.compile("\"suggestedDescription\"\\s*:\\s*\"([\\s\\S]*?)(?:\"\\s*,|\"\\s*\\}|$)");
    private static final Pattern CONTENT_PATTERN =
            Pattern.compile("\"content\"\\s*:\\s*\"([\\s\\S]*?)(?:\"\\s*,|\"\\s*\\}|$)");

    public AiQuestionResponseParser(ObjectMapper objectMapper) {
        super(objectMapper, BatchGenerateQuestionsResponse.class);
    }

    @Override
    public boolean supports(AiResponseType type) {
        return type == AiResponseType.QUESTION;
    }

    @Override
    protected BatchGenerateQuestionsResponse parseFallbackRegex(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        String title = null;
        Matcher titleMatcher = TITLE_PATTERN.matcher(rawResponse);
        if (titleMatcher.find()) {
            title = titleMatcher.group(1).trim();
            while (title.endsWith("\\")) {
                title = title.substring(0, title.length() - 1).trim();
            }
        }

        String desc = null;
        Matcher descMatcher = DESC_PATTERN.matcher(rawResponse);
        if (descMatcher.find()) {
            desc = descMatcher.group(1).trim();
            while (desc.endsWith("\\")) {
                desc = desc.substring(0, desc.length() - 1).trim();
            }
        }

        List<BatchQuestionItem> questions = new ArrayList<>();
        Matcher contentMatcher = CONTENT_PATTERN.matcher(rawResponse);
        while (contentMatcher.find()) {
            String questionContent = contentMatcher.group(1).trim();
            while (questionContent.endsWith("\\")) {
                questionContent = questionContent.substring(0, questionContent.length() - 1).trim();
            }
            if (!questionContent.isBlank()) {
                BatchQuestionItem item = BatchQuestionItem.builder()
                        .content(questionContent)
                        .suggestedScore(BigDecimal.valueOf(1.0))
                        .build();
                questions.add(item);
            }
        }

        if (title == null && questions.isEmpty()) {
            return null;
        }

        return BatchGenerateQuestionsResponse.builder()
                .suggestedTitle(title != null ? title : "Bộ câu hỏi sinh từ AI")
                .suggestedDescription(desc != null ? desc : "")
                .questions(questions)
                .totalQuestions(questions.size())
                .build();
    }
}
