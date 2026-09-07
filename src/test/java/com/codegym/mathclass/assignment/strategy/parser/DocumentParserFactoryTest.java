package com.codegym.mathclass.assignment.strategy.parser;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DocumentParserFactoryTest {

    @Mock
    private StorageService storageService;

    private DocumentParserFactory factory;

    @BeforeEach
    void setUp() {
        TxtDocumentParserStrategy txtParser = new TxtDocumentParserStrategy();
        DocxDocumentParserStrategy docxParser = new DocxDocumentParserStrategy(storageService);
        PdfDocumentParserStrategy pdfParser = new PdfDocumentParserStrategy();

        factory = new DocumentParserFactory(List.of(txtParser, docxParser, pdfParser));
    }

    @Test
    @DisplayName("Chọn đúng TxtDocumentParserStrategy khi file có đuôi .txt")
    void getParser_TxtFile_ReturnsTxtParser() {
        DocumentParserStrategy parser = factory.getParser("de_bai.txt");
        assertNotNull(parser);
        assertTrue(parser instanceof TxtDocumentParserStrategy);
    }

    @Test
    @DisplayName("Chọn đúng DocxDocumentParserStrategy khi file có đuôi .docx")
    void getParser_DocxFile_ReturnsDocxParser() {
        DocumentParserStrategy parser = factory.getParser("de_bai_toan.DOCX");
        assertNotNull(parser);
        assertTrue(parser instanceof DocxDocumentParserStrategy);
    }

    @Test
    @DisplayName("Chọn đúng PdfDocumentParserStrategy khi file có đuôi .pdf")
    void getParser_PdfFile_ReturnsPdfParser() {
        DocumentParserStrategy parser = factory.getParser("de_thi.pdf");
        assertNotNull(parser);
        assertTrue(parser instanceof PdfDocumentParserStrategy);
    }

    @Test
    @DisplayName("Ném BadRequestException khi đuôi file không được hỗ trợ hoặc filename bị null/trống")
    void getParser_UnsupportedExtension_ThrowsBadRequestException() {
        assertThrows(BadRequestException.class, () -> factory.getParser("excel_data.xlsx"));
        assertThrows(BadRequestException.class, () -> factory.getParser("archive.zip"));
        assertThrows(BadRequestException.class, () -> factory.getParser(null));
        assertThrows(BadRequestException.class, () -> factory.getParser("   "));
    }
}
