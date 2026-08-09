package com.codegym.mathclass.aiconfig.validator;

import com.codegym.mathclass.exception.InvalidVariableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SystemPromptValidatorTest {

    private SystemPromptValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SystemPromptValidator();
    }

    @Test
    @DisplayName("TC-VAL-01: Trích xuất chính xác tất cả các biến dạng {{variable_name}}")
    void testExtractVariables() {
        String content = "Môn học {{subject}} cho học sinh {{grade_level}} câu hỏi {{question_content}}";
        Set<String> extracted = validator.extractVariables(content);

        assertEquals(3, extracted.size());
        assertTrue(extracted.contains("subject"));
        assertTrue(extracted.contains("grade_level"));
        assertTrue(extracted.contains("question_content"));
    }

    @Test
    @DisplayName("TC-VAL-02: Validate thành công khi tất cả các biến đều thuộc allowedVariables")
    void testValidateVariables_Success() {
        String content = "Môn {{subject}} cấp {{grade_level}}";
        List<String> allowed = List.of("subject", "grade_level", "student_answer");

        assertDoesNotThrow(() -> validator.validateVariables(content, allowed));
    }

    @Test
    @DisplayName("TC-VAL-03: Thất bại khi prompt chứa biến không nằm trong allowedVariables")
    void testValidateVariables_ThrowsInvalidVariableException() {
        String content = "Môn {{subject}} và biến lạ {{invalid_var}}";
        List<String> allowed = List.of("subject", "grade_level");

        InvalidVariableException exception = assertThrows(
                InvalidVariableException.class,
                () -> validator.validateVariables(content, allowed)
        );

        assertTrue(exception.getMessage().contains("invalid_var"));
    }

    @Test
    @DisplayName("TC-VAL-04: Parse chuỗi allowedVariables dạng phẩy thành danh sách List String")
    void testParseAllowedVariables() {
        String input = "grade_level, subject, student_answer ";
        List<String> parsed = validator.parseAllowedVariables(input);

        assertEquals(3, parsed.size());
        assertEquals("grade_level", parsed.get(0));
        assertEquals("subject", parsed.get(1));
        assertEquals("student_answer", parsed.get(2));
    }
}
