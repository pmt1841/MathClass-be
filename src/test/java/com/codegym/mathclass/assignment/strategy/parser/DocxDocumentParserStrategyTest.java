package com.codegym.mathclass.assignment.strategy.parser;

import com.codegym.mathclass.storage.service.StorageService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DocxDocumentParserStrategyTest {

    @Mock
    private StorageService storageService;

    private DocxDocumentParserStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DocxDocumentParserStrategy(storageService);
    }

    @Test
    @DisplayName("supports trả về true cho file .docx")
    void testSupports() {
        assertTrue(strategy.supports("dethi.docx"));
        assertTrue(strategy.supports("DETHI.DOCX"));
        assertFalse(strategy.supports("dethi.pdf"));
        assertFalse(strategy.supports("dethi.txt"));
        assertFalse(strategy.supports(null));
    }

    @Test
    @DisplayName("parse chuyển đổi nội dung docx thành markdown")
    void testParse() throws Exception {
        byte[] docxBytes;
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            run.setText("Câu 1: Giải phương trình bậc 2 sau: x^2 - 5x + 6 = 0");
            doc.write(out);
            docxBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "dethi.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );

        DocumentParseResult result = strategy.parse(file);

        assertNotNull(result);
        assertTrue(result.content().contains("Câu 1: Giải phương trình bậc 2"));
    }
}
