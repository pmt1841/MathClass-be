package com.codegym.mathclass.assignment.strategy.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TxtDocumentParserStrategyTest {

    private TxtDocumentParserStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new TxtDocumentParserStrategy();
    }

    @Test
    @DisplayName("supports trả về true cho file đuôi .txt")
    void testSupports() {
        assertTrue(strategy.supports("assignment.txt"));
        assertTrue(strategy.supports("ASSIGNMENT.TXT"));
        assertFalse(strategy.supports("document.docx"));
        assertFalse(strategy.supports("file.pdf"));
        assertFalse(strategy.supports(null));
    }

    @Test
    @DisplayName("parse đọc chính xác nội dung file text UTF-8")
    void testParse() throws Exception {
        String originalText = "Câu 1: Cho hàm số y = f(x) liên tục trên R.\nTìm f'(x).";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                originalText.getBytes(StandardCharsets.UTF_8)
        );

        DocumentParseResult result = strategy.parse(file);

        assertNotNull(result);
        assertEquals(originalText, result.content());
        assertTrue(result.images().isEmpty());
    }
}
