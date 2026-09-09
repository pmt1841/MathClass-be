package com.codegym.mathclass.ai.strategy.parser.impl;

import com.codegym.mathclass.submission.dto.response.StudentHintResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiHintResponseParserTest {

    private AiHintResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiHintResponseParser(new ObjectMapper());
    }

    @Test
    @DisplayName("Parse thành công JSON gợi ý bài giải từng bước Lớp 1")
    void parse_StandardHintJson_ReturnsValidDto() {
        String json = """
                {
                  "hintText": "Hãy thử nhóm các hạng tử chứa x sang một vế.",
                  "isFinalStep": false,
                  "stepIndex": 1
                }
                """;

        StudentHintResponse response = parser.parse(json);

        assertThat(response).isNotNull();
        assertThat(response.getHintContent()).isEqualTo("Hãy thử nhóm các hạng tử chứa x sang một vế.");
        assertThat(response.getHintNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Parse Lớp 2 Regex khi AI trả về kèm văn bản dẫn nhập")
    void parse_RawTextWithExplanation_FallbackRegexSucceeds() {
        String rawText = """
                Gợi ý dành cho bạn:
                "hintText": "Áp dụng hằng đẳng thức $a^2 - b^2 = (a-b)(a+b)$",
                "isFinalStep": true
                """;

        StudentHintResponse response = parser.parse(rawText);

        assertThat(response).isNotNull();
        assertThat(response.getHintContent()).contains("hằng đẳng thức");
    }
}
