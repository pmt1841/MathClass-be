package com.codegym.mathclass.ai.strategy.parser;

import com.codegym.mathclass.ai.strategy.parser.impl.AiGradingResponseParser;
import com.codegym.mathclass.ai.strategy.parser.impl.AiHintResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResponseParserFactoryTest {

    private AiResponseParserFactory factory;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiGradingResponseParser gradingParser = new AiGradingResponseParser(objectMapper);
        AiHintResponseParser hintParser = new AiHintResponseParser(objectMapper);

        factory = new AiResponseParserFactory(List.of(gradingParser, hintParser));
    }

    @Test
    @DisplayName("Lấy đúng AiGradingResponseParser khi truyền AiResponseType.GRADING")
    void getParser_GradingType_ReturnsGradingParser() {
        AiResponseParser<?> parser = factory.getParser(AiResponseType.GRADING);
        assertThat(parser).isNotNull();
        assertThat(parser).isInstanceOf(AiGradingResponseParser.class);
    }

    @Test
    @DisplayName("Lấy đúng AiHintResponseParser khi truyền AiResponseType.HINT")
    void getParser_HintType_ReturnsHintParser() {
        AiResponseParser<?> parser = factory.getParser(AiResponseType.HINT);
        assertThat(parser).isNotNull();
        assertThat(parser).isInstanceOf(AiHintResponseParser.class);
    }

    @Test
    @DisplayName("Ném IllegalArgumentException khi loại AiResponseType chưa đăng ký")
    void getParser_UnregisteredType_ThrowsException() {
        assertThatThrownBy(() -> factory.getParser(AiResponseType.QUESTION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy AiResponseParser phù hợp cho type: QUESTION");
    }
}
