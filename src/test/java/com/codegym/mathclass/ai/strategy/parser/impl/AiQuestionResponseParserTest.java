package com.codegym.mathclass.ai.strategy.parser.impl;

import com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuestionResponseParserTest {

    private AiQuestionResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiQuestionResponseParser(new ObjectMapper());
    }

    @Test
    @DisplayName("Parse thành công danh sách câu hỏi sinh tự động Lớp 1")
    void parse_ValidQuestionBatchJson_ReturnsResponseDto() {
        String json = """
                {
                  "suggestedTitle": "Đề kiểm tra Đại số 9",
                  "suggestedDescription": "Ôn tập phương trình bậc hai",
                  "questions": [
                    { "content": "Giải phương trình $x^2 - 4 = 0$", "score": 2.0 },
                    { "content": "Giải phương trình $x^2 - 5x + 6 = 0$", "score": 3.0 }
                  ]
                }
                """;

        BatchGenerateQuestionsResponse response = parser.parse(json);

        assertThat(response).isNotNull();
        assertThat(response.getSuggestedTitle()).isEqualTo("Đề kiểm tra Đại số 9");
        assertThat(response.getQuestions()).hasSize(2);
        assertThat(response.getQuestions().get(0).getContent()).contains("x^2 - 4 = 0");
    }
}
