package com.codegym.mathclass.ai.strategy.parser.impl;

import com.codegym.mathclass.ai.strategy.parser.exception.AiParsingException;
import com.codegym.mathclass.submission.dto.response.AiGradingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGradingResponseParserTest {

    private AiGradingResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiGradingResponseParser(new ObjectMapper());
    }

    @Test
    @DisplayName("Parse thành công JSON chuẩn Lớp 1 cho AiGradingResponse")
    void parse_StandardJson_ReturnsValidDto() {
        String json = """
                ```json
                {
                  "suggestedScore": 8.5,
                  "draftFeedback": "Bài làm tốt, trình bày sạch sẽ."
                }
                ```
                """;

        AiGradingResponse response = parser.parse(json);

        assertThat(response).isNotNull();
        assertThat(response.getSuggestedScore()).isEqualTo(8.5);
        assertThat(response.getDraftFeedback()).isEqualTo("Bài làm tốt, trình bày sạch sẽ.");
    }

    @Test
    @DisplayName("Parse Lớp 2 (Regex Fallback) khi JSON bị lỗi cú pháp thiếu dấu ngoặc kép")
    void parse_MalformedJson_FallbackToRegexSucceeds() {
        String malformedJson = """
                Dưới đây là kết quả đánh giá:
                "suggestedScore": 9.0,
                "draftFeedback": "Học sinh áp dụng đúng công thức \\\\frac{a}{b}."
                """;

        AiGradingResponse response = parser.parse(malformedJson);

        assertThat(response).isNotNull();
        assertThat(response.getSuggestedScore()).isEqualTo(9.0);
        assertThat(response.getDraftFeedback()).contains("Học sinh áp dụng đúng công thức");
    }

    @Test
    @DisplayName("Ném AiParsingException khi chuỗi phản hồi AI bị rỗng hoặc null")
    void parse_EmptyInput_ThrowsAiParsingException() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(AiParsingException.class)
                .hasMessageContaining("rỗng hoặc null");

        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(AiParsingException.class)
                .hasMessageContaining("rỗng hoặc null");
    }
}
