package com.codegym.mathclass.ai.strategy.parser.impl;

import com.codegym.mathclass.classroom.dto.response.AiRemarkJsonResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRemarkResponseParserTest {

    private AiRemarkResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiRemarkResponseParser(new ObjectMapper());
    }

    @Test
    @DisplayName("Parse thành công JSON nhận xét tiến độ học sinh Lớp 1")
    void parse_StandardRemarkJson_ReturnsValidDto() {
        String json = """
                {
                  "strengths": "Nắm vững lý thuyết phương trình bậc hai",
                  "weaknesses": "Cần tính toán cẩn thận hơn khi tính Delta",
                  "generalAssessment": "Học sinh có nhiều tiến bộ trong tuần qua."
                }
                """;

        AiRemarkJsonResponse result = parser.parse(json);

        assertThat(result).isNotNull();
        assertThat(result.getStrengths()).isEqualTo("Nắm vững lý thuyết phương trình bậc hai");
        assertThat(result.getWeaknesses()).isEqualTo("Cần tính toán cẩn thận hơn khi tính Delta");
        assertThat(result.getGeneralAssessment()).isEqualTo("Học sinh có nhiều tiến bộ trong tuần qua.");
    }

    @Test
    @DisplayName("Parse Lớp 2 Regex khi AI trả về văn bản dẫn nhập kèm nhận xét")
    void parse_RawTextWithRemark_FallbackRegexSucceeds() {
        String rawText = """
                Đánh giá kết quả học tập từ AI:
                "strengths": "Tự giác hoàn thành bài tập về nhà đúng hạn",
                "weaknesses": "Cần trình bày các bước giải hình học chi tiết hơn",
                "generalAssessment": "Học sinh giữ vững phong độ học tập tốt."
                """;

        AiRemarkJsonResponse result = parser.parse(rawText);

        assertThat(result).isNotNull();
        assertThat(result.getStrengths()).contains("Tự giác hoàn thành");
        assertThat(result.getWeaknesses()).contains("hình học chi tiết hơn");
        assertThat(result.getGeneralAssessment()).contains("phong độ học tập tốt");
    }
}
