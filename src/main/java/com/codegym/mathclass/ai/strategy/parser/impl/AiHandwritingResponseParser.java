package com.codegym.mathclass.ai.strategy.parser.impl;

import com.codegym.mathclass.ai.strategy.parser.AbstractAiResponseParser;
import com.codegym.mathclass.ai.strategy.parser.AiResponseType;
import com.codegym.mathclass.submission.dto.HandwritingLatexResponse;
import com.codegym.mathclass.utils.AiResponseUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy Parser bóc tách dữ liệu phản hồi AI cho tính năng Nhận diện bài làm viết tay/Canvas (HandwritingLatexResponse).
 */
@Component
public class AiHandwritingResponseParser extends AbstractAiResponseParser<HandwritingLatexResponse> {

    private static final Pattern LATEX_PATTERN =
            Pattern.compile("\"(?:latex|code|expression|content)\"\\s*:\\s*\"([\\s\\S]*?)(?:\"\\s*,|\"\\s*\\}|$)");

    public AiHandwritingResponseParser(ObjectMapper objectMapper) {
        super(objectMapper, HandwritingLatexResponse.class);
    }

    @Override
    public boolean supports(AiResponseType type) {
        return type == AiResponseType.HANDWRITING;
    }

    @Override
    protected HandwritingLatexResponse parseFallbackRegex(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        String latex = "";
        Matcher latexMatcher = LATEX_PATTERN.matcher(rawResponse);
        if (latexMatcher.find()) {
            latex = latexMatcher.group(1).trim();
            while (latex.endsWith("\\")) {
                latex = latex.substring(0, latex.length() - 1).trim();
            }
        } else {
            latex = AiResponseUtils.stripMarkdownFences(rawResponse);
        }

        if (latex.isBlank()) {
            return null;
        }

        return HandwritingLatexResponse.builder()
                .latex(latex)
                .rawAiOutput(rawResponse)
                .build();
    }
}
