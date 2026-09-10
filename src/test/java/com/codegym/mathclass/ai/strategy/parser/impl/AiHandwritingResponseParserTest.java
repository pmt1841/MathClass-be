package com.codegym.mathclass.ai.strategy.parser.impl;

import com.codegym.mathclass.submission.dto.response.HandwritingLatexResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiHandwritingResponseParserTest {

    private AiHandwritingResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiHandwritingResponseParser(new ObjectMapper());
    }

    @Test
    @DisplayName("Parse thành công JSON nhận diện viết tay Lớp 1")
    void parse_StandardHandwritingJson_ReturnsValidDto() {
        String json = """
                {
                  "latex": "\\\\frac{a + b}{c}"
                }
                """;

        HandwritingLatexResponse response = parser.parse(json);

        assertThat(response).isNotNull();
        assertThat(response.getLatex()).contains("\\frac{a + b}{c}");
    }

    @Test
    @DisplayName("Parse Lớp 2 Markdown fence khi AI chỉ trả về mã KaTeX thuần")
    void parse_PureLatexText_ReturnsHandwritingResponse() {
        String rawText = "```latex\n\\int_{0}^{1} x^2 dx\n```";

        HandwritingLatexResponse response = parser.parse(rawText);

        assertThat(response).isNotNull();
        assertThat(response.getLatex()).contains("\\int_{0}^{1} x^2 dx");
    }
}
