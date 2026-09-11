package com.codegym.mathclass.assignment.strategy.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PdfDocumentParserStrategyTest {

    private PdfDocumentParserStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PdfDocumentParserStrategy();
    }

    @Test
    @DisplayName("supports trả về true cho file .pdf")
    void testSupports() {
        assertTrue(strategy.supports("exam.pdf"));
        assertTrue(strategy.supports("EXAM.PDF"));
        assertFalse(strategy.supports("exam.docx"));
        assertFalse(strategy.supports("exam.txt"));
        assertFalse(strategy.supports(null));
    }

    @Test
    @DisplayName("parse trích xuất văn bản từ tệp PDF hợp lệ")
    void testParse() throws Exception {
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(100, 700);
                stream.showText("Cau 1: Tinh dao ham cua ham so");
                stream.endText();
            }

            doc.save(out);
            pdfBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "exam.pdf",
                "application/pdf",
                pdfBytes
        );

        DocumentParseResult result = strategy.parse(file);

        assertNotNull(result);
        assertTrue(result.content().contains("Cau 1: Tinh dao ham cua ham so"));
        assertTrue(result.images().isEmpty());
    }
}
