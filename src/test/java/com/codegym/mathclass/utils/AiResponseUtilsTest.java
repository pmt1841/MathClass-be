package com.codegym.mathclass.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiResponseUtilsTest {

    @Test
    @DisplayName("extractCleanJson: bóc tách JSON object từ markdown block ```json")
    void extractCleanJson_markdownFenceObject_extractsJson() {
        String raw = "```json\n{\"shapeType\": \"CIRCLE\", \"radius\": 5}\n```";
        String extracted = AiResponseUtils.extractCleanJson(raw);
        assertThat(extracted).isEqualTo("{\"shapeType\": \"CIRCLE\", \"radius\": 5}");
    }

    @Test
    @DisplayName("extractCleanJson: bóc tách JSON object khi có text thừa bên ngoài")
    void extractCleanJson_textSurroundingObject_extractsInnerObject() {
        String raw = "Dưới đây là kết quả phân tích hình vẽ:\n{\"shapeType\": \"TRIANGLE\"}\nHy vọng hữu ích!";
        String extracted = AiResponseUtils.extractCleanJson(raw);
        assertThat(extracted).isEqualTo("{\"shapeType\": \"TRIANGLE\"}");
    }

    @Test
    @DisplayName("extractCleanJson: bóc tách JSON array khi phản hồi là array")
    void extractCleanJson_markdownFenceArray_extractsJsonArray() {
        String raw = "```json\n[{\"id\": 1}, {\"id\": 2}]\n```";
        String extracted = AiResponseUtils.extractCleanJson(raw);
        assertThat(extracted).isEqualTo("[{\"id\": 1}, {\"id\": 2}]");
    }

    @Test
    @DisplayName("extractCleanJson: trả về {} khi input null hoặc blank")
    void extractCleanJson_nullOrBlank_returnsEmptyObject() {
        assertThat(AiResponseUtils.extractCleanJson(null)).isEqualTo("{}");
        assertThat(AiResponseUtils.extractCleanJson("   ")).isEqualTo("{}");
    }

    @Test
    @DisplayName("stripMarkdownFences: loại bỏ code fence bất kỳ")
    void stripMarkdownFences_stripsMarkdownFences() {
        String raw = "```markdown\n# Tiêu đề\nNội dung\n```";
        String stripped = AiResponseUtils.stripMarkdownFences(raw);
        assertThat(stripped).isEqualTo("# Tiêu đề\nNội dung");

        assertThat(AiResponseUtils.stripMarkdownFences(null)).isEmpty();
        assertThat(AiResponseUtils.stripMarkdownFences("  ")).isEmpty();
    }
}
